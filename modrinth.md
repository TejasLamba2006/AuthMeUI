# AuthMeUI - Beautiful Login Dialogs for Your Server

Tired of players typing passwords in chat? Want a cleaner, more professional login experience? AuthMeUI brings modern popup dialogs to AuthMe authentication!

<iframe width="560" height="315" src="https://www.youtube-nocookie.com/embed/NH7pD3-_vso" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>

---

## What Does It Do?

When players join your server, instead of seeing "Please login with /login <password>" in chat, they get a nice popup window where they can type their password directly. Same goes for registration - a clean dialog with password fields, not messy chat commands.

It's like upgrading from a flip phone to a smartphone, but for your server's login system.

---

## The Experience

### For Returning Players

They see a welcoming dialog asking for their password. Type it in, click login, done. No commands to remember, no typos in chat for everyone to see.

![Login Dialog](https://cdn.modrinth.com/data/cached_images/67c218f0ef4204d3daaa53e78c84f8cf459a7cd4_0.webp)

### For New Players  

A registration dialog pops up with two password fields (password + confirm). They fill it out, click register, and they're good to go. You can even make them accept your server rules first!

![Registration Dialog](https://cdn.modrinth.com/data/cached_images/2e54179dbd4ac7048d2453c70218aed1878e5578_0.webp)

### Server Rules

Want players to read and accept rules before playing? AuthMeUI can show them a rules dialog they have to accept. No more "I didn't know that was against the rules" excuses.

![Rules Dialog](https://cdn.modrinth.com/data/cached_images/b34021f9e9eb4742e3d36d1f6f5dd92c8429e36b_0.webp)

---

## Why Players Love It

- **No more typing passwords in chat** where they might accidentally send them publicly
- **Clean, familiar interface** that feels like a proper game menu
- **Faster login process** - just type and click
- **Works on vanilla clients** - no mods needed

## Why Server Owners Love It

- **Pre-join authentication** - NEW! Make players authenticate before they even enter your world
- **Fully customizable** - change every single message, button, and title
- **Supports placeholders** - show player names, online counts, whatever you want
- **MiniMessage formatting** - gradients, colors, hover effects, the works
- **Lightweight** - doesn't affect server performance
- **Just works** - drop it in, configure it, forget about it

---

## 🆕 NEW: Pre-Join Authentication Mode

Want players to log in BEFORE they see your world? Enable configuration phase mode and the login dialog appears during the loading screen. Players who don't authenticate simply can't join!

```yaml
dialogs:
  use-configuration-phase: true
  configuration-phase-timeout: 60
```

---

AuthMeUI is compatible with all major AuthMe forks:

| Plugin | Support | Link |
|--------|---------|------|
| **AuthMeReloaded** | ✅ Full | [Spigot](https://www.spigotmc.org/resources/authmereloaded.6269/) · [GitHub](https://github.com/AuthMe/AuthMeReloaded) |
| **AuthMeReReloaded** | ✅ Full | [Modrinth](https://modrinth.com/plugin/authmerereloaded) · [GitHub](https://github.com/HaHaWTH/AuthMeReReloaded) |
| Other AuthMe forks | ✅ Should work | As long as they use the standard AuthMe API |

---

## Quick Setup

1. Make sure you have **AuthMe** or any forks installed
2. Drop AuthMeUI in your plugins folder  
3. Restart the server
4. Edit the config to match your server's style
5. That's it!

---

## Requirements

- Paper 1.21.7 or newer
- AuthMe or any valid forks
- Java 21

---

## Configuration Preview

Everything is customizable through a simple config file:

```yaml
login-dialog:
  title: "<gold>Welcome Back!"
  body:
    - "<gray>Please enter your password to continue."
    - ""
    - "<yellow>Forgot your password? Contact staff!"
  submit-button: "<green>Login"
```

---

## Need Help?

- Found a bug? [Open an issue](https://github.com/TejasLamba2006/AuthMeUI/issues)
- Have questions? Drop by the Discord - <https://discord.gg/msEkYDWpXM>

---

Made by **TejasLamba2006** for servers that care about player experience.
