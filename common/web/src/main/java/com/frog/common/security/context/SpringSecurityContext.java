package com.frog.common.security.context;

import com.frog.common.security.SecurityContext;
import com.frog.common.web.domain.SecurityUser;
import com.frog.common.web.util.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Spring Security implementation of SecurityContext interface.
 *
 * <p>This class bridges the gap between:
 * - Generic SecurityContext interface (common/core) - used by data/service layers
 * - Spring Security framework (common/web) - actual security implementation
 *
 * <p>Architecture Benefits:
 * - Data layer (DataScopeAspect) depends on interface, not this implementation
 * - Follows Dependency Inversion Principle (DIP)
 * - Enables testing with mock SecurityContext
 * - Allows swapping security implementations without changing data layer
 *
 * @author Refactored
 * @version 2.0
 * @since 2025-12-12
 */
@Component
@Slf4j
public class SpringSecurityContext implements SecurityContext {

    @Override
    public UUID getCurrentUserId() {
        SecurityUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser != null) {
            return currentUser.getUserId();
        }

        // Fallback: try to parse from JWT or OAuth2 claims
        return SecurityUtils.getCurrentUserUuid().orElse(null);
    }

    @Override
    public UUID getCurrentDeptId() {
        SecurityUser currentUser = SecurityUtils.getCurrentUser();
        return currentUser != null ? currentUser.getDeptId() : null;
    }

    @Override
    public Integer getDataScopeLevel() {
        SecurityUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser != null && currentUser.getAccountType() != null) {
            return currentUser.getAccountType();
        }

        // Default to SELF (level 5) for maximum security
        // If data scope level is unknown, restrict to user's own data only
        return 5;
    }

    @Override
    public boolean isAuthenticated() {
        return SecurityUtils.isAuthenticated();
    }

    @Override
    public String getCurrentUsername() {
        SecurityUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser != null) {
            return currentUser.getUsername();
        }

        // Fallback: try to get from Spring Security context
        return SecurityUtils.getCurrentUsername().orElse(null);
    }

    @Override
    public boolean hasRole(String role) {
        SecurityUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser != null && currentUser.getRoles() != null) {
            return currentUser.getRoles().contains(role);
        }

        // Fallback: check authorities from Spring Security
        return SecurityUtils.getAuthorities().contains(role);
    }
}