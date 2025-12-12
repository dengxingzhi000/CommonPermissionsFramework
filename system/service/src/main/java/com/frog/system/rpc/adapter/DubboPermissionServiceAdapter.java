package com.frog.system.rpc.adapter;

import com.frog.common.security.PermissionService;
import com.frog.system.api.PermissionDubboService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dubbo-backed implementation for PermissionService interface.
 *
 * <p>REFACTORED: Moved from common/web to system/service module.
 * This follows proper layering - business module provides implementation,
 * infrastructure module (common/web) depends on interface.
 *
 * <p>SECURITY: Implements fail-closed pattern - throws exception on service failure
 * to prevent unauthorized access due to permission check failures.
 *
 * <p>Architecture Benefits:
 * - Common modules no longer depend on business modules
 * - Follows Dependency Inversion Principle (DIP)
 * - Enables different PermissionService implementations per project
 *
 * @author Refactored from DubboPermissionAccess
 * @version 2.0
 * @since 2025-12-12
 */
@Component
@Primary
@ConditionalOnClass(DubboReference.class)
@Slf4j
public class DubboPermissionServiceAdapter implements PermissionService {

    @DubboReference
    private PermissionDubboService permissionDubboService;

    private final MeterRegistry meterRegistry;

    public DubboPermissionServiceAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Finds required permissions for a given URL and HTTP method via Dubbo.
     *
     * <p>SECURITY: Fail-closed - throws exception if permission lookup fails.
     * This prevents granting access when permission service is unavailable.
     *
     * @throws PermissionServiceException if permission lookup fails
     */
    @Override
    public List<String> findPermissionsByUrl(String url, String method) {
        try {
            List<String> permissions = permissionDubboService.findPermissionsByUrl(url, method);
            meterRegistry.counter("security.permissions.dubbo.lookup.success").increment();
            log.debug("Permission lookup success via Dubbo: url={}, method={}, permissions={}",
                     url, method, permissions);
            return permissions != null ? permissions : List.of();

        } catch (Exception ex) {
            meterRegistry.counter("security.permissions.dubbo.lookup.fail").increment();
            log.error("SECURITY: Permission lookup failed via Dubbo - DENYING ACCESS. " +
                     "url={}, method={}", url, method, ex);

            // FAIL-CLOSED: Throw exception to deny access when permission check fails
            throw new PermissionServiceException(
                "Permission service unavailable via Dubbo - access denied as safety measure", ex);
        }
    }

    /**
     * Finds all permissions for a given user via Dubbo.
     *
     * <p>SECURITY: Fail-closed - throws exception if permission lookup fails.
     *
     * @throws PermissionServiceException if permission lookup fails
     */
    @Override
    public Set<String> findAllPermissionsByUserId(UUID userId) {
        try {
            Set<String> perms = permissionDubboService.findAllPermissionsByUserId(userId);
            meterRegistry.counter("security.permissions.dubbo.user.success").increment();
            log.debug("User permission lookup success via Dubbo: userId={}, count={}",
                     userId, perms != null ? perms.size() : 0);
            return perms != null ? perms : Set.of();

        } catch (Exception ex) {
            meterRegistry.counter("security.permissions.dubbo.user.fail").increment();
            log.error("SECURITY: User permission lookup failed via Dubbo - DENYING ACCESS. " +
                     "userId={}", userId, ex);

            // FAIL-CLOSED: Throw exception to deny access when permission check fails
            throw new PermissionServiceException(
                "Permission service unavailable via Dubbo - access denied as safety measure", ex);
        }
    }
}