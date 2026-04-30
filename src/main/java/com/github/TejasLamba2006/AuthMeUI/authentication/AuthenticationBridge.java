package com.github.TejasLamba2006.AuthMeUI.authentication;

import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class AuthenticationBridge {

    private static final int DEFAULT_MIN_PASSWORD_LENGTH = 5;
    private static final int DEFAULT_MAX_PASSWORD_LENGTH = 30;
    private static final int DEFAULT_SESSION_TIMEOUT_MINUTES = 10;
    private static final RegistrationSecondArgMode DEFAULT_REGISTRATION_SECOND_ARG_MODE = RegistrationSecondArgMode.CONFIRMATION;
    private static final Pattern BASIC_EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final Plugin plugin;
    private final AuthMeApi authMeApi;

    private volatile boolean sessionsEnabled;
    private volatile int sessionTimeoutMinutes;
    private volatile int minPasswordLength;
    private volatile int maxPasswordLength;
    private volatile RegistrationSecondArgMode registrationSecondArgMode;

    public AuthenticationBridge(Plugin plugin) {
        this.plugin = plugin;
        this.authMeApi = initializeApi();
        refreshAuthMeSettingsCache();
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
     * Refreshes AuthMe config-derived settings.
     * Should be called from a safe server thread (e.g. startup/reload).
     */
    public void refreshAuthMeSettingsCache() {
        boolean cachedSessionsEnabled = false;
        int cachedSessionTimeoutMinutes = DEFAULT_SESSION_TIMEOUT_MINUTES;
        int cachedMinPasswordLength = DEFAULT_MIN_PASSWORD_LENGTH;
        int cachedMaxPasswordLength = DEFAULT_MAX_PASSWORD_LENGTH;
        RegistrationSecondArgMode cachedRegistrationSecondArgMode = DEFAULT_REGISTRATION_SECOND_ARG_MODE;

        Plugin authMe = plugin.getServer().getPluginManager().getPlugin("AuthMe");
        if (authMe != null && authMe.isEnabled()) {
            FileConfiguration authConfig = authMe.getConfig();
            cachedSessionsEnabled = readBooleanWithFallback(
                authConfig,
                "settings.sessions.enabled",
                "sessions.enabled",
                false);
            cachedSessionTimeoutMinutes = readIntWithFallback(
                authConfig,
                "settings.sessions.timeout",
                "sessions.timeout",
                DEFAULT_SESSION_TIMEOUT_MINUTES);
            cachedMinPasswordLength = readIntWithFallback(
                authConfig,
                "settings.security.minPasswordLength",
                "security.minPasswordLength",
                DEFAULT_MIN_PASSWORD_LENGTH);
            cachedMaxPasswordLength = readIntWithFallback(
                authConfig,
                "settings.security.passwordMaxLength",
                "security.passwordMaxLength",
                DEFAULT_MAX_PASSWORD_LENGTH);
            String secondArgRaw = readStringWithFallback(
                authConfig,
                "settings.registration.secondArg",
                "registration.secondArg",
                DEFAULT_REGISTRATION_SECOND_ARG_MODE.name());
            cachedRegistrationSecondArgMode = RegistrationSecondArgMode.fromConfig(secondArgRaw);
        }

        this.sessionsEnabled = cachedSessionsEnabled;
        this.sessionTimeoutMinutes = cachedSessionTimeoutMinutes;
        this.minPasswordLength = cachedMinPasswordLength;
        this.maxPasswordLength = cachedMaxPasswordLength;
        this.registrationSecondArgMode = cachedRegistrationSecondArgMode;
    }

    private boolean readBooleanWithFallback(
            FileConfiguration config,
            String preferredPath,
            String fallbackPath,
            boolean defaultValue) {

        if (config.contains(preferredPath)) {
            return config.getBoolean(preferredPath, defaultValue);
        }
        return config.getBoolean(fallbackPath, defaultValue);
    }

    private int readIntWithFallback(
            FileConfiguration config,
            String preferredPath,
            String fallbackPath,
            int defaultValue) {

        if (config.contains(preferredPath)) {
            return config.getInt(preferredPath, defaultValue);
        }
        return config.getInt(fallbackPath, defaultValue);
    }

    private String readStringWithFallback(
            FileConfiguration config,
            String preferredPath,
            String fallbackPath,
            String defaultValue) {

        if (config.contains(preferredPath)) {
            return config.getString(preferredPath, defaultValue);
        }
        return config.getString(fallbackPath, defaultValue);
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

    /**
     * Force logout a player immediately.
     */
    public void forceLogout(Player player) {
        if (isConnected()) {
            authMeApi.forceLogout(player);
        }
    }

    /**
     * Checks if the player should be able to resume an AuthMe session based on
     * AuthMe's session settings, last login timestamp and last login IP.
     */
    public boolean canResumeSession(String playerName, String currentIpAddress) {
        if (!isConnected() || playerName == null || playerName.isBlank()
                || currentIpAddress == null || currentIpAddress.isBlank()) {
            return false;
        }

        if (!sessionsEnabled) {
            return false;
        }

        int timeoutMinutes = sessionTimeoutMinutes;
        if (timeoutMinutes <= 0) {
            return false;
        }

        String lastIp = authMeApi.getLastIp(playerName);
        if (lastIp == null || lastIp.isBlank() || !lastIp.equals(currentIpAddress)) {
            return false;
        }

        Instant lastLogin = authMeApi.getLastLoginTime(playerName);
        if (lastLogin == null) {
            return false;
        }

        long elapsedMillis = Duration.between(lastLogin, Instant.now()).toMillis();
        long timeoutMillis = timeoutMinutes * 60_000L;
        return elapsedMillis >= 0 && elapsedMillis < timeoutMillis;
    }

    public int fetchMinPasswordLength() {
        return minPasswordLength;
    }

    public int fetchMaxPasswordLength() {
        return maxPasswordLength;
    }

    public RegistrationSecondArgMode getRegistrationSecondArgMode() {
        return registrationSecondArgMode;
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

    public RegistrationResult attemptRegistration(Player player, String password, String secondArgument) {
        if (!isConnected()) {
            return RegistrationResult.SERVICE_UNAVAILABLE;
        }

        String playerName = player.getName();
        RegistrationResult validationResult = validateRegistrationInput(playerName, password);
        if (validationResult != null) {
            return validationResult;
        }

        return resolveInGameRegistrationResult(
                player,
                playerName,
                password,
                normalizeSecondArgument(secondArgument));
    }

    /**
     * Attempt registration using only the player name (for configuration phase).
     * This registers the player without requiring a Player object.
     *
     * @param playerName     the player's name
     * @param password       the password to register with
     * @param secondArgument the configured second argument for registration
     * @return the registration result
     */
    public RegistrationResult attemptRegistrationByName(String playerName, String password, String secondArgument) {
        if (!isConnected()) {
            return RegistrationResult.SERVICE_UNAVAILABLE;
        }

        RegistrationResult validationResult = validateRegistrationInput(playerName, password);
        if (validationResult != null) {
            return validationResult;
        }

        return resolveConfigurationPhaseRegistrationResult(
                playerName,
                password,
                normalizeSecondArgument(secondArgument));
    }

    private RegistrationResult validateRegistrationInput(String playerName, String password) {
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

        return null;
    }

    private RegistrationResult resolveInGameRegistrationResult(
            Player player,
            String playerName,
            String password,
            String secondArgument) {

        return switch (registrationSecondArgMode) {
            case CONFIRMATION -> handleConfirmationSecondArgument(
                    password,
                    secondArgument,
                    () -> registerWithApi(player, playerName, password));
            case EMAIL_MANDATORY -> handleMandatoryEmailSecondArgument(
                    secondArgument,
                    () -> registerWithCommand(player, playerName, password, secondArgument));
            case EMAIL_OPTIONAL -> handleOptionalEmailSecondArgument(
                    secondArgument,
                    () -> registerWithCommand(player, playerName, password, secondArgument),
                    () -> registerWithApi(player, playerName, password));
            case NONE -> registerWithApi(player, playerName, password);
        };
    }

    private RegistrationResult resolveConfigurationPhaseRegistrationResult(
            String playerName,
            String password,
            String secondArgument) {

        return switch (registrationSecondArgMode) {
            case CONFIRMATION -> handleConfirmationSecondArgument(
                    password,
                    secondArgument,
                    () -> registerByNameWithApi(playerName, password));
            case EMAIL_MANDATORY -> handleMandatoryEmailSecondArgument(
                    secondArgument,
                    () -> RegistrationResult.UNSUPPORTED_IN_CONFIGURATION_PHASE);
            case EMAIL_OPTIONAL -> handleOptionalEmailSecondArgument(
                    secondArgument,
                    () -> RegistrationResult.UNSUPPORTED_IN_CONFIGURATION_PHASE,
                    () -> registerByNameWithApi(playerName, password));
            case NONE -> registerByNameWithApi(playerName, password);
        };
    }

    private RegistrationResult handleConfirmationSecondArgument(
            String password,
            String secondArgument,
            Supplier<RegistrationResult> successAction) {

        if (secondArgument == null || secondArgument.isBlank()) {
            return RegistrationResult.SECOND_ARGUMENT_REQUIRED;
        }

        if (!password.equals(secondArgument)) {
            return RegistrationResult.PASSWORD_MISMATCH;
        }

        return successAction.get();
    }

    private RegistrationResult handleMandatoryEmailSecondArgument(
            String secondArgument,
            Supplier<RegistrationResult> successAction) {

        if (secondArgument == null || secondArgument.isBlank()) {
            return RegistrationResult.EMAIL_REQUIRED;
        }

        if (!isValidEmail(secondArgument)) {
            return RegistrationResult.EMAIL_INVALID;
        }

        return successAction.get();
    }

    private RegistrationResult handleOptionalEmailSecondArgument(
            String secondArgument,
            Supplier<RegistrationResult> withEmailAction,
            Supplier<RegistrationResult> withoutEmailAction) {

        if (secondArgument == null || secondArgument.isBlank()) {
            return withoutEmailAction.get();
        }

        if (!isValidEmail(secondArgument)) {
            return RegistrationResult.EMAIL_INVALID;
        }

        return withEmailAction.get();
    }

    private String normalizeSecondArgument(String secondArgument) {
        return secondArgument == null ? null : secondArgument.trim();
    }

    private RegistrationResult registerWithApi(Player player, String playerName, String password) {
        try {
            authMeApi.forceRegister(player, password, true);
            return RegistrationResult.SUCCESS;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, ex, () -> "Registration failed for " + playerName);
            return RegistrationResult.ERROR;
        }
    }

    private RegistrationResult registerByNameWithApi(String playerName, String password) {
        try {
            authMeApi.registerPlayer(playerName, password);
            return RegistrationResult.SUCCESS;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, ex, () -> "Registration failed for " + playerName);
            return RegistrationResult.ERROR;
        }
    }

    private RegistrationResult registerWithCommand(Player player, String playerName, String password,
            String secondArg) {
        String commandLine = "register " + password + " " + secondArg;

        try {
            boolean commandAccepted = plugin.getServer().dispatchCommand(player, commandLine);
            if (!commandAccepted) {
                plugin.getLogger().log(Level.WARNING, () -> "Registration command was not accepted for " + playerName);
                return RegistrationResult.ERROR;
            }
            return RegistrationResult.SUCCESS;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, ex, () -> "Registration command failed for " + playerName);
            return RegistrationResult.ERROR;
        }
    }

    private boolean isValidEmail(String value) {
        return value != null && BASIC_EMAIL_PATTERN.matcher(value.trim()).matches();
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
        SECOND_ARGUMENT_REQUIRED,
        PASSWORD_MISMATCH,
        PASSWORD_TOO_SHORT,
        PASSWORD_TOO_LONG,
        EMAIL_REQUIRED,
        EMAIL_INVALID,
        UNSUPPORTED_IN_CONFIGURATION_PHASE,
        INVALID_PASSWORD,
        SERVICE_UNAVAILABLE,
        ERROR
    }

    public enum RegistrationSecondArgMode {
        NONE,
        CONFIRMATION,
        EMAIL_OPTIONAL,
        EMAIL_MANDATORY;

        public static RegistrationSecondArgMode fromConfig(String value) {
            if (value == null || value.isBlank()) {
                return DEFAULT_REGISTRATION_SECOND_ARG_MODE;
            }

            try {
                return RegistrationSecondArgMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return DEFAULT_REGISTRATION_SECOND_ARG_MODE;
            }
        }

        public boolean usesEmail() {
            return this == EMAIL_OPTIONAL || this == EMAIL_MANDATORY;
        }
    }
}
