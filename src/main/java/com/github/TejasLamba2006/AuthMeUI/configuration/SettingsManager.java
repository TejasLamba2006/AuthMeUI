package com.github.TejasLamba2006.AuthMeUI.configuration;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public class SettingsManager {

    private static final String LOGIN_ACTIONS_PATH = "login-dialog.actions";
    private static final String ACTION_TYPE_KEY = "type";
    private static final String ACTION_LABEL_KEY = "label";
    private static final String ACTION_TEMPLATE_KEY = "template";
    private static final String ACTION_URL_KEY = "url";
    private static final String ACTION_TYPE_SUBMIT = "submit";
    private static final String ACTION_TYPE_CANCEL = "cancel";
    private static final String ACTION_TYPE_FORGOT = "forgot";
    private static final String ACTION_TYPE_COMMAND = "command";
    private static final String ACTION_TYPE_URL = "url";
    private static final String ACTION_TYPE_OPEN_URL_DASH = "open-url";
    private static final String ACTION_TYPE_OPEN_URL_UNDERSCORE = "open_url";
    private static final String ACTION_LABEL_DEFAULT = "<gray>Button</gray>";
    private static final String ACTION_COMMAND_DEFAULT = "/help";
    private static final String URL_ACTION_WARNING = "Skipping URL action in %s: %s";
    private static final String ITEMS_ADDER_PLUGIN_NAME = "ItemsAdder";
    private static final String ITEMS_ADDER_FONT_WRAPPER_CLASS = "dev.lone.itemsadder.api.FontImages.FontImageWrapper";
    private static final String ITEMS_ADDER_FONT_REPLACE_METHOD = "replaceFontImages";

    private final AuthMeUIPlugin plugin;
    private final MiniMessage miniMessage;
    private final AtomicBoolean placeholderApiErrorLogged;
    private final AtomicBoolean itemsAdderErrorLogged;
    private final AtomicBoolean itemsAdderLookupAttempted;
    private final AtomicReference<Method> itemsAdderReplaceMethod;
    private FileConfiguration config;

    public SettingsManager(AuthMeUIPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.placeholderApiErrorLogged = new AtomicBoolean(false);
        this.itemsAdderErrorLogged = new AtomicBoolean(false);
        this.itemsAdderLookupAttempted = new AtomicBoolean(false);
        this.itemsAdderReplaceMethod = new AtomicReference<>();
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

    public boolean isLoginForgotEnabled() {
        return config.getBoolean("login-dialog.forgot-button-enabled", true);
    }

    public Component getLoginForgotButton() {
        String raw = config.getString("login-dialog.forgot-button", "<yellow>Forgot Password?</yellow>");
        return miniMessage.deserialize(raw);
    }

    public String getLoginForgotCommandTemplate() {
        String raw = config.getString("login-dialog.forgot-button-template", "/email recover");
        if (raw == null || raw.isBlank()) {
            return "/email recover";
        }
        return raw;
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

    public Component getRegisterEmailLabel() {
        String raw = config.getString("register-dialog.email-label", "Email Address");
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
        return parseText(raw, null);
    }

    public Component parseText(String raw, Player player) {
        String resolvedText = raw != null ? raw : "";

        if (!resolvedText.isEmpty()) {
            resolvedText = applyPlaceholderApi(resolvedText, player);
            resolvedText = applyItemsAdderFontImages(resolvedText);
        }

        return miniMessage.deserialize(resolvedText);
    }

    private String applyPlaceholderApi(String text, Player player) {
        if (player == null || !plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }

        try {
            return PlaceholderAPI.setPlaceholders(player, text);
        } catch (Exception exception) {
            if (placeholderApiErrorLogged.compareAndSet(false, true)) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to parse PlaceholderAPI placeholders for dialog text.",
                        exception);
            }
            return text;
        }
    }

    private String applyItemsAdderFontImages(String text) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled(ITEMS_ADDER_PLUGIN_NAME)) {
            return text;
        }

        Method replaceMethod = resolveItemsAdderReplaceMethod();
        if (replaceMethod == null) {
            return text;
        }

        try {
            Object result = replaceMethod.invoke(null, text);
            if (result instanceof String replacedText) {
                return replacedText;
            }
            return text;
        } catch (Exception exception) {
            if (itemsAdderErrorLogged.compareAndSet(false, true)) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to parse ItemsAdder font placeholders for dialog text.",
                        exception);
            }
            return text;
        }
    }

    private Method resolveItemsAdderReplaceMethod() {
        if (itemsAdderLookupAttempted.get()) {
            return itemsAdderReplaceMethod.get();
        }

        if (!itemsAdderLookupAttempted.compareAndSet(false, true)) {
            return itemsAdderReplaceMethod.get();
        }

        try {
            Class<?> fontWrapperClass = Class.forName(ITEMS_ADDER_FONT_WRAPPER_CLASS);
            Method method = fontWrapperClass.getMethod(ITEMS_ADDER_FONT_REPLACE_METHOD, String.class);
            if (Modifier.isStatic(method.getModifiers())
                    && Objects.equals(method.getReturnType(), String.class)) {
                itemsAdderReplaceMethod.set(method);
            }
        } catch (ReflectiveOperationException ignored) {
            itemsAdderReplaceMethod.set(null);
        }

        return itemsAdderReplaceMethod.get();
    }

    public List<ActionButton> buildActionButtons(String configSection, ActionButton primaryAction) {
        List<ActionButton> buttons = new ArrayList<>();
        List<Map<?, ?>> rawActions = config.getMapList(configSection + ".actions");
        String actionPath = configSection + ".actions";

        boolean hasPrimaryAction = false;

        if (!rawActions.isEmpty()) {
            for (Map<?, ?> actionMap : rawActions) {
                if (processConfiguredAction(buttons, actionMap, primaryAction, actionPath)) {
                    hasPrimaryAction = true;
                }
            }
        }

        if (!hasPrimaryAction) {
            buttons.addFirst(primaryAction);
        }

        return buttons;
    }

    public List<ActionButton> buildLoginActionButtons(
            ActionButton submitAction,
            ActionButton cancelAction,
            ActionButton forgotAction) {

        List<ActionButton> buttons = new ArrayList<>();
        List<Map<?, ?>> rawActions = config.getMapList(LOGIN_ACTIONS_PATH);

        boolean hasSubmitAction = false;
        boolean hasCancelAction = false;
        boolean hasForgotAction = false;
        boolean cancelEnabled = isLoginCancelEnabled();
        boolean forgotEnabled = isLoginForgotEnabled();

        if (!rawActions.isEmpty()) {
            for (Map<?, ?> actionMap : rawActions) {
                LoginActionResult result = processLoginConfiguredAction(
                        buttons,
                        actionMap,
                        submitAction,
                        cancelAction,
                        forgotAction,
                        cancelEnabled,
                        forgotEnabled);
                hasSubmitAction = hasSubmitAction || result.submitHandled();
                hasCancelAction = hasCancelAction || result.cancelHandled();
                hasForgotAction = hasForgotAction || result.forgotHandled();
            }
        }

        if (!hasSubmitAction) {
            buttons.addFirst(submitAction);
        }

        if (forgotEnabled && !hasForgotAction) {
            buttons.add(forgotAction);
        }

        if (cancelEnabled && !hasCancelAction) {
            buttons.add(cancelAction);
        }

        return buttons;
    }

    private boolean processConfiguredAction(
            List<ActionButton> buttons,
            Map<?, ?> actionMap,
            ActionButton primaryAction,
            String actionPath) {

        String type = extractString(actionMap, ACTION_TYPE_KEY, ACTION_TYPE_SUBMIT).toLowerCase(Locale.ROOT);
        Component label = miniMessage.deserialize(extractString(actionMap, ACTION_LABEL_KEY, ACTION_LABEL_DEFAULT));

        switch (type) {
            case ACTION_TYPE_SUBMIT -> {
                buttons.add(primaryAction);
                return true;
            }
            case ACTION_TYPE_COMMAND -> buttons.add(buildCommandActionButton(label, actionMap));
            case ACTION_TYPE_URL, ACTION_TYPE_OPEN_URL_DASH, ACTION_TYPE_OPEN_URL_UNDERSCORE -> {
                ActionButton urlActionButton = buildUrlActionButton(label, actionMap, actionPath);
                if (urlActionButton != null) {
                    buttons.add(urlActionButton);
                }
            }
            default -> {
                return false;
            }
        }

        return false;
    }

    private LoginActionResult processLoginConfiguredAction(
            List<ActionButton> buttons,
            Map<?, ?> actionMap,
            ActionButton submitAction,
            ActionButton cancelAction,
            ActionButton forgotAction,
            boolean cancelEnabled,
            boolean forgotEnabled) {

        String type = extractString(actionMap, ACTION_TYPE_KEY, ACTION_TYPE_SUBMIT).toLowerCase(Locale.ROOT);
        Component label = miniMessage.deserialize(extractString(actionMap, ACTION_LABEL_KEY, ACTION_LABEL_DEFAULT));

        switch (type) {
            case ACTION_TYPE_SUBMIT -> {
                buttons.add(submitAction);
                return LoginActionResult.SUBMIT;
            }
            case ACTION_TYPE_CANCEL -> {
                if (!cancelEnabled) {
                    return LoginActionResult.NONE;
                }

                buttons.add(cancelAction);
                return LoginActionResult.CANCEL;
            }
            case ACTION_TYPE_FORGOT -> {
                if (!forgotEnabled) {
                    return LoginActionResult.NONE;
                }

                buttons.add(forgotAction);
                return LoginActionResult.FORGOT;
            }
            case ACTION_TYPE_COMMAND -> buttons.add(buildCommandActionButton(label, actionMap));
            case ACTION_TYPE_URL, ACTION_TYPE_OPEN_URL_DASH, ACTION_TYPE_OPEN_URL_UNDERSCORE -> {
                ActionButton urlActionButton = buildUrlActionButton(label, actionMap, LOGIN_ACTIONS_PATH);
                if (urlActionButton != null) {
                    buttons.add(urlActionButton);
                }
            }
            default -> {
                return LoginActionResult.NONE;
            }
        }

        return LoginActionResult.NONE;
    }

    private ActionButton buildCommandActionButton(Component label, Map<?, ?> actionMap) {
        String commandTemplate = extractString(actionMap, ACTION_TEMPLATE_KEY, ACTION_COMMAND_DEFAULT);
        return ActionButton.builder(label)
                .action(DialogAction.commandTemplate(commandTemplate))
                .build();
    }

    private ActionButton buildUrlActionButton(Component label, Map<?, ?> actionMap, String actionPath) {
        String rawUrl = extractString(actionMap, ACTION_TEMPLATE_KEY, extractString(actionMap, ACTION_URL_KEY, ""))
                .trim();
        if (rawUrl.isBlank()) {
            plugin.getLogger().log(Level.WARNING, () -> URL_ACTION_WARNING.formatted(
                    actionPath,
                    "'template' (or 'url') is empty."));
            return null;
        }

        try {
            URI uri = URI.create(rawUrl);
            String scheme = uri.getScheme();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                plugin.getLogger().log(Level.WARNING, () -> URL_ACTION_WARNING.formatted(
                        actionPath,
                        "only http/https URLs are supported (got '" + rawUrl + "')."));
                return null;
            }

            return ActionButton.builder(label)
                    .action(DialogAction.staticAction(ClickEvent.openUrl(uri.toString())))
                    .build();
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().log(Level.WARNING, () -> URL_ACTION_WARNING.formatted(
                    actionPath,
                    "invalid URL '" + rawUrl + "'."));
            return null;
        }
    }

    private enum LoginActionResult {
        NONE(false, false, false),
        SUBMIT(true, false, false),
        CANCEL(false, true, false),
        FORGOT(false, false, true);

        private final boolean submitHandled;
        private final boolean cancelHandled;
        private final boolean forgotHandled;

        LoginActionResult(boolean submitHandled, boolean cancelHandled, boolean forgotHandled) {
            this.submitHandled = submitHandled;
            this.cancelHandled = cancelHandled;
            this.forgotHandled = forgotHandled;
        }

        public boolean submitHandled() {
            return submitHandled;
        }

        public boolean cancelHandled() {
            return cancelHandled;
        }

        public boolean forgotHandled() {
            return forgotHandled;
        }
    }

    private String extractString(Map<?, ?> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : defaultValue;
    }
}
