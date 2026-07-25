package ru.malfix.autobuy.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Small user-editable runtime settings file.
 * Stored outside the main account config so the same timings/anarchy can be
 * changed from the folder without touching target prices.
 */
public final class MalfixRuntimeSettings {
    private final File file;
    private long lastModified = -1L;

    private boolean autoRejoinEnabled = true;
    private long autoRejoinDelayMs = 5_000L;
    private boolean autoRejoinRestoreAuction = true;
    private String anarchy = "";
    private boolean telegramEnabled = false;
    private boolean telegramPollingEnabled = false;
    private String telegramToken = "";
    private String telegramChatId = "";
    private boolean telegramAutoEnableWhenConfigured = true;

    private boolean authEnabled = true;
    private String authPassword = "";
    private String authLoginCommand = "/login {password}";
    private String authRegisterCommand = "/register {password} {password}";
    private long authDelayMs = 800L;
    private long authCooldownMs = 10_000L;

    private String autoRejoinHubCommand = "/hub";
    private String autoRejoinAnarchyCommand = "/an{anarchy}";
    private String autoRejoinAuctionCommand = "/ah";
    private long autoRejoinPostLoginWaitMs = 1_000L;

    private long antiAfkHubWaitMs = MalfixTimings.ANTI_AFK_HUB_WAIT_MS;
    private long antiAfkJoinWaitMs = MalfixTimings.ANTI_AFK_JOIN_WAIT_MS;
    private long antiAfkAuctionRestoreWaitMs = MalfixTimings.ANTI_AFK_AUCTION_RESTORE_WAIT_MS;
    private long spamKickInitialWaitMs = MalfixTimings.SPAM_KICK_INITIAL_WAIT_MS;
    private long spamKickJoinWaitMs = MalfixTimings.SPAM_KICK_JOIN_WAIT_MS;
    private long spamKickAhWaitMs = MalfixTimings.SPAM_KICK_AH_WAIT_MS;
    private long spamKickAhRetryMs = MalfixTimings.SPAM_KICK_AH_RETRY_MS;
    private int spamKickAhMaxAttempts = MalfixTimings.SPAM_KICK_AH_MAX_ATTEMPTS;
    private long spamKickTotalTimeoutMs = MalfixTimings.SPAM_KICK_TOTAL_TIMEOUT_MS;

    public MalfixRuntimeSettings(File runDirectory) {
        File root = runDirectory == null ? new File(".") : runDirectory;
        File dir = new File(root, "malfix_autobuy");
        this.file = new File(dir, "runtime.properties");
        ensureFile();
        reload(true);
    }

    public void reloadIfChanged() {
        reload(false);
    }

    public void reload(boolean force) {
        try {
            ensureFile();
            ensureDefaultKeys();
            long modified = file.lastModified();
            if (!force && modified == lastModified) {
                return;
            }
            Properties p = new Properties();
            FileInputStream in = new FileInputStream(file);
            try {
                p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            } finally {
                in.close();
            }
            autoRejoinEnabled = parseBool(p.getProperty("autorejoin.enabled"), true);
            autoRejoinDelayMs = clamp(parseLong(p.getProperty("autorejoin.delayMs"), 5_000L), 500L, 120_000L);
            autoRejoinRestoreAuction = parseBool(p.getProperty("autorejoin.restoreAh"), true);
            anarchy = safe(p.getProperty("anarchy"));
            telegramToken = safe(firstNonEmpty(p.getProperty("telegram.token"), p.getProperty("telegram.botToken")));
            telegramChatId = safe(firstNonEmpty(p.getProperty("telegram.chatId"), p.getProperty("telegram.chat")));
            telegramAutoEnableWhenConfigured = parseBool(p.getProperty("telegram.autoEnableWhenConfigured"), true);
            telegramEnabled = parseBool(p.getProperty("telegram.enabled"), false);
            if (!telegramEnabled && telegramAutoEnableWhenConfigured && !telegramToken.isEmpty() && !telegramChatId.isEmpty()) {
                telegramEnabled = true;
            }
            telegramPollingEnabled = parseBool(p.getProperty("telegram.polling"), false);

            authEnabled = parseBool(p.getProperty("auth.enabled"), true);
            authPassword = safe(p.getProperty("auth.password"));
            authLoginCommand = safeOrDefault(p.getProperty("auth.loginCommand"), "/login {password}");
            authRegisterCommand = safeOrDefault(p.getProperty("auth.registerCommand"), "/register {password} {password}");
            authDelayMs = clamp(parseLong(p.getProperty("auth.delayMs"), 800L), 0L, 30_000L);
            authCooldownMs = clamp(parseLong(p.getProperty("auth.cooldownMs"), 10_000L), 1_000L, 120_000L);

            autoRejoinHubCommand = safeOrDefault(p.getProperty("autorejoin.hubCommand"), "/hub");
            autoRejoinAnarchyCommand = safeOrDefault(p.getProperty("autorejoin.anarchyCommand"), "/an{anarchy}");
            autoRejoinAuctionCommand = safeOrDefault(p.getProperty("autorejoin.auctionCommand"), "/ah");
            autoRejoinPostLoginWaitMs = clamp(parseLong(p.getProperty("autorejoin.postLoginWaitMs"), 1_000L), 0L, 30_000L);

            antiAfkHubWaitMs = clamp(parseLong(p.getProperty("timing.antiAfkHubWaitMs"), MalfixTimings.ANTI_AFK_HUB_WAIT_MS), 100L, 30_000L);
            antiAfkJoinWaitMs = clamp(parseLong(p.getProperty("timing.antiAfkJoinWaitMs"), MalfixTimings.ANTI_AFK_JOIN_WAIT_MS), 100L, 30_000L);
            antiAfkAuctionRestoreWaitMs = clamp(parseLong(p.getProperty("timing.antiAfkAuctionRestoreWaitMs"), MalfixTimings.ANTI_AFK_AUCTION_RESTORE_WAIT_MS), 100L, 30_000L);
            spamKickInitialWaitMs = clamp(parseLong(p.getProperty("timing.spamKickInitialWaitMs"), MalfixTimings.SPAM_KICK_INITIAL_WAIT_MS), 100L, 30_000L);
            spamKickJoinWaitMs = clamp(parseLong(p.getProperty("timing.spamKickJoinWaitMs"), MalfixTimings.SPAM_KICK_JOIN_WAIT_MS), 100L, 30_000L);
            spamKickAhWaitMs = clamp(parseLong(p.getProperty("timing.spamKickAhWaitMs"), MalfixTimings.SPAM_KICK_AH_WAIT_MS), 100L, 30_000L);
            spamKickAhRetryMs = clamp(parseLong(p.getProperty("timing.spamKickAhRetryMs"), MalfixTimings.SPAM_KICK_AH_RETRY_MS), 100L, 30_000L);
            spamKickAhMaxAttempts = (int) clamp(parseLong(p.getProperty("timing.spamKickAhMaxAttempts"), MalfixTimings.SPAM_KICK_AH_MAX_ATTEMPTS), 1L, 20L);
            spamKickTotalTimeoutMs = clamp(parseLong(p.getProperty("timing.spamKickTotalTimeoutMs"), MalfixTimings.SPAM_KICK_TOTAL_TIMEOUT_MS), 1_000L, 120_000L);
            lastModified = modified;
        } catch (Throwable throwable) {
            System.out.println("[MAB] runtime.properties load failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    public File getFile() { return file; }
    public boolean isAutoRejoinEnabled() { return autoRejoinEnabled; }
    public long getAutoRejoinDelayMs() { return autoRejoinDelayMs; }
    public boolean isAutoRejoinRestoreAuction() { return autoRejoinRestoreAuction; }
    public String getAnarchy() { return anarchy; }
    public boolean isTelegramEnabled() { return telegramEnabled; }
    public boolean isTelegramPollingEnabled() { return telegramPollingEnabled; }
    public String getTelegramToken() { return telegramToken; }
    public String getTelegramChatId() { return telegramChatId; }
    public boolean isAuthEnabled() { return authEnabled; }
    public String getAuthPassword() { return authPassword; }
    public String getAuthLoginCommand() { return authLoginCommand; }
    public String getAuthRegisterCommand() { return authRegisterCommand; }
    public long getAuthDelayMs() { return authDelayMs; }
    public long getAuthCooldownMs() { return authCooldownMs; }
    public String getAutoRejoinHubCommand() { return autoRejoinHubCommand; }
    public String getAutoRejoinAnarchyCommand() { return autoRejoinAnarchyCommand; }
    public String getAutoRejoinAuctionCommand() { return autoRejoinAuctionCommand; }
    public long getAutoRejoinPostLoginWaitMs() { return autoRejoinPostLoginWaitMs; }
    public long getAntiAfkHubWaitMs() { return antiAfkHubWaitMs; }
    public long getAntiAfkJoinWaitMs() { return antiAfkJoinWaitMs; }
    public long getAntiAfkAuctionRestoreWaitMs() { return antiAfkAuctionRestoreWaitMs; }
    public long getSpamKickInitialWaitMs() { return spamKickInitialWaitMs; }
    public long getSpamKickJoinWaitMs() { return spamKickJoinWaitMs; }
    public long getSpamKickAhWaitMs() { return spamKickAhWaitMs; }
    public long getSpamKickAhRetryMs() { return spamKickAhRetryMs; }
    public int getSpamKickAhMaxAttempts() { return spamKickAhMaxAttempts; }
    public long getSpamKickTotalTimeoutMs() { return spamKickTotalTimeoutMs; }

    public String compact() {
        return "file=" + file.getAbsolutePath()
                + ", anarchy=" + (anarchy.isEmpty() ? "auto" : anarchy)
                + ", autorejoin=" + autoRejoinEnabled
                + ", delayMs=" + autoRejoinDelayMs
                + ", restoreAh=" + autoRejoinRestoreAuction
                + ", telegram=" + telegramEnabled
                + ", telegramToken=" + (!telegramToken.isEmpty())
                + ", telegramChatId=" + (telegramChatId.isEmpty() ? "empty" : telegramChatId)
                + ", telegramAuto=" + telegramAutoEnableWhenConfigured
                + ", auth=" + authEnabled
                + ", authPassword=" + (!authPassword.isEmpty())
                + ", joinCmd=" + autoRejoinAnarchyCommand;
    }

    private void ensureFile() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (file.exists()) return;
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            try {
                out.write("# Malfix AutoBuy runtime settings. Edit while game is open; .mab runtime reload applies immediately.\n");
                out.write("# anarchy: empty = autodetect, or set 305 / 505 etc.\n");
                out.write("anarchy=\n");
                out.write("autorejoin.enabled=true\n");
                out.write("autorejoin.delayMs=5000\n");
                out.write("autorejoin.restoreAh=true\n");
                out.write("autorejoin.hubCommand=/hub\n");
                out.write("autorejoin.anarchyCommand=/an{anarchy}\n");
                out.write("autorejoin.auctionCommand=/ah\n");
                out.write("autorejoin.postLoginWaitMs=1000\n");
                out.write("auth.enabled=true\n");
                out.write("auth.password=\n");
                out.write("auth.loginCommand=/login {password}\n");
                out.write("auth.registerCommand=/register {password} {password}\n");
                out.write("auth.delayMs=800\n");
                out.write("auth.cooldownMs=10000\n");
                out.write("telegram.enabled=false\n");
                out.write("telegram.autoEnableWhenConfigured=true\n");
                out.write("telegram.token=\n");
                out.write("telegram.chatId=\n");
                out.write("telegram.polling=false\n");
                out.write("timing.antiAfkHubWaitMs=900\n");
                out.write("timing.antiAfkJoinWaitMs=1300\n");
                out.write("timing.antiAfkAuctionRestoreWaitMs=350\n");
                out.write("timing.spamKickInitialWaitMs=800\n");
                out.write("timing.spamKickJoinWaitMs=2000\n");
                out.write("timing.spamKickAhWaitMs=500\n");
                out.write("timing.spamKickAhRetryMs=900\n");
                out.write("timing.spamKickAhMaxAttempts=5\n");
                out.write("timing.spamKickTotalTimeoutMs=18000\n");
            } finally {
                out.close();
            }
        } catch (Throwable throwable) {
            System.out.println("[MAB] runtime.properties create failed: " + throwable.getMessage());
        }
    }


    private void ensureDefaultKeys() {
        try {
            if (file == null || !file.exists()) {
                return;
            }
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            List<String> missing = new ArrayList<String>();
            addMissing(content, missing, "anarchy", "anarchy=");
            addMissing(content, missing, "autorejoin.enabled", "autorejoin.enabled=true");
            addMissing(content, missing, "autorejoin.delayMs", "autorejoin.delayMs=5000");
            addMissing(content, missing, "autorejoin.restoreAh", "autorejoin.restoreAh=true");
            addMissing(content, missing, "autorejoin.hubCommand", "autorejoin.hubCommand=/hub");
            addMissing(content, missing, "autorejoin.anarchyCommand", "autorejoin.anarchyCommand=/an{anarchy}");
            addMissing(content, missing, "autorejoin.auctionCommand", "autorejoin.auctionCommand=/ah");
            addMissing(content, missing, "autorejoin.postLoginWaitMs", "autorejoin.postLoginWaitMs=1000");
            addMissing(content, missing, "auth.enabled", "auth.enabled=true");
            addMissing(content, missing, "auth.password", "auth.password=");
            addMissing(content, missing, "auth.loginCommand", "auth.loginCommand=/login {password}");
            addMissing(content, missing, "auth.registerCommand", "auth.registerCommand=/register {password} {password}");
            addMissing(content, missing, "auth.delayMs", "auth.delayMs=800");
            addMissing(content, missing, "auth.cooldownMs", "auth.cooldownMs=10000");
            addMissing(content, missing, "telegram.enabled", "telegram.enabled=false");
            addMissing(content, missing, "telegram.autoEnableWhenConfigured", "telegram.autoEnableWhenConfigured=true");
            addMissing(content, missing, "telegram.token", "telegram.token=");
            addMissing(content, missing, "telegram.chatId", "telegram.chatId=");
            addMissing(content, missing, "telegram.polling", "telegram.polling=false");
            addMissing(content, missing, "timing.antiAfkHubWaitMs", "timing.antiAfkHubWaitMs=900");
            addMissing(content, missing, "timing.antiAfkJoinWaitMs", "timing.antiAfkJoinWaitMs=1300");
            addMissing(content, missing, "timing.antiAfkAuctionRestoreWaitMs", "timing.antiAfkAuctionRestoreWaitMs=350");
            addMissing(content, missing, "timing.spamKickInitialWaitMs", "timing.spamKickInitialWaitMs=800");
            addMissing(content, missing, "timing.spamKickJoinWaitMs", "timing.spamKickJoinWaitMs=2000");
            addMissing(content, missing, "timing.spamKickAhWaitMs", "timing.spamKickAhWaitMs=500");
            addMissing(content, missing, "timing.spamKickAhRetryMs", "timing.spamKickAhRetryMs=900");
            addMissing(content, missing, "timing.spamKickAhMaxAttempts", "timing.spamKickAhMaxAttempts=5");
            addMissing(content, missing, "timing.spamKickTotalTimeoutMs", "timing.spamKickTotalTimeoutMs=18000");
            if (missing.isEmpty()) {
                return;
            }
            OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8);
            try {
                out.write("\n# Added by Malfix AutoBuy auto-upgrade.\n");
                for (String line : missing) {
                    out.write(line);
                    out.write("\n");
                }
            } finally {
                out.close();
            }
        } catch (Throwable throwable) {
            System.out.println("[MAB] runtime.properties upgrade failed: " + throwable.getMessage());
        }
    }

    private static void addMissing(String content, List<String> out, String key, String line) {
        if (content == null || key == null || line == null) {
            return;
        }
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?m)^\\s*" + java.util.regex.Pattern.quote(key) + "\\s*=");
        if (!pattern.matcher(content).find()) {
            out.add(line);
        }
    }

    private static String firstNonEmpty(String a, String b) {
        String sa = safe(a);
        if (!sa.isEmpty()) return sa;
        return safe(b);
    }
    private static String safe(String v) { return v == null ? "" : v.trim(); }
    private static String safeOrDefault(String v, String fallback) { String s = safe(v); return s.isEmpty() ? fallback : s; }
    private static boolean parseBool(String v, boolean fallback) {
        if (v == null) return fallback;
        String s = v.trim().toLowerCase();
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s)) return true;
        if ("false".equals(s) || "0".equals(s) || "no".equals(s) || "off".equals(s)) return false;
        return fallback;
    }
    private static long parseLong(String v, long fallback) {
        if (v == null) return fallback;
        try { return Long.parseLong(v.trim()); } catch (Throwable ignored) { return fallback; }
    }
    private static long clamp(long v, long min, long max) {
        return Math.max(min, Math.min(max, v));
    }
}
