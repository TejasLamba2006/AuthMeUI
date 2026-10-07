package com.github.TejasLamba2006.AuthMeUI.webhook;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;

/**
 * Ponytail Discord webhook sender (feature N). Uses Java 21's built-in
 * {@link HttpClient} — no okhttp/gson dependency (AuthMeBia pulls both for
 * this; AuthMeUI has neither shaded, and the payload is a one-line JSON
 * string, so hand-rolling beats adding two deps). The client is shared and
 * async-safe; requests are fire-and-forget with failure logging.
 *
 * <p>AuthMeBia's parallel feature also composites a welcome image
 * ({@code api/Welcome.java}, java.awt layer compositing from a welcome.json)
 * and uploads it via multipart. That is a distinct "welcome image" feature,
 * not the webhook itself, so it is out of scope for N — skipped, add when a
 * server needs rendered welcome cards.
 */
public final class DiscordWebhook {

    private final AuthMeUIPlugin plugin;
    private final HttpClient client;

    public DiscordWebhook(AuthMeUIPlugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Send a plain content message to the webhook URL. Runs synchronously —
     * callers should invoke from an async task. Returns true on 2xx.
     * No-op if the URL is blank/invalid.
     */
    public boolean send(String webhookUrl, String content) {
        if (webhookUrl == null || webhookUrl.isBlank()) return false;
        try {
            String payload = "{\"content\":\"" + escape(content) + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                plugin.getLogger().warning("Discord webhook failed: HTTP " + response.statusCode());
                return false;
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Discord webhook error: " + ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Send an embed-style message with a title, description, and color.
     * Mirrors the common Discord webhook embed shape so auth events render
     * as a coloured card rather than a flat line.
     */
    public boolean sendEmbed(String webhookUrl, String title, String description, int color) {
        if (webhookUrl == null || webhookUrl.isBlank()) return false;
        try {
            String payload = "{\"embeds\":[{\"title\":\"" + escape(title)
                    + "\",\"description\":\"" + escape(description)
                    + "\",\"color\":" + color + "}]}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                plugin.getLogger().warning("Discord webhook failed: HTTP " + response.statusCode());
                return false;
            }
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Discord webhook error: " + ex.getMessage(), ex);
            return false;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
