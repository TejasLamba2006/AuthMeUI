package com.github.TejasLamba2006.AuthMeUI.conflict;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Detects whether the running AuthMe is 6.0+ and has its own built-in dialog
 * UI enabled, which would collide with AuthMeUI's dialogs (two dialogs at once).
 *
 * <p>AuthMe 6.0.x gates its dialog under {@code settings.registration.dialog}
 * with {@code preJoin.enable} and {@code postJoin.enable}. AuthMeUI runs in
 * exactly one of those two modes, so only the matching toggle is a conflict.
 *
 * <p>This is runtime-only detection: the compile dependency is AuthMe 5.7.x
 * (codemc), but 6.0.x keeps the same v3 API surface, so reading its config
 * via {@link Plugin#getConfig()} works at runtime regardless.
 */
public final class DialogConflictGuard {

    private static final String AUTHME_PLUGIN_NAME = "AuthMe";
    private static final int AUTHME_MAJOR_WITH_DIALOG = 6;
    private static final String PREJOIN_ENABLE_PATH = "settings.registration.dialog.preJoin.enable";
    private static final String POSTJOIN_ENABLE_PATH = "settings.registration.dialog.postJoin.enable";

    private final AuthMeUIPlugin plugin;
    private final Logger logger;

    public DialogConflictGuard(AuthMeUIPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Check AuthMe for a dialog conflict and log a prominent warning if one exists.
     *
     * @param useConfigurationPhase true if AuthMeUI is in pre-join (config-phase) mode
     */
    public void check(boolean useConfigurationPhase) {
        Plugin authMe = plugin.getServer().getPluginManager().getPlugin(AUTHME_PLUGIN_NAME);
        if (authMe == null || !authMe.isEnabled()) {
            return;
        }

        Integer majorVersion = parseMajorVersion(authMe.getDescription().getVersion());
        if (majorVersion == null || majorVersion < AUTHME_MAJOR_WITH_DIALOG) {
            // AuthMe < 6.0 has no built-in dialog UI — no conflict possible.
            return;
        }

        FileConfiguration authConfig = authMe.getConfig();
        // AuthMe 6.0 defaults both to true, so treat absent keys as enabled.
        boolean authMePreJoin = authConfig.getBoolean(PREJOIN_ENABLE_PATH, true);
        boolean authMePostJoin = authConfig.getBoolean(POSTJOIN_ENABLE_PATH, true);

        boolean conflict = useConfigurationPhase ? authMePreJoin : authMePostJoin;
        if (!conflict) {
            return;
        }

        String authMeMode = useConfigurationPhase ? "preJoin" : "postJoin";
        String authMePath = useConfigurationPhase ? PREJOIN_ENABLE_PATH : POSTJOIN_ENABLE_PATH;
        String ourMode = useConfigurationPhase
                ? "dialogs.use-configuration-phase"
                : "post-join (default)";

        logger.severe("==============================================================");
        logger.severe(" AuthMeUI: DIALOG CONFLICT DETECTED with AuthMe " + authMe.getDescription().getVersion());
        logger.severe(" AuthMe's built-in dialog is enabled (" + authMePath + " = true)");
        logger.severe(" and AuthMeUI is running in the same mode (" + ourMode + ").");
        logger.severe(" Two dialogs will appear at once — players will be unable to authenticate.");
        logger.severe("");
        logger.severe(" Fix ONE of:");
        logger.severe("   1. In AuthMe's config.yml, set " + authMePath + ": false");
        logger.severe("      (keeps AuthMeUI's dialogs, disables AuthMe's built-in)");
        logger.severe("   2. In AuthMeUI's config.yml, set dialogs.use-configuration-phase: false");
        logger.severe("      and disable AuthMeUI, letting AuthMe handle dialogs natively");
        logger.severe(" AuthMe's " + authMeMode + " dialog toggle is on by default since 6.0.0.");
        logger.severe("==============================================================");
    }

    // ponytail: package-private for the self-check test; not part of public API.
    static Integer parseMajorVersion(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        // Strip any suffix like "6.0.1-SNAPSHOT" or "6.0.0-R1" → "6.0.1"
        String trimmed = version.trim().split("[\\s-]")[0];
        int dot = trimmed.indexOf('.');
        String major = (dot > 0) ? trimmed.substring(0, dot) : trimmed;
        try {
            return Integer.parseInt(major);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
