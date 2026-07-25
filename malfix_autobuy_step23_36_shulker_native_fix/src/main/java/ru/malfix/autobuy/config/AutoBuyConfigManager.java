package ru.malfix.autobuy.config;

import org.lwjgl.glfw.GLFW;
import ru.malfix.autobuy.scanner.ScanMode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small dependency-free JSON config manager.
 */
public final class AutoBuyConfigManager {

    private final File configFile;

    public AutoBuyConfigManager(File runDirectory) {
        File root = runDirectory == null ? new File(".") : runDirectory;
        File configDir = new File(root, "config");
        this.configFile = new File(configDir, "malfix-autobuy.json");
    }

    public AutoBuyConfig loadOrCreate() {
        if (!configFile.exists()) {
            AutoBuyConfig config = AutoBuyConfig.defaults();
            save(config);
            return config;
        }

        try {
            String json = readAll(configFile);
            AutoBuyConfig config = parse(json);
            if (config == null) {
                config = AutoBuyConfig.defaults();
                save(config);
            }
            return config;
        } catch (Throwable throwable) {
            System.out.println("[MAB] Failed to load config, using defaults:");
            throwable.printStackTrace(System.out);
            AutoBuyConfig config = AutoBuyConfig.defaults();
            save(config);
            return config;
        }
    }

    public boolean save(AutoBuyConfig config) {
        try {
            AutoBuyConfig safe = config == null ? AutoBuyConfig.defaults() : config;
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            FileOutputStream out = new FileOutputStream(configFile);
            out.write(toJson(safe).getBytes(StandardCharsets.UTF_8));
            out.close();
            return true;
        } catch (Throwable throwable) {
            System.out.println("[MAB] Failed to save config:");
            throwable.printStackTrace(System.out);
            return false;
        }
    }

    public File getConfigFile() {
        return configFile;
    }

    public String getConfigPath() {
        return configFile.getAbsolutePath();
    }

    private AutoBuyConfig parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return AutoBuyConfig.defaults();
        }

        int cycles = parseInt(json, "defaultLoopCycles", 10);
        int buys = parseInt(json, "defaultLoopBuys", 3);
        long delay = parseLong(json, "loopDelayMs", MalfixTimings.AB_UPDATE_MS);
        // Step 22.61: migrate the old Step 22.60 default 400ms to the server-safe
        // minimum refresh cadence 300ms. Higher custom values are kept.
        if (delay == 400L) {
            delay = MalfixTimings.AB_UPDATE_MS;
        }
        List<TargetConfig> targets = parseTargets(json);

        String keysBody = extractObjectBody(json, "keybinds");

        int keyDebug = parseInt(keysBody, "debug", GLFW.GLFW_KEY_D);
        int keyFingerprint = parseInt(keysBody, "fingerprint", GLFW.GLFW_KEY_F);
        int keyScan = parseInt(keysBody, "scan", GLFW.GLFW_KEY_S);
        int keyObserver = parseInt(keysBody, "observer", GLFW.GLFW_KEY_O);
        int keyRefresh = parseInt(keysBody, "refresh", GLFW.GLFW_KEY_R);
        int keyBuy = parseInt(keysBody, "buy", GLFW.GLFW_KEY_B);
        int keyOneCycle = parseInt(keysBody, "oneCycle", GLFW.GLFW_KEY_A);
        int keyLimitedLoop = parseInt(keysBody, "limitedLoop", GLFW.GLFW_KEY_L);
        int keyGui = parseInt(keysBody, "gui", GLFW.GLFW_KEY_G);
        int keyParser = parseInt(keysBody, "parser", GLFW.GLFW_KEY_P);
        int keyFullAuto = parseInt(keysBody, "fullAuto", GLFW.GLFW_KEY_R);
        int keySellOnly = parseInt(keysBody, "sellOnly", GLFW.GLFW_KEY_V);

        String safetyBody = extractObjectBody(json, "safety");
        ScanMode scanMode = ScanMode.fromString(parseString(safetyBody, "scanMode", parseString(json, "scanMode", "ALL45")));
        boolean requireMaxPrice = parseBoolean(safetyBody, "requireMaxPrice", parseBoolean(json, "requireMaxPrice", true));
        boolean allowUnlimitedPrice = parseBoolean(safetyBody, "allowUnlimitedPrice", parseBoolean(json, "allowUnlimitedPrice", false));
        long refreshTimeoutMs = parseLong(safetyBody, "refreshTimeoutMs", parseLong(json, "refreshTimeoutMs", MalfixTimings.SMART_REOPEN_REFRESH_FAIL_MS));
        int maxRefreshFailStreak = parseInt(safetyBody, "maxRefreshFailStreak", parseInt(json, "maxRefreshFailStreak", MalfixTimings.DEFAULT_MAX_REFRESH_FAIL_STREAK));
        List<String> blacklistKeywords = parseStringArray(safetyBody == null ? json : safetyBody, "blacklistKeywords");

        String sellerBody = extractObjectBody(json, "seller");
        int sellerMarkupPercent = parseInt(sellerBody, "markupPercent", parseInt(json, "sellerMarkupPercent", 10));

        AutoBuyConfig config = new AutoBuyConfig(
                cycles,
                buys,
                delay,
                targets,
                keyDebug,
                keyFingerprint,
                keyScan,
                keyObserver,
                keyRefresh,
                keyBuy,
                keyOneCycle,
                keyLimitedLoop,
                keyGui,
                keyParser,
                keyFullAuto,
                scanMode,
                requireMaxPrice,
                allowUnlimitedPrice,
                refreshTimeoutMs,
                maxRefreshFailStreak,
                blacklistKeywords,
                sellerMarkupPercent
        );
        config.setKeySellOnly(keySellOnly);

        String parserBody = extractObjectBody(json, "parser");
        config.setParserBuyPercent(parseInt(parserBody, "buyPercent", parseInt(json, "parserBuyPercent", MalfixTimings.PARSER_BUY_PERCENT)));
        config.setParserSellPercent(parseInt(parserBody, "sellPercent", parseInt(json, "parserSellPercent", MalfixTimings.PARSER_SELL_PERCENT)));
        config.setParserOpenWaitMs(parseLong(parserBody, "openWaitMs", parseLong(json, "parserOpenWaitMs", MalfixTimings.PARSER_OPEN_WAIT_MS)));
        config.setParserBetweenItemsMs(parseLong(parserBody, "betweenItemsMs", parseLong(json, "parserBetweenItemsMs", MalfixTimings.PARSER_BETWEEN_ITEMS_MS)));

        String antiAfkBody = extractObjectBody(json, "antiAfk");
        config.setAntiAfkEnabled(parseBoolean(antiAfkBody, "enabled", parseBoolean(json, "antiAfkEnabled", true)));
        long antiAfkInterval = parseLong(antiAfkBody, "intervalMs", parseLong(json, "antiAfkIntervalMs", MalfixTimings.ANTI_AFK_INTERVAL_MS));
        if (antiAfkInterval == 290_000L) {
            antiAfkInterval = MalfixTimings.ANTI_AFK_INTERVAL_MS;
        }
        config.setAntiAfkIntervalMs(antiAfkInterval);
        config.setAntiAfkAnarchy(parseString(antiAfkBody, "anarchy", parseString(json, "antiAfkAnarchy", "")));

        return config;
    }

    private List<TargetConfig> parseTargets(String json) {
        List<TargetConfig> targets = new ArrayList<TargetConfig>();

        String targetsBody = extractArrayBody(json, "targets");
        if (targetsBody == null || targetsBody.trim().isEmpty()) {
            return targets;
        }

        Pattern objectPattern = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);
        Matcher matcher = objectPattern.matcher(targetsBody);

        while (matcher.find()) {
            String object = matcher.group(1);

            String label = parseString(object, "label", "");
            boolean enabled = parseBoolean(object, "enabled", false);
            long maxUnitPrice = parseLong(object, "maxUnitPrice", 0L);
            long sellUnitPrice = parseLong(object, "sellUnitPrice", parseLong(object, "sellPrice", 0L));
            boolean unstack = parseBoolean(object, "unstack", false);
            int unstackAmount = parseInt(object, "unstackAmount", 1);
            int potionDragMinSourceCount = parseInt(object, "potionDragMinSourceCount", 24);
            boolean parserEnabled = parseBoolean(object, "parserEnabled", parseBoolean(object, "parse", false));
            String itemId = parseString(object, "itemId", "");
            String tagContains = parseString(object, "tagContains", "");
            List<String> contains = parseStringArray(object, "contains");

            if (!label.trim().isEmpty() && (!contains.isEmpty() || !itemId.trim().isEmpty() || !tagContains.trim().isEmpty())) {
                targets.add(new TargetConfig(label, contains, itemId, tagContains, maxUnitPrice, sellUnitPrice, enabled, unstack, unstackAmount, potionDragMinSourceCount, parserEnabled));
            }
        }

        return targets;
    }

    private String toJson(AutoBuyConfig config) {
        StringBuilder builder = new StringBuilder(8192);

        builder.append("{\n");
        builder.append("  \"defaultLoopCycles\": ").append(config.getDefaultLoopCycles()).append(",\n");
        builder.append("  \"defaultLoopBuys\": ").append(config.getDefaultLoopBuys()).append(",\n");
        builder.append("  \"loopDelayMs\": ").append(config.getLoopDelayMs()).append(",\n");

        builder.append("  \"safety\": {\n");
        builder.append("    \"scanMode\": \"").append(config.getScanMode().name()).append("\",\n");
        builder.append("    \"requireMaxPrice\": ").append(config.isRequireMaxPrice()).append(",\n");
        builder.append("    \"allowUnlimitedPrice\": ").append(config.isAllowUnlimitedPrice()).append(",\n");
        builder.append("    \"refreshTimeoutMs\": ").append(config.getRefreshTimeoutMs()).append(",\n");
        builder.append("    \"maxRefreshFailStreak\": ").append(config.getMaxRefreshFailStreak()).append(",\n");
        builder.append("    \"blacklistKeywords\": [");
        List<String> blacklist = config.getBlacklistKeywords();
        for (int i = 0; i < blacklist.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("\"").append(escape(blacklist.get(i))).append("\"");
        }
        builder.append("]\n");
        builder.append("  },\n");

        builder.append("  \"seller\": {\n");
        builder.append("    \"markupPercent\": ").append(config.getSellerMarkupPercent()).append("\n");
        builder.append("  },\n");

        builder.append("  \"parser\": {\n");
        builder.append("    \"buyPercent\": ").append(config.getParserBuyPercent()).append(",\n");
        builder.append("    \"sellPercent\": ").append(config.getParserSellPercent()).append(",\n");
        builder.append("    \"openWaitMs\": ").append(config.getParserOpenWaitMs()).append(",\n");
        builder.append("    \"betweenItemsMs\": ").append(config.getParserBetweenItemsMs()).append("\n");
        builder.append("  },\n");

        builder.append("  \"antiAfk\": {\n");
        builder.append("    \"enabled\": ").append(config.isAntiAfkEnabled()).append(",\n");
        builder.append("    \"intervalMs\": ").append(config.getAntiAfkIntervalMs()).append(",\n");
        builder.append("    \"anarchy\": \"").append(escape(config.getAntiAfkAnarchy())).append("\"\n");
        builder.append("  },\n");

        builder.append("  \"keybinds\": {\n");
        builder.append("    \"debug\": ").append(config.getKeyDebug()).append(",\n");
        builder.append("    \"fingerprint\": ").append(config.getKeyFingerprint()).append(",\n");
        builder.append("    \"scan\": ").append(config.getKeyScan()).append(",\n");
        builder.append("    \"observer\": ").append(config.getKeyObserver()).append(",\n");
        builder.append("    \"refresh\": ").append(config.getKeyRefresh()).append(",\n");
        builder.append("    \"buy\": ").append(config.getKeyBuy()).append(",\n");
        builder.append("    \"oneCycle\": ").append(config.getKeyOneCycle()).append(",\n");
        builder.append("    \"limitedLoop\": ").append(config.getKeyLimitedLoop()).append(",\n");
        builder.append("    \"gui\": ").append(config.getKeyGui()).append(",\n");
        builder.append("    \"parser\": ").append(config.getKeyParser()).append(",\n");
        builder.append("    \"fullAuto\": ").append(config.getKeyFullAuto()).append(",\n");
        builder.append("    \"sellOnly\": ").append(config.getKeySellOnly()).append("\n");
        builder.append("  },\n");

        builder.append("  \"targets\": [\n");

        List<TargetConfig> targets = config.getTargets();
        for (int i = 0; i < targets.size(); i++) {
            TargetConfig target = targets.get(i);
            builder.append("    {\n");
            builder.append("      \"label\": \"").append(escape(target.getLabel())).append("\",\n");
            builder.append("      \"enabled\": ").append(target.isEnabled()).append(",\n");
            builder.append("      \"maxUnitPrice\": ").append(target.getMaxUnitPrice()).append(",\n");
            builder.append("      \"sellUnitPrice\": ").append(target.getSellUnitPrice()).append(",\n");
            builder.append("      \"unstack\": ").append(target.isUnstack()).append(",\n");
            builder.append("      \"unstackAmount\": ").append(target.getUnstackAmount()).append(",\n");
            builder.append("      \"potionDragMinSourceCount\": ").append(target.getPotionDragMinSourceCount()).append(",\n");
            builder.append("      \"parserEnabled\": ").append(target.isParserEnabled()).append(",\n");
            builder.append("      \"itemId\": \"").append(escape(target.getItemId())).append("\",\n");
            builder.append("      \"tagContains\": \"").append(escape(target.getTagContains())).append("\",\n");
            builder.append("      \"contains\": [");

            List<String> contains = target.getContains();
            for (int j = 0; j < contains.size(); j++) {
                if (j > 0) {
                    builder.append(", ");
                }
                builder.append("\"").append(escape(contains.get(j))).append("\"");
            }

            builder.append("]\n");
            builder.append("    }");

            if (i + 1 < targets.size()) {
                builder.append(",");
            }

            builder.append("\n");
        }

        builder.append("  ]\n");
        builder.append("}\n");

        return builder.toString();
    }

    private int parseInt(String json, String key, int fallback) {
        long value = parseLong(json, key, fallback);
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            return fallback;
        }
        return (int) value;
    }

    private long parseLong(String json, String key, long fallback) {
        if (json == null || key == null) {
            return fallback;
        }

        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return fallback;
        }

        try {
            return Long.parseLong(matcher.group(1));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private boolean parseBoolean(String json, String key, boolean fallback) {
        if (json == null || key == null) {
            return fallback;
        }

        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return fallback;
        }

        return Boolean.parseBoolean(matcher.group(1));
    }

    private String parseString(String json, String key, String fallback) {
        if (json == null || key == null) {
            return fallback;
        }

        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return fallback;
        }

        return unescape(matcher.group(1));
    }

    private List<String> parseStringArray(String json, String key) {
        List<String> result = new ArrayList<String>();

        String body = extractArrayBody(json, key);
        if (body == null) {
            return result;
        }

        Pattern itemPattern = Pattern.compile("\"((?:\\\\.|[^\"])*)\"");
        Matcher matcher = itemPattern.matcher(body);
        while (matcher.find()) {
            String item = unescape(matcher.group(1)).trim().toLowerCase();
            if (!item.isEmpty() && !result.contains(item)) {
                result.add(item);
            }
        }

        return result;
    }

    private String extractObjectBody(String json, String key) {
        if (json == null || key == null) {
            return null;
        }

        Pattern keyPattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\{", Pattern.DOTALL);
        Matcher matcher = keyPattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }

        int start = matcher.end();
        int depth = 1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i);
                }
            }
        }

        return null;
    }

    private String extractArrayBody(String json, String key) {
        if (json == null || key == null) {
            return null;
        }

        Pattern keyPattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[", Pattern.DOTALL);
        Matcher matcher = keyPattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }

        int start = matcher.end();
        int depth = 1;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (inString) {
                continue;
            }

            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(start, i);
                }
            }
        }

        return null;
    }

    private String readAll(File file) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            builder.append(line).append('\n');
        }

        reader.close();
        return builder.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private String unescape(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder(value.length());
        boolean escaped = false;

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            if (!escaped) {
                if (c == '\\') {
                    escaped = true;
                } else {
                    builder.append(c);
                }
                continue;
            }

            if (c == 'n') {
                builder.append('\n');
            } else if (c == 'r') {
                builder.append('\r');
            } else if (c == 't') {
                builder.append('\t');
            } else {
                builder.append(c);
            }

            escaped = false;
        }

        if (escaped) {
            builder.append('\\');
        }

        return builder.toString();
    }
}
