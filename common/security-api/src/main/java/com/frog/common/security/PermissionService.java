package com.frog.common.security;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Permission Service Interface
 *
 * <p>Provides abstraction for permission queries, avoiding direct dependency
 * on specific business modules (system/api) from common infrastructure modules.
 *
 * <p>This follows the Dependency Inversion Principle (DIP):
 * - Common/web depends on this interface (common/core)
 * - Business modules (system/auth) provide implementations (Dubbo/Feign)
 *
 * <p>Benefits:
 * - Decouples common modules from business modules
 * - Enables different implementations (Dubbo, Feign, REST, database)
 * - Allows independent testing with mock implementations
 * - Promotes reusability of common modules across projects
 *
 * @author Refactored
 * @version 2.0
 * @since 2025-12-12
 */
public interface PermissionService {

    /**
     * Find required permission codes by API URL and HTTP method.
     *
     * <p>This method is typically used by security filters to determine
     * which permissions are required to access a specific endpoint.
     *
     * @param url API URL path (e.g., "/api/users")
     * @param method HTTP method (e.g., "GET", "POST", "DELETE")
     * @return List of required permission codes (e.g., ["user:read", "admin:access"])
     *         Empty list if no permissions required or URL not found
     * @throws PermissionServiceException if permission lookup fails (fail-closed)
     */
    List<String> findPermissionsByUrl(String url, String method);

    /**
     * Get all effective permission codes for a user.
     *
     * <p>Returns the complete set of permissions the user has, based on their roles
     * and assignments. This is typically used for authorization decisions.
     *
     * @param userId User ID (UUID)
     * @return Set of permission codes the user has (e.g., ["user:read", "user:write"])
     *         Empty set if user has no permissions or not found
     * @throws PermissionServiceException if permission lookup fails (fail-closed)
     */
    Set<String> findAllPermissionsByUserId(UUID userId);

    /**
     * Check if a user has a specific permission.
     *
     * <p>Default implementation checks if the permission exists in user's permission set.
     * Implementations can override for optimized single-permission checks.
     *
     * @param userId User ID (UUID)
     * @param permissionCode Permission code to check (e.g., "user:delete")
     * @return true if user has the permission, false otherwise
     * @throws PermissionServiceException if permission lookup fails (fail-closed)
     */
    default boolean hasPermission(UUID userId, String permissionCode) {
        Set<String> userPermissions = findAllPermissionsByUserId(userId);
        return userPermissions != null && userPermissions.contains(permissionCode);
    }

    /**
     * Check if a user has any of the specified permissions.
     *
     * <p>Returns true if user has at least one of the required permissions.
     *
     * @param userId User ID (UUID)
     * @param permissionCodes Required permission codes (OR logic)
     * @return true if user has any of the permissions, false otherwise
     * @throws PermissionServiceException if permission lookup fails (fail-closed)
     */
    default boolean hasAnyPermission(UUID userId, Set<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return true; // No permissions required
        }

        Set<String> userPermissions = findAllPermissionsByUserId(userId);
        if (userPermissions == null || userPermissions.isEmpty()) {
            return false;
        }

        return permissionCodes.stream().anyMatch(userPermissions::contains);
    }

    /**
     * Check if a user has all of the specified permissions.
     *
     * <p>Returns true only if user has all required permissions.
     *
     * @param userId User ID (UUID)
     * @param permissionCodes Required permission codes (AND logic)
     * @return true if user has all permissions, false otherwise
     * @throws PermissionServiceException if permission lookup fails (fail-closed)
     */
    default boolean hasAllPermissions(UUID userId, Set<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return true; // No permissions required
        }

        Set<String> userPermissions = findAllPermissionsByUserId(userId);
        if (userPermissions == null || userPermissions.isEmpty()) {
            return false;
        }

        return userPermissions.containsAll(permissionCodes);
    }

    /**
     * Exception thrown when permission service operations fail.
     *
     * <p>This exception is used to implement fail-closed security pattern:
     * when permission lookup fails, access should be denied rather than granted.
     */
    class PermissionServiceException extends RuntimeException {
        public PermissionServiceException(String message) {
            super(message);
        }

        public PermissionServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}