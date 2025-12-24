package com.frog.common.uaa.service;

import com.frog.common.web.domain.SecurityUser;
import com.frog.system.api.UserDubboService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Custom UserDetailsService Implementation
 * Uses Dubbo RPC for high-performance user authentication
 *
 * @author Deng
 * @version 2.0
 * createData 2025/10/24 14:36
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {
    private final UserDubboService userDubboService;

    @Override
    @NonNull
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        // 通过 Dubbo 高性能 RPC 获取用户信息（包含密码、角色、权限）
        SecurityUser user = userDubboService.getUserByUsername(username);

        if (user == null) {
            log.warn("User not found: {}", username);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        log.debug("User loaded: {}, roles: {}, permissions: {}",
                username, user.getRoles().size(), user.getPermissions().size());

        return user;
    }
}
