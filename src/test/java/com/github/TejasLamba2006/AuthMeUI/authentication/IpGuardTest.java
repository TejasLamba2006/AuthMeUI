package com.github.TejasLamba2006.AuthMeUI.authentication;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IpGuardTest {

    @Test
    void picksEscalatingDurationsInOrder() {
        List<Integer> ladder = List.of(300, 1800, 3600, 86400);
        assertEquals(300, IpGuard.pickDuration(ladder, 0));
        assertEquals(1800, IpGuard.pickDuration(ladder, 1));
        assertEquals(3600, IpGuard.pickDuration(ladder, 2));
        assertEquals(86400, IpGuard.pickDuration(ladder, 3));
    }

    @Test
    void clampsToLongestPastLadderCeiling() {
        List<Integer> ladder = List.of(300, 1800, 3600, 86400);
        // ponytail: past the last rung, reuse the longest ban (the ceiling).
        assertEquals(86400, IpGuard.pickDuration(ladder, 4));
        assertEquals(86400, IpGuard.pickDuration(ladder, 99));
    }

    @Test
    void clampsNegativeIndexToFirst() {
        List<Integer> ladder = List.of(300, 1800, 3600, 86400);
        assertEquals(300, IpGuard.pickDuration(ladder, -1));
    }

    @Test
    void fallsBackToDefaultWhenEmpty() {
        assertEquals(300, IpGuard.pickDuration(List.of(), 0));
        assertEquals(86400, IpGuard.pickDuration(null, 3));
    }
}
