package com.github.TejasLamba2006.AuthMeUI.authentication;

import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

public class AuthenticationBridge {

    private final Plugin plugin;
    private final AuthMeApi authMeApi;

    public AuthenticationBridge(Plugin plugin) {
        this.plugin = plugin;
        this.authMeApi = initializeApi();
    }

    private AuthMeApi initializeApi() {
        if (plugin.getServer().getPluginManager().isPluginEnabled("AuthMe")) {
            return AuthMeApi.getInstance();
        }
        return null;
    }

    public boolean isConnected() {
        return authMeApi != null;
    }

    public boolean isPlayerAuthenticated(Player player) {
        return isConnected() && authMeApi.isAuthenticated(player);
    }

    public boolean isPlayerRegistered(String playerName) {
        return isConnected() && authMeApi.isRegistered(playerName);
    }

    public boolean validateCredentials(String playerName, String password) {
        return isConnected() && authMeApi.checkPassword(playerName, password);
    }

    /**
     * Force login a player without password validation.
     * Used after configuration phase authentication where the password was already
     * verified.
     */
    public void forceLogin(Player player) {
        if (isConnected()) {
            authMeApi.forceLogin(player);
        }
    }

    public int fetchMinPasswordLength() {
        Plugin authMe = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (authMe != null && authMe.isEnabled()) {
            return authMe.getConfig().getInt("security.minPasswordLength", 5);
        }
        return 5;
    }

    public int fetchMaxPasswordLength() {
        Plugin authMe = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (authMe != null && authMe.isEnabled()) {
            return authMe.getConfig().getInt("security.passwordMaxLength", 30);
        }
        return 30;
    }

    public AuthResult attemptLogin(Player player, String password) {
        if (!isConnected()) {
            return AuthResult.SERVICE_UNAVAILABLE;
        }

        if (password == null || password.isBlank()) {
            return AuthResult.EMPTY_PASSWORD;
        }

        String playerName = player.getName();

        if (!isPlayerRegistered(playerName)) {
            return AuthResult.NOT_REGISTERED;
        }

        try {
            if (authMeApi.checkPassword(playerName, password)) {
                authMeApi.forceLogin(player);
                return AuthResult.SUCCESS;
            }
            return AuthResult.INVALID_PASSWORD;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Login attempt failed for " + playerName, ex);
            return AuthResult.ERROR;
        }
    }

    /**
     * Attempt login using only the player name (for configuration phase).
     * This only validates the password but does NOT force login since the player
     * isn't in-game yet.
     *
     * @param playerName the player's name
     * @param password   the password to check
     * @return the authentication result
     */
    public AuthResult attemptLoginByName(String playerName, String password) {
        if (!isConnected()) {
            return AuthResult.SERVICE_UNAVAILABLE;
        }

        if (password == null || password.isBlank()) {
            return AuthResult.EMPTY_PASSWORD;
        }

        if (!isPlayerRegistered(playerName)) {
            return AuthResult.NOT_REGISTERED;
        }

        try {
            if (authMeApi.checkPassword(playerName, password)) {
                // Note: We don't call forceLogin here because the player isn't in-game yet.
                // The player will be authenticated when they complete the configuration phase.
                return AuthResult.SUCCESS;
            }
            return AuthResult.INVALID_PASSWORD;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Login attempt failed for " + playerName, ex);
            return AuthResult.ERROR;
        }
    }

    public RegistrationResult attemptRegistration(Player player, String password, String confirmPassword) {
        if (!isConnected()) {
            return RegistrationResult.SERVICE_UNAVAILABLE;
        }

        String playerName = player.getName();

        if (isPlayerRegistered(playerName)) {
            return RegistrationResult.ALREADY_EXISTS;
        }

        if (password == null || password.isBlank()) {
            return RegistrationResult.INVALID_PASSWORD;
        }

        int minLength = fetchMinPasswordLength();
        int maxLength = fetchMaxPasswordLength();

        if (password.length() < minLength) {
            return RegistrationResult.PASSWORD_TOO_SHORT;
        }

        if (password.length() > maxLength) {
            return RegistrationResult.PASSWORD_TOO_LONG;
        }

        if (confirmPassword != null && !confirmPassword.isBlank() && !password.equals(confirmPassword)) {
            return RegistrationResult.PASSWORD_MISMATCH;
        }

        try {
            authMeApi.forceRegister(player, password, true);
            return RegistrationResult.SUCCESS;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Registration failed for " + playerName, ex);
            return RegistrationResult.ERROR;
        }
    }

    /**
     * Attempt registration using only the player name (for configuration phase).
     * This registers the player without requiring a Player object.
     *
     * @param playerName      the player's name
     * @param password        the password to register with
     * @param confirmPassword the password confirmation
     * @return the registration result
     */
    public RegistrationResult attemptRegistrationByName(String playerName, String password, String confirmPassword) {
        if (!isConnected()) {
            return RegistrationResult.SERVICE_UNAVAILABLE;
        }

        if (isPlayerRegistered(playerName)) {
            return RegistrationResult.ALREADY_EXISTS;
        }

        if (password == null || password.isBlank()) {
            return RegistrationResult.INVALID_PASSWORD;
        }

        int minLength = fetchMinPasswordLength();
        int maxLength = fetchMaxPasswordLength();

        if (password.length() < minLength) {
            return RegistrationResult.PASSWORD_TOO_SHORT;
        }

        if (password.length() > maxLength) {
            return RegistrationResult.PASSWORD_TOO_LONG;
        }

        if (confirmPassword != null && !confirmPassword.isBlank() && !password.equals(confirmPassword)) {
            return RegistrationResult.PASSWORD_MISMATCH;
        }

        try {
            // Register the player using AuthMe API with player name only
            authMeApi.registerPlayer(playerName, password);
            return RegistrationResult.SUCCESS;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Registration failed for " + playerName, ex);
            return RegistrationResult.ERROR;
        }
    }

    public enum AuthResult {
        SUCCESS,
        INVALID_PASSWORD,
        NOT_REGISTERED,
        EMPTY_PASSWORD,
        SERVICE_UNAVAILABLE,
        ERROR
    }

    public enum RegistrationResult {
        SUCCESS,
        ALREADY_EXISTS,
        PASSWORD_MISMATCH,
        PASSWORD_TOO_SHORT,
        PASSWORD_TOO_LONG,
        INVALID_PASSWORD,
        SERVICE_UNAVAILABLE,
        ERROR
    }
}
