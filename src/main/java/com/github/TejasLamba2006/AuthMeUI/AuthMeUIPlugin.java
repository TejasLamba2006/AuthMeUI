package com.github.TejasLamba2006.AuthMeUI;

import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge;
import com.github.TejasLamba2006.AuthMeUI.commands.AuthMeUICommand;
import com.github.TejasLamba2006.AuthMeUI.conflict.DialogConflictGuard;
import com.github.TejasLamba2006.AuthMeUI.configuration.SettingsManager;
import com.github.TejasLamba2006.AuthMeUI.dialogs.DialogManager;
import com.github.TejasLamba2006.AuthMeUI.listeners.AuthenticationStateListener;
import com.github.TejasLamba2006.AuthMeUI.listeners.ConfigurationPhaseListener;
import com.github.TejasLamba2006.AuthMeUI.listeners.DialogInteractionListener;
import com.github.TejasLamba2006.AuthMeUI.listeners.PlayerSessionListener;
import com.github.TejasLamba2006.AuthMeUI.statistics.AnalyticsHandler;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class AuthMeUIPlugin extends JavaPlugin {

    private static AuthMeUIPlugin instance;

    private SettingsManager settingsManager;
    private AuthenticationBridge authBridge;
    private DialogManager dialogManager;
    private AnalyticsHandler analyticsHandler;
    private ConfigurationPhaseListener configurationPhaseListener;

    @Override
    public void onEnable() {
        instance = this;

        initializeConfiguration();
        initializeServices();
        registerEventHandlers();
        registerCommands();
        initializeAnalytics();

        // Warn if AuthMe 6.0+ has its own dialog UI enabled in the same mode as us.
        new DialogConflictGuard(this).check(settingsManager.useConfigurationPhase());

        // ponytail: O — warn if bedrock auto-login is set to GEYSER mode,
        // which trusts any bedrock connection with no linking verification.
        warnIfBedrockGeyserModeUnsafe();

        logStartupBanner();
    }

    @Override
    public void onDisable() {
        getLogger().info("AuthMeUI has been disabled. Goodbye!");
        instance = null;
    }

    private void initializeConfiguration() {
        saveDefaultConfig();
        this.settingsManager = new SettingsManager(this);
    }

    private void initializeServices() {
        this.authBridge = new AuthenticationBridge(this);
        this.dialogManager = new DialogManager(settingsManager, authBridge);

        if (!authBridge.isConnected()) {
            getLogger().log(Level.SEVERE, "Failed to connect to AuthMe! Plugin functionality will be limited.");
        }
    }

    private void registerEventHandlers() {
        PluginManager pluginManager = getServer().getPluginManager();

        // Create the dialog interaction listener
        DialogInteractionListener dialogInteractionListener = new DialogInteractionListener(
                this, authBridge, dialogManager, settingsManager);

        // Bind the captcha trust window so DialogManager can gate login dialogs.
        dialogManager.bindCaptcha(dialogInteractionListener.getCaptcha());

        // ponytail: bind the custom-screen dismiss store (feature J) so both
        // the dialog facade (showCustomScreen) and the listener's click
        // handler share the same in-memory + flat-file persistence.
        dialogManager.bindScreenDismissStore(
                new com.github.TejasLamba2006.AuthMeUI.dialogs.ScreenDismissStore(this));

        // ponytail: K+L — bind the admin-forced-reset flag store so both
        // the facade (presentAuthDialog recover branch) and the listener's
        // RECOVER_SUBMIT handler share one instance.
        dialogManager.bindRecoverStore(
                new com.github.TejasLamba2006.AuthMeUI.dialogs.RecoverStore(this));

        // ponytail: N — bind the shared Discord webhook sender so auth-event
        // listeners + the admin-reset command can fire embeds off a single
        // async-safe HttpClient (no okhttp/gson dep; java.net.http only).
        dialogManager.bindDiscordWebhook(
                new com.github.TejasLamba2006.AuthMeUI.webhook.DiscordWebhook(this));

        // ponytail: M — bind the toast store (flat-file "shown" persistence)
        // and register the toast listener. Toasts fire on AuthMe register/
        // login events (first_register/first_login) and via advancement/
        // message triggers, so the listener is registered in both modes.
        com.github.TejasLamba2006.AuthMeUI.notifications.ToastStore toastStore =
                new com.github.TejasLamba2006.AuthMeUI.notifications.ToastStore(this);
        pluginManager.registerEvents(
                new com.github.TejasLamba2006.AuthMeUI.notifications.ToastListener(
                        this, authBridge, settingsManager, toastStore),
                this);

        // Create and register configuration phase listener if enabled
        if (settingsManager.useConfigurationPhase()) {
            configurationPhaseListener = new ConfigurationPhaseListener(
                    this, authBridge, dialogManager, settingsManager);

            // Wire up the configuration phase listener to the dialog interaction listener
            dialogInteractionListener.setConfigurationPhaseListener(configurationPhaseListener);

            pluginManager.registerEvents(configurationPhaseListener, this);

            getLogger().info("Configuration phase authentication enabled - dialogs will be shown before players join.");
        } else {
            // Register the player session listener for post-join authentication
            pluginManager.registerEvents(
                    new PlayerSessionListener(this, authBridge, dialogManager, settingsManager),
                    this);
        }

        // Always register the dialog interaction listener
        pluginManager.registerEvents(dialogInteractionListener, this);

        // Register the authentication state listener
        pluginManager.registerEvents(
                new AuthenticationStateListener(this, authBridge, dialogManager),
                this);
    }

    private void registerCommands() {
        AuthMeUICommand commandHandler = new AuthMeUICommand(settingsManager, authBridge, dialogManager);
        Objects.requireNonNull(getCommand("authmeui")).setExecutor(commandHandler);
        Objects.requireNonNull(getCommand("authmeui")).setTabCompleter(commandHandler);
    }

    private void initializeAnalytics() {
        if (settingsManager.isMetricsEnabled()) {
            this.analyticsHandler = new AnalyticsHandler(this);
        }
    }

    private void logStartupBanner() {
        getLogger().info("========================================");
        getLogger().info("  AuthMeUI v" + getDescription().getVersion());
        getLogger().info("  Author: TejasLamba2006");
        getLogger().info("  Modern Dialog Authentication for AuthMe");
        getLogger().info("========================================");
        if (settingsManager.useConfigurationPhase()) {
            getLogger().info("Mode: Configuration Phase (pre-join authentication)");
        } else {
            getLogger().info("Mode: In-Game (post-join authentication)");
        }
        getLogger().info("Plugin enabled successfully!");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        settingsManager.reload();
        // Re-check conflict after reload — admin may have flipped modes.
        new DialogConflictGuard(this).check(settingsManager.useConfigurationPhase());
    }

    /**
     * Ponytail: O — ported from AuthMeBia's warnIfBedrockGeyserModeUnsafe.
     * GEYSER auto-login mode trusts any bedrock connection with no account
     * linking; only safe when Geyser itself runs auth-type=online.
     */
    private void warnIfBedrockGeyserModeUnsafe() {
        if (!settingsManager.isBedrockAutoLoginEnabled()) return;
        if (settingsManager.getBedrockAutoLoginMode()
                != SettingsManager.BedrockAutoLoginMode.GEYSER) return;

        getLogger().warning("bedrock.auto-login-mode is set to 'geyser'. This trusts ANY Bedrock/Geyser "
                + "connection with NO account-linking verification -- it is only safe if Geyser's own "
                + "auth-type is set to 'online' (real Xbox Live sign-in) in Geyser's config.yml. If Geyser "
                + "is running in offline mode, this setting lets anyone auto-login as any Bedrock identity "
                + "with no password. See the bedrock.auto-login-mode comment in config.yml for details.");
    }

    public static AuthMeUIPlugin getInstance() {
        return instance;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public AuthenticationBridge getAuthBridge() {
        return authBridge;
    }

    public DialogManager getDialogManager() {
        return dialogManager;
    }

    public ConfigurationPhaseListener getConfigurationPhaseListener() {
        return configurationPhaseListener;
    }
}
