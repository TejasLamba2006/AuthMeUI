package com.github.TejasLamba2006.AuthMeUI.dialogs;

import com.github.TejasLamba2006.AuthMeUI.authentication.AuthenticationBridge;
import com.github.TejasLamba2006.AuthMeUI.configuration.SettingsManager;
import io.papermc.paper.dialog.Dialog;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public class DialogManager {

    private final SettingsManager settings;
    private final LoginDialogBuilder loginBuilder;
    private final RegistrationDialogBuilder registerBuilder;
    private final RulesDialogBuilder rulesBuilder;

    public DialogManager(SettingsManager settings, AuthenticationBridge authBridge) {
        this.settings = settings;
        this.loginBuilder = new LoginDialogBuilder(settings);
        this.registerBuilder = new RegistrationDialogBuilder(settings, authBridge);
        this.rulesBuilder = new RulesDialogBuilder(settings);
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
        if (isRegistered) {
            player.showDialog(createLoginDialog(player));
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
}
