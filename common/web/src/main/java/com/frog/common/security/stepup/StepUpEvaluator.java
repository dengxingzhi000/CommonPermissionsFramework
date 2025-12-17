package com.frog.common.security.stepup;

import com.frog.common.security.util.HttpServletRequestUtils;
import com.frog.common.web.domain.SecurityUser;
import com.frog.common.security.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StepUpEvaluator {
    private final StepUpProperties properties;
    private final HttpServletRequestUtils requestUtils;
    private final SessionManager sessionManager;
    private final StepUpPolicyLoader policyLoader;

    public StepUpRequirement evaluate(HttpServletRequest request, SecurityUser user) {
        if (!properties.isEnabled() || user == null) return StepUpRequirement.NONE;

        String uri = request.getRequestURI().toLowerCase(Locale.ROOT);
        String method = request.getMethod().toUpperCase(Locale.ROOT);

        StepUpPolicy policy = policyLoader.getPolicy();
        if (policy != null && policy.getStepup() != null && policy.getStepup().getTriggers() != null) {
            for (StepUpPolicy.Trigger t : policy.getStepup().getTriggers()) {
                String action = t.getAction();
                String require = t.getRequire();
                if (action == null || require == null) continue;
                if (matchesAction(action, uri, method)) {
                    boolean conditionsOk = true;
                    if (t.getConditions() != null && !t.getConditions().isEmpty()) {
                        for (String cond : t.getConditions()) {
                            if ("outside_business_hours".equalsIgnoreCase(cond) && !outsideBusinessHours()) {
                                conditionsOk = false; break;
                            }
                            if ("new_device".equalsIgnoreCase(cond) && !isNewDevice(request, user.getUserId())) {
                                conditionsOk = false; break;
                            }
                        }
                    }
                    if (conditionsOk) {
                        return mapRequire(require);
                    }
                }
            }
        } else {
            // Fallback to built-in defaults
            if (isRoleGrant(uri, method)) {
                return StepUpRequirement.MFA;
            }
            if (isFileDownload(uri, method) && (outsideBusinessHours() || isNewDevice(request, user.getUserId()))) {
                return StepUpRequirement.MFA;
            }
            if (isPayoutApprove(uri, method)) {
                return StepUpRequirement.WEBAUTHN;
            }
        }
        return StepUpRequirement.NONE;
    }

    private boolean matchesAction(String action, String uri, String method) {
        if ("role:grant".equalsIgnoreCase(action)) return isRoleGrant(uri, method);
        if ("file:download".equalsIgnoreCase(action)) return isFileDownload(uri, method);
        if ("payout:approve".equalsIgnoreCase(action)) return isPayoutApprove(uri, method);
        return false;
    }

    private StepUpRequirement mapRequire(String require) {
        if ("mfa".equalsIgnoreCase(require)) return StepUpRequirement.MFA;
        if ("webauthn".equalsIgnoreCase(require)) return StepUpRequirement.WEBAUTHN;
        return StepUpRequirement.NONE;
    }

    private boolean isRoleGrant(String uri, String method) {
        return (uri.contains("/role/grant") || uri.contains("/roles/grant") || uri.contains("/permission/grant")
                || (uri.contains("grant") && (uri.contains("/role") || uri.contains("/permission"))))
                && ("POST".equals(method) || "PUT".equals(method));
    }

    private boolean isFileDownload(String uri, String method) {
        return uri.contains("/file") && uri.contains("download") && "GET".equals(method);
    }

    private boolean isPayoutApprove(String uri, String method) {
        return uri.contains("payout") && (uri.contains("approve") || "POST".equals(method) || "PUT".equals(method));
    }

    private boolean outsideBusinessHours() {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek dow = now.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return true;
        int h = now.getHour();
        return h < properties.getBusinessStartHour() || h > properties.getBusinessEndHour();
    }

    private boolean isNewDevice(HttpServletRequest request, UUID userId) {
        if (!properties.isNewDeviceTrigger()) return false;
        String currentDeviceId = requestUtils.getDeviceId(request);
        var sessions = sessionManager.getUserSessions(userId);
        return sessions.stream().map(s -> String.valueOf(s.get("deviceId")))
                .noneMatch(currentDeviceId::equals);
    }
}
