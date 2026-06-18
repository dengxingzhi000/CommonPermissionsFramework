package com.frog.common.security.util;

import com.frog.common.security.properties.JwtProperties;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JWT Utilities Test Suite
 *
 * Tests token generation, validation, and revocation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JWT Utils Tests")
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

    private static final String SECRET = "dGhpc19pc19hX3Zlcnlfc2VjdXJlX3NlY3JldF9rZXlfdGhhdF9pc19hdF9sZWFzdF81MTJfYml0c19sb25nX2Zvcl9obWFjX3NoYTUxMl90b19mdW5jdGlvbl9wcm9wZXJseQ==";

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.getSecret()).thenReturn(SECRET);
        lenient().when(jwtProperties.getAccessTokenExpiration()).thenReturn(3600000L);
        lenient().when(jwtProperties.getRefreshTokenExpiration()).thenReturn(604800000L);
        lenient().when(jwtProperties.getIssuer()).thenReturn("test");
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Should generate access token")
    void testGenerateAccessToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtils.generateAccessToken(userId, "admin", "127.0.0.1", "device-1");

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    @DisplayName("Should generate refresh token")
    void testGenerateRefreshToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtils.generateRefreshToken(userId, "admin", "device-1");

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("Should validate token with correct IP and device")
    void testValidateToken() {
        UUID userId = UUID.randomUUID();
        when(hashOperations.get(anyString(), anyString())).thenReturn(userId.toString());

        String token = jwtUtils.generateAccessToken(userId, "admin", "127.0.0.1", "device-1");

        boolean valid = jwtUtils.validateToken(token, "127.0.0.1", "device-1");
        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Should reject token with wrong IP")
    void testValidateToken_WrongIp() {
        UUID userId = UUID.randomUUID();
        when(hashOperations.get(anyString(), anyString())).thenReturn(userId.toString());

        String token = jwtUtils.generateAccessToken(userId, "admin", "127.0.0.1", "device-1");

        boolean valid = jwtUtils.validateToken(token, "192.168.1.1", "device-1");
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Should revoke token")
    void testRevokeToken() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtils.generateAccessToken(userId, "admin", "127.0.0.1", "device-1");

        jwtUtils.revokeToken(token, "test revocation");

        verify(hashOperations).delete(anyString(), anyString());
    }
}
