package com.github.TejasLamba2006.AuthMeUI.dialogs;

import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.ItemDialogBody;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * A configurable inline icon for dialog text (feature I). Three types:
 *
 * <ul>
 *   <li><b>SPRITE</b> — a vanilla atlas sprite (e.g. {@code item/trial_key}),
 *       rendered inline via {@code Component.object(ObjectContents.sprite(...))}.
 *       Inserted into any MiniMessage text through the {@code <icons:name>} tag.</li>
 *   <li><b>PLAYER_HEAD</b> — a player-head skin rendered inline. The
 *       {@code player} template supports {@code {player}} for the viewing
 *       player's name.</li>
 *   <li><b>ITEM</b> — a Bukkit ItemStack rendered as a separate dialog body
 *       (above the text). ITEM icons do NOT inline — they are returned by
 *       {@link #toItemBody()} and appended to the dialog body list by the
 *       caller (used by custom screens in feature J).</li>
 * </ul>
 *
 * <p>Mirrors AuthMeBia's {@code dialog.util.IconSpec}, adapted to pass the
 * player name explicitly instead of via a ThreadLocal — AuthMeUI already
 * threads the {@code Player} (or player name for config-phase) through
 * {@link com.github.TejasLamba2006.AuthMeUI.configuration.SettingsManager#parseText}.
 */
public final class IconSpec {

    public enum Type { SPRITE, PLAYER_HEAD, ITEM }

    private final Type type;
    private final Key atlas;
    private final Key sprite;
    private final String playerNameTemplate;
    private final Material material;
    private final boolean showDecorations;
    private final boolean showTooltip;
    private final int width;
    private final int height;

    private IconSpec(Type type, Key atlas, Key sprite, String playerNameTemplate,
                     Material material, boolean showDecorations, boolean showTooltip,
                     int width, int height) {
        this.type = type;
        this.atlas = atlas;
        this.sprite = sprite;
        this.playerNameTemplate = playerNameTemplate;
        this.material = material;
        this.showDecorations = showDecorations;
        this.showTooltip = showTooltip;
        this.width = width;
        this.height = height;
    }

    /**
     * Parse an icon definition from a config map. Returns {@code null} for
     * any malformed/unknown entry so a bad icon never breaks the surrounding
     * text or dialog.
     */
    public static IconSpec parse(Map<?, ?> map) {
        if (map == null || map.isEmpty()) return null;
        String typeStr = str(map, "type", "");
        try {
            switch (typeStr.toLowerCase(java.util.Locale.ROOT)) {
                case "sprite": {
                    String spriteStr = str(map, "sprite", null);
                    if (spriteStr == null || spriteStr.isBlank()) return null;
                    Key atlas = Key.key(str(map, "atlas", "items"));
                    Key sprite = Key.key(spriteStr);
                    return new IconSpec(Type.SPRITE, atlas, sprite, null, null,
                            true, true, 16, 16);
                }
                case "player_head": {
                    String player = str(map, "player", "{player}");
                    return new IconSpec(Type.PLAYER_HEAD, null, null, player, null,
                            true, true, 16, 16);
                }
                case "item": {
                    String matStr = str(map, "material", null);
                    if (matStr == null) return null;
                    Material material = Material.matchMaterial(matStr);
                    if (material == null) return null;
                    boolean showDecorations = bool(map, "show_decorations", true);
                    boolean showTooltip = bool(map, "show_tooltip", true);
                    int width = clampSize(intVal(map, "width", 16));
                    int height = clampSize(intVal(map, "height", 16));
                    return new IconSpec(Type.ITEM, null, null, null, material,
                            showDecorations, showTooltip, width, height);
                }
                default:
                    return null;
            }
        } catch (Exception e) {
            // Malformed Key (bad namespace/path characters), bad material
            // name, etc. -- treat exactly like "no icon configured".
            return null;
        }
    }

    /**
     * @param playerName the viewing player's name (for {@code {player}}
     *                    resolution in PLAYER_HEAD); may be null.
     * @return the inline Component for SPRITE/PLAYER_HEAD, or null for ITEM
     *         (ITEM is a body, not inline — see {@link #toItemBody()}).
     */
    public Component toInlineComponent(String playerName) {
        return switch (type) {
            case SPRITE -> Component.object(ObjectContents.sprite(atlas, sprite));
            case PLAYER_HEAD -> {
                String resolved = playerNameTemplate == null ? null
                        : playerNameTemplate.replace("{player}", playerName == null ? "" : playerName);
                if (resolved == null || resolved.isBlank()) yield null;
                yield Component.object(ObjectContents.playerHead(resolved));
            }
            case ITEM -> null;
        };
    }

    /**
     * @return a dialog body for ITEM-type icons, or null for SPRITE/PLAYER_HEAD.
     */
    public DialogBody toItemBody() {
        if (type != Type.ITEM) return null;
        ItemStack stack = new ItemStack(material);
        ItemDialogBody.Builder builder = DialogBody.item(stack)
                .showDecorations(showDecorations)
                .showTooltip(showTooltip)
                .width(width)
                .height(height);
        return builder.build();
    }

    public Type type() { return type; }

    private static int clampSize(int v) {
        return Math.max(1, Math.min(256, v));
    }

    private static String str(Map<?, ?> map, String key, String def) {
        Object v = map.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static boolean bool(Map<?, ?> map, String key, boolean def) {
        Object v = map.get(key);
        if (v instanceof Boolean b) return b;
        if (v == null) return def;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static int intVal(Map<?, ?> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v == null) return def;
        try { return Integer.parseInt(String.valueOf(v)); }
        catch (NumberFormatException e) { return def; }
    }
}
