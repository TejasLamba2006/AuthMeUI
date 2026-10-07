package com.github.TejasLamba2006.AuthMeUI.authentication;

import com.github.TejasLamba2006.AuthMeUI.AuthMeUIPlugin;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ban.IpBanList;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Tracks failed login attempts per IP and bans repeat offenders with
 * escalating durations. Wraps {@link Bukkit#getBanList(BanList.Type)}.
 *
 * <p>Failure counts are in-memory only (cleared on restart); AuthMe already
 * persists its own auth state. This is a fast anti-brute-force tripwire, not
 * a durable ban history — the ban itself persists via the server's ban list,
 * but the per-IP failure counter resets on restart. That is intentional: a
 * restart clears the rolling window, which is the right behaviour for a
 * rate limiter.
 */
public final class IpGuard {

    // ponytail: default escalation ladder (seconds). 5m → 30m → 1h → 1d.
    // Config-overridable; if a server needs longer/shorter bans they tune
    // ip-guard.durations in config.yml.
    private static final List<Integer> DEFAULT_DURATIONS = List.of(300, 1800, 3600, 86400);
    private static final int DEFAULT_MAX_FAILURES = 5;

    private final AuthMeUIPlugin plugin;
    private final Map<String, AtomicInteger> failureCounts = new ConcurrentHashMap<>();

    public IpGuard(AuthMeUIPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Record a failed login attempt for an IP. If the configured threshold is
     * reached, ban the IP for the next duration in the escalation ladder and
     * reset the counter.
     *
     * @param ipAddress the player's IP (host address, no port)
     * @return true if the IP was just banned
     */
    public boolean recordFailure(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return false;
        }

        int maxFailures = plugin.getSettingsManager().getIpGuardMaxFailures();
        AtomicInteger count = failureCounts.computeIfAbsent(ipAddress, k -> new AtomicInteger(0));
        int current = count.incrementAndGet();

        if (current < maxFailures) {
            return false;
        }

        int banDurationSeconds = pickDuration(failureBansAlreadyServed(ipAddress));
        banIp(ipAddress, banDurationSeconds);
        count.set(0);
        return true;
    }

    /**
     * Clear failure tracking for an IP (e.g. on successful login).
     */
    public void clearFailures(String ipAddress) {
        if (ipAddress != null) {
            failureCounts.remove(ipAddress);
        }
    }

    private int pickDuration(int banIndex) {
        List<Integer> durations = plugin.getSettingsManager().getIpGuardDurations();
        if (durations.isEmpty()) {
            durations = DEFAULT_DURATIONS;
        }
        return pickDuration(durations, banIndex);
    }

    // ponytail: pure duration-ladder logic extracted so it can be tested
    // without a server. Clamps to the last entry past the ladder ceiling.
    static int pickDuration(List<Integer> durations, int banIndex) {
        if (durations == null || durations.isEmpty()) {
            durations = DEFAULT_DURATIONS;
        }
        int idx = Math.min(Math.max(banIndex, 0), durations.size() - 1);
        return durations.get(idx);
    }

    // ponytail: separate counter for how many bans an IP has already served,
    // so the ladder escalates across multiple ban windows within one session.
    private final Map<String, AtomicInteger> banLadderIndex = new ConcurrentHashMap<>();

    private int failureBansAlreadyServed(String ipAddress) {
        return banLadderIndex.computeIfAbsent(ipAddress, k -> new AtomicInteger(0)).get();
    }

    private void banIp(String ipAddress, int durationSeconds) {
        try {
            IpBanList banList = Bukkit.getBanList(BanList.Type.IP);
            Date expires = new Date(System.currentTimeMillis() + durationSeconds * 1000L);
            String reason = "AuthMeUI: Too many failed login attempts.";
            banList.addBan(ipAddress, reason, expires, "AuthMeUI");

            AtomicInteger ladder = banLadderIndex.computeIfAbsent(ipAddress, k -> new AtomicInteger(0));
            ladder.incrementAndGet();

            plugin.getLogger().log(Level.WARNING,
                    "IpGuard: Banned " + ipAddress + " for " + durationSeconds + "s "
                            + "(escalation level " + ladder.get() + ").");
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "IpGuard: Failed to ban IP " + ipAddress, ex);
        }
    }

    /**
     * Extract the host address from a player's connection, or null.
     */
    public static String extractIp(InetSocketAddress address) {
        if (address == null) {
            return null;
        }
        InetAddress inet = address.getAddress();
        return inet != null ? inet.getHostAddress() : null;
    }
}
