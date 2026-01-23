# AuthMeUI

A modern, sleek dialog-based authentication interface for [AuthMe](https://github.com/AuthMe/AuthMeReloaded) on Paper servers.

<center>

![auth-me-banner](image.png)

</center>

https://www.youtube.com/watch?v=NH7pD3-_vso&source_ve_path=MTc4NDI0

## Overview

AuthMeUI replaces the traditional chat-based login and registration prompts with beautiful, native Minecraft dialogs. Players see clean popup windows for entering passwords, registering accounts, and accepting server rules - no more spamming commands in chat!

## Features

- **Native Dialog Windows** - Uses Minecraft's built-in dialog system for a seamless experience
- **Login Dialog** - Clean password entry with customizable buttons
- **Registration Dialog** - Password + confirmation fields with validation
- **Rules Dialog** - Display server rules players must accept before playing
- **Fully Customizable** - Every message, button, and title can be configured
- **PlaceholderAPI Support** - Use any placeholder in your dialog text
- **MiniMessage Formatting** - Full support for colors, gradients, and formatting
- **Universal AuthMe Support** - Works with all AuthMe forks out of the box
- **bStats Integration** - Anonymous usage statistics

## Supported AuthMe Versions

AuthMeUI is compatible with all major AuthMe forks:

| Plugin | Support | Link |
|--------|---------|------|
| **AuthMeReloaded** | ✅ Full | [Spigot](https://www.spigotmc.org/resources/authmereloaded.6269/) · [GitHub](https://github.com/AuthMe/AuthMeReloaded) |
| **AuthMeReReloaded** | ✅ Full | [Modrinth](https://modrinth.com/plugin/authmerereloaded) · [GitHub](https://github.com/HaHaWTH/AuthMeReReloaded) |
| Other AuthMe forks | ✅ Should work | As long as they use the standard AuthMe API |

## Screenshots

### Login Dialog

![Login Dialog](https://cdn.modrinth.com/data/cached_images/67c218f0ef4204d3daaa53e78c84f8cf459a7cd4_0.webp)

### Registration Dialog  

![Registration Dialog](https://cdn.modrinth.com/data/cached_images/2e54179dbd4ac7048d2453c70218aed1878e5578_0.webp)

### Rules Dialog

![Rules Dialog](https://cdn.modrinth.com/data/cached_images/b34021f9e9eb4742e3d36d1f6f5dd92c8429e36b_0.webp)

## Requirements

- **Paper** 1.21.7 or newer (requires Dialog API)
- **AuthMe** 5.6.0 or newer (or any compatible fork like AuthMeReReloaded)
- **Java** 21 or newer

## Installation

1. Download the latest release from [Modrinth](https://modrinth.com/plugin/authmeui) or [GitHub Releases](https://github.com/TejasLamba2006/AuthMeUI/releases)
2. Drop the JAR file into your server's `plugins` folder
3. Restart your server
4. Edit `plugins/AuthMeUI/config.yml` to customize messages
5. Run `/authmeui reload` to apply changes

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/authmeui` | - | Shows plugin info |
| `/authmeui reload` | `authmeui.reload` | Reloads configuration |
| `/authmeui show <player>` | `authmeui.show` | Shows login dialog to a player |

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `authmeui.reload` | OP | Reload plugin configuration |
| `authmeui.show` | OP | Show dialogs to other players |

## Configuration

```yaml
# Dialog Settings
dialog:
  can-close-with-escape: false  # Prevent closing dialogs with ESC
  button-columns: 2             # Number of button columns
  input-width: 200              # Width of text input fields

# Login Dialog
login-dialog:
  title: "<gold>Welcome Back!"
  body:
    - "<gray>Please enter your password to continue."
  submit-button: "<green>Login"
  password-label: "Password"

# And much more...
```

See the full [config.yml](https://github.com/TejasLamba2006/AuthMeUI/blob/main/src/main/resources/config.yml) for all options.

## Building from Source

```bash
git clone https://github.com/TejasLamba2006/AuthMeUI.git
cd AuthMeUI
mvn clean package
```

The compiled JAR will be in `target/AuthMeUI-1.0.0.jar`

## Support

- **Issues**: [GitHub Issues](https://github.com/TejasLamba2006/AuthMeUI/issues)
- **Discord**: [Join our Discord](https://discord.gg/msEkYDWpXM)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

- **Author**: TejasLamba2006
- **Built for**: Paper servers running AuthMe
- **Statistics**: [bStats](https://bstats.org/plugin/bukkit/AuthMeUI/26894)

---

Made with love for the Minecraft community
