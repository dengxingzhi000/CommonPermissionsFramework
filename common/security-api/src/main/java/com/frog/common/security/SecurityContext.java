package com.frog.common.security;

import java.util.UUID;

/**
 * Security Context Interface
 *
 * <p>Provides abstraction for security context access, avoiding direct dependency
 * on Spring Security or web layer components from data/service layers.
 *
 * <p>This follows the Dependency Inversion Principle (DIP):
 * - Data layer (common/data) depends on this interface (common/core)
 * - Web layer (common/web) provides implementation using Spring Security
 *
 * <p>Benefits:
 * - Decouples data layer from web layer
 * - Enables independent testing with mock implementations
 * - Allows different security implementations (Spring Security, custom, etc.)
 *
 * @author Refactored
 * @version 2.0
 * @since 2025-12-12
 */
public interface SecurityContext {

    /**
     * Get current authenticated user ID.
     *
     * @return User ID (UUID), or null if not authenticated
     */
    UUID getCurrentUserId();

    /**
     * Get current authenticated user's department ID.
     *
     * @return Department ID (UUID), or null if not authenticated or no department
     */
    UUID getCurrentDeptId();

    /**
     * Get current user's data scope level.
     *
     * <p>Data scope levels:
     * <ul>
     *   <li>1 - ALL: No filtering, access all data</li>
     *   <li>2 - CUSTOM: Custom rules from database</li>
     *   <li>3 - DEPT: Current department only</li>
     *   <li>4 - DEPT_AND_CHILDREN: Current department and sub-departments</li>
     *   <li>5 - SELF: Current user only (default)</li>
     * </ul>
     *
     * @return Data scope level (1-5), defaults to 5 (SELF) if not available
     */
    Integer getDataScopeLevel();

    /**
     * Check if current user is authenticated.
     *
     * @return true if authenticated, false otherwise
     */
    boolean isAuthenticated();

    /**
     * Get current authenticated username.
     *
     * @return Username (String), or null if not authenticated
     */
    default String getCurrentUsername() {
        return null;
    }

    /**
     * Check if current user has a specific role.
     *
     * @param role Role name (e.g., "ROLE_ADMIN")
     * @return true if user has the role, false otherwise
     */
    default boolean hasRole(String role) {
        return false;
    }
}