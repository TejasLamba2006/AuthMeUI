package com.github.TejasLamba2006.AuthMeUI.dialogs;

import com.github.TejasLamba2006.AuthMeUI.configuration.SettingsManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry.Builder;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.TagStringIO;
import net.kyori.adventure.nbt.api.BinaryTagHolder;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the PIN-pad login dialog (mode G). A 3-column grid of digit buttons
 * (1-9, 0), a delete, and a confirm. Each digit button carries the SAME
 * action key ({@link DialogIdentifiers#PIN_INPUT}) but encodes the digit
 * value in the custom-click NBT additions payload ({@code {"d": N}}); the
 * listener decodes it via {@code event.getTag()}.
 *
 * <p>Mirrors AuthMeBia's {@code AuthInput.buildPinDialog}, adapted to
 * AuthMeUI's event-driven (key + NBT) click model instead of AuthMeBia's
 * blocking server-side {@code DialogActionCallback}. Progress dots (●/○) are
 * rendered in the body so the player sees how many digits they've entered.
 */
public class PinDialogBuilder {

    private final SettingsManager settings;

    public PinDialogBuilder(SettingsManager settings) {
        this.settings = settings;
    }

    /**
     * @param entered   how many digits the player has entered so far (for the progress dots).
     * @param statusLine optional error/status line appended to the body, or null.
     */
    public Dialog construct(Player player, int entered, String statusLine) {
        return build(entered, statusLine, player);
    }

    public Dialog constructForAudience(int entered, String statusLine) {
        return build(entered, statusLine, null);
    }

    private Dialog build(int entered, String statusLine, Player player) {
        int length = settings.getPinLength();
        int width = settings.getPinButtonWidth();

        List<ActionButton> buttons = new ArrayList<>(12);
        for (int n = 1; n <= 9; n++) {
            buttons.add(digitButton(n, width, player));
        }
        buttons.add(digitButton(0, width, player));
        buttons.add(actionButton(settings.getPinDeleteButton(), DialogIdentifiers.PIN_DELETE, null, width, player));
        buttons.add(actionButton(settings.getPinConfirmButton(), DialogIdentifiers.PIN_CONFIRM, null, width, player));

        ActionButton cancelButton = actionButton(
                settings.getPinLogoutButton(), DialogIdentifiers.LOGIN_CANCEL, null, width, player);

        List<DialogBody> body = new ArrayList<>();
        for (String line : settings.getPinBodyRaw()) {
            body.add(DialogBody.plainMessage(settings.parseText(line, player)));
        }
        body.add(DialogBody.plainMessage(progressDots(entered, length)));
        if (statusLine != null && !statusLine.isBlank()) {
            body.add(DialogBody.plainMessage(settings.parseText(statusLine, player)));
        }

        DialogBase dialogBase = DialogBase.builder(settings.getPinTitle())
                .canCloseWithEscape(settings.canCloseWithEscape())
                .afterAction(DialogAfterAction.CLOSE)
                .body(body)
                .inputs(List.of())
                .build();

        return Dialog.create(builder -> {
            ((Builder) builder.empty())
                    .base(dialogBase)
                    .type(DialogType.multiAction(buttons, cancelButton, 3));
        });
    }

    private ActionButton digitButton(int digit, int width, Player player) {
        return actionButton(
                Component.text(Integer.toString(digit)),
                DialogIdentifiers.PIN_INPUT,
                nbtPayload("d", digit),
                width,
                player);
    }

    /**
     * Build an ActionButton whose custom-click carries the given key and an
     * SNBT payload. The payload is decoded on the server side from
     * {@code event.getTag()}.
     */
    private ActionButton actionButton(Component label, net.kyori.adventure.key.Key key,
                                       BinaryTagHolder additions, int width, Player player) {
        return ActionButton.builder(label)
                .width(width)
                .action(DialogAction.customClick(key, additions))
                .build();
    }

    private static Component progressDots(int entered, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(i < entered ? '●' : '○');
            if (i < length - 1) sb.append(' ');
        }
        return Component.text(sb.toString());
    }

    /**
     * Encode a single int field into an SNBT {@link BinaryTagHolder} payload.
     * ponytail: SNBT round-trips through TagStringIO; for a single int field
     * this is the cheapest NBT construction that survives the client→server
     * click trip. If multiple fields are ever needed, switch to CompoundBinaryTag.builder().
     */
    static BinaryTagHolder nbtPayload(String key, int value) {
        try {
            CompoundBinaryTag tag = CompoundBinaryTag.builder().putInt(key, value).build();
            String snbt = TagStringIO.get().asString(tag);
            return BinaryTagHolder.of(snbt);
        } catch (Exception e) {
            // ponytail: SNBT for a single int is infallible in practice; if
            // it ever fails, ship a null payload and let the listener default.
            return null;
        }
    }
}
