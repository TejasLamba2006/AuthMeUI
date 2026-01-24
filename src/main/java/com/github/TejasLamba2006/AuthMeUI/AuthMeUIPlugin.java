package com.github.TejasLamba2006.AuthMeUI;

import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge;
import com.github.TejasLamba2006.AuthMeUI.commands.AuthMeUICommand;
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
        this.dialogManager = new DialogManager(this, settingsManager);

        if (!authBridge.isConnected()) {
            getLogger().log(Level.SEVERE, "Failed to connect to AuthMe! Plugin functionality will be limited.");
        }
    }

    private void registerEventHandlers() {
        PluginManager pluginManager = getServer().getPluginManager();

        // Create the dialog interaction listener
        DialogInteractionListener dialogInteractionListener = new DialogInteractionListener(
                this, authBridge, dialogManager, settingsManager);

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
                    new PlayerSessionListener(this, authBridge, dialogManager),
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
