package com.frog.common.security.stepup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
@RequiredArgsConstructor
public class StepUpPolicyLoader {
    private static final Yaml YAML = new Yaml();

    private final StepUpProperties properties;
    private volatile StepUpPolicy cached;
    private volatile long loadedAt = 0L;
    private volatile long refreshIntervalMs = 0L;

    public StepUpPolicy getPolicy() {
        long interval = getRefreshIntervalMs();
        long now = System.currentTimeMillis();
        StepUpPolicy snap = cached;
        if (snap != null && (now - loadedAt) < interval) {
            return snap;
        }
        synchronized (this) {
            if (cached != null && (now - loadedAt) < interval) {
                return cached;
            }
            StepUpPolicy newPolicy = loadInternal();
            if (newPolicy != null) {
                cached = newPolicy;
                loadedAt = System.currentTimeMillis();
            }
            return cached;
        }
    }

    private long getRefreshIntervalMs() {
        if (refreshIntervalMs == 0L) {
            refreshIntervalMs = Math.max(5, properties.getRefreshSeconds()) * 1000L;
        }
        return refreshIntervalMs;
    }

    private StepUpPolicy loadInternal() {
        List<Resource> candidates = buildCandidateResources();

        for (Resource resource : candidates) {
            if (resource != null && resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    Map<String, Object> map = YAML.load(is);
                    StepUpPolicy policy = mapToPolicy(map);
                    if (policy != null) {
                        log.debug("Successfully loaded step-up policy from: {}", resource.getDescription());
                        return policy;
                    }
                } catch (IOException e) {
                    log.warn("IO error reading step-up policy from {}: {}",
                            resource.getDescription(), e.getMessage());
                } catch (YAMLException e) {
                    log.error("YAML parsing error in step-up policy from {}: {}",
                            resource.getDescription(), e.getMessage(), e);
                } catch (Exception e) {
                    log.error("Unexpected error loading step-up policy from {}: {}",
                            resource.getDescription(), e.getMessage(), e);
                }
            }
        }

        log.warn("No valid step-up policy found in any candidate location");
        return null;
    }

    private List<Resource> buildCandidateResources() {
        List<Resource> candidates = new ArrayList<>(3);

        if (properties.getPolicyPath() != null && !properties.getPolicyPath().isBlank()) {
            candidates.add(new FileSystemResource(properties.getPolicyPath()));
        }

        candidates.add(new ClassPathResource("security/stepup-policy.yaml"));
        candidates.add(new FileSystemResource("docs/security/stepup-policy.yaml"));

        return candidates;
    }

    private StepUpPolicy mapToPolicy(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }

        StepUpPolicy policy = new StepUpPolicy();
        StepUpPolicy.Stepup stepup = new StepUpPolicy.Stepup();
        policy.setStepup(stepup);

        Object stepupObj = map.get("stepup");
        if (!(stepupObj instanceof Map<?, ?> stepupMap)) {
            log.warn("Invalid stepup configuration: expected Map but got {}",
                    stepupObj != null ? stepupObj.getClass().getSimpleName() : "null");
            return null;
        }

        Object triggersObj = stepupMap.get("triggers");
        if (!(triggersObj instanceof List<?> triggersList)) {
            log.warn("Invalid triggers configuration: expected List but got {}",
                    triggersObj != null ? triggersObj.getClass().getSimpleName() : "null");
            return policy;
        }

        List<StepUpPolicy.Trigger> triggers = parseTriggers(triggersList);
        stepup.setTriggers(triggers);

        return policy;
    }

    private List<StepUpPolicy.Trigger> parseTriggers(List<?> triggersList) {
        if (triggersList == null || triggersList.isEmpty()) {
            return Collections.emptyList();
        }

        List<StepUpPolicy.Trigger> triggers = new ArrayList<>(triggersList.size());

        for (Object triggerObj : triggersList) {
            if (!(triggerObj instanceof Map<?, ?> triggerMap)) {
                log.warn("Skipping invalid trigger: expected Map but got {}",
                        triggerObj != null ? triggerObj.getClass().getSimpleName() : "null");
                continue;
            }

            StepUpPolicy.Trigger trigger = parseTrigger(triggerMap);
            triggers.add(trigger);
        }

        return triggers;
    }

    private StepUpPolicy.Trigger parseTrigger(Map<?, ?> triggerMap) {
        StepUpPolicy.Trigger trigger = new StepUpPolicy.Trigger();

        Object action = triggerMap.get("action");
        if (action != null) {
            trigger.setAction(action.toString());
        }

        Object require = triggerMap.get("require");
        if (require != null) {
            trigger.setRequire(require.toString());
        }

        Object conditions = triggerMap.get("conditions");
        if (conditions instanceof List<?> conditionsList) {
            List<String> conditionStrings = conditionsList.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .toList();
            trigger.setConditions(conditionStrings);
        }

        return trigger;
    }
}
