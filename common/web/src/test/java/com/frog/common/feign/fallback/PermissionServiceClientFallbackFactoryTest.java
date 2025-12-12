package com.frog.common.feign.fallback;

import com.frog.common.dto.permission.PermissionDTO;
import com.frog.common.feign.client.SysPermissionServiceClient;
import com.frog.common.response.ApiResponse;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionServiceClientFallbackFactoryTest {

    @Test
    void fallbackReturnsEmptyPermissionsOnTimeout() {
        PermissionServiceClientFallbackFactory factory = new PermissionServiceClientFallbackFactory();
        SysPermissionServiceClient client = factory.create(new SocketTimeoutException("timeout"));

        ApiResponse<Set<String>> resp = client.getUserPermissions(UUID.randomUUID());
        assertEquals(200, resp.code());
        assertTrue(resp.data().isEmpty());
    }

    @Test
    void fallbackReturns503ForPermissionDetails() {
        PermissionServiceClientFallbackFactory factory = new PermissionServiceClientFallbackFactory();
        SysPermissionServiceClient client = factory.create(new SocketTimeoutException("timeout"));

        ApiResponse<PermissionDTO> resp = client.getPermissionById(UUID.randomUUID());
        assertEquals(503, resp.code());
        assertEquals("权限服务暂时不可用", resp.message());
    }
}

