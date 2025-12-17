package com.frog.common.uaa.service;

import com.frog.common.response.ApiResponse;
import com.frog.common.web.domain.SecurityUser;
import com.frog.common.feign.client.SysUserServiceClient;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 *
 *
 * @author Deng
 * createData 2025/10/24 14:36
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {
    private final SysUserServiceClient systemUserClient;

    @Override
    @NonNull
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        // 从 system服务获取用户信息
        ApiResponse<SecurityUser> result = systemUserClient.getUserByUsername(username);

        if (result == null || result.data() == null) {
            log.warn("User not found: {}", username);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        SecurityUser userAuth = result.data();

        // 获取用户权限
        var rolesResult = systemUserClient.getUserRoles(userAuth.getUserId());
        var permissionsResult = systemUserClient.getUserPermissions(userAuth.getUserId());

        var authorities = rolesResult.data().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        authorities.addAll(permissionsResult.data().stream()
                .map(SimpleGrantedAuthority::new)
                .toList());

        return User.builder()
                .username(userAuth.getUsername())
                .password(userAuth.getPassword())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(userAuth.getStatus() == 2)
                .credentialsExpired(false)
                .disabled(userAuth.getStatus() != 1)
                .build();
    }
}
