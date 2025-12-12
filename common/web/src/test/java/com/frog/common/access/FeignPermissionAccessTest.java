package com.frog.common.access;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.frog.common.feign.client.SysPermissionServiceClient;
import com.frog.common.response.ApiResponse;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * FeignPermissionAccess Test Suite
 *
 * SECURITY CRITICAL: Tests fail-closed pattern for permission lookups
 * - Service failures must DENY access (not grant)
 * - Sentinel circuit open must DENY access
 * - Metrics tracking for security events
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Feign Permission Access Security Tests")
class FeignPermissionAccessTest {

    @Mock
    private SysPermissionServiceClient permissionServiceClient;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter successCounter;

    @Mock
    private Counter failureCounter;

    @Mock
    private Counter blockedCounter;

    @InjectMocks
    private FeignPermissionAccess feignPermissionAccess;

    private String testUrl;
    private String testMethod;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        testUrl = "/api/users";
        testMethod = "GET";
        testUserId = UUID.randomUUID();

        // Mock meter registry counters
        when(meterRegistry.counter("security.permissions.lookup.success")).thenReturn(successCounter);
        when(meterRegistry.counter("security.permissions.lookup.fail")).thenReturn(failureCounter);
        when(meterRegistry.counter("security.permissions.lookup.blocked")).thenReturn(blockedCounter);
        when(meterRegistry.counter("security.permissions.user.success")).thenReturn(successCounter);
        when(meterRegistry.counter("security.permissions.user.fail")).thenReturn(failureCounter);
        when(meterRegistry.counter("security.permissions.user.blocked")).thenReturn(blockedCounter);
    }

    @Test
    @DisplayName("Should return permissions when service call succeeds")
    void testFindPermissionsByUrl_Success() {
        // Arrange
        List<String> expectedPermissions = List.of("user:read", "user:list");
        when(permissionServiceClient.findPermissionsByUrl(testUrl, testMethod))
                .thenReturn(expectedPermissions);

        // Act
        List<String> actualPermissions = feignPermissionAccess.findPermissionsByUrl(testUrl, testMethod);

        // Assert
        assertThat(actualPermissions).containsExactlyInAnyOrderElementsOf(expectedPermissions);
        verify(successCounter).increment();
        verify(failureCounter, never()).increment();
        verify(blockedCounter, never()).increment();
    }

    @Test
    @DisplayName("Should return empty list when no permissions found")
    void testFindPermissionsByUrl_NoPermissions() {
        // Arrange
        when(permissionServiceClient.findPermissionsByUrl(testUrl, testMethod))
                .thenReturn(null);

        // Act
        List<String> actualPermissions = feignPermissionAccess.findPermissionsByUrl(testUrl, testMethod);

        // Assert
        assertThat(actualPermissions).isEmpty();
        verify(successCounter).increment();
    }

    @Test
    @DisplayName("SECURITY: Should DENY access when service call fails (fail-closed)")
    void testFindPermissionsByUrl_ServiceFailure_DeniesAccess() {
        // Arrange
        when(permissionServiceClient.findPermissionsByUrl(testUrl, testMethod))
                .thenThrow(new RuntimeException("Service unavailable"));

        // Act & Assert
        assertThatThrownBy(() -> feignPermissionAccess.findPermissionsByUrl(testUrl, testMethod))
                .isInstanceOf(PermissionServiceException.class)
                .hasMessageContaining("Permission service unavailable")
                .hasMessageContaining("access denied");

        verify(failureCounter).increment();
        verify(successCounter, never()).increment();
    }

    @Test
    @DisplayName("SECURITY: Should DENY access when Sentinel circuit is open (fail-closed)")
    void testFindPermissionsByUrl_SentinelCircuitOpen_DeniesAccess() {
        // Note: This test verifies the expected behavior
        // Actual Sentinel integration requires SphU.entry() which is tested in integration tests

        // Arrange: Simulate Sentinel blocking the call
        when(permissionServiceClient.findPermissionsByUrl(testUrl, testMethod))
                .thenThrow(new RuntimeException("Blocked by Sentinel"));

        // Act & Assert
        assertThatThrownBy(() -> feignPermissionAccess.findPermissionsByUrl(testUrl, testMethod))
                .isInstanceOf(PermissionServiceException.class);

        verify(failureCounter).increment();
    }

    @Test
    @DisplayName("Should find user permissions successfully")
    void testFindAllPermissionsByUserId_Success() {
        // Arrange
        Set<String> expectedPermissions = Set.of("user:read", "user:write", "admin:access");
        ApiResponse<Set<String>> response = ApiResponse.success(expectedPermissions);

        when(permissionServiceClient.getUserPermissions(testUserId))
                .thenReturn(response);

        // Act
        Set<String> actualPermissions = feignPermissionAccess.findAllPermissionsByUserId(testUserId);

        // Assert
        assertThat(actualPermissions).containsExactlyInAnyOrderElementsOf(expectedPermissions);
        verify(successCounter).increment();
    }

    @Test
    @DisplayName("Should return empty set when user has no permissions")
    void testFindAllPermissionsByUserId_NoPermissions() {
        // Arrange
        when(permissionServiceClient.getUserPermissions(testUserId))
                .thenReturn(null);

        // Act
        Set<String> actualPermissions = feignPermissionAccess.findAllPermissionsByUserId(testUserId);

        // Assert
        assertThat(actualPermissions).isEmpty();
        verify(successCounter).increment();
    }

    @Test
    @DisplayName("SECURITY: Should DENY access when user permission lookup fails (fail-closed)")
    void testFindAllPermissionsByUserId_ServiceFailure_DeniesAccess() {
        // Arrange
        when(permissionServiceClient.getUserPermissions(testUserId))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        assertThatThrownBy(() -> feignPermissionAccess.findAllPermissionsByUserId(testUserId))
                .isInstanceOf(PermissionServiceException.class)
                .hasMessageContaining("Permission service unavailable")
                .hasMessageContaining("access denied");

        verify(failureCounter).increment();
    }

    @Test
    @DisplayName("Should handle Feign timeout gracefully - DENY access")
    void testFindPermissionsByUrl_FeignTimeout_DeniesAccess() {
        // Arrange
        when(permissionServiceClient.findPermissionsByUrl(testUrl, testMethod))
                .thenThrow(new RuntimeException("Read timed out"));

        // Act & Assert
        assertThatThrownBy(() -> feignPermissionAccess.findPermissionsByUrl(testUrl, testMethod))
                .isInstanceOf(PermissionServiceException.class)
                .hasMessageContaining("access denied");

        verify(failureCounter).increment();
    }

    @Test
    @DisplayName("Should log security event when permission check fails")
    void testFindPermissionsByUrl_LogsSecurityEvent() {
        // Arrange
        when(permissionServiceClient.findPermissionsByUrl(testUrl, testMethod))
                .thenThrow(new RuntimeException("Network error"));

        // Act & Assert
        assertThatThrownBy(() -> feignPermissionAccess.findPermissionsByUrl(testUrl, testMethod))
                .isInstanceOf(PermissionServiceException.class);

        // Verify metrics were recorded (for security monitoring)
        verify(failureCounter).increment();
    }

    @Test
    @DisplayName("Should handle multiple rapid permission checks")
    void testConcurrentPermissionChecks() throws InterruptedException {
        // Arrange
        List<String> permissions = List.of("user:read");
        when(permissionServiceClient.findPermissionsByUrl(anyString(), anyString()))
                .thenReturn(permissions);

        // Act: Simulate 10 concurrent requests
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; i++) {
            threads[i] = new Thread(() -> {
                List<String> result = feignPermissionAccess.findPermissionsByUrl(testUrl, testMethod);
                assertThat(result).containsExactly("user:read");
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Assert: All calls should succeed
        verify(successCounter, times(10)).increment();
    }

    @Test
    @DisplayName("Should differentiate between GET and POST method permissions")
    void testFindPermissionsByUrl_DifferentMethods() {
        // Arrange
        List<String> getPermissions = List.of("user:read");
        List<String> postPermissions = List.of("user:write");

        when(permissionServiceClient.findPermissionsByUrl(testUrl, "GET"))
                .thenReturn(getPermissions);
        when(permissionServiceClient.findPermissionsByUrl(testUrl, "POST"))
                .thenReturn(postPermissions);

        // Act
        List<String> getResult = feignPermissionAccess.findPermissionsByUrl(testUrl, "GET");
        List<String> postResult = feignPermissionAccess.findPermissionsByUrl(testUrl, "POST");

        // Assert
        assertThat(getResult).containsExactly("user:read");
        assertThat(postResult).containsExactly("user:write");
    }

    @Test
    @DisplayName("Should handle API response with null data gracefully")
    void testFindAllPermissionsByUserId_NullResponseData() {
        // Arrange
        ApiResponse<Set<String>> response = ApiResponse.success(null);
        when(permissionServiceClient.getUserPermissions(testUserId))
                .thenReturn(response);

        // Act
        Set<String> result = feignPermissionAccess.findAllPermissionsByUserId(testUserId);

        // Assert
        assertThat(result).isEmpty(); // Should return empty set, not throw
    }
}