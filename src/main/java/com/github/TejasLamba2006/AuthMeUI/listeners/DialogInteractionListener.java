package com.github.TejasLamba2006.AuthMeUI.listeners;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge;
import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge.AuthResult;
import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge.RegistrationResult;
import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge.RegistrationSecondArgMode;
import com.github.TejasLamba2006.AuthMeUI.authentication.Captcha;
import com.github.TejasLamba2006.AuthMeUI.authentication.IpGuard;
import com.github.TejasLamba2006.AuthMeUI.configuration.SettingsManager;
import com.github.TejasLamba2006.AuthMeUI.dialogs.CaptchaDialogBuilder;
import com.github.TejasLamba2006.AuthMeUI.dialogs.DialogIdentifiers;
import com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen;
import com.github.TejasLamba2006.AuthMeUI.dialogs.DialogManager;
import com.github.TejasLamba2006.AuthMeUI.dialogs.EmailVerifyDialogBuilder;
import com.github.TejasLamba2006.AuthMeUI.dialogs.ForgotDialogBuilder;
import com.github.TejasLamba2006.AuthMeUI.dialogs.PinDialogBuilder;
import com.github.TejasLamba2006.AuthMeUI.dialogs.RecoverDialogBuilder;
import com.github.TejasLamba2006.AuthMeUI.dialogs.ScreenDismissStore;
import com.github.TejasLamba2006.AuthMeUI.dialogs.TwoFactorDialogBuilder;
import io.papermc.paper.connection.PlayerCommonConnection;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class DialogInteractionListener implements Listener {

    private static final String PASSWORD_FIELD = "password";
    private static final String CONFIRM_FIELD = "confirm";
    private static final int REGISTRATION_VERIFICATION_ATTEMPTS = 5;
    private static final long REGISTRATION_VERIFICATION_DELAY_TICKS = 8L;

    private final AuthMeUIPlugin plugin;
    private final AuthenticationBridge authBridge;
    private final DialogManager dialogManager;
    private final SettingsManager settings;
    private final IpGuard ipGuard;
    private final Captcha captcha;

    // ponytail: per-player (by UUID) login-failure counter for the captcha
    // trigger. Separate from IpGuard's IP-based counter: captcha is about
    // the individual session's failure streak, IpGuard about the IP.
    private final Map<UUID, AtomicInteger> playerLoginFailures = new ConcurrentHashMap<>();

    // ponytail: per-player wrong-TOTP-code counter. Mirrors AuthMeBia's
    // wrongTries AtomicInteger; kicks the player once it reaches max-tries.
    private final Map<UUID, AtomicInteger> playerTotpTries = new ConcurrentHashMap<>();

    // ponytail: per-player forgot-password session state (event-driven analog
    // of AuthMeBia's ForgotPasswordSession, which blocks on a CountDownLatch).
    // Holds email + expected code + lastSent millis (resend cooldown) + wrong
    // code counter between dialog clicks.
    private final Map<UUID, ForgotPasswordSession> forgotSessions = new ConcurrentHashMap<>();

    // ponytail: PIN/Slider input-mode session state (G+H). PIN keeps a
    // StringBuilder of entered digits; Slider keeps an int[] of per-slot
    // digits. Both persist between dialog re-renders (event-driven analog
    // of AuthMeBia's AuthInput PIN_SESSIONS/SLIDER_SESSIONS maps).
    private final Map<UUID, StringBuilder> pinSessions = new ConcurrentHashMap<>();
    private final Map<UUID, int[]> sliderSessions = new ConcurrentHashMap<>();

    // ponytail: K+L — the recover-mode state (why this player is seeing the
    // recover/password-set dialog) lives on DialogManager, which owns the
    // recover/premium stores and sets the mode when it routes a player into
    // the recover flow. This class just reads + clears it on submit.
    // Absent entry = forgot-password flow (return to login, the original F path).

    private ConfigurationPhaseListener configPhaseListener;

    public DialogInteractionListener(
            AuthMeUIPlugin plugin,
            AuthenticationBridge authBridge,
            DialogManager dialogManager,
            SettingsManager settings) {
        this.plugin = plugin;
        this.authBridge = authBridge;
        this.dialogManager = dialogManager;
        this.settings = settings;
        this.ipGuard = new IpGuard(plugin);
        this.captcha = new Captcha();
    }

    /**
     * Set the configuration phase listener for coordinating authentication.
     */
    public void setConfigurationPhaseListener(ConfigurationPhaseListener listener) {
        this.configPhaseListener = listener;
    }

    /**
     * Expose the captcha trust-window instance so {@link DialogManager} can
     * gate login dialogs behind it.
     */
    public Captcha getCaptcha() {
        return captcha;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDialogInteraction(PlayerCustomClickEvent event) {
        PlayerCommonConnection connection = event.getCommonConnection();
        Key actionKey = event.getIdentifier();
        DialogResponseView responseView = event.getDialogResponseView();

        // Per-button sounds: play the configured sound for this action on the
        // clicking player/audience before routing the interaction.
        String actionId = resolveActionId(actionKey);
        if (actionId != null) {
            playDialogSound(actionId, connection);
        }

        // Handle configuration phase connections
        if (connection instanceof PlayerConfigurationConnection configConnection) {
            handleConfigurationPhaseInteraction(configConnection, actionKey, responseView, event.getTag());
            return;
        }

        // Handle in-game connections
        if (!(connection instanceof PlayerGameConnection gameConnection)) {
            return;
        }

        Player player = gameConnection.getPlayer();

        if (actionKey.equals(DialogIdentifiers.LOGIN_CANCEL)) {
            handleLoginCancel(player);
            return;
        }

        if (actionKey.equals(DialogIdentifiers.LOGIN_FORGOT)) {
            handleLoginForgot(player);
            return;
        }

        // ponytail: custom-screen buttons carry no required text input
        // (CLOSE/COMMAND/CONSOLE), so route before the responseView==null
        // guard. The optional dismiss-forever checkbox is read defensively.
        if (actionKey.equals(DialogIdentifiers.CUSTOM_SCREEN_BUTTON)) {
            handleCustomScreenButton(player, responseView, event.getTag());
            return;
        }

        if (responseView == null) {
            return;
        }

        if (actionKey.equals(DialogIdentifiers.LOGIN_SUBMIT)) {
            handleLoginSubmission(player, responseView);
        } else if (actionKey.equals(DialogIdentifiers.CAPTCHA_SUBMIT)) {
            handleCaptchaSubmit(player, responseView);
        } else if (actionKey.equals(DialogIdentifiers.TOTP_SUBMIT)) {
            handleTotpSubmit(player, responseView);
        } else if (actionKey.equals(DialogIdentifiers.TOTP_CANCEL)) {
            handleLoginCancel(player);
        } else if (actionKey.equals(DialogIdentifiers.REGISTER_SUBMIT)) {
            handleRegistrationSubmission(player, responseView);
        } else if (actionKey.equals(DialogIdentifiers.RULES_CONFIRM)) {
            handleRulesConfirmation(player, responseView);
        } else if (actionKey.equals(DialogIdentifiers.SUPPORT_ACTION)) {
            handleSupportAction(player);
        } else if (actionKey.equals(DialogIdentifiers.FORGOT_SUBMIT)) {
            handleForgotEmailSubmit(player, responseView);
        } else if (actionKey.equals(DialogIdentifiers.FORGOT_BACK)) {
            handleForgotBack(player);
        } else if (actionKey.equals(DialogIdentifiers.FORGOT_VERIFY)) {
            handleForgotVerify(player, responseView);
        } else if (actionKey.equals(DialogIdentifiers.FORGOT_RESEND)) {
            handleForgotResend(player);
        } else if (actionKey.equals(DialogIdentifiers.RECOVER_SUBMIT)) {
            handleRecoverSubmit(player, responseView);
        } else if (actionKey.equals(DialogIdentifiers.PIN_INPUT)) {
            handlePinDigit(player, event.getTag(), false);
        } else if (actionKey.equals(DialogIdentifiers.PIN_DELETE)) {
            handlePinDigit(player, null, true);
        } else if (actionKey.equals(DialogIdentifiers.PIN_CONFIRM)) {
            handlePinConfirm(player);
        } else if (actionKey.equals(DialogIdentifiers.SLIDER_UP)) {
            handleSliderStep(player, event.getTag(), +1);
        } else if (actionKey.equals(DialogIdentifiers.SLIDER_DOWN)) {
            handleSliderStep(player, event.getTag(), -1);
        } else if (actionKey.equals(DialogIdentifiers.SLIDER_CONFIRM)) {
            handleSliderConfirm(player);
        }
    }

    // ==================== Configuration Phase Handlers ====================

    private void handleConfigurationPhaseInteraction(
            PlayerConfigurationConnection connection,
            Key actionKey,
            DialogResponseView responseView,
            BinaryTagHolder clickTag) {

        UUID uniqueId = connection.getProfile().getId();
        String playerName = connection.getProfile().getName();
        Audience audience = connection.getAudience();

        if (uniqueId == null || playerName == null) {
            return;
        }

        if (actionKey.equals(DialogIdentifiers.LOGIN_CANCEL)) {
            handleConfigPhaseCancel(uniqueId);
            return;
        }

        if (actionKey.equals(DialogIdentifiers.LOGIN_FORGOT)) {
            handleConfigPhaseForgot(audience, playerName, uniqueId);
            return;
        }

        // ponytail: PIN/Slider clicks carry no text-input response view (the
        // "input" is the button grid itself), so route them before the
        // responseView == null guard that the text-field handlers need.
        if (actionKey.equals(DialogIdentifiers.PIN_INPUT)) {
            handleConfigPhasePinDigit(connection, uniqueId, playerName, audience, clickTag, false);
            return;
        }
        if (actionKey.equals(DialogIdentifiers.PIN_DELETE)) {
            handleConfigPhasePinDigit(connection, uniqueId, playerName, audience, null, true);
            return;
        }
        if (actionKey.equals(DialogIdentifiers.PIN_CONFIRM)) {
            handleConfigPhasePinConfirm(connection, uniqueId, playerName, audience);
            return;
        }
        if (actionKey.equals(DialogIdentifiers.SLIDER_UP)) {
            handleConfigPhaseSliderStep(connection, uniqueId, playerName, audience, clickTag, +1);
            return;
        }
        if (actionKey.equals(DialogIdentifiers.SLIDER_DOWN)) {
            handleConfigPhaseSliderStep(connection, uniqueId, playerName, audience, clickTag, -1);
            return;
        }
        if (actionKey.equals(DialogIdentifiers.SLIDER_CONFIRM)) {
            handleConfigPhaseSliderConfirm(connection, uniqueId, playerName, audience);
            return;
        }

        // ponytail: custom-screen buttons carry no required text input, so
        // route before the responseView==null guard. PREJOIN-trigger screens
        // are shown during config phase; COMMAND buttons are no-ops here
        // (no Player exists yet), CONSOLE works via the console sender.
        if (actionKey.equals(DialogIdentifiers.CUSTOM_SCREEN_BUTTON)) {
            handleConfigPhaseCustomScreenButton(
                    connection, uniqueId, playerName, audience, responseView, clickTag);
            return;
        }

        if (responseView == null) {
            return;
        }

        if (actionKey.equals(DialogIdentifiers.LOGIN_SUBMIT)) {
            handleConfigPhaseLogin(connection, uniqueId, playerName, audience, responseView);
        } else if (actionKey.equals(DialogIdentifiers.CAPTCHA_SUBMIT)) {
            handleConfigPhaseCaptchaSubmit(connection, uniqueId, playerName, audience, responseView);
        } else if (actionKey.equals(DialogIdentifiers.TOTP_SUBMIT)) {
            handleConfigPhaseTotpSubmit(connection, uniqueId, playerName, audience, responseView);
        } else if (actionKey.equals(DialogIdentifiers.TOTP_CANCEL)) {
            handleConfigPhaseCancel(uniqueId);
        } else if (actionKey.equals(DialogIdentifiers.REGISTER_SUBMIT)) {
            handleConfigPhaseRegistration(connection, uniqueId, playerName, audience, responseView);
        } else if (actionKey.equals(DialogIdentifiers.RULES_CONFIRM)) {
            handleConfigPhaseRulesConfirmation(connection, uniqueId, playerName, audience, responseView);
        } else if (actionKey.equals(DialogIdentifiers.FORGOT_SUBMIT)) {
            handleConfigPhaseForgotEmailSubmit(connection, uniqueId, playerName, audience, responseView);
        } else if (actionKey.equals(DialogIdentifiers.FORGOT_BACK)) {
            handleConfigPhaseForgotBack(audience, playerName);
        } else if (actionKey.equals(DialogIdentifiers.FORGOT_VERIFY)) {
            handleConfigPhaseForgotVerify(connection, uniqueId, playerName, audience, responseView);
        } else if (actionKey.equals(DialogIdentifiers.FORGOT_RESEND)) {
            handleConfigPhaseForgotResend(connection, uniqueId, playerName, audience);
        } else if (actionKey.equals(DialogIdentifiers.RECOVER_SUBMIT)) {
            handleConfigPhaseRecoverSubmit(connection, uniqueId, playerName, audience, responseView);
        }
    }

    private void handleConfigPhaseLogin(
            PlayerConfigurationConnection connection,
            UUID uniqueId,
            String playerName,
            Audience audience,
            DialogResponseView response) {

        String enteredPassword = response.getText(PASSWORD_FIELD);

        if (isNullOrEmpty(enteredPassword)) {
            Component error = settings.getMessage("login.password-empty", "<red>Please enter your password!</red>");
            audience.showDialog(dialogManager.createLoginDialogForAudience(playerName, error));
            return;
        }

        // ponytail: delegate to the shared config-phase credential check
        // (also used by PIN/slider confirm) — not-registered gate, 2FA
        // deferral, captcha escalation, all in one place.
        checkLoginCodeConfigPhase(connection, uniqueId, playerName, audience, enteredPassword);
    }

    private void handleConfigPhaseRegistration(
            PlayerConfigurationConnection connection,
            UUID uniqueId,
            String playerName,
            Audience audience,
            DialogResponseView response) {

        String enteredPassword = response.getText(PASSWORD_FIELD);
        String secondArgument = response.getText(CONFIRM_FIELD);

        if (isNullOrEmpty(enteredPassword)) {
            Component error = settings.getMessage("register.password-required",
                    "<red>Please enter a password.</red>");
            audience.showDialog(dialogManager.createRegistrationDialogForAudience(playerName, error));
            return;
        }

        RegistrationResult result = authBridge.attemptRegistrationByName(playerName, enteredPassword, secondArgument);

        switch (result) {
            case SUCCESS -> {
                // Complete the configuration phase authentication
                if (configPhaseListener != null) {
                    configPhaseListener.updateRegistrationStatus(uniqueId, true);
                    configPhaseListener.completeAuthentication(uniqueId, true);
                }
            }
            case ALREADY_EXISTS -> {
                Component error = settings.getMessage("register.already-registered",
                        "<yellow>You already have an account. Please login instead!</yellow>");
                if (configPhaseListener != null) {
                    configPhaseListener.updateRegistrationStatus(uniqueId, true);
                }
                audience.showDialog(dialogManager.createLoginDialogForAudience(playerName, error));
            }
            case PASSWORD_MISMATCH -> {
                Component error = settings.getMessage("register.passwords-mismatch",
                        "<red>Passwords do not match. Please try again.</red>");
                audience.showDialog(dialogManager.createRegistrationDialogForAudience(playerName, error));
            }
            case PASSWORD_TOO_SHORT -> {
                int minLength = authBridge.fetchMinPasswordLength();
                Component error = settings.getMessage("register.password-too-short",
                        "<red>Password must be at least <white>%min%</white> characters!</red>",
                        Map.of("min", String.valueOf(minLength)));
                audience.showDialog(dialogManager.createRegistrationDialogForAudience(playerName, error));
            }
            case PASSWORD_TOO_LONG -> {
                int maxLength = authBridge.fetchMaxPasswordLength();
                Component error = settings.getMessage("register.password-too-long",
                        "<red>Password cannot exceed <white>%max%</white> characters!</red>",
                        Map.of("max", String.valueOf(maxLength)));
                audience.showDialog(dialogManager.createRegistrationDialogForAudience(playerName, error));
            }
            case SECOND_ARGUMENT_REQUIRED -> {
                Component error = settings.getMessage("register.confirm-required",
                        "<red>Please confirm your password.</red>");
                audience.showDialog(dialogManager.createRegistrationDialogForAudience(playerName, error));
            }
            case EMAIL_REQUIRED -> {
                Component error = settings.getMessage("register.email-required",
                        "<red>Please enter your email address.</red>");
                audience.showDialog(dialogManager.createRegistrationDialogForAudience(playerName, error));
            }
            case EMAIL_INVALID -> {
                Component error = settings.getMessage("register.email-invalid",
                        "<red>Please enter a valid email address.</red>");
                audience.showDialog(dialogManager.createRegistrationDialogForAudience(playerName, error));
            }
            case UNSUPPORTED_IN_CONFIGURATION_PHASE -> {
                Component error = settings.getMessage("register.prejoin-email-unsupported",
                        "<red>Email-based registration is not supported in pre-join mode. Please use in-game mode.</red>");
                audience.showDialog(dialogManager.createRegistrationDialogForAudience(playerName, error));
            }
            case INVALID_PASSWORD -> {
                Component error = settings.getMessage("register.password-invalid",
                        "<red>Invalid password format. Please try again.</red>");
                audience.showDialog(dialogManager.createRegistrationDialogForAudience(playerName, error));
            }
            default -> {
                Component error = settings.getMessage("register.failed",
                        "<red>Registration failed. Please try again.</red>");
                audience.showDialog(dialogManager.createRegistrationDialogForAudience(playerName, error));
            }
        }
    }

    private void handleConfigPhaseRulesConfirmation(
            PlayerConfigurationConnection connection,
            UUID uniqueId,
            String playerName,
            Audience audience,
            DialogResponseView response) {

        if (settings.isAgreementRequired()) {
            Boolean hasAgreed = response.getBoolean(settings.getAgreementKey());

            if (hasAgreed == null || !hasAgreed) {
                // Re-show the rules dialog
                audience.showDialog(dialogManager.createRulesDialogForAudience(playerName));
                return;
            }
        }

        // Show registration dialog
        audience.showDialog(dialogManager.createRegistrationDialogForAudience(playerName));
    }

    private void handleConfigPhaseCancel(UUID uniqueId) {
        if (configPhaseListener != null) {
            configPhaseListener.cancelAuthentication(uniqueId);
        }
    }

    // ==================== PIN / Slider input modes (G+H) ====================
    //
    // ponytail: AuthMeBia uses blocking server-side DialogActionCallback
    // closures with PIN_SESSIONS/SLIDER_SESSIONS maps. AuthMeUI is event-
    // driven, so we reuse the same session maps but mutate them on each
    // PlayerCustomClickEvent and re-render the dialog. The digit (PIN) or
    // slot index (slider) is carried in the click's NBT additions payload,
    // decoded here via event.getTag() / the clickTag param.

    private static int decodePayload(BinaryTagHolder tag, String key, int fallback) {
        if (tag == null) {
            return fallback;
        }
        try {
            CompoundBinaryTag compound = TagStringIO.get().asCompound(tag.string());
            return compound.getInt(key, fallback);
        } catch (Exception e) {
            // ponytail: corrupt/missing payload — default rather than crash.
            return fallback;
        }
    }

    // ---- In-game PIN ----

    private void handlePinDigit(Player player, BinaryTagHolder clickTag, boolean isDelete) {
        UUID uniqueId = player.getUniqueId();
        int length = settings.getPinLength();
        StringBuilder session = pinSessions.computeIfAbsent(uniqueId, k -> new StringBuilder());

        if (isDelete) {
            if (!session.isEmpty()) {
                session.deleteCharAt(session.length() - 1);
            }
        } else {
            int digit = decodePayload(clickTag, "d", -1);
            if (digit < 0) {
                return;
            }
            if (session.length() < length) {
                session.append(digit);
            }
        }
        scheduleDialogReopen(player, () ->
                player.showDialog(dialogManager.createPinDialog(player, session.length(), null)));
    }

    private void handlePinConfirm(Player player) {
        UUID uniqueId = player.getUniqueId();
        StringBuilder session = pinSessions.remove(uniqueId);
        String code = session != null ? session.toString() : "";
        if (code.isEmpty()) {
            reShowAuthDialog(player, "login.password-empty", "<red>Please enter your password!</red>");
            return;
        }
        checkLoginCode(player, code);
    }

    // ---- In-game Slider ----

    private void handleSliderStep(Player player, BinaryTagHolder clickTag, int delta) {
        UUID uniqueId = player.getUniqueId();
        int length = settings.getSliderLength();
        int[] existing = sliderSessions.computeIfAbsent(uniqueId, k -> new int[length]);
        // ponytail: capture into a final for the lambda below — `digits` is
        // reassigned in the resize branch so it isn't effectively final.
        final int[] digits;
        if (existing.length != length) {
            // ponytail: length changed in config mid-session; resize.
            digits = new int[length];
            sliderSessions.put(uniqueId, digits);
        } else {
            digits = existing;
        }
        int pos = decodePayload(clickTag, "p", -1);
        if (pos < 0 || pos >= length) {
            return;
        }
        // ponytail: AuthMeBia wraps via +1/+9 mod 10 so up=0→1, down=0→9.
        digits[pos] = (digits[pos] + (delta > 0 ? 1 : 9)) % 10;
        scheduleDialogReopen(player, () ->
                player.showDialog(dialogManager.createSliderDialog(player, digits, null)));
    }

    private void handleSliderConfirm(Player player) {
        UUID uniqueId = player.getUniqueId();
        int[] digits = sliderSessions.remove(uniqueId);
        if (digits == null) {
            reShowAuthDialog(player, "login.password-empty", "<red>Please enter your password!</red>");
            return;
        }
        StringBuilder sb = new StringBuilder(digits.length);
        for (int d : digits) {
            sb.append(d);
        }
        checkLoginCode(player, sb.toString());
    }

    // ---- Custom screens (J) ----

    /**
     * Handle a custom-screen button click. Decodes the screen + button index
     * from the NBT additions payload, looks up the configured {@link
    * com.github.TejasLamba2006.AuthMeUI.dialogs.CustomScreen}, and executes
     * the button's action (COMMAND/CONSOLE/CLOSE). URL/COPY buttons never
     * reach here (they use {@link DialogAction#staticAction} and have no
     * server round-trip). The optional dismiss-forever checkbox is recorded
     * via {@link DialogManager#getScreenDismissStore()}.
     */
    private void handleCustomScreenButton(
            Player player,
            DialogResponseView responseView,
            BinaryTagHolder clickTag) {
        int screenIndex = decodePayload(clickTag, "s", -1);
        int buttonIndex = decodePayload(clickTag, "b", -1);
        if (screenIndex < 0) {
            return; // CLOSE-only fallback (e.g. the OK button on an empty screen)
        }
        java.util.List<CustomScreen> screens = settings.getCustomScreens();
        if (screenIndex >= screens.size()) {
            return; // config reloaded and indices shifted — bail safely
        }
        CustomScreen screen = screens.get(screenIndex);

        // Record dismiss-forever checkbox if the screen has one and it's checked.
        if (responseView != null
                && screen.checkboxAction() == CustomScreen.CheckboxAction.CLOSE
                && screen.checkboxLabelRaw() != null) {
            try {
                if (responseView.getBoolean(DialogIdentifiers.CUSTOM_SCREEN_DISMISS_KEY)) {
                    ScreenDismissStore store = dialogManager.getScreenDismissStore();
                    if (store != null) {
                        store.markDismissed(player.getUniqueId(), screen.id());
                    }
                }
            } catch (Exception ignored) {
                // ponytail: checkbox absent on this click — defensive, no-op.
            }
        }

        if (buttonIndex < 0) {
            return; // CLOSE action (OK/Close buttons) — dialog already closes.
        }
        if (buttonIndex >= screen.buttons().size()) {
            return;
        }
        CustomScreen.ButtonRaw btn = screen.buttons().get(buttonIndex);

        // ponytail: per-button sound (feature A pattern). The generic
        // resolveActionId path plays a single "screen_button" sound for
        // all custom-screen clicks; this overrides with the button's own
        // sound field if configured.
        if (btn.sound() != null && !btn.sound().isBlank()) {
            net.kyori.adventure.sound.Sound sound = settings.parseSound(btn.sound(), "screen_button");
            if (sound != null) {
                player.playSound(sound);
            }
        }

        switch (btn.action()) {
            case COMMAND -> {
                String raw = btn.value();
                if (raw == null || raw.isBlank()) return;
                final String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
                // ponytail: run on main thread (player.performCommand is
                // sync-only). Player-custom-click fires on the main thread
                // already for PlayerGameConnection, but be explicit for Folia.
                player.getScheduler().run(plugin, t -> player.performCommand(cmd), null);
            }
            case CONSOLE -> {
                String raw = btn.value();
                if (raw == null || raw.isBlank()) return;
                final String cmd = raw.startsWith("/") ? raw.substring(1) : raw;
                player.getScheduler().run(plugin, t ->
                        plugin.getServer().dispatchCommand(
                                plugin.getServer().getConsoleSender(), cmd), null);
            }
            case CLOSE -> {
                // no-op: dialog auto-closes (afterAction=CLOSE)
            }
            case OPEN_URL, COPY -> {
                // unreachable: URL/COPY use staticAction, no server round-trip
            }
        }
    }

    /**
     * Config-phase custom-screen button click. No {@link Player} exists yet
     * (we're pre-join), so COMMAND buttons are silently skipped (matching
     * AuthMeBia's {@code instanceof Player} guard); CONSOLE works via the
     * console sender; the dismiss-forever checkbox is recorded against the
     * UUID for later application.
     */
    private void handleConfigPhaseCustomScreenButton(
            PlayerConfigurationConnection connection,
            UUID uniqueId,
            String playerName,
            Audience audience,
            DialogResponseView responseView,
            BinaryTagHolder clickTag) {
        int screenIndex = decodePayload(clickTag, "s", -1);
        int buttonIndex = decodePayload(clickTag, "b", -1);
        if (screenIndex < 0) {
            return;
        }
        java.util.List<CustomScreen> screens = settings.getCustomScreens();
        if (screenIndex >= screens.size()) {
            return;
        }
        CustomScreen screen = screens.get(screenIndex);

        if (responseView != null
                && screen.checkboxAction() == CustomScreen.CheckboxAction.CLOSE
                && screen.checkboxLabelRaw() != null) {
            try {
                if (responseView.getBoolean(DialogIdentifiers.CUSTOM_SCREEN_DISMISS_KEY)) {
                    ScreenDismissStore store = dialogManager.getScreenDismissStore();
                    if (store != null) {
                        store.markDismissed(uniqueId, screen.id());
                    }
                }
            } catch (Exception ignored) {
                // ponytail: checkbox absent — defensive, no-op.
            }
        }

        if (buttonIndex < 0 || buttonIndex >= screen.buttons().size()) {
            return;
        }
        CustomScreen.ButtonRaw btn = screen.buttons().get(buttonIndex);
        switch (btn.action()) {
            case CONSOLE -> {
                String cmd = btn.value();
                if (cmd == null || cmd.isBlank()) return;
                cmd = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                final String finalCmd = cmd;
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () ->
                        plugin.getServer().dispatchCommand(
                                plugin.getServer().getConsoleSender(), finalCmd));
            }
            case COMMAND -> {
                // no Player during config phase — silently skip (matches AuthMeBia)
            }
            case CLOSE -> {
                // no-op: dialog auto-closes
            }
            case OPEN_URL, COPY -> {
                // unreachable: staticAction, no server round-trip
            }
        }
    }

    // ---- Config-phase PIN ----

    private void handleConfigPhasePinDigit(
            PlayerConfigurationConnection connection,
            UUID uniqueId,
            String playerName,
            Audience audience,
            BinaryTagHolder clickTag,
            boolean isDelete) {
        int length = settings.getPinLength();
        StringBuilder session = pinSessions.computeIfAbsent(uniqueId, k -> new StringBuilder());

        if (isDelete) {
            if (!session.isEmpty()) {
                session.deleteCharAt(session.length() - 1);
            }
        } else {
            int digit = decodePayload(clickTag, "d", -1);
            if (digit < 0) {
                return;
            }
            if (session.length() < length) {
                session.append(digit);
            }
        }
        final int entered = session.length();
        audience.showDialog(dialogManager.createPinDialogForAudience(entered, null));
    }

    private void handleConfigPhasePinConfirm(
            PlayerConfigurationConnection connection,
            UUID uniqueId,
            String playerName,
            Audience audience) {
        StringBuilder session = pinSessions.remove(uniqueId);
        String code = session != null ? session.toString() : "";
        if (code.isEmpty()) {
            Component error = settings.getMessage("login.password-empty",
                    "<red>Please enter your password!</red>");
            audience.showDialog(dialogManager.createPinDialogForAudience(0, null));
            return;
        }
        checkLoginCodeConfigPhase(connection, uniqueId, playerName, audience, code);
    }

    // ---- Config-phase Slider ----

    private void handleConfigPhaseSliderStep(
            PlayerConfigurationConnection connection,
            UUID uniqueId,
            String playerName,
            Audience audience,
            BinaryTagHolder clickTag,
            int delta) {
        int length = settings.getSliderLength();
        int[] digits = sliderSessions.computeIfAbsent(uniqueId, k -> new int[length]);
        if (digits.length != length) {
            digits = new int[length];
            sliderSessions.put(uniqueId, digits);
        }
        int pos = decodePayload(clickTag, "p", -1);
        if (pos < 0 || pos >= length) {
            return;
        }
        digits[pos] = (digits[pos] + (delta > 0 ? 1 : 9)) % 10;
        audience.showDialog(dialogManager.createSliderDialogForAudience(digits, null));
    }

    private void handleConfigPhaseSliderConfirm(
            PlayerConfigurationConnection connection,
            UUID uniqueId,
            String playerName,
            Audience audience) {
        int[] digits = sliderSessions.remove(uniqueId);
        if (digits == null) {
            audience.showDialog(dialogManager.createSliderDialogForAudience(new int[settings.getSliderLength()], null));
            return;
        }
        StringBuilder sb = new StringBuilder(digits.length);
        for (int d : digits) {
            sb.append(d);
        }
        checkLoginCodeConfigPhase(connection, uniqueId, playerName, audience, sb.toString());
    }

    /**
     * Shared config-phase credential check (text/PIN/slider). Mirrors the
     * in-game {@link #checkLoginCode} but via the *ByName AuthMe API variant
     * and config-phase completion instead of forceLogin.
     */
    private void checkLoginCodeConfigPhase(
            PlayerConfigurationConnection connection,
            UUID uniqueId,
            String playerName,
            Audience audience,
            String code) {
        if (!authBridge.isPlayerRegistered(playerName)) {
            Component error = settings.getMessage("login.not-registered",
                    "<yellow>You don't have an account. Please register first!</yellow>");
            if (configPhaseListener != null) {
                configPhaseListener.updateRegistrationStatus(uniqueId, false);
            }
            if (settings.isRulesDialogEnabled()) {
                audience.showDialog(dialogManager.createRulesDialogForAudience(playerName));
            } else {
                audience.showDialog(dialogManager.createRegistrationDialogForAudience(playerName, error));
            }
            return;
        }

        boolean needsTotp = settings.isTotp2faEnabled()
                && authBridge.hasTotpEnabled(playerName);

        if (needsTotp) {
            if (!authBridge.validateCredentials(playerName, code)) {
                recordIpFailure(uniqueId);
                recordPlayerLoginFailure(uniqueId);
                if (needsCaptcha(uniqueId)) {
                    showCaptchaToAudience(uniqueId, audience);
                } else {
                    reShowAuthDialogConfigPhase(uniqueId, playerName, audience, "login.password-incorrect",
                            "<red>Incorrect password. Please try again.</red>");
                }
                return;
            }
            clearIpFailures(uniqueId);
            clearPlayerLoginFailures(uniqueId);
            playerTotpTries.remove(uniqueId);
            audience.showDialog(dialogManager.createTotpDialogForAudience(null));
            return;
        }

        AuthResult result = authBridge.attemptLoginByName(playerName, code);

        switch (result) {
            case SUCCESS -> {
                clearIpFailures(uniqueId);
                clearPlayerLoginFailures(uniqueId);
                if (configPhaseListener != null) {
                    configPhaseListener.completeAuthentication(uniqueId, true);
                }
            }
            case INVALID_PASSWORD -> {
                recordIpFailure(uniqueId);
                recordPlayerLoginFailure(uniqueId);
                if (needsCaptcha(uniqueId)) {
                    showCaptchaToAudience(uniqueId, audience);
                } else {
                    reShowAuthDialogConfigPhase(uniqueId, playerName, audience, "login.password-incorrect",
                            "<red>Incorrect password. Please try again.</red>");
                }
            }
            case NOT_REGISTERED -> {
                Component error = settings.getMessage("login.not-registered",
                        "<yellow>You don't have an account. Please register first!</yellow>");
                if (configPhaseListener != null) {
                    configPhaseListener.updateRegistrationStatus(uniqueId, false);
                }
                audience.showDialog(dialogManager.createRegistrationDialogForAudience(playerName, error));
            }
            default -> reShowAuthDialogConfigPhase(uniqueId, playerName, audience, "login.password-incorrect",
                    "<red>Incorrect password. Please try again.</red>");
        }
    }

    /**
     * Re-show the player's current auth-mode dialog (config-phase variant).
     */
    private void reShowAuthDialogConfigPhase(
            UUID uniqueId,
            String playerName,
            Audience audience,
            String messageKey,
            String defaultMessage) {
        SettingsManager.AuthMode mode = settings.getAuthMode();
        if (mode == SettingsManager.AuthMode.PIN) {
            int entered = pinSessions.containsKey(uniqueId)
                    ? pinSessions.get(uniqueId).length() : 0;
            pinSessions.remove(uniqueId);
            audience.showDialog(dialogManager.createPinDialogForAudience(entered, defaultMessage));
        } else if (mode == SettingsManager.AuthMode.SLIDER) {
            int[] digits = sliderSessions.getOrDefault(uniqueId, new int[settings.getSliderLength()]);
            audience.showDialog(dialogManager.createSliderDialogForAudience(digits, defaultMessage));
        } else {
            Component error = settings.getMessage(messageKey, defaultMessage);
            audience.showDialog(dialogManager.createLoginDialogForAudience(playerName, error));
        }
    }

    // ==================== In-Game Handlers ====================

    private void handleLoginSubmission(Player player, DialogResponseView response) {
        String enteredPassword = response.getText(PASSWORD_FIELD);

        if (isNullOrEmpty(enteredPassword)) {
            if (authBridge.isPlayerAuthenticated(player)) {
                return;
            }

            displayLoginWithError(player, "login.password-empty", "<red>Please enter your password!</red>");
            return;
        }

        checkLoginCode(player, enteredPassword);
    }

    /**
     * Shared credential-check flow for text, PIN, and slider login. The
     * assembled code IS the password (AuthMe stores a hash regardless of how
     * the player entered it). Mirrors AuthMeBia's {@code loginNumericBlocking}:
     * not-registered gate, 2FA deferral, captcha escalation on failure.
     */
    private void checkLoginCode(Player player, String code) {
        if (!authBridge.isPlayerRegistered(player.getName())) {
            displayRegistrationWithError(player, "login.not-registered",
                    "<yellow>You don't have an account. Please register first!</yellow>");
            return;
        }

        // ponytail: when 2FA is on for this player, validate the password
        // WITHOUT force-login (validateCredentials), then gate the actual
        // login behind the TOTP dialog. This mirrors AuthMeBia, which checks
        // the password itself and only calls authMe.login(player) inside the
        // 2FA onSuccess callback. Without this, attemptLogin would force-login
        // before 2FA and the player could act in-game during the TOTP dialog.
        boolean needsTotp = settings.isTotp2faEnabled()
                && authBridge.hasTotpEnabled(player.getName());

        if (needsTotp) {
            if (!authBridge.validateCredentials(player.getName(), code)) {
                recordIpFailure(player);
                recordPlayerLoginFailure(player.getUniqueId());
                if (needsCaptcha(player.getUniqueId())) {
                    scheduleDialogReopen(player, () -> showCaptchaToPlayer(player));
                } else {
                    reShowAuthDialog(player, "login.password-incorrect",
                            "<red>Incorrect password. Please try again.</red>");
                }
                return;
            }
            // Password correct — clear counters, show TOTP (login deferred).
            clearIpFailures(player);
            clearPlayerLoginFailures(player.getUniqueId());
            playerTotpTries.remove(player.getUniqueId());
            scheduleDialogReopen(player, () ->
                    player.showDialog(dialogManager.createTotpDialog(player, null)));
            return;
        }

        AuthResult result = authBridge.attemptLogin(player, code);

        switch (result) {
            case SUCCESS -> {
                clearIpFailures(player);
                clearPlayerLoginFailures(player.getUniqueId());
            }
            case INVALID_PASSWORD -> {
                recordIpFailure(player);
                recordPlayerLoginFailure(player.getUniqueId());
                // ponytail: if the captcha threshold is now met, gate the
                // next login attempt behind a captcha instead of re-showing
                // the bare login dialog (which would just burn more attempts).
                if (needsCaptcha(player.getUniqueId())) {
                    scheduleDialogReopen(player, () -> showCaptchaToPlayer(player));
                } else {
                    reShowAuthDialog(player, "login.password-incorrect",
                            "<red>Incorrect password. Please try again.</red>");
                }
            }
            case NOT_REGISTERED -> displayRegistrationWithError(player, "login.not-registered",
                    "<yellow>You don't have an account. Please register first!</yellow>");
            default -> reShowAuthDialog(player, "login.password-incorrect",
                    "<red>Incorrect password. Please try again.</red>");
        }
    }

    /**
     * Re-show the player's current auth-mode dialog (text login / PIN / slider)
     * with an error status line. Mode-aware so a PIN-mode failure re-opens the
     * PIN pad, not the text login dialog.
     */
    private void reShowAuthDialog(Player player, String messageKey, String defaultMessage) {
        Component error = settings.getMessage(messageKey, defaultMessage);
        scheduleDialogReopen(player, () -> {
            if (authBridge.isPlayerAuthenticated(player)) {
                return;
            }
            SettingsManager.AuthMode mode = settings.getAuthMode();
            if (mode == SettingsManager.AuthMode.PIN) {
                int entered = pinSessions.containsKey(player.getUniqueId())
                        ? pinSessions.get(player.getUniqueId()).length() : 0;
                pinSessions.remove(player.getUniqueId());
                player.showDialog(dialogManager.createPinDialog(player, entered, defaultMessage));
            } else if (mode == SettingsManager.AuthMode.SLIDER) {
                int[] digits = sliderSessions.getOrDefault(player.getUniqueId(), new int[settings.getSliderLength()]);
                player.showDialog(dialogManager.createSliderDialog(player, digits, defaultMessage));
            } else {
                player.showDialog(dialogManager.createLoginDialog(player, error));
            }
        });
    }

    private void handleLoginCancel(Player player) {
        if (!player.isOnline()) {
            return;
        }

        authBridge.forceLogout(player);
        Component disconnectMessage = settings.getMessage("login.cancelled",
                "<yellow>Authentication cancelled.</yellow>");
        player.kick(disconnectMessage);
    }

    private void handleRegistrationSubmission(Player player, DialogResponseView response) {
        String enteredPassword = response.getText(PASSWORD_FIELD);
        String secondArgument = response.getText(CONFIRM_FIELD);

        if (isNullOrEmpty(enteredPassword)) {
            displayRegistrationWithError(player, "register.password-required",
                    "<red>Please enter a password.</red>");
            return;
        }

        RegistrationResult result = authBridge.attemptRegistration(player, enteredPassword, secondArgument);
        RegistrationSecondArgMode secondArgMode = authBridge.getRegistrationSecondArgMode();

        switch (result) {
            case SUCCESS -> scheduleRegistrationVerification(player);
            case ALREADY_EXISTS -> displayLoginWithError(player, "register.already-registered",
                    "<yellow>You already have an account. Please login instead!</yellow>");
            case SECOND_ARGUMENT_REQUIRED -> displayRegistrationWithError(player, "register.confirm-required",
                    "<red>Please confirm your password.</red>");
            case PASSWORD_MISMATCH -> displayRegistrationWithError(player, "register.passwords-mismatch",
                    "<red>Passwords do not match. Please try again.</red>");
            case PASSWORD_TOO_SHORT -> {
                int minLength = authBridge.fetchMinPasswordLength();
                displayRegistrationWithError(player, "register.password-too-short",
                        "<red>Password must be at least <white>%min%</white> characters!</red>",
                        Map.of("min", String.valueOf(minLength)));
            }
            case PASSWORD_TOO_LONG -> {
                int maxLength = authBridge.fetchMaxPasswordLength();
                displayRegistrationWithError(player, "register.password-too-long",
                        "<red>Password cannot exceed <white>%max%</white> characters!</red>",
                        Map.of("max", String.valueOf(maxLength)));
            }
            case EMAIL_REQUIRED -> displayRegistrationWithError(player, "register.email-required",
                    "<red>Please enter your email address.</red>");
            case EMAIL_INVALID -> displayRegistrationWithError(player, "register.email-invalid",
                    "<red>Please enter a valid email address.</red>");
            case UNSUPPORTED_IN_CONFIGURATION_PHASE -> displayRegistrationWithError(
                    player,
                    "register.prejoin-email-unsupported",
                    secondArgMode.usesEmail()
                            ? "<red>Email-based registration is unavailable in this mode. Please contact staff.</red>"
                            : "<red>Registration mode is not supported right now. Please contact staff.</red>");
            case INVALID_PASSWORD -> displayRegistrationWithError(player, "register.password-invalid",
                    "<red>Invalid password format. Please try again.</red>");
            default -> displayRegistrationWithError(player, "register.failed",
                    "<red>Registration failed. Please try again.</red>");
        }
    }

    private void handleRulesConfirmation(Player player, DialogResponseView response) {
        if (settings.isAgreementRequired()) {
            Boolean hasAgreed = response.getBoolean(settings.getAgreementKey());

            if (hasAgreed == null || !hasAgreed) {
                scheduleDialogReopen(player, () -> {
                    if (!authBridge.isPlayerAuthenticated(player)) {
                        player.showDialog(dialogManager.createRulesDialog(player));
                    }
                });
                return;
            }
        }

        scheduleDialogReopen(player, () -> player.showDialog(dialogManager.createRegistrationDialog(player)));
    }

    private void handleSupportAction(Player player) {
        boolean hasAccount = authBridge.isPlayerRegistered(player.getName());
        dialogManager.presentAuthDialog(player, hasAccount);
    }

    // ==================== Captcha (D) ====================

    /**
     * Whether the player must solve a captcha before being shown the login
     * dialog. Driven by: captcha enabled, player not already trusted, and the
     * per-player login-failure count meeting the configured trigger threshold.
     */
    private boolean needsCaptcha(UUID uniqueId) {
        if (!settings.isCaptchaEnabled()) {
            return false;
        }
        if (captcha.isTrusted(uniqueId)) {
            return false;
        }
        int trigger = settings.getCaptchaFailuresBeforeTrigger();
        if (trigger <= 0) {
            return true;
        }
        AtomicInteger count = playerLoginFailures.get(uniqueId);
        return count != null && count.get() >= trigger;
    }

    /**
     * Show the captcha dialog to an in-game player, generating a fresh code.
     */
    private void showCaptchaToPlayer(Player player) {
        String code = captcha.generateFor(player.getUniqueId(), settings.getCaptchaCodeLength());
        player.showDialog(dialogManager.createCaptchaDialog(player, code, null));
    }

    /**
     * Show the captcha dialog to a config-phase audience.
     */
    private void showCaptchaToAudience(UUID uniqueId, Audience audience) {
        String code = captcha.generateFor(uniqueId, settings.getCaptchaCodeLength());
        audience.showDialog(dialogManager.createCaptchaDialogForAudience(code, null));
    }

    private void handleCaptchaSubmit(Player player, DialogResponseView response) {
        String typed = response.getText(CaptchaDialogBuilder.CODE_INPUT_KEY);
        UUID uniqueId = player.getUniqueId();

        if (captcha.matchesFor(uniqueId, typed)) {
            long trustSeconds = settings.getCaptchaTrustDurationSeconds();
            if (trustSeconds > 0) {
                captcha.markTrusted(uniqueId, trustSeconds);
            }
            captcha.clearCode(uniqueId);
            // Route to the real login dialog now that the player is verified human.
            scheduleDialogReopen(player, () -> {
                if (!authBridge.isPlayerAuthenticated(player)) {
                    player.showDialog(dialogManager.createLoginDialog(player));
                }
            });
        } else {
            Component error = settings.getMessage("captcha.incorrect",
                    "<red>Incorrect code. Please try again.</red>");
            String code = captcha.generateFor(uniqueId, settings.getCaptchaCodeLength());
            scheduleDialogReopen(player, () ->
                    player.showDialog(dialogManager.createCaptchaDialog(player, code, error)));
        }
    }

    private void handleConfigPhaseCaptchaSubmit(
            PlayerConfigurationConnection connection,
            UUID uniqueId,
            String playerName,
            Audience audience,
            DialogResponseView response) {

        String typed = response.getText(CaptchaDialogBuilder.CODE_INPUT_KEY);

        if (captcha.matchesFor(uniqueId, typed)) {
            long trustSeconds = settings.getCaptchaTrustDurationSeconds();
            if (trustSeconds > 0) {
                captcha.markTrusted(uniqueId, trustSeconds);
            }
            captcha.clearCode(uniqueId);
            // Proceed to the real login dialog for config phase.
            audience.showDialog(dialogManager.createLoginDialogForAudience(playerName));
        } else {
            Component error = settings.getMessage("captcha.incorrect",
                    "<red>Incorrect code. Please try again.</red>");
            String code = captcha.generateFor(uniqueId, settings.getCaptchaCodeLength());
            audience.showDialog(dialogManager.createCaptchaDialogForAudience(code, error));
        }
    }

    // ==================== TOTP 2FA (E) ====================

    private void handleTotpSubmit(Player player, DialogResponseView response) {
        String code = response.getText(TwoFactorDialogBuilder.TOTP_INPUT_KEY);
        UUID uniqueId = player.getUniqueId();
        String playerName = player.getName();

        if (code != null && authBridge.checkTotpCode(playerName, code.trim())) {
            playerTotpTries.remove(uniqueId);
            // Complete the deferred login (password was already validated).
            authBridge.forceLogin(player);
        } else {
            recordIpFailure(player);
            if (totpTriesExceeded(uniqueId)) {
                playerTotpTries.remove(uniqueId);
                Component msg = settings.getMessage("totp.too-many-attempts",
                        "<red>Too many incorrect 2FA attempts. Please reconnect.</red>");
                player.kick(msg);
            } else {
                Component error = settings.getMessage("totp.incorrect",
                        "<red>Invalid authenticator code. Please try again.</red>");
                scheduleDialogReopen(player, () ->
                        player.showDialog(dialogManager.createTotpDialog(player, error)));
            }
        }
    }

    private void handleConfigPhaseTotpSubmit(
            PlayerConfigurationConnection connection,
            UUID uniqueId,
            String playerName,
            Audience audience,
            DialogResponseView response) {

        String code = response.getText(TwoFactorDialogBuilder.TOTP_INPUT_KEY);

        if (code != null && authBridge.checkTotpCode(playerName, code.trim())) {
            playerTotpTries.remove(uniqueId);
            // Complete the config-phase auth (password already validated).
            if (configPhaseListener != null) {
                configPhaseListener.completeAuthentication(uniqueId, true);
            }
        } else {
            recordIpFailure(uniqueId);
            if (totpTriesExceeded(uniqueId)) {
                playerTotpTries.remove(uniqueId);
                Component msg = settings.getMessage("totp.too-many-attempts",
                        "<red>Too many incorrect 2FA attempts. Please reconnect.</red>");
                connection.disconnect(msg);
            } else {
                Component error = settings.getMessage("totp.incorrect",
                        "<red>Invalid authenticator code. Please try again.</red>");
                audience.showDialog(dialogManager.createTotpDialogForAudience(error));
            }
        }
    }

    private boolean totpTriesExceeded(UUID uniqueId) {
        if (!settings.isTotpAttemptsEnabled()) {
            return false;
        }
        int tries = playerTotpTries.computeIfAbsent(uniqueId, k -> new AtomicInteger(0))
                .incrementAndGet();
        return tries >= settings.getTotpMaxTries();
    }

    /**
     * Record a per-player login failure (for the captcha trigger threshold).
     */
    private void recordPlayerLoginFailure(UUID uniqueId) {
        playerLoginFailures.computeIfAbsent(uniqueId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private void clearPlayerLoginFailures(UUID uniqueId) {
        playerLoginFailures.remove(uniqueId);
    }

    private void handleLoginForgot(Player player) {
        // ponytail: gate the dialog flow behind forgot-password.enabled;
        // when disabled, fall back to the legacy command dispatch so the
        // login forgot-button stays useful without email configured.
        if (!settings.isForgotPasswordEnabled()) {
            String commandTemplate = settings.getLoginForgotCommandTemplate();
            plugin.getServer().dispatchCommand(player, commandTemplate);
            return;
        }
        startForgotFlow(player.getUniqueId(), player.getName(), player, null);
    }

    private void handleConfigPhaseForgot(Audience audience, String playerName, UUID uniqueId) {
        if (!settings.isForgotPasswordEnabled()) {
            Component message = settings.getMessage("forgot.info",
                    "<yellow>Please use the /email recover command to reset your password.</yellow>");
            audience.sendMessage(message);
            return;
        }
        startForgotFlow(uniqueId, playerName, null, audience);
    }

    // ==================== Forgot Password / Email Recovery (F) ====================

    /**
     * Per-player forgot-password session state. Event-driven analog of
     * AuthMeBia's {@code ForgotPasswordSession} (which blocks on a
     * CountDownLatch). Holds the verified email, the expected code, the
     * last-sent timestamp (for resend cooldown), and the wrong-code counter
     * between dialog clicks.
     */
    private static final class ForgotPasswordSession {
        final String email;
        String code;
        volatile long lastSent;
        final AtomicInteger wrongTries = new AtomicInteger(0);

        ForgotPasswordSession(String email) {
            this.email = email;
        }
    }

    /**
     * Stage 1 entry: look up the player's stored email. If none, show the
     * no-email dialog; otherwise show the email-entry dialog. The in-game
     * and config-phase paths share this; only the re-show mechanism differs.
     */
    private void startForgotFlow(UUID uniqueId, String playerName, Player player, Audience audience) {
        String storedEmail = authBridge.getStoredEmail(playerName);
        if (storedEmail == null || storedEmail.isBlank()) {
            if (player != null) {
                scheduleDialogReopen(player, () ->
                        player.showDialog(dialogManager.createNoEmailDialog(player)));
            } else if (audience != null) {
                audience.showDialog(dialogManager.createNoEmailDialogForAudience());
            }
            return;
        }
        // Pre-create the session so the verify stage has the email on hand.
        forgotSessions.put(uniqueId, new ForgotPasswordSession(storedEmail));
        if (player != null) {
            scheduleDialogReopen(player, () ->
                    player.showDialog(dialogManager.createForgotEmailDialog(player, null)));
        } else if (audience != null) {
            audience.showDialog(dialogManager.createForgotEmailDialogForAudience(null));
        }
    }

    // ---- In-game handlers ----

    private void handleForgotEmailSubmit(Player player, DialogResponseView response) {
        UUID uniqueId = player.getUniqueId();
        String playerName = player.getName();
        String typed = response.getText(ForgotDialogBuilder.EMAIL_INPUT_KEY);

        if (typed == null || typed.isBlank()) {
            Component error = settings.getMessage("forgot.invalid-email", "<red>Incorrect email.</red>");
            scheduleDialogReopen(player, () ->
                    player.showDialog(dialogManager.createForgotEmailDialog(player, error)));
            return;
        }

        String storedEmail = authBridge.getStoredEmail(playerName);
        if (storedEmail == null || storedEmail.isBlank()
                || !authBridge.isValidEmail(typed)
                || !typed.trim().equalsIgnoreCase(storedEmail.trim())) {
            Component error = settings.getMessage("forgot.invalid-email", "<red>Incorrect email.</red>");
            scheduleDialogReopen(player, () ->
                    player.showDialog(dialogManager.createForgotEmailDialog(player, error)));
            return;
        }

        // Email verified — create/refresh session and send the code.
        ForgotPasswordSession session = new ForgotPasswordSession(storedEmail);
        forgotSessions.put(uniqueId, session);
        sendForgotCodeAndShowVerify(uniqueId, playerName, storedEmail, player, null);
    }

    private void handleForgotBack(Player player) {
        forgotSessions.remove(player.getUniqueId());
        scheduleDialogReopen(player, () -> {
            if (!authBridge.isPlayerAuthenticated(player)) {
                player.showDialog(dialogManager.createLoginDialog(player));
            }
        });
    }

    private void handleForgotVerify(Player player, DialogResponseView response) {
        UUID uniqueId = player.getUniqueId();
        ForgotPasswordSession session = forgotSessions.get(uniqueId);
        String typed = response.getText(EmailVerifyDialogBuilder.CODE_INPUT_KEY);

        if (session == null || typed == null || typed.isBlank()
                || !session.code.equals(typed.trim())) {
            recordIpFailure(player);
            if (session != null && forgotTriesExceeded(uniqueId, session)) {
                forgotSessions.remove(uniqueId);
                Component msg = settings.getMessage("forgot.too-many-attempts",
                        "<red>Too many incorrect code attempts. Please reconnect.</red>");
                player.kick(msg);
                return;
            }
            Component error = settings.getMessage("forgot.wrong-code", "<red>Incorrect code.</red>");
            long cooldown = resendCooldownRemaining(session);
            scheduleDialogReopen(player, () ->
                    player.showDialog(dialogManager.createEmailVerifyDialog(
                            player, session != null ? session.email : "", cooldown, error)));
            return;
        }

        // Code verified — clear IP failures, drop session, show recover.
        clearIpFailures(player);
        forgotSessions.remove(uniqueId);
        scheduleDialogReopen(player, () ->
                player.showDialog(dialogManager.createRecoverDialog(player, false)));
    }

    private void handleForgotResend(Player player) {
        UUID uniqueId = player.getUniqueId();
        ForgotPasswordSession session = forgotSessions.get(uniqueId);
        if (session == null) {
            scheduleDialogReopen(player, () ->
                    player.showDialog(dialogManager.createForgotEmailDialog(player, null)));
            return;
        }
        long cooldown = resendCooldownRemaining(session);
        if (cooldown > 0) {
            // Still on cooldown — re-show with updated label, no new code.
            scheduleDialogReopen(player, () ->
                    player.showDialog(dialogManager.createEmailVerifyDialog(
                            player, session.email, cooldown, null)));
            return;
        }
        sendForgotCodeAndShowVerify(uniqueId, player.getName(), session.email, player, null);
    }

    private void handleRecoverSubmit(Player player, DialogResponseView response) {
        UUID uniqueId = player.getUniqueId();
        String playerName = player.getName();
        String newPass = response.getText(RecoverDialogBuilder.NEW_PASSWORD_KEY);
        String confirm = response.getText(RecoverDialogBuilder.CONFIRM_PASSWORD_KEY);

        if (newPass == null || newPass.isBlank() || confirm == null
                || !newPass.equals(confirm)) {
            Component msg = settings.getMessage("forgot.recover-mismatch",
                    "<red>Passwords do not match. Try again.</red>");
            player.sendMessage(msg);
            scheduleDialogReopen(player, () ->
                    player.showDialog(dialogManager.createRecoverDialog(player, true)));
            return;
        }

        // ponytail: changePassword is a DB write — run async, then branch on
        // the recover mode on the main thread. Mirrors AuthMeBia's runAsync +
        // runOnMain. In-game path uses the Folia-aware player scheduler.
        // The mode is owned by DialogManager (which set it when routing into
        // the recover flow); read + clear it here so the handler stays stateless.
        DialogManager.RecoverMode mode = dialogManager.getRecoverMode(uniqueId);
        dialogManager.clearRecoverMode(uniqueId);
        final DialogManager.RecoverMode finalMode = mode;
        final String password = newPass;
        plugin.getServer().getAsyncScheduler().runNow(plugin, asyncTask1 -> {
            boolean ok = authBridge.changePassword(playerName, password);
            player.getScheduler().run(plugin, scheduledTask -> {
                if (!player.isOnline()) {
                    return;
                }
                if (!ok) {
                    Component error = settings.getMessage("forgot.send-failed",
                            "<red>Could not update the password. Please contact an admin.</red>");
                    player.showDialog(dialogManager.createRecoverDialog(player, false));
                    player.sendMessage(error);
                    return;
                }
                Component msg = settings.getMessage("forgot.recover-success",
                        "<green>Password updated successfully.</green>");
                player.sendMessage(msg);
                switch (finalMode) {
                    case ADMIN_RESET -> {
                        // ponytail: L — clear the admin flag and log the player
                        // in (they already proved ownership by setting a new pw).
                        com.github.TejasLamba2006.AuthMeUI.dialogs.RecoverStore store =
                                dialogManager.getRecoverStore();
                        if (store != null) {
                            store.clear(uniqueId);
                        }
                        plugin.getServer().getAsyncScheduler().runNow(plugin,
                                asyncTask2 -> authBridge.forceLogin(player));
                    }
                    case PREMIUM_SET -> {
                        // ponytail: K — no flag to clear; the premium player is
                        // already authenticated via the auto-login path. Nothing
                        // further to do.
                    }
                    case FORGOT -> player.showDialog(dialogManager.createLoginDialog(player));
                }
            }, null);
        });
    }

    // ---- Config-phase handlers ----

    private void handleConfigPhaseForgotEmailSubmit(
            PlayerConfigurationConnection connection, UUID uniqueId, String playerName,
            Audience audience, DialogResponseView response) {
        String typed = response.getText(ForgotDialogBuilder.EMAIL_INPUT_KEY);

        if (typed == null || typed.isBlank()) {
            Component error = settings.getMessage("forgot.invalid-email", "<red>Incorrect email.</red>");
            audience.showDialog(dialogManager.createForgotEmailDialogForAudience(error));
            return;
        }

        String storedEmail = authBridge.getStoredEmail(playerName);
        if (storedEmail == null || storedEmail.isBlank()
                || !authBridge.isValidEmail(typed)
                || !typed.trim().equalsIgnoreCase(storedEmail.trim())) {
            Component error = settings.getMessage("forgot.invalid-email", "<red>Incorrect email.</red>");
            audience.showDialog(dialogManager.createForgotEmailDialogForAudience(error));
            return;
        }

        ForgotPasswordSession session = new ForgotPasswordSession(storedEmail);
        forgotSessions.put(uniqueId, session);
        sendForgotCodeAndShowVerify(uniqueId, playerName, storedEmail, null, audience);
    }

    private void handleConfigPhaseForgotBack(Audience audience, String playerName) {
        audience.showDialog(dialogManager.createLoginDialogForAudience(playerName));
    }

    private void handleConfigPhaseForgotVerify(
            PlayerConfigurationConnection connection, UUID uniqueId, String playerName,
            Audience audience, DialogResponseView response) {
        ForgotPasswordSession session = forgotSessions.get(uniqueId);
        String typed = response.getText(EmailVerifyDialogBuilder.CODE_INPUT_KEY);

        if (session == null || typed == null || typed.isBlank()
                || !session.code.equals(typed.trim())) {
            recordIpFailure(uniqueId);
            if (session != null && forgotTriesExceeded(uniqueId, session)) {
                forgotSessions.remove(uniqueId);
                Component msg = settings.getMessage("forgot.too-many-attempts",
                        "<red>Too many incorrect code attempts. Please reconnect.</red>");
                connection.disconnect(msg);
                return;
            }
            Component error = settings.getMessage("forgot.wrong-code", "<red>Incorrect code.</red>");
            long cooldown = resendCooldownRemaining(session);
            audience.showDialog(dialogManager.createEmailVerifyDialogForAudience(
                    session != null ? session.email : "", cooldown, error));
            return;
        }

        clearIpFailures(uniqueId);
        forgotSessions.remove(uniqueId);
        audience.showDialog(dialogManager.createRecoverDialogForAudience(false));
    }

    private void handleConfigPhaseForgotResend(
            PlayerConfigurationConnection connection, UUID uniqueId, String playerName,
            Audience audience) {
        ForgotPasswordSession session = forgotSessions.get(uniqueId);
        if (session == null) {
            audience.showDialog(dialogManager.createForgotEmailDialogForAudience(null));
            return;
        }
        long cooldown = resendCooldownRemaining(session);
        if (cooldown > 0) {
            audience.showDialog(dialogManager.createEmailVerifyDialogForAudience(
                    session.email, cooldown, null));
            return;
        }
        sendForgotCodeAndShowVerify(uniqueId, playerName, session.email, null, audience);
    }

    private void handleConfigPhaseRecoverSubmit(
            PlayerConfigurationConnection connection, UUID uniqueId, String playerName,
            Audience audience, DialogResponseView response) {
        String newPass = response.getText(RecoverDialogBuilder.NEW_PASSWORD_KEY);
        String confirm = response.getText(RecoverDialogBuilder.CONFIRM_PASSWORD_KEY);

        if (newPass == null || newPass.isBlank() || confirm == null
                || !newPass.equals(confirm)) {
            Component msg = settings.getMessage("forgot.recover-mismatch",
                    "<red>Passwords do not match. Try again.</red>");
            audience.sendMessage(msg);
            audience.showDialog(dialogManager.createRecoverDialogForAudience(true));
            return;
        }

        // ponytail: K+L — branch on recover mode, same as the in-game path.
        DialogManager.RecoverMode mode = dialogManager.getRecoverMode(uniqueId);
        dialogManager.clearRecoverMode(uniqueId);
        final DialogManager.RecoverMode finalMode = mode;
        final String password = newPass;
        plugin.getServer().getAsyncScheduler().runNow(plugin, asyncTask3 -> {
            boolean ok = authBridge.changePassword(playerName, password);
            plugin.getServer().getGlobalRegionScheduler().execute(plugin, () -> {
                if (!ok) {
                    Component error = settings.getMessage("forgot.send-failed",
                            "<red>Could not update the password. Please contact an admin.</red>");
                    audience.sendMessage(error);
                    audience.showDialog(dialogManager.createRecoverDialogForAudience(false));
                    return;
                }
                Component msg = settings.getMessage("forgot.recover-success",
                        "<green>Password updated successfully.</green>");
                audience.sendMessage(msg);
                switch (finalMode) {
                    case ADMIN_RESET -> {
                        // ponytail: L — clear the admin flag + complete
                        // config-phase auth (forceLogin happens on join).
                        com.github.TejasLamba2006.AuthMeUI.dialogs.RecoverStore store =
                                dialogManager.getRecoverStore();
                        if (store != null) {
                            store.clear(uniqueId);
                        }
                        if (configPhaseListener != null) {
                            configPhaseListener.completeAuthentication(uniqueId, true);
                        }
                    }
                    case PREMIUM_SET -> {
                        // ponytail: K — complete config-phase auth.
                        if (configPhaseListener != null) {
                            configPhaseListener.completeAuthentication(uniqueId, true);
                        }
                    }
                    case FORGOT -> audience.showDialog(
                            dialogManager.createLoginDialogForAudience(playerName));
                }
            });
        });
    }

    // ---- F helpers ----

    /**
     * Generate a numeric code, store it on the session, send the verification
     * email async, then on the main thread either show the verify dialog or
     * (on send failure) drop the session and return to the email-entry dialog
     * with a send-failed message. Mirrors AuthMeBia's
     * {@code startForgotPasswordVerifyIngame}.
     */
    private void sendForgotCodeAndShowVerify(
            UUID uniqueId, String playerName, String email, Player player, Audience audience) {
        ForgotPasswordSession session = forgotSessions.get(uniqueId);
        if (session == null) {
            session = new ForgotPasswordSession(email);
            forgotSessions.put(uniqueId, session);
        }
        String code = generateNumericCode(settings.getEmailCodeLength());
        session.code = code;
        session.lastSent = System.currentTimeMillis();

        // ponytail: capture into finals for the nested lambdas below — `session`
        // is reassigned above so it isn't effectively final.
        final Player p = player;
        final Audience a = audience;
        final ForgotPasswordSession s = session;

        // ponytail: sendVerificationEmail hits AuthMe's mail layer (SMTP) —
        // run it off the main thread. On completion, re-show on main thread.
        // In-game uses the Folia-aware player scheduler; config-phase (no
        // Player) falls back to BukkitScheduler.runTask.
        plugin.getServer().getAsyncScheduler().runNow(plugin, asyncTask4 -> {
            final boolean sent = authBridge.sendVerificationEmail(playerName, email, code);
            if (p != null) {
                p.getScheduler().run(plugin, scheduledTask ->
                        finishSendForgot(p, a, uniqueId, email, sent, s),
                        null);
            } else {
                plugin.getServer().getGlobalRegionScheduler().execute(plugin, () ->
                        finishSendForgot(null, a, uniqueId, email, sent, s));
            }
        });
    }

    private void finishSendForgot(
            Player player, Audience audience, UUID uniqueId,
            String email, boolean sent, ForgotPasswordSession session) {
        if (sent) {
            long cooldown = resendCooldownRemaining(session);
            if (player != null) {
                player.showDialog(dialogManager.createEmailVerifyDialog(
                        player, email, cooldown, null));
            } else if (audience != null) {
                audience.showDialog(dialogManager.createEmailVerifyDialogForAudience(
                        email, cooldown, null));
            }
        } else {
            forgotSessions.remove(uniqueId);
            Component error = settings.getMessage("forgot.send-failed",
                    "<red>Could not send the email. Please contact an admin.</red>");
            if (player != null) {
                player.sendMessage(error);
                player.showDialog(dialogManager.createForgotEmailDialog(player, null));
            } else if (audience != null) {
                audience.sendMessage(error);
                audience.showDialog(dialogManager.createForgotEmailDialogForAudience(null));
            }
        }
    }

    private long resendCooldownRemaining(ForgotPasswordSession session) {
        if (session == null) {
            return 0;
        }
        long cooldownMs = settings.getEmailResendCooldown() * 1000L;
        long elapsed = System.currentTimeMillis() - session.lastSent;
        long remaining = (cooldownMs - elapsed) / 1000;
        return Math.max(0, remaining);
    }

    private boolean forgotTriesExceeded(UUID uniqueId, ForgotPasswordSession session) {
        int tries = session.wrongTries.incrementAndGet();
        return tries >= settings.getEmailMaxTries();
    }

    /**
     * Generate a numeric verification code of the given length using a
     * non-cryptographic RNG. Mirrors AuthMeBia's {@code Util.genNumericCode}
     * (which uses SecureRandom); ThreadLocalRandom is sufficient here — the
     * code is single-use, IP-guarded, and rate-limited, not a secret token.
     * ponytail: ThreadLocalRandom over SecureRandom; upgrade if a real
     * secret-token use appears.
     */
    private static String generateNumericCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }

    private void displayLoginWithError(Player player, String messageKey, String defaultMessage) {
        scheduleDialogReopen(player, () -> {
            if (!authBridge.isPlayerAuthenticated(player)) {
                Component errorMsg = settings.getMessage(messageKey, defaultMessage);
                player.showDialog(dialogManager.createLoginDialog(player, errorMsg));
            }
        });
    }

    private void displayRegistrationWithError(Player player, String messageKey, String defaultMessage) {
        displayRegistrationWithError(player, messageKey, defaultMessage, null);
    }

    private void displayRegistrationWithError(Player player, String messageKey, String defaultMessage,
            Map<String, String> placeholders) {
        scheduleDialogReopen(player, () -> {
            if (!authBridge.isPlayerAuthenticated(player)) {
                Component errorMsg = placeholders != null
                        ? settings.getMessage(messageKey, defaultMessage, placeholders)
                        : settings.getMessage(messageKey, defaultMessage);
                player.showDialog(dialogManager.createRegistrationDialog(player, errorMsg));
            }
        });
    }

    private void scheduleRegistrationVerification(Player player) {
        scheduleRegistrationVerification(player, 0);
    }

    private void scheduleRegistrationVerification(Player player, int attempt) {
        player.getScheduler().runDelayed(plugin, scheduledTask -> {
            if (!player.isOnline()) {
                return;
            }

            boolean isValid = authBridge.isPlayerAuthenticated(player)
                    || authBridge.isPlayerRegistered(player.getName());

            if (isValid) {
                return;
            }

            if (attempt + 1 < REGISTRATION_VERIFICATION_ATTEMPTS) {
                scheduleRegistrationVerification(player, attempt + 1);
                return;
            }

            Component errorMsg = settings.getMessage("register.failed",
                    "<red>Registration failed. Please try again.</red>");
            player.showDialog(dialogManager.createRegistrationDialog(player, errorMsg));
        }, null, REGISTRATION_VERIFICATION_DELAY_TICKS);
    }

    private void scheduleDialogReopen(Player player, Runnable action) {
        player.getScheduler().run(plugin, scheduledTask -> action.run(), null);
    }

    private boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    // ==================== Per-Button Sounds ====================

    /**
     * Map a action {@link Key} to the config id used in {@code dialog-sounds}.
     */
    private String resolveActionId(Key actionKey) {
        String value = actionKey.value();
        return switch (value) {
            case "action/login" -> "login_submit";
            case "action/login_cancel" -> "login_cancel";
            case "action/login_forgot" -> "login_forgot";
            case "action/captcha" -> "captcha_submit";
            case "action/totp" -> "totp_submit";
            case "action/totp_cancel" -> "totp_cancel";
            case "action/register" -> "register_submit";
            case "action/rules_confirm" -> "rules_confirm";
            case "action/support" -> "support";
            case "action/forgot" -> "forgot_submit";
            case "action/forgot_back" -> "forgot_back";
            case "action/forgot_verify" -> "forgot_verify";
            case "action/forgot_resend" -> "forgot_resend";
            case "action/recover" -> "recover_submit";
            case "action/pin" -> "pin_button";
            case "action/pin_delete" -> "pin_delete";
            case "action/pin_confirm" -> "pin_confirm";
            case "action/slider_up" -> "slider_step";
            case "action/slider_down" -> "slider_step";
            case "action/slider_confirm" -> "slider_confirm";
            case "action/screen_button" -> "screen_button";
            default -> null;
        };
    }

    /**
     * Play the configured sound for an action id. Works for both in-game
     * players and config-phase audiences (both implement {@link Audience}.
     * Sound.Emitter Self) supports emitting to an arbitrary audience.
     */
    private void playDialogSound(String actionId, PlayerCommonConnection connection) {
        String raw = settings.getDialogSound(actionId);
        if (raw == null) {
            return;
        }
        Sound sound = settings.parseSound(raw, actionId);
        if (sound == null) {
            return;
        }

        if (connection instanceof PlayerGameConnection gameConnection) {
            gameConnection.getPlayer().playSound(sound);
        } else if (connection instanceof PlayerConfigurationConnection configConnection) {
            configConnection.getAudience().playSound(sound);
        }
    }

    // ==================== IP Guard Hooks ====================

    /**
     * Record a failed login attempt for the player's IP, if IpGuard is enabled.
     * On in-game players, extract IP from {@link Player#getAddress()}.
     */
    private void recordIpFailure(Player player) {
        if (!settings.isIpGuardEnabled()) {
            return;
        }
        InetSocketAddress address = player.getAddress();
        String ip = IpGuard.extractIp(address);
        if (ip != null) {
            ipGuard.recordFailure(ip);
        }
    }

    /**
     * Record a failed login attempt for a config-phase player, using the IP
     * captured during {@link AsyncPlayerPreLoginEvent}.
     */
    private void recordIpFailure(UUID uniqueId) {
        if (!settings.isIpGuardEnabled() || configPhaseListener == null) {
            return;
        }
        Map<UUID, String> ips = getConfigPhaseIps();
        if (ips == null) {
            return;
        }
        String ip = ips.get(uniqueId);
        if (ip != null) {
            ipGuard.recordFailure(ip);
        }
    }

    /**
     * Clear failure tracking for a player's IP (on successful login).
     */
    private void clearIpFailures(Player player) {
        if (!settings.isIpGuardEnabled()) {
            return;
        }
        InetSocketAddress address = player.getAddress();
        String ip = IpGuard.extractIp(address);
        ipGuard.clearFailures(ip);
    }

    private void clearIpFailures(UUID uniqueId) {
        if (!settings.isIpGuardEnabled() || configPhaseListener == null) {
            return;
        }
        Map<UUID, String> ips = getConfigPhaseIps();
        if (ips == null) {
            return;
        }
        String ip = ips.get(uniqueId);
        if (ip != null) {
            ipGuard.clearFailures(ip);
        }
    }

    /**
     * Accessor for the config-phase listener's IP map. Held on the listener
     * type directly to avoid a circular dependency on
     * {@link ConfigurationPhaseListener} internals.
     */
    private Map<UUID, String> getConfigPhaseIps() {
        if (configPhaseListener == null) {
            return null;
        }
        return configPhaseListener.getConnectionIps();
    }
}
