# Changelog

All notable changes to AuthMeUI will be documented in this file.

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

### Notes

- Maintains compatibility with normal Paper while adding Folia-safe scheduling paths.

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
