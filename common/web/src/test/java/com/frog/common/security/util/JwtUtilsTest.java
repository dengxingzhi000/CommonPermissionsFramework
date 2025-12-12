package com.frog.common.security.util;

import com.frog.common.exception.UnauthorizedException;
import com.frog.common.security.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JWT Utilities Test Suite
 *
 * Tests cover:
 * - Token generation and validation
 * - Token revocation and blacklisting
 * - Device binding and IP verification
 * - Hash-based user token storage (no KEYS command)
 * - Security edge cases
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JWT Utils Security Tests")
class JwtUtilsTest {

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private JwtUtils jwtUtils;

    private static final String VALID_SECRET = "dGhpc19pc19hX3Zlcnlfc2VjdXJlX3NlY3JldF9rZXlfdGhhdF9pc19hdF9sZWFzdF81MTJfYml0c19sb25nX2Zvcl9obWFjX3NoYTUxMl90b19mdW5jdGlvbl9wcm9wZXJseQ=="; // 512-bit secret
    private static final long EXPIRATION = 3600000L; // 1 hour
    private static final long REFRESH_EXPIRATION = 604800000L; // 7 days
    private static final String ISSUER = "nearsync-test";

    private UUID testUserId;
    private String testUsername;
    private Set<String> testRoles;
    private Set<String> testPermissions;
    private String testDeviceId;
    private String testIpAddress;

    @BeforeEach
    void setUp() {
        // Setup test data
        testUserId = UUID.randomUUID();
        testUsername = "testuser";
        testRoles = Set.of("ROLE_USER", "ROLE_ADMIN");
        testPermissions = Set.of("user:read", "user:write");
        testDeviceId = "device-001";
        testIpAddress = "192.168.1.100";

        // Mock JWT properties
        when(jwtProperties.getSecret()).thenReturn(VALID_SECRET);
        when(jwtProperties.getExpiration()).thenReturn(EXPIRATION);
        when(jwtProperties.getRefreshExpiration()).thenReturn(REFRESH_EXPIRATION);
        when(jwtProperties.getIssuer()).thenReturn(ISSUER);

        // Mock Redis operations
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Initialize JwtUtils (simulates @PostConstruct)
        jwtUtils.init();
    }

    @Test
    @DisplayName("Should initialize with valid 512-bit secret")
    void testInitialization_WithValidSecret() {
        assertThatCode(() -> jwtUtils.init()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should fail initialization with short secret")
    void testInitialization_WithShortSecret() {
        when(jwtProperties.getSecret()).thenReturn("tooshort");

        assertThatThrownBy(() -> jwtUtils.init())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("512 bits");
    }

    @Test
    @DisplayName("Should fail initialization with null secret")
    void testInitialization_WithNullSecret() {
        when(jwtProperties.getSecret()).thenReturn(null);

        assertThatThrownBy(() -> jwtUtils.init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret must be configured");
    }

    @Test
    @DisplayName("Should generate valid access token with all claims")
    void testGenerateAccessToken_Success() {
        // Act
        String token = jwtUtils.generateAccessToken(
                testUserId, testUsername, testRoles, testPermissions,
                testDeviceId, testIpAddress
        );

        // Assert
        assertThat(token).isNotNull().isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3); // JWT format: header.payload.signature

        // Verify token metadata storage in Redis Hash
        verify(hashOperations).put(
                eq("jwt:user:tokens:" + testUserId),
                eq(testDeviceId),
                anyString()
        );
        verify(redisTemplate).expire(
                eq("jwt:user:tokens:" + testUserId),
                any(Duration.class)
        );
    }

    @Test
    @DisplayName("Should generate token with AMR (Authentication Methods Reference)")
    void testGenerateAccessToken_WithAMR() {
        List<String> amr = List.of("mfa", "webauthn");

        String token = jwtUtils.generateAccessToken(
                testUserId, testUsername, testRoles, testPermissions,
                testDeviceId, testIpAddress, amr
        );

        assertThat(token).isNotNull();

        // Parse and verify AMR claim
        Claims claims = jwtUtils.parseToken(token);
        @SuppressWarnings("unchecked")
        List<String> actualAmr = (List<String>) claims.get("amr");
        assertThat(actualAmr).containsExactlyInAnyOrderElementsOf(amr);
    }

    @Test
    @DisplayName("Should validate token successfully")
    void testValidateToken_ValidToken() {
        String token = jwtUtils.generateAccessToken(
                testUserId, testUsername, testRoles, testPermissions,
                testDeviceId, testIpAddress
        );

        // Mock Redis checks
        when(valueOperations.get(anyString())).thenReturn(null); // Not blacklisted

        boolean isValid = jwtUtils.validateToken(token);
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject blacklisted token")
    void testValidateToken_BlacklistedToken() {
        String token = jwtUtils.generateAccessToken(
                testUserId, testUsername, testRoles, testPermissions,
                testDeviceId, testIpAddress
        );

        // Mock blacklisted token
        when(valueOperations.get(anyString())).thenReturn("REVOKED");

        boolean isValid = jwtUtils.validateToken(token);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject malformed token")
    void testValidateToken_MalformedToken() {
        boolean isValid = jwtUtils.validateToken("invalid.token.format");
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject expired token")
    void testValidateToken_ExpiredToken() {
        // Create token with -1 expiration (already expired)
        when(jwtProperties.getExpiration()).thenReturn(-1L);
        jwtUtils.init();

        String token = jwtUtils.generateAccessToken(
                testUserId, testUsername, testRoles, testPermissions,
                testDeviceId, testIpAddress
        );

        // Wait a moment to ensure expiration
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        boolean isValid = jwtUtils.validateToken(token);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should parse token claims correctly")
    void testParseToken_Success() {
        String token = jwtUtils.generateAccessToken(
                testUserId, testUsername, testRoles, testPermissions,
                testDeviceId, testIpAddress
        );

        Claims claims = jwtUtils.parseToken(token);

        assertThat(claims).isNotNull();
        assertThat(claims.get("userId", UUID.class)).isEqualTo(testUserId);
        assertThat(claims.get("username", String.class)).isEqualTo(testUsername);
        assertThat(claims.get("deviceId", String.class)).isEqualTo(testDeviceId);
        assertThat(claims.get("ipAddress", String.class)).isEqualTo(testIpAddress);
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);

        // Verify roles and permissions
        @SuppressWarnings("unchecked")
        Set<String> roles = new HashSet<>((List<String>) claims.get("roles"));
        @SuppressWarnings("unchecked")
        Set<String> permissions = new HashSet<>((List<String>) claims.get("permissions"));

        assertThat(roles).containsExactlyInAnyOrderElementsOf(testRoles);
        assertThat(permissions).containsExactlyInAnyOrderElementsOf(testPermissions);
    }

    @Test
    @DisplayName("Should throw exception when parsing invalid token")
    void testParseToken_InvalidToken() {
        assertThatThrownBy(() -> jwtUtils.parseToken("invalid.token.here"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should extract roles from token")
    void testGetRolesFromToken() {
        String token = jwtUtils.generateAccessToken(
                testUserId, testUsername, testRoles, testPermissions,
                testDeviceId, testIpAddress
        );

        Set<String> extractedRoles = jwtUtils.getRolesFromToken(token);
        assertThat(extractedRoles).containsExactlyInAnyOrderElementsOf(testRoles);
    }

    @Test
    @DisplayName("Should extract permissions from token")
    void testGetPermissionsFromToken() {
        String token = jwtUtils.generateAccessToken(
                testUserId, testUsername, testRoles, testPermissions,
                testDeviceId, testIpAddress
        );

        Set<String> extractedPermissions = jwtUtils.getPermissionsFromToken(token);
        assertThat(extractedPermissions).containsExactlyInAnyOrderElementsOf(testPermissions);
    }

    @Test
    @DisplayName("Should extract AMR from token")
    void testGetAmrFromToken() {
        List<String> amr = List.of("mfa", "webauthn");
        String token = jwtUtils.generateAccessToken(
                testUserId, testUsername, testRoles, testPermissions,
                testDeviceId, testIpAddress, amr
        );

        Set<String> extractedAmr = jwtUtils.getAmrFromToken(token);
        assertThat(extractedAmr).containsExactlyInAnyOrderElementsOf(amr);
    }

    @Test
    @DisplayName("Should revoke token successfully")
    void testRevokeToken() {
        String token = jwtUtils.generateAccessToken(
                testUserId, testUsername, testRoles, testPermissions,
                testDeviceId, testIpAddress
        );

        String reason = "User logout";
        jwtUtils.revokeToken(token, reason);

        // Verify blacklist entry
        verify(valueOperations).set(
                startsWith("jwt:blacklist:"),
                anyMap(),
                any(Duration.class)
        );

        // Verify user token hash removal
        verify(hashOperations).delete(
                eq("jwt:user:tokens:" + testUserId),
                eq(testDeviceId)
        );

        // Verify fingerprint deletion
        verify(redisTemplate).delete(startsWith("jwt:fingerprint:"));
    }

    @Test
    @DisplayName("Should revoke all user tokens using Hash (no KEYS command)")
    void testRevokeAllUserTokens_UsingHash() {
        // Setup: User has 3 device tokens
        Map<Object, Object> deviceTokens = Map.of(
                "device-001", "token1",
                "device-002", "token2",
                "device-003", "token3"
        );

        when(hashOperations.entries("jwt:user:tokens:" + testUserId))
                .thenReturn(deviceTokens);

        // Act
        jwtUtils.revokeAllUserTokens(testUserId);

        // Assert: KEYS command should NEVER be called
        verify(redisTemplate, never()).keys(anyString());

        // Verify Hash cleanup
        verify(redisTemplate).delete("jwt:user:tokens:" + testUserId);
    }

    @Test
    @DisplayName("Should generate refresh token")
    void testGenerateRefreshToken() {
        String refreshToken = jwtUtils.generateRefreshToken(
                testUserId, testUsername, testDeviceId
        );

        assertThat(refreshToken).isNotNull().isNotEmpty();

        Claims claims = jwtUtils.parseToken(refreshToken);
        assertThat(claims.get("tokenType", String.class)).isEqualTo("refresh");
        assertThat(claims.get("userId", UUID.class)).isEqualTo(testUserId);
        assertThat(claims.get("deviceId", String.class)).isEqualTo(testDeviceId);
    }

    @Test
    @DisplayName("Should handle concurrent token generation safely")
    void testConcurrentTokenGeneration() throws InterruptedException {
        final int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        final List<String> tokens = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                String token = jwtUtils.generateAccessToken(
                        UUID.randomUUID(), "user-" + Thread.currentThread().getId(),
                        testRoles, testPermissions, testDeviceId, testIpAddress
                );
                tokens.add(token);
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // All tokens should be unique
        assertThat(tokens).hasSize(threadCount);
        assertThat(new HashSet<>(tokens)).hasSize(threadCount);
    }

    @Test
    @DisplayName("Should reject token with tampered signature")
    void testValidateToken_TamperedSignature() {
        String token = jwtUtils.generateAccessToken(
                testUserId, testUsername, testRoles, testPermissions,
                testDeviceId, testIpAddress
        );

        // Tamper with signature (last part of JWT)
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + ".tampered_signature";

        boolean isValid = jwtUtils.validateToken(tamperedToken);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject token with tampered payload")
    void testValidateToken_TamperedPayload() {
        String token = jwtUtils.generateAccessToken(
                testUserId, testUsername, testRoles, testPermissions,
                testDeviceId, testIpAddress
        );

        // Tamper with payload (middle part)
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + ".tampered_payload." + parts[2];

        assertThatThrownBy(() -> jwtUtils.parseToken(tamperedToken))
                .isInstanceOf(Exception.class);
    }
}