package com.frog.common.access;

import com.frog.system.api.permission.IPermissionDubboService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DubboPermissionAccess Test Suite
 *
 * SECURITY CRITICAL: Tests fail-closed pattern for Dubbo RPC permission lookups
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Dubbo Permission Access Security Tests")
class DubboPermissionAccessTest {

    @Mock
    private IPermissionDubboService permissionDubboService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter successCounter;

    @Mock
    private Counter failureCounter;

    @InjectMocks
    private DubboPermissionAccess dubboPermissionAccess;

    private String testUrl;
    private String testMethod;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUrl = "/api/admin/users";
        testMethod = "DELETE";
        testUserId = UUID.randomUUID();

        // Mock meter registry
        when(meterRegistry.counter("security.permissions.dubbo.lookup.success")).thenReturn(successCounter);
        when(meterRegistry.counter("security.permissions.dubbo.lookup.fail")).thenReturn(failureCounter);
    }

    @Test
    @DisplayName("Should return permissions via Dubbo RPC successfully")
    void testFindPermissionsByUrl_Success() {
        // Arrange
        List<String> expectedPermissions = List.of("admin:user:delete", "admin:access");
        when(permissionDubboService.findPermissionsByUrl(testUrl, testMethod))
                .thenReturn(expectedPermissions);

        // Act
        List<String> actualPermissions = dubboPermissionAccess.findPermissionsByUrl(testUrl, testMethod);

        // Assert
        assertThat(actualPermissions).containsExactlyInAnyOrderElementsOf(expectedPermissions);
        verify(successCounter).increment();
        verify(permissionDubboService).findPermissionsByUrl(testUrl, testMethod);
    }

    @Test
    @DisplayName("SECURITY: Should DENY access when Dubbo service fails (fail-closed)")
    void testFindPermissionsByUrl_DubboFailure_DeniesAccess() {
        // Arrange: Simulate Dubbo RPC failure
        when(permissionDubboService.findPermissionsByUrl(testUrl, testMethod))
                .thenThrow(new org.apache.dubbo.rpc.RpcException("No provider available"));

        // Act & Assert
        assertThatThrownBy(() -> dubboPermissionAccess.findPermissionsByUrl(testUrl, testMethod))
                .isInstanceOf(PermissionServiceException.class)
                .hasMessageContaining("Permission service unavailable")
                .hasMessageContaining("access denied");

        verify(failureCounter).increment();
    }

    @Test
    @DisplayName("SECURITY: Should DENY access when Dubbo timeout occurs")
    void testFindPermissionsByUrl_DubboTimeout_DeniesAccess() {
        // Arrange
        when(permissionDubboService.findPermissionsByUrl(testUrl, testMethod))
                .thenThrow(new org.apache.dubbo.remoting.TimeoutException("Timeout"));

        // Act & Assert
        assertThatThrownBy(() -> dubboPermissionAccess.findPermissionsByUrl(testUrl, testMethod))
                .isInstanceOf(PermissionServiceException.class);

        verify(failureCounter).increment();
    }

    @Test
    @DisplayName("Should find user permissions via Dubbo successfully")
    void testFindAllPermissionsByUserId_Success() {
        // Arrange
        Set<String> expectedPermissions = Set.of("user:profile:read", "user:settings:write");
        when(permissionDubboService.getUserPermissions(testUserId))
                .thenReturn(expectedPermissions);

        // Act
        Set<String> actualPermissions = dubboPermissionAccess.findAllPermissionsByUserId(testUserId);

        // Assert
        assertThat(actualPermissions).containsExactlyInAnyOrderElementsOf(expectedPermissions);
        verify(successCounter).increment();
    }

    @Test
    @DisplayName("SECURITY: Should DENY access when user permission lookup fails via Dubbo")
    void testFindAllPermissionsByUserId_DubboFailure_DeniesAccess() {
        // Arrange
        when(permissionDubboService.getUserPermissions(testUserId))
                .thenThrow(new org.apache.dubbo.rpc.RpcException("Network error"));

        // Act & Assert
        assertThatThrownBy(() -> dubboPermissionAccess.findAllPermissionsByUserId(testUserId))
                .isInstanceOf(PermissionServiceException.class)
                .hasMessageContaining("access denied");

        verify(failureCounter).increment();
    }

    @Test
    @DisplayName("Should handle empty permissions list gracefully")
    void testFindPermissionsByUrl_EmptyList() {
        // Arrange
        when(permissionDubboService.findPermissionsByUrl(testUrl, testMethod))
                .thenReturn(List.of());

        // Act
        List<String> result = dubboPermissionAccess.findPermissionsByUrl(testUrl, testMethod);

        // Assert
        assertThat(result).isEmpty();
        verify(successCounter).increment();
    }

    @Test
    @DisplayName("Should handle null response from Dubbo service")
    void testFindPermissionsByUrl_NullResponse() {
        // Arrange
        when(permissionDubboService.findPermissionsByUrl(testUrl, testMethod))
                .thenReturn(null);

        // Act
        List<String> result = dubboPermissionAccess.findPermissionsByUrl(testUrl, testMethod);

        // Assert
        assertThat(result).isEmpty();
        verify(successCounter).increment();
    }

    @Test
    @DisplayName("Should verify Dubbo service is called with correct parameters")
    void testFindPermissionsByUrl_CorrectParameters() {
        // Arrange
        when(permissionDubboService.findPermissionsByUrl(testUrl, testMethod))
                .thenReturn(List.of("permission:test"));

        // Act
        dubboPermissionAccess.findPermissionsByUrl(testUrl, testMethod);

        // Assert
        verify(permissionDubboService).findPermissionsByUrl(
                eq(testUrl),
                eq(testMethod)
        );
    }

    @Test
    @DisplayName("Should handle Dubbo registry connection failure")
    void testFindPermissionsByUrl_RegistryFailure_DeniesAccess() {
        // Arrange
        when(permissionDubboService.findPermissionsByUrl(testUrl, testMethod))
                .thenThrow(new org.apache.dubbo.rpc.RpcException("Registry unavailable"));

        // Act & Assert
        assertThatThrownBy(() -> dubboPermissionAccess.findPermissionsByUrl(testUrl, testMethod))
                .isInstanceOf(PermissionServiceException.class);

        verify(failureCounter).increment();
    }
}