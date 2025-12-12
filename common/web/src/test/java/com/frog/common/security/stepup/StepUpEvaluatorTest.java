package com.frog.common.security.stepup;

import com.frog.common.security.session.SessionManager;
import com.frog.common.security.util.HttpServletRequestUtils;
import com.frog.common.security.properties.JwtProperties;
import com.frog.common.web.domain.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

class StepUpEvaluatorTest {

    @Test
    void returnsStepUpWhenPolicyMatchesAction() {
        StepUpProperties properties = new StepUpProperties();
        properties.setBusinessStartHour(0);
        properties.setBusinessEndHour(23);

        HttpServletRequestUtils requestUtils = new HttpServletRequestUtils(new JwtProperties());
        SessionManager sessionManager = Mockito.mock(SessionManager.class);
        when(sessionManager.getUserSessions(Mockito.any())).thenReturn(new ArrayList<>());

        StepUpPolicyLoader loader = Mockito.mock(StepUpPolicyLoader.class);
        StepUpPolicy policy = new StepUpPolicy();
        StepUpPolicy.Stepup stepup = new StepUpPolicy.Stepup();
        StepUpPolicy.Trigger trigger = new StepUpPolicy.Trigger();
        trigger.setAction("role:grant");
        trigger.setRequire("mfa");
        stepup.setTriggers(List.of(trigger));
        policy.setStepup(stepup);
        when(loader.getPolicy()).thenReturn(policy);

        StepUpEvaluator evaluator = new StepUpEvaluator(properties, requestUtils, sessionManager, loader);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/role/grant");
        request.addHeader("X-Device-ID", "dev-1");
        SecurityUser user = SecurityUser.builder()
                .userId(UUID.randomUUID())
                .username("alice")
                .roles(Set.of("role:a"))
                .permissions(Set.of())
                .build();

        StepUpRequirement result = evaluator.evaluate(request, user);
        assertSame(StepUpRequirement.MFA, result);
    }

    @Test
    void newDeviceConditionTriggersStepUp() {
        StepUpProperties properties = new StepUpProperties();
        properties.setBusinessStartHour(0);
        properties.setBusinessEndHour(23);

        HttpServletRequestUtils requestUtils = Mockito.mock(HttpServletRequestUtils.class);
        when(requestUtils.getDeviceId(Mockito.any(HttpServletRequest.class))).thenReturn("device-new");

        SessionManager sessionManager = Mockito.mock(SessionManager.class);
        List<java.util.Map<String, Object>> sessions = new ArrayList<>();
        java.util.Map<String, Object> sess = new HashMap<>();
        sess.put("deviceId", "device-old");
        sessions.add(sess);
        when(sessionManager.getUserSessions(Mockito.any())).thenReturn(sessions);

        StepUpPolicyLoader loader = Mockito.mock(StepUpPolicyLoader.class);
        StepUpPolicy policy = new StepUpPolicy();
        StepUpPolicy.Stepup stepup = new StepUpPolicy.Stepup();
        StepUpPolicy.Trigger trigger = new StepUpPolicy.Trigger();
        trigger.setAction("file:download");
        trigger.setRequire("mfa");
        trigger.setConditions(List.of("new_device"));
        stepup.setTriggers(List.of(trigger));
        policy.setStepup(stepup);
        when(loader.getPolicy()).thenReturn(policy);

        StepUpEvaluator evaluator = new StepUpEvaluator(properties, requestUtils, sessionManager, loader);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/file/download/report");
        SecurityUser user = SecurityUser.builder()
                .userId(UUID.randomUUID())
                .username("bob")
                .roles(Set.of("role:b"))
                .permissions(Set.of())
                .build();

        StepUpRequirement result = evaluator.evaluate(request, user);
        assertEquals(StepUpRequirement.MFA, result);
    }
}

