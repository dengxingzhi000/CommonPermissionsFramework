package com.frog.common.security.stepup;

import lombok.Data;

import java.util.List;

@Data
public class StepUpPolicy {

    private Stepup stepup;

    @Data
    public static class Stepup {
        private List<Trigger> triggers;
        private Recovery recovery;
    }

    @Data
    public static class Trigger {
        private String action;       // e.g. "role:grant", "file:download"
        private String require;      // e.g. "mfa", "webauthn"
        private List<String> conditions; // optional
    }

    @Data
    public static class Recovery {
        private Integer backup_codes;
        private Boolean allow_rebind;
    }
}

