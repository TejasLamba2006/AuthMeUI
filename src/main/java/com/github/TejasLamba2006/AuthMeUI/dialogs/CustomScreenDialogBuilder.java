package com.github.TejasLamba2006.AuthMeUI.dialogs;

import com.github.TejasLamba2006.AuthMeUI.configuration.SettingsManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry.Builder;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link CustomScreen} into a rendered {@link Dialog} (feature J).
 * Parses raw title/content/button strings with the viewing player's context
 * (so {@code {player}} and {@code <icons:name>} resolve correctly), prepends
 * an ITEM-type icon body if configured, and binds every button to the single
 * {@link DialogIdentifiers#CUSTOM_SCREEN_BUTTON} key with the screen index
 * and button index in the NBT additions payload ({@code {"s":si,"b":bi}}).
 *
 * <p>URL/COPY buttons use {@link DialogAction#staticAction(ClickEvent)} (no
 * server round-trip); CLOSE/COMMAND/CONSOLE use
 * {@link DialogAction#customClick(Key, BinaryTagHolder)} so the listener can
 * execute the action server-side.
 *
 * <p>Mirrors AuthMeBia's {@code CustomScreen.showCustomScreen}, adapted to
 * AuthMeUI's event-driven (key + NBT) click model.
 */
public class CustomScreenDialogBuilder {

    private final SettingsManager settings;

    public CustomScreenDialogBuilder(SettingsManager settings) {
        this.settings = settings;
    }

    public Dialog construct(Player player, CustomScreen screen, int screenIndex) {
        return build(screen, screenIndex, player);
    }

    public Dialog constructForAudience(CustomScreen screen, int screenIndex) {
        return build(screen, screenIndex, null);
    }

    private Dialog build(CustomScreen screen, int screenIndex, Player player) {
        int width = screen.buttonWidth();
        List<ActionButton> buttons = new ArrayList<>();

        int btnIdx = 0;
        for (CustomScreen.ButtonRaw btn : screen.buttons()) {
            Component label = settings.parseText(btn.label(), player);
            int bw = btn.width() > 0 ? btn.width() : width;
            buttons.add(switch (btn.action()) {
                case OPEN_URL -> ActionButton.builder(label)
                        .width(bw)
                        .action(DialogAction.staticAction(ClickEvent.openUrl(btn.value())))
                        .build();
                case COPY -> ActionButton.builder(label)
                        .width(bw)
                        .action(DialogAction.staticAction(ClickEvent.copyToClipboard(btn.value())))
                        .build();
                case CLOSE, COMMAND, CONSOLE -> ActionButton.builder(label)
                        .width(bw)
                        .action(DialogAction.customClick(
                                DialogIdentifiers.CUSTOM_SCREEN_BUTTON,
                                payload(screenIndex, btnIdx)))
                        .build();
            });
            btnIdx++;
        }

        // ponytail: always ensure at least one close button so the player
        // can dismiss an empty-button screen.
        if (buttons.isEmpty()) {
            buttons.add(ActionButton.builder(Component.text("OK"))
                    .width(width)
                    .action(DialogAction.customClick(
                            DialogIdentifiers.CUSTOM_SCREEN_BUTTON,
                            payload(screenIndex, -1)))
                    .build());
        }

        ActionButton cancelButton = screen.allowClose()
                ? ActionButton.builder(Component.text("Close"))
                    .action(DialogAction.customClick(
                            DialogIdentifiers.CUSTOM_SCREEN_BUTTON,
                            payload(screenIndex, -1)))
                    .build()
                : null;

        // Body: optional leading ITEM icon, then content lines.
        List<DialogBody> body = new ArrayList<>();
        if (screen.iconName() != null) {
            IconSpec icon = settings.icon(screen.iconName());
            if (icon != null) {
                DialogBody iconBody = icon.toItemBody();
                if (iconBody != null) {
                    body.add(iconBody);
                } else {
                    // Inline sprite/player-head — prepend to the title instead.
                }
            }
        }
        for (String line : screen.contentRaw()) {
            body.add(DialogBody.plainMessage(settings.parseText(line, player)));
        }

        // Checkbox input for "dismiss forever".
        List<DialogInput> inputs = new ArrayList<>();
        if (screen.checkboxLabelRaw() != null && screen.checkboxAction() == CustomScreen.CheckboxAction.CLOSE) {
            inputs.add(DialogInput.bool(
                            DialogIdentifiers.CUSTOM_SCREEN_DISMISS_KEY,
                            settings.parseText(screen.checkboxLabelRaw(), player))
                    .initial(false)
                    .build());
        }

        Component title = resolveTitle(screen, player);

        DialogBase dialogBase = DialogBase.builder(title)
                .canCloseWithEscape(screen.allowClose())
                .afterAction(DialogAfterAction.CLOSE)
                .body(body)
                .inputs(inputs)
                .build();

        int columns = Math.max(1, Math.min(6, settings.getCustomScreenColumns()));
        return Dialog.create(builder -> {
            ((Builder) builder.empty())
                    .base(dialogBase)
                    .type(DialogType.multiAction(buttons, cancelButton, columns));
        });
    }

    /**
     * Resolve the title. If the icon is an inline-capable type (sprite/
     * player-head), prepend it to the title text.
     */
    private Component resolveTitle(CustomScreen screen, Player player) {
        Component title = settings.parseText(screen.titleRaw(), player);
        if (screen.iconName() == null) return title;
        IconSpec icon = settings.icon(screen.iconName());
        if (icon == null) return title;
        Component inline = icon.toInlineComponent(player == null ? null : player.getName());
        if (inline == null) return title; // ITEM-type: handled as a body
        return inline.appendSpace().append(title);
    }

    /**
     * Encode screen index + button index into an SNBT payload.
     */
    static BinaryTagHolder payload(int screenIndex, int buttonIndex) {
        try {
            CompoundBinaryTag tag = CompoundBinaryTag.builder()
                    .putInt("s", screenIndex)
                    .putInt("b", buttonIndex)
                    .build();
            String snbt = TagStringIO.get().asString(tag);
            return BinaryTagHolder.of(snbt);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Decode a screen index from a click tag.
     */
    static int screenIndex(BinaryTagHolder tag) {
        return decodePayload(tag, "s", -1);
    }

    /**
     * Decode a button index from a click tag.
     */
    static int buttonIndex(BinaryTagHolder tag) {
        return decodePayload(tag, "b", -1);
    }

    private static int decodePayload(BinaryTagHolder tag, String key, int fallback) {
        if (tag == null) return fallback;
        try {
            CompoundBinaryTag compound = TagStringIO.get().asCompound(tag.string());
            return compound.getInt(key, fallback);
        } catch (Exception e) {
            return fallback;
        }
    }
}
