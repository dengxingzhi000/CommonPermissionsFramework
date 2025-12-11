package com.frog.system.rpc;

import com.frog.common.dto.user.UserInfo;
import com.frog.system.service.ISysUserService;
import com.frog.system.api.UserDubboService;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@DubboService
@Component
public class UserDubboServiceImpl implements UserDubboService {
    private final ISysUserService sysUserService;

    public UserDubboServiceImpl(ISysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @Override
    public UserInfo getUserInfo(UUID userId) {
        return sysUserService.getUserInfo(userId);
    }

    @Override
    public void updateLastLogin(UUID userId, String ipAddress, LocalDateTime loginTime) {
        // Delegate to existing method; ignore loginTime if not used.
        sysUserService.updateLastLogin(userId, ipAddress);
    }
}

