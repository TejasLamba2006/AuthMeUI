package com.github.TejasLamba2006.AuthMeUI.authentication;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistrationIpLimitTest {

    @Test
    void limitAppliesOnlyWithPositiveLimitKnownIpAndNoBypass() {
        assertTrue(AuthenticationBridge.isIpLimitApplicable(5, "203.0.113.7", false));
        assertFalse(AuthenticationBridge.isIpLimitApplicable(0, "203.0.113.7", false));
        assertFalse(AuthenticationBridge.isIpLimitApplicable(-1, "203.0.113.7", false));
        assertFalse(AuthenticationBridge.isIpLimitApplicable(5, null, false));
        assertFalse(AuthenticationBridge.isIpLimitApplicable(5, " ", false));
        assertFalse(AuthenticationBridge.isIpLimitApplicable(5, "203.0.113.7", true));
    }

    @Test
    void blocksOnceExistingAccountsReachTheLimit() {
        // Same comparison as AuthMe's /register: refuse when existing >= limit.
        assertFalse(AuthenticationBridge.isAtIpLimit(5, 4));
        assertTrue(AuthenticationBridge.isAtIpLimit(5, 5));
        assertTrue(AuthenticationBridge.isAtIpLimit(5, 6));
        assertTrue(AuthenticationBridge.isAtIpLimit(1, 1));
        assertFalse(AuthenticationBridge.isAtIpLimit(1, 0));
    }
}
