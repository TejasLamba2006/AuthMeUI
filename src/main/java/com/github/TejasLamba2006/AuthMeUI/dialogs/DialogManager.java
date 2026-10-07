package com.github.TejasLamba2006.AuthMeUI.dialogs;

import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge;
import com.github.TejasLamba2006.AuthMeUI.authentication.Captcha;
import com.github.TejasLamba2006.AuthMeUI.configuration.SettingsManager;
import com.github.TejasLamba2006.AuthMeUI.webhook.DiscordWebhook;
import io.papermc.paper.dialog.Dialog;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public class DialogManager {

    private final SettingsManager settings;
    private final LoginDialogBuilder loginBuilder;
    private final RegistrationDialogBuilder registerBuilder;
    private final RulesDialogBuilder rulesBuilder;
    private final CaptchaDialogBuilder captchaBuilder;
    private final TwoFactorDialogBuilder totpBuilder;
    private final ForgotDialogBuilder forgotBuilder;
    private final EmailVerifyDialogBuilder emailVerifyBuilder;
    private final RecoverDialogBuilder recoverBuilder;
    private final PinDialogBuilder pinBuilder;
    private final SliderDialogBuilder sliderBuilder;
    private final CustomScreenDialogBuilder customScreenBuilder;
    private ScreenDismissStore screenDismissStore;
    // ponytail: K+L — admin-forced reset flag store + premium-set (once-per-
    // session) tracker. Both late-bound from AuthMeUIPlugin so the listener
    // and the login-path facade share one instance.
    private RecoverStore recoverStore;
    private final PremiumSetTracker premiumSetTracker = new PremiumSetTracker();
    // ponytail: N — shared webhook instance (HttpClient is async-safe, so
    // one shared client covers all auth-event notifications).
    private DiscordWebhook discordWebhook;
    // ponytail: K+L — why the recover dialog is showing, so the shared
    // RECOVER_SUBMIT handler in DialogInteractionListener knows what to do
    // on success. Lives on the facade (which owns the recover/premium state)
    // rather than the listener, so presentAuthDialog can set it directly.
    public enum RecoverMode { FORGOT, ADMIN_RESET, PREMIUM_SET }
    private final java.util.Map<java.util.UUID, RecoverMode> recoverMode = new java.util.concurrent.ConcurrentHashMap<>();
    // ponytail: captcha instance lives on the listener (which owns the trust
    // window + failure counters and all click dispatch). DialogManager only
    // needs it to decide whether to gate the login dialog, so we hold it as
    // a late-bound reference set by the listener after construction.
    private Captcha captcha;

    public DialogManager(SettingsManager settings, AuthenticationBridge authBridge) {
        this.settings = settings;
        this.loginBuilder = new LoginDialogBuilder(settings);
        this.registerBuilder = new RegistrationDialogBuilder(settings, authBridge);
        this.rulesBuilder = new RulesDialogBuilder(settings);
        this.captchaBuilder = new CaptchaDialogBuilder(settings);
        this.totpBuilder = new TwoFactorDialogBuilder(settings);
        this.forgotBuilder = new ForgotDialogBuilder(settings);
        this.emailVerifyBuilder = new EmailVerifyDialogBuilder(settings);
        this.recoverBuilder = new RecoverDialogBuilder(settings);
        this.pinBuilder = new PinDialogBuilder(settings);
        this.sliderBuilder = new SliderDialogBuilder(settings);
        this.customScreenBuilder = new CustomScreenDialogBuilder(settings);
    }

    /**
     * Bind the captcha trust-window instance (owned by
     * {@link DialogInteractionListener}) so this facade can decide whether to
     * gate the login dialog behind a captcha.
     */
    public void bindCaptcha(Captcha captcha) {
        this.captcha = captcha;
    }

    /**
     * Expose the bound captcha trust-window (may be null if not yet bound).
     */
    public Captcha getCaptcha() {
        return captcha;
    }

    // ==================== Player-based methods (for in-game use)
    // ====================

    public Dialog createLoginDialog(Player player) {
        return loginBuilder.construct(player);
    }

    public Dialog createLoginDialog(Player player, Component errorMessage) {
        return loginBuilder.construct(player, errorMessage);
    }

    public Dialog createRegistrationDialog(Player player) {
        return registerBuilder.construct(player);
    }

    public Dialog createRegistrationDialog(Player player, Component errorMessage) {
        return registerBuilder.construct(player, errorMessage);
    }

    public Dialog createRulesDialog(Player player) {
        return rulesBuilder.construct(player);
    }

    public void presentAuthDialog(Player player, boolean isRegistered) {
        // ponytail: K+L — an admin-forced reset flag takes priority over
        // the normal login/register/captcha flow. Premium-set (K) is handled
        // by the caller (PlayerSessionListener) via the premium tracker, not
        // here, because it depends on the connecting UUID (premium skip)
        // which the post-join listener already evaluated.
        if (isRegistered && recoverStore != null && recoverStore.isFlagged(player.getUniqueId())) {
            // ponytail: L — admin-flagged reset: mark the mode so the shared
            // RECOVER_SUBMIT handler clears the flag + forceLogins on success.
            markRecoverMode(player.getUniqueId(), RecoverMode.ADMIN_RESET);
            player.showDialog(createRecoverDialog(player, false));
            return;
        }
        // ponytail: K — premium password-set for auto-logged-in premium
        // players who have no real password yet. The caller (PlayerSessionListener)
        // checks isPremiumSkip + the once-per-session tracker before calling
        // presentAuthDialog; when it routes here it marks PREMIUM_SET first.
        // No flag to clear — just prompt once, then let them play through.
        if (isRegistered) {
            // ponytail: captcha gate for trigger==0 (always-show). For
            // trigger>0 the gate fires from the listener's login-failure
            // handler once the per-player counter crosses the threshold,
            // so DialogManager only needs the trust check here.
            if (settings.isCaptchaEnabled()
                    && settings.getCaptchaFailuresBeforeTrigger() == 0
                    && captcha != null
                    && !captcha.isTrusted(player.getUniqueId())) {
                String code = captcha.generateFor(player.getUniqueId(), settings.getCaptchaCodeLength());
                player.showDialog(createCaptchaDialog(player, code, null));
            } else {
                // ponytail: PIN/Slider modes replace the text login dialog for
                // registered players (G+H). Registration always uses a password
                // text field, so only the login path branches on auth-mode.
                SettingsManager.AuthMode mode = settings.getAuthMode();
                if (mode == SettingsManager.AuthMode.PIN) {
                    player.showDialog(createPinDialog(player, 0, null));
                } else if (mode == SettingsManager.AuthMode.SLIDER) {
                    player.showDialog(createSliderDialog(player, new int[settings.getSliderLength()], null));
                } else {
                    player.showDialog(createLoginDialog(player));
                }
            }
        } else {
            if (settings.isRulesDialogEnabled()) {
                player.showDialog(createRulesDialog(player));
            } else {
                player.showDialog(createRegistrationDialog(player));
            }
        }
    }

    // ==================== Audience-based methods (for configuration phase)
    // ====================

    /**
     * Create a login dialog for use with Audience (e.g., during configuration
     * phase).
     *
     * @param playerName the name of the player (for potential future use)
     * @return the constructed dialog
     */
    public Dialog createLoginDialogForAudience(String playerName) {
        return loginBuilder.construct(null);
    }

    /**
     * Create a login dialog with error message for use with Audience.
     *
     * @param playerName   the name of the player
     * @param errorMessage the error message to display
     * @return the constructed dialog
     */
    public Dialog createLoginDialogForAudience(String playerName, Component errorMessage) {
        return loginBuilder.construct(null, errorMessage);
    }

    /**
     * Create a registration dialog for use with Audience.
     *
     * @param playerName the name of the player
     * @return the constructed dialog
     */
    public Dialog createRegistrationDialogForAudience(String playerName) {
        return registerBuilder.construct(null);
    }

    /**
     * Create a registration dialog with error message for use with Audience.
     *
     * @param playerName   the name of the player
     * @param errorMessage the error message to display
     * @return the constructed dialog
     */
    public Dialog createRegistrationDialogForAudience(String playerName, Component errorMessage) {
        return registerBuilder.construct(null, errorMessage);
    }

    /**
     * Create a rules dialog for use with Audience.
     *
     * @param playerName the name of the player
     * @return the constructed dialog
     */
    public Dialog createRulesDialogForAudience(String playerName) {
        return rulesBuilder.construct(null);
    }

    /**
     * Present the appropriate login dialog to a config-phase audience, gated
     * on auth-mode (text / PIN / slider). Captcha gate for trigger==0 is the
     * caller's responsibility (it owns the captcha instance and trust window).
     */
    public void presentLoginDialogForAudience(Audience audience, String playerName) {
        SettingsManager.AuthMode mode = settings.getAuthMode();
        if (mode == SettingsManager.AuthMode.PIN) {
            audience.showDialog(createPinDialogForAudience(0, null));
        } else if (mode == SettingsManager.AuthMode.SLIDER) {
            audience.showDialog(createSliderDialogForAudience(new int[settings.getSliderLength()], null));
        } else {
            audience.showDialog(createLoginDialogForAudience(playerName));
        }
    }

    // ==================== Builder accessors ====================

    public LoginDialogBuilder getLoginBuilder() {
        return loginBuilder;
    }

    public RegistrationDialogBuilder getRegisterBuilder() {
        return registerBuilder;
    }

    public RulesDialogBuilder getRulesBuilder() {
        return rulesBuilder;
    }

    public CaptchaDialogBuilder getCaptchaBuilder() {
        return captchaBuilder;
    }

    // ==================== Captcha dialog (D) ====================

    public Dialog createCaptchaDialog(Player player, String code, Component errorNotice) {
        return captchaBuilder.construct(player, code, errorNotice);
    }

    public Dialog createCaptchaDialogForAudience(String code, Component errorNotice) {
        return captchaBuilder.constructForAudience(code, errorNotice);
    }

    // ==================== TOTP 2FA dialog (E) ====================

    public Dialog createTotpDialog(Player player, Component errorNotice) {
        return totpBuilder.construct(player, errorNotice);
    }

    public Dialog createTotpDialogForAudience(Component errorNotice) {
        return totpBuilder.constructForAudience(errorNotice);
    }

    public TwoFactorDialogBuilder getTotpBuilder() {
        return totpBuilder;
    }

    // ==================== Forgot password / email recovery dialog (F)
    // ====================

    public Dialog createForgotEmailDialog(Player player, Component errorNotice) {
        return forgotBuilder.construct(player, errorNotice);
    }

    public Dialog createForgotEmailDialogForAudience(Component errorNotice) {
        return forgotBuilder.constructForAudience(errorNotice);
    }

    public Dialog createNoEmailDialog(Player player) {
        return forgotBuilder.constructNoEmail(player);
    }

    public Dialog createNoEmailDialogForAudience() {
        return forgotBuilder.constructNoEmailForAudience();
    }

    public Dialog createEmailVerifyDialog(Player player, String email, long cooldownRemaining, Component errorNotice) {
        return emailVerifyBuilder.construct(player, email, cooldownRemaining, errorNotice);
    }

    public Dialog createEmailVerifyDialogForAudience(String email, long cooldownRemaining, Component errorNotice) {
        return emailVerifyBuilder.constructForAudience(email, cooldownRemaining, errorNotice);
    }

    public Dialog createRecoverDialog(Player player, boolean mismatch) {
        return recoverBuilder.construct(player, mismatch);
    }

    public Dialog createRecoverDialogForAudience(boolean mismatch) {
        return recoverBuilder.constructForAudience(mismatch);
    }

    // ==================== PIN / Slider input modes (G+H) ====================

    public Dialog createPinDialog(Player player, int entered, String statusLine) {
        return pinBuilder.construct(player, entered, statusLine);
    }

    public Dialog createPinDialogForAudience(int entered, String statusLine) {
        return pinBuilder.constructForAudience(entered, statusLine);
    }

    public Dialog createSliderDialog(Player player, int[] digits, String statusLine) {
        return sliderBuilder.construct(player, digits, statusLine);
    }

    public Dialog createSliderDialogForAudience(int[] digits, String statusLine) {
        return sliderBuilder.constructForAudience(digits, statusLine);
    }

    // ==================== Custom screens (J) ====================

    public void bindScreenDismissStore(ScreenDismissStore store) {
        this.screenDismissStore = store;
    }

    public ScreenDismissStore getScreenDismissStore() {
        return screenDismissStore;
    }

    // ==================== Admin reset + premium set (K+L) ====================

    public void bindRecoverStore(RecoverStore store) {
        this.recoverStore = store;
    }

    public RecoverStore getRecoverStore() {
        return recoverStore;
    }

    public PremiumSetTracker getPremiumSetTracker() {
        return premiumSetTracker;
    }

    // ==================== Discord webhook (N) ====================

    public void bindDiscordWebhook(DiscordWebhook webhook) {
        this.discordWebhook = webhook;
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    public void markRecoverMode(java.util.UUID uuid, RecoverMode mode) {
        if (mode == RecoverMode.FORGOT) {
            recoverMode.remove(uuid);
        } else {
            recoverMode.put(uuid, mode);
        }
    }

    public RecoverMode getRecoverMode(java.util.UUID uuid) {
        return recoverMode.getOrDefault(uuid, RecoverMode.FORGOT);
    }

    public void clearRecoverMode(java.util.UUID uuid) {
        recoverMode.remove(uuid);
    }

    /**
     * Premium password-set dialog (feature K). Structurally identical to the
     * recover dialog (two password fields + RECOVER_SUBMIT), but labelled as a
     * "set your password" prompt for premium-auto-login players. Reuses the
     * recover builder so the existing submit handler applies the password via
     * {@code authBridge.changePassword}.
     */
    public Dialog createPremiumSetDialog(Player player, boolean mismatch) {
        return recoverBuilder.construct(player, mismatch);
    }

    public Dialog createPremiumSetDialogForAudience(boolean mismatch) {
        return recoverBuilder.constructForAudience(mismatch);
    }

    public Dialog createCustomScreenDialog(Player player, CustomScreen screen, int screenIndex) {
        return customScreenBuilder.construct(player, screen, screenIndex);
    }

    public Dialog createCustomScreenDialogForAudience(CustomScreen screen, int screenIndex) {
        return customScreenBuilder.constructForAudience(screen, screenIndex);
    }

    /**
     * Show a custom screen to an in-game player, respecting the dismiss
     * store and playing the on-show sound.
     */
    public void showCustomScreen(Player player, CustomScreen screen, int screenIndex) {
        if (screen == null || !screen.enabled()) return;
        if (screenDismissStore != null
                && screen.checkboxAction() == CustomScreen.CheckboxAction.CLOSE
                && screenDismissStore.isDismissed(player.getUniqueId(), screen.id())) {
            return;
        }
        if (screen.soundOnShow() != null && !screen.soundOnShow().isBlank()) {
            net.kyori.adventure.sound.Sound sound = settings.parseSound(screen.soundOnShow(), "screen_show");
            if (sound != null) {
                player.playSound(sound);
            }
        }
        player.showDialog(createCustomScreenDialog(player, screen, screenIndex));
    }
}
