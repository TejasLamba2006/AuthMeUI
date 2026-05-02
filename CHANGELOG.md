# Changelog

All notable changes to AuthMeUI will be documented in this file.

---

## [1.3.3] - 2026-05-02

### Behavior update

- Removed the automatic dialog reopen loop from the post-join authentication watchdog.
- The auth dialog now opens once after the initial delay and stays closed until the next auth flow is triggered.

---

## [1.3.2] - 2026-04-26

### Hotfix

- Fixed AuthMe config path detection for registration/security settings when forks expose them under `settings.*`.
- `registration.secondArg` is now resolved with compatibility fallbacks, so `EMAIL_MANDATORY` is detected correctly in affected setups.
- Added a default "Forgot Password" action button to the login dialog (configurable via `login-dialog.forgot-button-*`).
- Added `type: forgot` support in `login-dialog.actions` for explicit action ordering in custom button layouts.
- Added placeholder parsing support for dialog body text:
  - Parses PlaceholderAPI placeholders when available (player-bound dialogs).
  - Parses ItemsAdder font tokens (e.g. `:token:` style) when ItemsAdder is installed.

---

## [1.3.1] - 2026-04-24

### Registration fixes

- Added support for AuthMe `registration.secondArg` modes in the dialog flow:
  - `NONE`: registration now accepts a single password input.
  - `CONFIRMATION`: registration requires password confirmation.
  - `EMAIL_OPTIONAL` / `EMAIL_MANDATORY`: registration now uses an email-aware second field.
- Registration now uses command-path submission for email modes so `EMAIL_MANDATORY` works with AuthMe's expected `/register <password> <email>` behavior.
- Added explicit validation and user-facing errors for missing/invalid email and missing confirmation.

### Stability improvements

- Reworked post-submit registration verification to retry for a short window instead of checking once after a fixed 3 ticks.
- This prevents false "Registration failed" dialogs when AuthMe registration state becomes visible a little later.
- Added a post-join authentication dialog watchdog that re-opens the auth dialog for still unauthenticated players.
- This helps recover the flow when external URL confirmation prompts close the dialog without authenticating.

### Custom action button improvements

- Added URL button support for configurable dialog actions using `type: url` (also accepts `open-url` and `open_url`).
- URL actions now accept links via `template` (or `url`) and open external pages from the dialog button click.
- Added URL validation and warning logs for missing/invalid links or unsupported URL schemes.

---

## [1.3.0] - 2026-04-10

### Folia compatibility

- Added `folia-supported: true` in plugin metadata.
- Replaced Bukkit scheduler usage with player entity schedulers for player-bound tasks.
- Updated delayed and repeating auth/dialog flows to run on player-owned schedulers.

### Async-safety improvements

- Added cached AuthMe settings in `AuthenticationBridge` for session and password constraints.
- Removed async-path Bukkit plugin/config lookups in configuration-phase checks.
- Added cache refresh call during `/authmeui reload`.

---

## [1.2.0] - 2026-04-08

### Compatibility updates

- Configuration phase now respects AuthMe session behavior for returning players when session compatibility is enabled.
- Added FastLogin-aware handling in configuration phase mode so premium players are not blocked by an unnecessary pre-join login dialog.
- Registered players can be deferred to a post-join auth check with a configurable delay.

### Login dialog improvements

- Added a dedicated Cancel action for the login dialog.
- Cancel now disconnects the player immediately, and the in-game flow also triggers a server-side AuthMe logout.
- Added a configurable cancellation message (`messages.login.cancelled`).

### Behavior fixes

- Login submissions with an empty password no longer show a false error if the player is already authenticated.
- Improved cleanup for deferred and cancelled configuration-phase authentication states.

### New config options

- `dialogs.configuration-phase-respect-authme-sessions` (default: `true`)
- `dialogs.configuration-phase-fastlogin-compatibility` (default: `true`)
- `dialogs.configuration-phase-deferred-login-check-delay-ticks` (default: `40`)
- `login-dialog.cancel-button-enabled` (default: `true`)
- `login-dialog.cancel-button` (default: `<red>Cancel</red>`)

---

## [1.1.0] - 2026-01-24

### New Feature: Pre-Join Authentication

**Authenticate players BEFORE they enter your server!**

Ever wanted players to log in or register before they even see your spawn? Now you can! With the new "Configuration Phase" mode, the login/registration dialog appears during the loading screen - players can't enter your world until they've authenticated.

#### How it works

- **Default mode (off)**: Players join the server first, then see the login dialog (same as before)
- **New mode (on)**: Players see the login dialog while connecting - they can't join until they authenticate

#### Why use it?

- Prevents unauthenticated players from ever seeing your world
- Cleaner experience - no "please login" messages in chat
- Players who fail to authenticate are simply disconnected

#### How to enable

Add these lines to your `config.yml`:

```yaml
dialogs:
  use-configuration-phase: true
  configuration-phase-timeout: 60
```

The timeout is how many seconds a player has to authenticate before being disconnected (default: 60 seconds).

---

## [1.0.0] - Initial Release

- Native dialog windows for login and registration
- Server rules dialog with agreement checkbox
- Full MiniMessage formatting support
- PlaceholderAPI support
- Works with all AuthMe forks
- Fully customizable messages and buttons
