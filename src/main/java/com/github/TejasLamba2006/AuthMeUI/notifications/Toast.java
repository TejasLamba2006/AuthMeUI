package com.github.TejasLamba2006.AuthMeUI.notifications;

import net.kyori.adventure.text.Component;

/**
 * Ponytail: M — ported verbatim from AuthMeBia's {@code notifications/Toast}.
 * A toast definition parsed from {@code notifications.toasts} in config.yml.
 * Each entry binds a name to a trigger {@link Check}, display components, an
 * optional sound, a revoke delay, and an advancement {@link Frame}.
 */
public record Toast(
        String name,
        Check check,
        Component title,
        Component content,
        String icon,
        String sound,
        int delaySeconds,
        Frame frame) {

    /** When a toast fires. */
    public enum Check {
        FIRST_REGISTER,
        FIRST_LOGIN,
        FIRST_MESSAGE,
        FIRST_ADVANCEMENT;

        private static final java.util.Map<String, Check> BY_KEY = java.util.Map.of(
                "first_register", FIRST_REGISTER,
                "first_login", FIRST_LOGIN,
                "first_message", FIRST_MESSAGE,
                "first_advancement", FIRST_ADVANCEMENT);

        public static Check parse(String s) {
            if (s == null) return null;
            return BY_KEY.get(s.toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** Advancement frame shape; controls the toast's icon styling. */
    public enum Frame {
        TASK,
        GOAL,
        CHALLENGE;

        public static Frame parse(String s) {
            if (s == null) return TASK;
            return switch (s.toLowerCase(java.util.Locale.ROOT)) {
                case "goal" -> GOAL;
                case "challenge" -> CHALLENGE;
                default -> TASK;
            };
        }

        public String jsonValue() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
