package com.frog.system.api;

import com.frog.common.dto.user.UserDTO;
import com.frog.common.dto.user.UserInfo;
import com.frog.common.web.domain.SecurityUser;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Dubbo API for internal high-speed RPC between services.
 * Provides high-performance user-related operations for authentication and authorization.
 */
public interface UserDubboService {

    /**
     * Get user by username for authentication.
     * Used by Spring Security's UserDetailsService.
     *
     * @param username the username to search for
     * @return SecurityUser containing authentication information, or null if not found
     */
    SecurityUser getUserByUsername(String username);

    /**
     * Get user roles by userId.
     * Used for token refresh and authentication.
     *
     * @param userId the user ID
     * @return set of role codes
     */
    Set<String> getUserRoles(UUID userId);

    /**
     * Get user permissions by userId.
     * Used for token refresh and authentication.
     *
     * @param userId the user ID
     * @return set of permission codes
     */
    Set<String> getUserPermissions(UUID userId);

    /**
     * Fetch user info by userId.
     *
     * @param userId the user ID
     * @return UserInfo DTO
     */
    UserInfo getUserInfo(UUID userId);

    /**
     * Update last login info.
     *
     * @param userId the user ID
     * @param ipAddress the login IP address
     * @param loginTime the login timestamp
     */
    void updateLastLogin(UUID userId, String ipAddress, LocalDateTime loginTime);

    /**
     * Get user name by userId.
     *
     * @param userId the user ID
     * @return UserDTO
     */
    UserDTO getUserById(UUID userId);

    /**
     * Get user roles by userId (alias for getUserRoles).
     * Used for token refresh and authentication.
     *
     * @param userId the user ID
     * @return set of role codes
     */
    Set<String> findRolesByUserId(UUID userId);

    /**
     * Get user permissions by userId (alias for getUserPermissions).
     * Used for token refresh and authentication.
     *
     * @param userId the user ID
     * @return set of permission codes
     */
    Set<String> findPermissionsByUserId(UUID userId);
}

