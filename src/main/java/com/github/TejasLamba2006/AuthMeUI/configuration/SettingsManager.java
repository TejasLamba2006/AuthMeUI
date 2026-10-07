package com.github.TejasLamba2006.AuthMeUI.configuration;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import com.github.TejasLamba2006.AuthMeUI.dialogs.IconSpec;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
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
    // ponytail: copy-to-clipboard aliases — accept both kebab and snake so
    // admins don't trip on YAML key conventions. Clipboard text comes from
    // the same 'template'/'text' field used by other action types.
    private static final String ACTION_TYPE_COPY = "copy";
    private static final String ACTION_TYPE_COPY_DASH = "copy-to-clipboard";
    private static final String ACTION_TYPE_COPY_UNDERSCORE = "copy_to_clipboard";
    private static final String ACTION_TEXT_KEY = "text";
    private static final String ACTION_LABEL_DEFAULT = "<gray>Button</gray>";
    private static final String ACTION_COMMAND_DEFAULT = "/help";
    private static final String URL_ACTION_WARNING = "Skipping URL action in %s: %s";
    private static final String COPY_ACTION_WARNING = "Skipping copy action in %s: %s";
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

    /**
     * Ponytail: player-aware width/columns (feature O). When the bedrock
     * dialog override is enabled and the player is a Floodgate bedrock player,
     * return the bedrock-specific width/columns instead of the Java defaults.
     * Mirrors AuthMeBia's {@code BedrockCfg} per-UUID override, but applied
     * at the call site rather than via a Cfg subclass (AuthMeUI's builders
     * hold one {@code settings} instance, not a per-player Cfg).
     */
    public int getButtonColumns(org.bukkit.entity.Player player) {
        if (player != null && isBedrockDialogOverrideEnabled()
                && com.github.TejasLamba2006.AuthMeUI.authentication.Floodgate
                        .isFloodgatePlayer(player.getUniqueId())) {
            return getBedrockButtonColumns();
        }
        return getButtonColumns();
    }

    public int getInputWidth(org.bukkit.entity.Player player) {
        if (player != null && isBedrockDialogOverrideEnabled()
                && com.github.TejasLamba2006.AuthMeUI.authentication.Floodgate
                        .isFloodgatePlayer(player.getUniqueId())) {
            return getBedrockInputWidth();
        }
        return getInputWidth();
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

    // ==================== Link Button Layout ====================

    /**
     * Layout mode for link (url/copy) buttons. {@code grouped} places them
     * in the main action grid alongside other buttons; {@code separated} is
     * accepted as an alias and currently renders identically — Paper's
     * multiAction dialog type only supports a single button grid, so a
     * truly separated layout would require a second dialog. The ceiling is
     * documented here; upgrade by splitting into a follow-up dialog if a
     * server needs the visual separation.
     */
    public String getLinkButtonLayout() {
        String raw = config.getString("dialogs.link-button-layout", "grouped");
        return (raw != null && !raw.isBlank()) ? raw.toLowerCase(Locale.ROOT) : "grouped";
    }

    // ==================== Per-Button Sounds ====================

    /**
     * Get the sound string configured for a dialog action id, or null.
     * Action ids: {@code login_submit}, {@code login_cancel},
     * {@code login_forgot}, {@code register_submit}, {@code rules_confirm},
     * {@code support}. Format: {@code "namespace:path volume pitch"}
     * (e.g. {@code "minecraft:ui.button.click 1 1"}).
     */
    public String getDialogSound(String actionId) {
        String raw = config.getString("dialog-sounds." + actionId, null);
        return (raw != null && !raw.isBlank()) ? raw : null;
    }

    /**
     * Parse a configured sound string into an Adventure {@link Sound}, or null
     * on parse failure (which is logged once per shape).
     * Format: {@code "namespace:path volume pitch"} — volume and pitch are
     * optional (default 1.0 / 1.0). Source is always {@link Sound.Source#MASTER}.
     */
    public Sound parseSound(String raw, String actionId) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split("\\s+");
        try {
            Key key = Key.key(parts[0]);
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            return Sound.sound(key, Sound.Source.MASTER, volume, pitch);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Invalid sound '" + raw + "' for dialog-sounds." + actionId + " (expected 'namespace:path [volume] [pitch]')");
            return null;
        }
    }

    // ==================== IP Guard ====================

    public boolean isIpGuardEnabled() {
        return config.getBoolean("ip-guard.enabled", true);
    }

    public int getIpGuardMaxFailures() {
        return Math.max(1, config.getInt("ip-guard.max-failures", 5));
    }

    /**
     * Escalating ban durations in seconds. Defaults to
     * {@code [300, 1800, 3600, 86400]} (5m, 30m, 1h, 1d).
     */
    public List<Integer> getIpGuardDurations() {
        List<Integer> durations = config.getIntegerList("ip-guard.durations");
        return (durations != null && !durations.isEmpty()) ? durations : List.of(300, 1800, 3600, 86400);
    }

    // ==================== Captcha (D) ====================

    public boolean isCaptchaEnabled() {
        return config.getBoolean("captcha.enabled", false);
    }

    public int getCaptchaCodeLength() {
        return Math.max(4, Math.min(10, config.getInt("captcha.code-length", 5)));
    }

    public long getCaptchaTrustDurationSeconds() {
        return Math.max(0, config.getLong("captcha.trust-duration-seconds", 600));
    }

    public int getCaptchaFailuresBeforeTrigger() {
        return Math.max(0, config.getInt("captcha.failures-before-trigger", 0));
    }

    public Component getCaptchaTitle() {
        String raw = config.getString("captcha.title", "<white><bold>Are you human?</bold></white>");
        return miniMessage.deserialize(raw);
    }

    public List<String> getCaptchaBodyRaw() {
        List<String> body = config.getStringList("captcha.body");
        return body.isEmpty() ? List.of("<gray>Please type the code below to continue:</gray>",
                "<dark_gray><bold>%code%</bold></dark_gray>") : body;
    }

    public String getCaptchaInputLabel() {
        String raw = config.getString("captcha.input-label", "Captcha code");
        return (raw != null && !raw.isBlank()) ? raw : "Captcha code";
    }

    public Component getCaptchaSubmitButton() {
        String raw = config.getString("captcha.submit-button", "<green>Verify</green>");
        return miniMessage.deserialize(raw);
    }

    // ==================== TOTP 2FA (E) ====================

    public boolean isTotp2faEnabled() {
        return config.getBoolean("totp-2fa.enabled", false);
    }

    public boolean isTotpAttemptsEnabled() {
        return config.getBoolean("totp-2fa.attempts-enabled", true);
    }

    public int getTotpMaxTries() {
        return Math.max(1, config.getInt("totp-2fa.max-tries", 5));
    }

    public Component getTotpTitle() {
        String raw = config.getString("totp-2fa.title", "<gold>Two-Factor Authentication</gold>");
        return miniMessage.deserialize(raw);
    }

    public List<String> getTotpBodyRaw() {
        List<String> body = config.getStringList("totp-2fa.body");
        return body.isEmpty() ? List.of("<gray>Enter your 6-digit authenticator code:</gray>") : body;
    }

    public String getTotpInputLabel() {
        String raw = config.getString("totp-2fa.input-label", "Authenticator code");
        return (raw != null && !raw.isBlank()) ? raw : "Authenticator code";
    }

    public Component getTotpSubmitButton() {
        String raw = config.getString("totp-2fa.submit-button", "<green>Verify</green>");
        return miniMessage.deserialize(raw);
    }

    public Component getTotpCancelButton() {
        String raw = config.getString("totp-2fa.cancel-button", "<red>Cancel</red>");
        return miniMessage.deserialize(raw);
    }

    // ==================== Forgot password / email recovery (F) ====================

    public boolean isForgotPasswordEnabled() {
        return config.getBoolean("forgot-password.enabled", false);
    }

    // ==================== Premium password-set (K) ====================

    public boolean isPremiumSetEnabled() {
        return config.getBoolean("premium-set.enabled", false);
    }

    public String getPremiumSetTitle() {
        return config.getString("premium-set.title",
                "<white><bold>Set Your Password</bold></white>");
    }

    public List<String> getPremiumSetBodyRaw() {
        List<String> body = config.getStringList("premium-set.body");
        return body.isEmpty()
                ? List.of("<gray>You logged in via a premium account. Set a password so you can sign in if that ever changes.</gray>")
                : body;
    }

    public String getPremiumSetSubmitButton() {
        return config.getString("premium-set.submit-button", "<green>Save Password</green>");
    }

    // ==================== Admin-forced reset (L) ====================

    public String getRecoverDialogTitle() {
        return config.getString("recover-dialog.title",
                "<white><bold>Reset Your Password</bold></white>");
    }

    // ==================== Discord webhook (N) ====================

    public boolean isDiscordWebhookEnabled() {
        return config.getBoolean("discord.enabled", false);
    }

    public String getDiscordWebhookUrl() {
        return config.getString("discord.webhook-url", "");
    }

    public boolean isDiscordNotifyLogin() {
        return config.getBoolean("discord.events.login", true);
    }

    public boolean isDiscordNotifyRegister() {
        return config.getBoolean("discord.events.register", true);
    }

    public boolean isDiscordNotifyLogout() {
        return config.getBoolean("discord.events.logout", false);
    }

    public boolean isDiscordNotifyAdminReset() {
        return config.getBoolean("discord.events.admin-reset", true);
    }

    public int getDiscordLoginColor() {
        return parseColor(config.getString("discord.colors.login", "#57F287"));
    }

    public int getDiscordRegisterColor() {
        return parseColor(config.getString("discord.colors.register", "#3498DB"));
    }

    public int getDiscordLogoutColor() {
        return parseColor(config.getString("discord.colors.logout", "#95A5A6"));
    }

    public int getDiscordAdminResetColor() {
        return parseColor(config.getString("discord.colors.admin-reset", "#ED4245"));
    }

    private int parseColor(String hex) {
        if (hex == null || hex.isBlank()) return 0x57F287;
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        try {
            return Integer.parseInt(h, 16);
        } catch (NumberFormatException ex) {
            return 0x57F287;
        }
    }

    // ==================== Bedrock / Floodgate overrides (O)
    // ====================
    public boolean isBedrockAutoLoginEnabled() {
        return config.getBoolean("bedrock.auto-login", false);
    }

    public BedrockAutoLoginMode getBedrockAutoLoginMode() {
        return BedrockAutoLoginMode.parse(config.getString("bedrock.auto-login-mode", "link"));
    }

    public boolean isBedrockDialogOverrideEnabled() {
        return config.getBoolean("bedrock.dialog-override.enabled", false);
    }

    public int getBedrockButtonColumns() {
        return Math.max(1, Math.min(8, config.getInt("bedrock.dialog-override.button-columns", 2)));
    }

    public int getBedrockInputWidth() {
        return Math.max(40, Math.min(400, config.getInt("bedrock.dialog-override.input-width", 300)));
    }

    public enum BedrockAutoLoginMode {
        LINK,
        GEYSER;
        public static BedrockAutoLoginMode parse(String raw) {
            if (raw == null) return LINK;
            return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "geyser" -> GEYSER;
                default -> LINK;
            };
        }
    }

    public Component getForgotEmailTitle() {
        String raw = config.getString("forgot-password.email-title", "<gold>Forgot Password</gold>");
        return miniMessage.deserialize(raw);
    }

    public List<String> getForgotEmailBodyRaw() {
        List<String> body = config.getStringList("forgot-password.email-body");
        return body.isEmpty() ? List.of("<gray>Enter the email linked to your account.</gray>") : body;
    }

    public String getForgotEmailLabel() {
        String raw = config.getString("forgot-password.email-label", "Email");
        return (raw != null && !raw.isBlank()) ? raw : "Email";
    }

    public Component getForgotEmailSubmitButton() {
        String raw = config.getString("forgot-password.email-submit-button", "<green>Send Code</green>");
        return miniMessage.deserialize(raw);
    }

    public Component getForgotBackButton() {
        String raw = config.getString("forgot-password.back-button", "<red>Back</red>");
        return miniMessage.deserialize(raw);
    }

    public Component getForgotNoEmailTitle() {
        String raw = config.getString("forgot-password.no-email-title", "<gold>No Email Linked</gold>");
        return miniMessage.deserialize(raw);
    }

    public List<String> getForgotNoEmailBodyRaw() {
        List<String> body = config.getStringList("forgot-password.no-email-body");
        return body.isEmpty()
                ? List.of("<red>No email is linked to this account. Please contact an admin.</red>")
                : body;
    }

    public int getEmailCodeLength() {
        return Math.max(4, Math.min(8, config.getInt("forgot-password.code-length", 6)));
    }

    public int getEmailResendCooldown() {
        return Math.max(10, Math.min(600, config.getInt("forgot-password.resend-cooldown", 60)));
    }

    public int getEmailMaxTries() {
        return Math.max(1, config.getInt("forgot-password.max-tries", 5));
    }

    public Component getEmailVerifyTitle() {
        String raw = config.getString("forgot-password.verify-title", "<gold>Verify Your Email</gold>");
        return miniMessage.deserialize(raw);
    }

    public List<String> getEmailVerifyBodyRaw() {
        List<String> body = config.getStringList("forgot-password.verify-body");
        return body.isEmpty()
                ? List.of("<gray>A code was sent to {email}. Enter it below.</gray>")
                : body;
    }

    public String getEmailCodeLabel() {
        String raw = config.getString("forgot-password.code-label", "Code");
        return (raw != null && !raw.isBlank()) ? raw : "Code";
    }

    public Component getEmailVerifyButton() {
        String raw = config.getString("forgot-password.verify-button", "<green>Verify</green>");
        return miniMessage.deserialize(raw);
    }

    public Component getEmailResendButton() {
        String raw = config.getString("forgot-password.resend-button", "<yellow>Resend code</yellow>");
        return miniMessage.deserialize(raw);
    }

    public Component getEmailResendCooldownLabel(long remaining) {
        String raw = config.getString("forgot-password.resend-cooldown-label",
                "<gray>Resend ({cooldown}s)</gray>");
        String resolved = raw.replace("{cooldown}", String.valueOf(Math.max(0, remaining)));
        return miniMessage.deserialize(resolved);
    }

    public Component getRecoverTitle() {
        String raw = config.getString("forgot-password.recover-title", "<gold>Reset Password</gold>");
        return miniMessage.deserialize(raw);
    }

    public List<String> getRecoverBodyRaw() {
        List<String> body = config.getStringList("forgot-password.recover-body");
        return body.isEmpty()
                ? List.of("<gray>Enter a new password for your account.</gray>")
                : body;
    }

    public String getRecoverNewPasswordLabel() {
        String raw = config.getString("forgot-password.recover-new-label", "New password");
        return (raw != null && !raw.isBlank()) ? raw : "New password";
    }

    public String getRecoverConfirmPasswordLabel() {
        String raw = config.getString("forgot-password.recover-confirm-label", "Confirm password");
        return (raw != null && !raw.isBlank()) ? raw : "Confirm password";
    }

    public Component getRecoverSubmitButton() {
        String raw = config.getString("forgot-password.recover-submit-button", "<green>Set Password</green>");
        return miniMessage.deserialize(raw);
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

        // ponytail: per-call <icons:name> resolver so PLAYER_HEAD's {player}
        // resolves to the actual viewing player's name (null for config-phase
        // Audience rendering, where the name isn't known at this layer).
        String playerName = player == null ? null : player.getName();
        TagResolver iconsResolver = TagResolver.resolver("icons", (args, ctx) -> {
            String name = args.popOr("icons tag requires a name, e.g. <icons:trial_key>").value();
            IconSpec spec = icon(name);
            if (spec == null) return Tag.selfClosingInserting(Component.empty());
            Component inline = spec.toInlineComponent(playerName);
            return Tag.selfClosingInserting(inline == null ? Component.empty() : inline);
        });

        return miniMessage.deserialize(resolvedText, iconsResolver);
    }

    /**
     * Look up a named entry under {@code custom_icons} in config. Returns
     * null for missing/malformed entries (never throws — a bad icon is
     * treated as "no icon" so it can't break surrounding text).
     */
    public IconSpec icon(String name) {
        if (name == null || name.isBlank()) return null;
        ConfigurationSection section = config.getConfigurationSection("custom_icons." + name);
        if (section == null) return null;
        return IconSpec.parse(section.getValues(false));
    }

    /**
     * Parse all enabled custom_screens entries (feature J). Returns a list
     * of {@link com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen}
     * in config order; disabled entries are skipped.
     */
    public java.util.List<com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen> getCustomScreens() {
        java.util.List<com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen> result = new java.util.ArrayList<>();
        List<Map<?, ?>> raw = config.getMapList("custom_screens");
        int defaultWidth = getButtonColumns() * 100;
        for (Map<?, ?> map : raw) {
            com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen screen =
                    com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen.parse(map, defaultWidth);
            if (screen != null && screen.enabled()) {
                result.add(screen);
            }
        }
        return result;
    }

    /**
     * Look up a custom screen by id (feature J). Returns null if not found
     * or disabled.
     */
    public com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen getCustomScreen(String id) {
        if (id == null || id.isBlank()) return null;
        for (com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen screen : getCustomScreens()) {
            if (screen.id().equalsIgnoreCase(id)) return screen;
        }
        return null;
    }

    /**
     * Index of a custom screen in the parsed list (feature J), or -1.
     * Used to bind the NBT payload {@code {"s":idx,"b":btnIdx}} on manual
     * /authmeui screen &lt;id&gt; shows.
     */
    public int getCustomScreenIndex(String id) {
        if (id == null || id.isBlank()) return -1;
        java.util.List<com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen> screens = getCustomScreens();
        for (int i = 0; i < screens.size(); i++) {
            if (screens.get(i).id().equalsIgnoreCase(id)) return i;
        }
        return -1;
    }

    /**
     * Ids of all enabled custom screens (feature J), for tab completion.
     */
    public java.util.List<String> getCustomScreenIds() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen screen : getCustomScreens()) {
            ids.add(screen.id());
        }
        return ids;
    }

    /**
     * Button-column count for custom-screen dialogs (feature J).
     */
    public int getCustomScreenColumns() {
        return Math.max(1, config.getInt("custom_screens_columns", 2));
    }

    // ==================== Toast notifications (M) ====================

    /**
     * Ponytail: M — parse {@code notifications.toasts} entries (feature M).
     * Returns {@link com.github.TejasLamba2006.AuthMeUI.notifications.Toast}
     * records in config order; entries missing a name or check are skipped.
     * Title/content are MiniMessage-parsed; {@code {player}} in title is
     * replaced with the viewing player's name at fire time (passed to
     * {@link #parseText(String, Player)} when the toast fires — see
     * {@link com.github.TejasLamba2006.AuthMeUI.notifications.ToastListener#showToast}).
     * Raw strings are stored here and re-parsed per-player at fire time so
     * {player} resolves correctly; the Toast record holds pre-parsed
     * components for the no-player case (config-phase), which is the same
     * tradeoff AuthMeBia makes.
     */
    public java.util.List<com.github.TejasLamba2006.AuthMeUI.notifications.Toast> getToasts() {
        java.util.List<com.github.TejasLamba2006.AuthMeUI.notifications.Toast> result = new java.util.ArrayList<>();
        List<Map<?, ?>> raw = config.getMapList("notifications.toasts");
        for (Map<?, ?> map : raw) {
            com.github.TejasLamba2006.AuthMeUI.notifications.Toast toast = buildToast(map);
            if (toast != null) result.add(toast);
        }
        return result;
    }

    private com.github.TejasLamba2006.AuthMeUI.notifications.Toast buildToast(Map<?, ?> map) {
        if (map == null) return null;
        String name = map.get("name") instanceof String s ? s : null;
        if (name == null || name.isBlank()) return null;
        String checkStr = map.get("check") instanceof String s ? s : null;
        com.github.TejasLamba2006.AuthMeUI.notifications.Toast.Check check =
                com.github.TejasLamba2006.AuthMeUI.notifications.Toast.Check.parse(checkStr);
        if (check == null) return null;

        String titleRaw = map.get("title") instanceof String s ? s : "";
        String contentRaw = map.get("content") instanceof String s ? s : "";
        // {player} is resolved per-player at fire time by ToastListener;
        // here we parse the no-player form so the record always carries a
        // non-null Component. ToastListener re-parses with the player when
        // it fires, so this is only the fallback shape.
        Component title = parseText(titleRaw.replace("{player}", ""), null);
        Component content = parseText(contentRaw.replace("{player}", ""), null);

        String icon = map.get("icon") instanceof String s ? s : "minecraft:player_head";
        String sound = map.get("sound") instanceof String s ? s : null;
        int delay = map.get("delay") instanceof Number n ? n.intValue() : 5;
        String frameStr = map.get("frame") instanceof String s ? s : "task";
        com.github.TejasLamba2006.AuthMeUI.notifications.Toast.Frame frame =
                com.github.TejasLamba2006.AuthMeUI.notifications.Toast.Frame.parse(frameStr);

        return new com.github.TejasLamba2006.AuthMeUI.notifications.Toast(
                name, check, title, content, icon, sound, delay, frame);
    }

    /**
     * Ponytail: M — re-parse a toast's title/content with the firing player
     * (so {player} and PlaceholderAPI resolve correctly). Returns the
     * per-player Toast with refreshed title/content components.
     */
    public com.github.TejasLamba2006.AuthMeUI.notifications.Toast resolveToastForPlayer(
            com.github.TejasLamba2006.AuthMeUI.notifications.Toast toast, Player player) {
        // Re-derive the raw strings from the (no-player) components is lossy;
        // instead re-read from config so {player} is intact.
        List<Map<?, ?>> raw = config.getMapList("notifications.toasts");
        for (Map<?, ?> map : raw) {
            String name = map.get("name") instanceof String s ? s : null;
            if (name == null || !name.equalsIgnoreCase(toast.name())) continue;
            String titleRaw = map.get("title") instanceof String s ? s : "";
            String contentRaw = map.get("content") instanceof String s ? s : "";
            Component title = parseText(titleRaw, player);
            Component content = parseText(contentRaw, player);
            return new com.github.TejasLamba2006.AuthMeUI.notifications.Toast(
                    toast.name(), toast.check(), title, content,
                    toast.icon(), toast.sound(), toast.delaySeconds(), toast.frame());
        }
        return toast; // config changed between parse and fire — use the stored form
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
            case ACTION_TYPE_COPY, ACTION_TYPE_COPY_DASH, ACTION_TYPE_COPY_UNDERSCORE -> {
                ActionButton copyActionButton = buildCopyActionButton(label, actionMap, actionPath);
                if (copyActionButton != null) {
                    buttons.add(copyActionButton);
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
            case ACTION_TYPE_COPY, ACTION_TYPE_COPY_DASH, ACTION_TYPE_COPY_UNDERSCORE -> {
                ActionButton copyActionButton = buildCopyActionButton(label, actionMap, LOGIN_ACTIONS_PATH);
                if (copyActionButton != null) {
                    buttons.add(copyActionButton);
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

    /**
     * Build an action button that copies text to the player's clipboard on click.
     * Reads the clipboard text from the {@code template} or {@code text} field.
     */
    private ActionButton buildCopyActionButton(Component label, Map<?, ?> actionMap, String actionPath) {
        String copyText = extractString(actionMap, ACTION_TEMPLATE_KEY,
                extractString(actionMap, ACTION_TEXT_KEY, "")).trim();
        if (copyText.isBlank()) {
            plugin.getLogger().log(Level.WARNING, () -> COPY_ACTION_WARNING.formatted(
                    actionPath,
                    "'template' (or 'text') is empty."));
            return null;
        }

        return ActionButton.builder(label)
                .action(DialogAction.staticAction(ClickEvent.copyToClipboard(copyText)))
                .build();
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

    // ==================== Auth input modes: PIN & Slider (G+H) ====================

    public enum AuthMode { PASSWORD, PIN, SLIDER;

        public static AuthMode parse(String raw) {
            if (raw == null) return PASSWORD;
            return switch (raw.trim().toLowerCase()) {
                case "pin" -> PIN;
                case "slider" -> SLIDER;
                default -> PASSWORD;
            };
        }
    }

    public AuthMode getAuthMode() {
        // auth-mode is a section (pin/slider settings); the mode lives in auth-mode.type.
        // A plain "auth-mode: pin" string from older configs still works.
        return AuthMode.parse(config.isString("auth-mode")
                ? config.getString("auth-mode")
                : config.getString("auth-mode.type", "password"));
    }

    public int getPinLength() {
        return Math.max(4, Math.min(8, config.getInt("auth-mode.pin.length", 4)));
    }

    public Component getPinTitle() {
        return miniMessage.deserialize(config.getString("auth-mode.pin.title", "<gold>Enter your PIN</gold>"));
    }

    public Component getPinConfirmButton() {
        return miniMessage.deserialize(config.getString("auth-mode.pin.confirm-button", "<green>Confirm</green>"));
    }

    public Component getPinDeleteButton() {
        return miniMessage.deserialize(config.getString("auth-mode.pin.delete-button", "<red>⌫ Delete</red>"));
    }

    public int getPinButtonWidth() {
        return Math.max(40, Math.min(300, config.getInt("auth-mode.pin.button-width", 100)));
    }

    public Component getPinLogoutButton() {
        return miniMessage.deserialize(config.getString("auth-mode.pin.logout-button", "<red>Cancel</red>"));
    }

    public List<String> getPinBodyRaw() {
        List<String> body = config.getStringList("auth-mode.pin.body");
        return body.isEmpty() ? List.of("<gray>Tap the digits to enter your PIN.</gray>") : body;
    }

    public int getSliderLength() {
        return Math.max(4, Math.min(8, config.getInt("auth-mode.slider.length", 4)));
    }

    public Component getSliderTitle() {
        return miniMessage.deserialize(config.getString("auth-mode.slider.title", "<gold>Enter your code</gold>"));
    }

    public Component getSliderConfirmButton() {
        return miniMessage.deserialize(config.getString("auth-mode.slider.confirm-button", "<green>Confirm</green>"));
    }

    public int getSliderButtonWidth() {
        return Math.max(40, Math.min(300, config.getInt("auth-mode.slider.button-width", 100)));
    }

    public Component getSliderLogoutButton() {
        return miniMessage.deserialize(config.getString("auth-mode.slider.logout-button", "<red>Cancel</red>"));
    }

    public List<String> getSliderBodyRaw() {
        List<String> body = config.getStringList("auth-mode.slider.body");
        return body.isEmpty() ? List.of("<gray>Use ▲/▼ to set each digit.</gray>") : body;
    }
}
