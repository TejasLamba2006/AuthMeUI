package com.github.TejasLamba2006.AuthMeUI.authentication;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Captcha trust window: generates human-readable codes and remembers players
 * who solved one within a configurable trust duration, so they aren't nagged
 * for a captcha on every dialog re-open in the same session.
 *
 * <p>Mirrors AuthMeBia's {@code listeners/captcha/Captcha.java}: same CHARS
 * alphabet (no ambiguous 0/O/1/I), {@code generate} clamps 4–10, matches
 * case-insensitively after trim, 30-min eviction daemon for the trust map.
 * The per-player active-code map is added because AuthMeUI is event-driven
 * (dialog clicks) rather than the blocking loop AuthMeBia uses, so the
 * expected code has to live somewhere between clicks.
 */
public final class Captcha {

    // ponytail: ambiguous characters removed (0/O/1/I/L) so codes are readable
    // in Minecraft's chat font. Same alphabet AuthMeBia uses.
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 10;
    private static final long EVICTION_INTERVAL_MINUTES = 30L;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<UUID, Long> trustedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeCode = new ConcurrentHashMap<>();

    private final ScheduledExecutorService evictionScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "authmeui-captcha-evict");
                t.setDaemon(true);
                return t;
            });

    public Captcha() {
        evictionScheduler.scheduleAtFixedRate(
                this::evictExpired,
                EVICTION_INTERVAL_MINUTES,
                EVICTION_INTERVAL_MINUTES,
                TimeUnit.MINUTES);
    }

    public void shutdown() {
        evictionScheduler.shutdownNow();
    }

    private void evictExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = trustedUntil.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() <= now) {
                it.remove();
            }
        }
    }

    /**
     * Generate a random code, clamping length to [4, 10].
     */
    public String generate(int length) {
        int len = Math.max(MIN_LENGTH, Math.min(MAX_LENGTH, length));
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * Generate a fresh code for a player and remember it as the expected answer.
     */
    public String generateFor(UUID uuid, int length) {
        String code = generate(length);
        activeCode.put(uuid, code);
        return code;
    }

    /**
     * Match a typed answer against an expected code (case-insensitive, trimmed).
     */
    public boolean matches(String typed, String expected) {
        if (typed == null || expected == null) {
            return false;
        }
        return typed.trim().equalsIgnoreCase(expected.trim());
    }

    /**
     * Match a typed answer against the player's stored active code. Does not
     * clear the code on success (the caller may want to re-show on a later
     * failure of the real auth dialog); clear with {@link #clearCode}.
     */
    public boolean matchesFor(UUID uuid, String typed) {
        String expected = activeCode.get(uuid);
        return matches(typed, expected);
    }

    public void clearCode(UUID uuid) {
        activeCode.remove(uuid);
    }

    public void markTrusted(UUID uuid, long durationSeconds) {
        trustedUntil.put(uuid, System.currentTimeMillis() + durationSeconds * 1000L);
    }

    public boolean isTrusted(UUID uuid) {
        Long until = trustedUntil.get(uuid);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() > until) {
            trustedUntil.remove(uuid);
            return false;
        }
        return true;
    }

    public void clearTrust(UUID uuid) {
        trustedUntil.remove(uuid);
    }

    // ponytail: kept for completeness/parity with AuthMeBia surface; currently
    // unused but the alphabet is the documented contract for captcha codes.
    @SuppressWarnings("unused")
    public static String alphabet() {
        return CHARS;
    }

    // ponytail: lowercase not actually used here, but Locale.ROOT is the
    // project convention for case folding (matches AuthenticationBridge).
    @SuppressWarnings("unused")
    private static String lower(String s) {
        return s != null ? s.toLowerCase(Locale.ROOT) : null;
    }
}
