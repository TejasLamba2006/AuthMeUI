package com.github.TejasLamba2006.AuthMeUI.authentication;

import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
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

    // ponytail: reflection handles into AuthMe internals (DataSource, PlayerCache,
    // EmailService, TotpAuthenticator). Mirrors AuthMeBia's AuthMe.initReflection()
    // exactly — these classes aren't on the public AuthMeApi surface, so reflection
    // is the only source-compatible path across 5.7/6.0. All null-gated; a missing
    // handle just disables the feature (email/2FA/premium-skip) gracefully.
    private Object dataSource;
    private Method dsGetAuth;
    private Method dsHasSession;
    private Method dsUpdateEmail;
    private Object playerCache;
    private Method pcIsAuthenticatedByName;
    private Object emailService;
    private Method emailHasAllInfo;
    private Method emailSendVerification;
    private Method authIsPremium;
    private Method authGetPremiumUuid;
    private Method authGetEmail;
    private Method authSetEmail;
    private Method authGetTotpKey;
    private Object totpAuthenticator;
    private Method totpCheckCode;

    private volatile boolean sessionsEnabled;
    private volatile int sessionTimeoutMinutes;
    private volatile int minPasswordLength;
    private volatile int maxPasswordLength;
    private volatile RegistrationSecondArgMode registrationSecondArgMode;

    public AuthenticationBridge(Plugin plugin) {
        this.plugin = plugin;
        this.authMeApi = initializeApi();
        initReflection();
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

    /**
     * Bind reflection handles into AuthMe internals (DataSource, PlayerCache,
     * EmailService, TotpAuthenticator) for features the public AuthMeApi
     * doesn't expose (email verify, 2FA, premium-skip, stored-email read).
     * Mirrors AuthMeBia's AuthMe.initReflection() exactly. Each block is
     * independently try/caught so a missing class in one area doesn't kill
     * the others.
     */
    private void initReflection() {
        if (authMeApi == null) {
            return;
        }

        try {
            Class<?> dsClass = Class.forName("fr.xephi.authme.datasource.DataSource");
            Field dsField = authMeApi.getClass().getDeclaredField("dataSource");
            dsField.setAccessible(true);
            dataSource = dsField.get(authMeApi);
            dsGetAuth = dsClass.getMethod("getAuth", String.class);
            dsHasSession = dsClass.getMethod("hasSession", String.class);
            dsUpdateEmail = dsClass.getMethod("updateEmail",
                    Class.forName("fr.xephi.authme.data.auth.PlayerAuth"));
            Class<?> authClass = Class.forName("fr.xephi.authme.data.auth.PlayerAuth");
            authGetEmail = authClass.getMethod("getEmail");
            authSetEmail = authClass.getMethod("setEmail", String.class);
            authGetTotpKey = authClass.getMethod("getTotpKey");
            try {
                authIsPremium = authClass.getMethod("isPremium");
                authGetPremiumUuid = authClass.getMethod("getPremiumUuid");
            } catch (NoSuchMethodException e) {
                // ponytail: premium fields are fork-specific; missing them only disables premium skip.
                authIsPremium = null;
                authGetPremiumUuid = null;
            }
        } catch (Throwable t) {
            plugin.getLogger().info("AuthMe DataSource reflection unavailable; "
                    + "premium skip, email, and 2FA features disabled (" + t + ")");
            dataSource = null;
        }

        try {
            Field pcField = authMeApi.getClass().getDeclaredField("playerCache");
            pcField.setAccessible(true);
            playerCache = pcField.get(authMeApi);
            pcIsAuthenticatedByName = Class.forName("fr.xephi.authme.data.auth.PlayerCache")
                    .getMethod("isAuthenticated", String.class);
        } catch (Throwable t) {
            playerCache = null;
        }

        Object injector = resolveInjector();
        if (injector == null) {
            return;
        }
        try {
            Method getSingleton = injectorClass(injector).getMethod("getSingleton", Class.class);
            Class<?> emailSvcClass = Class.forName("fr.xephi.authme.mail.EmailService");
            emailService = getSingleton.invoke(injector, emailSvcClass);
            emailHasAllInfo = emailSvcClass.getMethod("hasAllInformation");
            try {
                emailSendVerification = emailSvcClass.getMethod(
                        "sendVerificationMail", String.class, String.class, String.class);
            } catch (NoSuchMethodException e) {
                // AuthMeReReloaded fork: extra <time /> tag argument, returns void.
                emailSendVerification = emailSvcClass.getMethod("sendVerificationMail",
                        String.class, String.class, String.class, String.class);
            }
        } catch (Throwable t) {
            plugin.getLogger().info("AuthMe email reflection unavailable; email verification disabled (" + t + ")");
            emailService = null;
        }

        try {
            Method getSingleton = injectorClass(injector).getMethod("getSingleton", Class.class);
            Class<?> totpClass = Class.forName("fr.xephi.authme.security.totp.TotpAuthenticator");
            totpAuthenticator = getSingleton.invoke(injector, totpClass);
            Class<?> playerAuthClass = Class.forName("fr.xephi.authme.data.auth.PlayerAuth");
            totpCheckCode = totpClass.getMethod("checkCode", playerAuthClass, String.class);
        } catch (Throwable t) {
            plugin.getLogger().info("AuthMe TOTP reflection unavailable; 2FA dialog disabled (" + t + ")");
            totpAuthenticator = null;
        }
    }

    /** AuthMe builds may relocate the injector library under fr.xephi.authme.libs. */
    private static Class<?> injectorClass(Object injector) throws ClassNotFoundException {
        // AuthMe 6 loads the injector via plugin.yml libraries, invisible to dependents' loaders.
        ClassLoader loader = injector.getClass().getClassLoader();
        try {
            return Class.forName("ch.jalu.injector.Injector", false, loader);
        } catch (ClassNotFoundException e) {
            return Class.forName("fr.xephi.authme.libs.ch.jalu.injector.Injector", false, loader);
        }
    }

    /**
     * Reach AuthMe's injector via api.getPlugin().injector — the verified path
     * AuthMeBia uses to obtain EmailService/TotpAuthenticator singletons.
     */
    private Object resolveInjector() {
        try {
            Object authMePlugin = authMeApi.getClass().getMethod("getPlugin").invoke(authMeApi);
            Field injField = authMePlugin.getClass().getDeclaredField("injector");
            injField.setAccessible(true);
            return injField.get(authMePlugin);
        } catch (Throwable t) {
            plugin.getLogger().info("AuthMe injector unreachable; email/TOTP features disabled (" + t + ")");
            return null;
        }
    }

    // ==================== Reflection-backed AuthMe internals ====================
    // ponytail: all of these return null/false silently on reflection failure —
    // callers treat null as "feature unavailable" and fall back to the plain
    // login/register flow. No throwing into the dialog layer.

    public boolean isEmailVerificationActive() {
        if (emailService == null || emailHasAllInfo == null) {
            return false;
        }
        try {
            return (boolean) emailHasAllInfo.invoke(emailService);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean sendVerificationEmail(String name, String email, String code) {
        if (emailService == null || emailSendVerification == null) {
            return false;
        }
        try {
            if (emailSendVerification.getParameterCount() == 4) {
                // ponytail: void variant can't report SMTP failure; config completeness is the best signal.
                if (emailHasAllInfo != null && !(boolean) emailHasAllInfo.invoke(emailService)) {
                    return false;
                }
                String time = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                emailSendVerification.invoke(emailService, name, email, code, time);
                return true;
            }
            return (boolean) emailSendVerification.invoke(emailService, name, email, code);
        } catch (Exception e) {
            plugin.getLogger().warning("sendVerificationMail failed for " + name + ": " + e.getMessage());
            return false;
        }
    }

    public String getStoredEmail(String name) {
        if (dataSource == null || dsGetAuth == null || authGetEmail == null || name == null) {
            return null;
        }
        try {
            Object auth = dsGetAuth.invoke(dataSource, name.toLowerCase(Locale.ROOT));
            if (auth == null) {
                return null;
            }
            return (String) authGetEmail.invoke(auth);
        } catch (Exception e) {
            return null;
        }
    }

    public void storeEmail(String name, String email) {
        if (dataSource == null || dsGetAuth == null || dsUpdateEmail == null || authSetEmail == null) {
            return;
        }
        try {
            Object auth = dsGetAuth.invoke(dataSource, name.toLowerCase(Locale.ROOT));
            if (auth == null) {
                return;
            }
            authSetEmail.invoke(auth, email);
            dsUpdateEmail.invoke(dataSource, auth);
        } catch (Exception e) {
            plugin.getLogger().warning("storeEmail failed for " + name + ": " + e.getMessage());
        }
    }

    public boolean hasTotpEnabled(String name) {
        if (totpAuthenticator == null || dataSource == null || dsGetAuth == null
                || authGetTotpKey == null || name == null) {
            return false;
        }
        try {
            Object auth = dsGetAuth.invoke(dataSource, name.toLowerCase(Locale.ROOT));
            if (auth == null) {
                return false;
            }
            String totpKey = (String) authGetTotpKey.invoke(auth);
            return totpKey != null && !totpKey.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean checkTotpCode(String name, String code) {
        if (totpAuthenticator == null || dataSource == null || dsGetAuth == null
                || totpCheckCode == null || name == null) {
            return false;
        }
        try {
            Object auth = dsGetAuth.invoke(dataSource, name.toLowerCase(Locale.ROOT));
            if (auth == null) {
                return false;
            }
            return (boolean) totpCheckCode.invoke(totpAuthenticator, auth, code);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isPremiumSkip(UUID connectingUuid, String name) {
        if (dataSource == null || dsGetAuth == null || authIsPremium == null || name == null) {
            return false;
        }
        if (connectingUuid == null || connectingUuid.version() != 4) {
            return false;
        }
        try {
            Object auth = dsGetAuth.invoke(dataSource, name.toLowerCase(Locale.ROOT));
            if (auth == null) {
                return false;
            }
            if (!(boolean) authIsPremium.invoke(auth)) {
                return false;
            }
            Object premiumUuid = authGetPremiumUuid.invoke(auth);
            return connectingUuid.equals(premiumUuid);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Change a player's password by name (public AuthMeApi call, no reflection).
     * Used by forgot-password recovery and admin-forced reset (F, L).
     */
    public boolean changePassword(String playerName, String newPassword) {
        if (!isConnected()) {
            return false;
        }
        try {
            authMeApi.changePassword(playerName, newPassword);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("changePassword failed for " + playerName + ": " + e.getMessage());
            return false;
        }
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

    public boolean isValidEmail(String value) {
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
