package com.frog.common.web.util;

import com.frog.common.web.domain.SecurityUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Security 工具类
 *
 * @author Deng
 * createData 2025/10/14 17:37
 * @version 1.0
 */
public final class SecurityUtils {
    private static volatile CurrentUserProvider provider = new SpringSecurityCurrentUserProvider();

    public static void setProvider(CurrentUserProvider custom) {
        provider = (custom != null) ? custom : new SpringSecurityCurrentUserProvider();
    }

    /**
     * 获取当前登录用户
     */
    public static SecurityUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser) {
            return (SecurityUser) authentication.getPrincipal();
        }
        return null;
    }

    public static Optional<String> getCurrentUsername() { return provider.getCurrentUsername(); }
    public static Optional<String> getCurrentUserId()     { return provider.getCurrentUserId(); }
    public static boolean isAuthenticated()                { return provider.isAuthenticated(); }
    public static Collection<String> getAuthorities()      { return provider.getAuthorities(); }

    public static Optional<UUID> getCurrentUserUuid() {
        return provider.getCurrentUserId()
                .flatMap(SecurityUtils.SpringSecurityCurrentUserProvider::parseUuid);
    }

    public interface CurrentUserProvider {
        Optional<String> getCurrentUsername();
        Optional<String> getCurrentUserId();
        boolean isAuthenticated();
        Collection<String> getAuthorities();
    }

    static final class SpringSecurityCurrentUserProvider implements CurrentUserProvider {
        @Override public Optional<String> getCurrentUsername() {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a == null || !a.isAuthenticated()) return Optional.empty();
            Object p = a.getPrincipal();
            if (p instanceof UserDetails u) {
                String username = u.getUsername();
                return Optional.of(username);
            }
            if (p instanceof Jwt jwt) return Optional.ofNullable(jwt.getClaimAsString("preferred_username"))
                    .or(() -> Optional.ofNullable(jwt.getClaimAsString("username")))
                    .or(() -> Optional.ofNullable(jwt.getSubject()));
            if (p instanceof OAuth2AuthenticatedPrincipal op) {
                String name = op.getAttribute("preferred_username");
                if (name == null) name = op.getAttribute("username");
                if (name == null) name = op.getName();
                return Optional.of(name);
            }
            if (p instanceof String s && !"anonymousUser".equalsIgnoreCase(s)) return Optional.of(s);
            return Optional.empty();
        }

        @Override public Optional<String> getCurrentUserId() {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a == null || !a.isAuthenticated()) return Optional.empty();
            Object p = a.getPrincipal();
            
            // 使用 switch 语句替换 if 语句
            return switch (p) {
                case null -> Optional.empty();
                case Jwt jwt -> Optional.ofNullable(jwt.getClaimAsString("userId"))
                        .or(() -> Optional.ofNullable(jwt.getSubject()));
                case OAuth2AuthenticatedPrincipal op -> {
                    String uid = op.getAttribute("userId");
                    if (uid == null) uid = op.getAttribute("sub");
                    yield uid != null ? Optional.of(uid) : Optional.empty();
                }
                default -> {
                    // 自定义 SecurityUser 可在此判断并取 userId
                    try {
                        var method = p.getClass().getMethod("getUserId");
                        Object v = method.invoke(p);
                        String userId = String.valueOf(v);
                        yield v != null ? Optional.of(userId) : Optional.empty();
                    } catch (Exception ignored) {
                        yield Optional.empty();
                    }
                }
            };
        }

        @Override public boolean isAuthenticated() {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            return a != null && a.isAuthenticated() && !"anonymousUser".equals(a.getPrincipal());
        }

        @Override public Collection<String> getAuthorities() {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a == null) return java.util.List.of();
            return a.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        }

        public static UUID requireCurrentUserUuid() {
            return getCurrentUserUuid().orElseThrow(() -> new IllegalStateException("Missing or invalid userId (UUID)"));
        }

        // 兼容 36 位（带横线）与 32 位（纯 hex）两种字符串
        static Optional<UUID> parseUuid(String raw) {
            if (raw == null || raw.isBlank()) return Optional.empty();
            String s = raw.trim();
            if (s.length() == 32 && s.matches("^[0-9a-fA-F]{32}$")) {
                s = s.replaceFirst(
                        "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                        "$1-$2-$3-$4-$5"
                );
            }
            try { return Optional.of(UUID.fromString(s)); } catch (IllegalArgumentException e) { return Optional.empty(); }
        }
    }
}
