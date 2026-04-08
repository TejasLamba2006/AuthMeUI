package com.github.TejasLamba2006.AuthMeUI.configuration;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SettingsManager {

    private final AuthMeUIPlugin plugin;
    private final MiniMessage miniMessage;
    private FileConfiguration config;

    public SettingsManager(AuthMeUIPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public boolean useConfigurationPhase() {
        return config.getBoolean("dialogs.use-configuration-phase", false);
    }

    public int getConfigurationPhaseTimeout() {
        return config.getInt("dialogs.configuration-phase-timeout", 60);
    }

    public boolean respectAuthMeSessionsInConfigurationPhase() {
        return config.getBoolean("dialogs.configuration-phase-respect-authme-sessions", true);
    }

    public boolean isFastLoginCompatibilityEnabled() {
        return config.getBoolean("dialogs.configuration-phase-fastlogin-compatibility", true);
    }

    public int getConfigurationPhaseDeferredLoginCheckDelayTicks() {
        return Math.max(0, config.getInt("dialogs.configuration-phase-deferred-login-check-delay-ticks", 40));
    }

    public boolean canCloseWithEscape() {
        return config.getBoolean("dialogs.allow-escape-close", false);
    }

    public int getButtonColumns() {
        return config.getInt("dialogs.button-columns", 2);
    }

    public int getInputWidth() {
        return config.getInt("dialogs.input-width", 150);
    }

    public Component getLoginTitle() {
        String raw = config.getString("login-dialog.title", "<white><bold>Welcome Back!</bold></white>");
        return miniMessage.deserialize(raw);
    }

    public List<String> getLoginBodyRaw() {
        return config.getStringList("login-dialog.body");
    }

    public Component getLoginPasswordLabel() {
        String raw = config.getString("login-dialog.password-label", "Password");
        return miniMessage.deserialize(raw);
    }

    public Component getLoginSubmitButton() {
        String raw = config.getString("login-dialog.submit-button", "<green>Sign In</green>");
        return miniMessage.deserialize(raw);
    }

    public boolean isLoginCancelEnabled() {
        return config.getBoolean("login-dialog.cancel-button-enabled", true);
    }

    public Component getLoginCancelButton() {
        String raw = config.getString("login-dialog.cancel-button", "<red>Cancel</red>");
        return miniMessage.deserialize(raw);
    }

    public Component getRegisterTitle() {
        String raw = config.getString("register-dialog.title", "<white><bold>Create Account</bold></white>");
        return miniMessage.deserialize(raw);
    }

    public List<String> getRegisterBodyRaw() {
        return config.getStringList("register-dialog.body");
    }

    public Component getRegisterPasswordLabel() {
        String raw = config.getString("register-dialog.password-label", "Password");
        return miniMessage.deserialize(raw);
    }

    public Component getRegisterConfirmLabel() {
        String raw = config.getString("register-dialog.confirm-label", "Confirm Password");
        return miniMessage.deserialize(raw);
    }

    public Component getRegisterSubmitButton() {
        String raw = config.getString("register-dialog.submit-button", "<green>Register</green>");
        return miniMessage.deserialize(raw);
    }

    public boolean isRulesDialogEnabled() {
        return config.getBoolean("rules-dialog.enabled", true);
    }

    public Component getRulesTitle() {
        String raw = config.getString("rules-dialog.title", "<white><bold>Server Rules</bold></white>");
        return miniMessage.deserialize(raw);
    }

    public List<String> getRulesBodyRaw() {
        return config.getStringList("rules-dialog.body");
    }

    public boolean isAgreementRequired() {
        return config.getBoolean("rules-dialog.agreement.enabled", true);
    }

    public String getAgreementKey() {
        String key = config.getString("rules-dialog.agreement.checkbox-key", "rules_accepted");
        return (key != null && !key.isBlank()) ? key : "rules_accepted";
    }

    public Component getAgreementLabel() {
        String raw = config.getString("rules-dialog.agreement.label",
                "<gray>I have read and agree to the server rules</gray>");
        return miniMessage.deserialize(raw);
    }

    public Component getRulesConfirmButton() {
        String raw = config.getString("rules-dialog.confirm-button", "<green>I Accept</green>");
        return miniMessage.deserialize(raw);
    }

    public boolean isMetricsEnabled() {
        return config.getBoolean("metrics.enabled", true);
    }

    public Component getMessage(String path, String defaultValue) {
        String raw = config.getString("messages." + path, defaultValue);
        return miniMessage.deserialize(raw);
    }

    public Component getMessage(String path, String defaultValue, Map<String, String> replacements) {
        String raw = config.getString("messages." + path, defaultValue);

        if (replacements != null) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
            }
        }

        return miniMessage.deserialize(raw);
    }

    public Component parseText(String raw) {
        return miniMessage.deserialize(raw != null ? raw : "");
    }

    public List<ActionButton> buildActionButtons(String configSection, ActionButton primaryAction) {
        List<ActionButton> buttons = new ArrayList<>();
        List<Map<?, ?>> rawActions = config.getMapList(configSection + ".actions");

        boolean hasPrimaryAction = false;

        if (rawActions != null && !rawActions.isEmpty()) {
            for (Map<?, ?> actionMap : rawActions) {
                String type = extractString(actionMap, "type", "submit").toLowerCase(Locale.ROOT);
                String labelText = extractString(actionMap, "label", "<gray>Button</gray>");
                Component label = miniMessage.deserialize(labelText);

                switch (type) {
                    case "submit" -> {
                        buttons.add(primaryAction);
                        hasPrimaryAction = true;
                    }
                    case "command" -> {
                        String commandTemplate = extractString(actionMap, "template", "/help");
                        buttons.add(
                                ActionButton.builder(label)
                                        .action(DialogAction.commandTemplate(commandTemplate))
                                        .build());
                    }
                }
            }
        }

        if (!hasPrimaryAction) {
            buttons.addFirst(primaryAction);
        }

        return buttons;
    }

    public List<ActionButton> buildLoginActionButtons(ActionButton submitAction, ActionButton cancelAction) {
        List<ActionButton> buttons = new ArrayList<>();
        List<Map<?, ?>> rawActions = config.getMapList("login-dialog.actions");

        boolean hasSubmitAction = false;
        boolean hasCancelAction = false;
        boolean cancelEnabled = isLoginCancelEnabled();

        if (rawActions != null && !rawActions.isEmpty()) {
            for (Map<?, ?> actionMap : rawActions) {
                String type = extractString(actionMap, "type", "submit").toLowerCase(Locale.ROOT);
                String labelText = extractString(actionMap, "label", "<gray>Button</gray>");
                Component label = miniMessage.deserialize(labelText);

                switch (type) {
                    case "submit" -> {
                        buttons.add(submitAction);
                        hasSubmitAction = true;
                    }
                    case "cancel" -> {
                        if (cancelEnabled) {
                            buttons.add(cancelAction);
                            hasCancelAction = true;
                        }
                    }
                    case "command" -> {
                        String commandTemplate = extractString(actionMap, "template", "/help");
                        buttons.add(
                                ActionButton.builder(label)
                                        .action(DialogAction.commandTemplate(commandTemplate))
                                        .build());
                    }
                }
            }
        }

        if (!hasSubmitAction) {
            buttons.addFirst(submitAction);
        }

        if (cancelEnabled && !hasCancelAction) {
            buttons.add(cancelAction);
        }

        return buttons;
    }

    private String extractString(Map<?, ?> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }
}
