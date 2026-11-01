package com.github.TejasLamba2006.AuthMeUI.statistics;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;

public class AnalyticsHandler {

    private static final int BSTATS_PLUGIN_ID = 28962;

    private final AuthMeUIPlugin plugin;
    private final Metrics metrics;

    public AnalyticsHandler(AuthMeUIPlugin plugin) {
        this.plugin = plugin;
        this.metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);

        registerCustomCharts();
        logInitialization();
    }

    private void registerCustomCharts() {
        metrics.addCustomChart(new SimplePie("authmeui_version", () -> plugin.getDescription().getVersion()));
        metrics.addCustomChart(new SimplePie("minecraft_version", () -> Bukkit.getMinecraftVersion()));
        metrics.addCustomChart(new SimplePie("server_platform", () -> {
            String serverBrand = Bukkit.getName();
            String serverVersion = Bukkit.getBukkitVersion();
            return serverBrand + " (" + serverVersion + ")";
        }));
        metrics.addCustomChart(new SimplePie("java_version", () -> System.getProperty("java.version")));
        metrics.addCustomChart(new SimplePie("active_servers", () -> "Active"));
    }

    private void logInitialization() {
        plugin.getLogger().info("Analytics (bStats) enabled successfully.");
    }
}
