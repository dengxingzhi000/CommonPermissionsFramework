package com.frog.common.access;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.frog.common.feign.client.SysPermissionServiceClient;
import com.frog.common.response.ApiResponse;
import com.frog.common.security.PermissionService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Feign-backed fallback implementation for PermissionService.
 *
 * <p>REFACTORED: Now implements PermissionService interface (common/core)
 * instead of PermissionAccessPort. This decouples common/web from system/api.
 *
 * <p>SECURITY: Implements fail-closed pattern with Sentinel circuit breaking
 * - Uses Sentinel SphU for manual resource protection
 * - Throws exception on service failure or circuit open
 * - Metrics tracking for success/failure rates
 *
 * <p>Sentinel Resources:
 * - "permission:findByUrl" - Permission lookup by URL
 * - "permission:findByUserId" - Permission lookup by user ID
 *
 * <p>This implementation is used as a fallback when Dubbo is not available.
 * It's conditionally created only if no other PermissionService bean exists.
 *
 * @author Refactored
 * @version 2.0
 * @since 2025-12-12
 */
@Component
@ConditionalOnMissingBean(PermissionService.class)
@Slf4j
public class FeignPermissionAccess implements PermissionService {
    private final SysPermissionServiceClient permissionServiceClient;
    private final MeterRegistry meterRegistry;

    public FeignPermissionAccess(SysPermissionServiceClient permissionServiceClient,
                                 MeterRegistry meterRegistry) {
        this.permissionServiceClient = permissionServiceClient;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Finds required permissions for a given URL and HTTP method via Feign.
     *
     * <p>SECURITY: Fail-closed with Sentinel protection
     * - Throws exception if permission lookup fails
     * - Throws exception if Sentinel circuit is open
     *
     * @throws PermissionServiceException if permission lookup fails or circuit is open
     */
    @Override
    public List<String> findPermissionsByUrl(String url, String method) {
        try (Entry entry = SphU.entry("permission:findByUrl")) {
            List<String> permissions = permissionServiceClient.findPermissionsByUrl(url, method);
            meterRegistry.counter("security.permissions.lookup.success").increment();
            log.debug("Permission lookup success via Feign: url={}, method={}, permissions={}",
                     url, method, permissions);
            return permissions != null ? permissions : List.of();

        } catch (BlockException ex) {
            // Sentinel circuit is open - deny access
            meterRegistry.counter("security.permissions.lookup.blocked").increment();
            log.error("SECURITY: Permission lookup BLOCKED by Sentinel - DENYING ACCESS. " +
                     "url={}, method={}", url, method, ex);
            throw new PermissionServiceException(
                "Permission service circuit open (rate limit/degraded) - access denied as safety measure", ex);

        } catch (Exception ex) {
            meterRegistry.counter("security.permissions.lookup.fail").increment();
            log.error("SECURITY: Permission lookup failed via Feign - DENYING ACCESS. " +
                     "url={}, method={}", url, method, ex);

            // FAIL-CLOSED: Throw exception to deny access when permission check fails
            throw new PermissionServiceException(
                "Permission service unavailable via Feign - access denied as safety measure", ex);
        }
    }

    /**
     * Finds all permissions for a given user via Feign.
     *
     * <p>SECURITY: Fail-closed with Sentinel protection
     * - Throws exception if permission lookup fails
     * - Throws exception if Sentinel circuit is open
     *
     * @throws PermissionServiceException if permission lookup fails or circuit is open
     */
    @Override
    public Set<String> findAllPermissionsByUserId(UUID userId) {
        try (Entry entry = SphU.entry("permission:findByUserId")) {
            ApiResponse<Set<String>> resp = permissionServiceClient.getUserPermissions(userId);
            Set<String> perms = resp != null ? resp.data() : null;
            meterRegistry.counter("security.permissions.user.success").increment();
            log.debug("User permission lookup success via Feign: userId={}, count={}",
                     userId, perms != null ? perms.size() : 0);
            return perms != null ? perms : Set.of();

        } catch (BlockException ex) {
            // Sentinel circuit is open - deny access
            meterRegistry.counter("security.permissions.user.blocked").increment();
            log.error("SECURITY: User permission lookup BLOCKED by Sentinel - DENYING ACCESS. " +
                     "userId={}", userId, ex);
            throw new PermissionServiceException(
                "Permission service circuit open (rate limit/degraded) - access denied as safety measure", ex);

        } catch (Exception ex) {
            meterRegistry.counter("security.permissions.user.fail").increment();
            log.error("SECURITY: User permission lookup failed via Feign - DENYING ACCESS. " +
                     "userId={}", userId, ex);

            // FAIL-CLOSED: Throw exception to deny access when permission check fails
            throw new PermissionServiceException(
                "Permission service unavailable via Feign - access denied as safety measure", ex);
        }
    }
}
