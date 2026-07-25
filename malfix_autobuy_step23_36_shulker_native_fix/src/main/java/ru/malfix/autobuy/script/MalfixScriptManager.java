package ru.malfix.autobuy.script;

import net.minecraft.client.MinecraftClient;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

public final class MalfixScriptManager {
    private final MinecraftClient client;
    private final File primaryScriptsDir;
    private final File legacyScriptsDir;
    private final Map<String, List<Object>> handlers = new HashMap<String, List<Object>>();
    private final List<String> loadedScripts = new ArrayList<String>();
    private final List<String> scriptLoadErrors = new ArrayList<String>();
    private final List<Thread> repeatThreads = new ArrayList<Thread>();
    private String lastStatus = "not_loaded";
    private long lastReloadAtMs = 0L;
    private int tickHandlerErrors = 0;
    private int messageHandlerErrors = 0;
    private ScriptEngine sharedEngine;
    private Bindings sharedBindings;
    private volatile boolean asyncReloadRunning = false;
    private volatile boolean includeLegacyScripts = false;

    public MalfixScriptManager(MinecraftClient client) {
        this.client = client;
        File runDir = client == null || client.runDirectory == null ? new File(".") : client.runDirectory;

        // Safe Malfix scripts folder. This is loaded automatically.
        // Do NOT auto-load the old ./scripts folder because many old onLoad.js files
        // contain SpookyBuy-specific bootstrap code and can freeze the launcher.
        this.primaryScriptsDir = new File(new File(runDir, "malfix_autobuy"), "scripts");

        // Old Spooky/Never folder. Supported only by manual legacy reload.
        this.legacyScriptsDir = new File(runDir, "scripts");
    }

    public void initAndLoad() {
        ensureScriptsDir();
        reloadAsync(false);
    }

    public void reloadAsync(final boolean includeLegacy) {
        if (asyncReloadRunning) {
            System.out.println("[MAB SCRIPT] reload already running");
            return;
        }
        asyncReloadRunning = true;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    reloadInternal(includeLegacy);
                } finally {
                    asyncReloadRunning = false;
                }
            }
        }, "MAB-Script-Loader");
        thread.setDaemon(true);
        thread.start();
    }

    public void ensureScriptsDir() {
        ensureOneDir(primaryScriptsDir, true);
        ensureOneDir(legacyScriptsDir, false);
    }

    private void ensureOneDir(File dir, boolean createOnLoad) {
        if (dir == null) {
            return;
        }

        if (!dir.exists()) {
            if (!createOnLoad) {
                return;
            }
            if (!dir.mkdirs()) {
                lastStatus = "cannot_create_dir=" + dir.getAbsolutePath();
                return;
            }
        }

        File readme = new File(dir, "README_RU.txt");
        if (!readme.exists()) {
            try {
                FileOutputStream out = new FileOutputStream(readme);
                String text = "Кидай .js скрипты в эту безопасную папку и используй .mab scripts reload.\r\n"
                        + "Автозагрузка читает только эту папку: " + primaryScriptsDir.getAbsolutePath() + "\r\n"
                        + "Старая папка ./scripts НЕ грузится автоматически, чтобы старый onLoad.js не ломал запуск: " + legacyScriptsDir.getAbsolutePath() + "\r\n"
                        + "Если очень нужно загрузить старую папку вручную: .mab scripts legacyreload\r\n"
                        + "Поддерживаются старые NeverAPI/SpookyBuy-скрипты: on.accept(...), print.accept(...), chat.accept(...).\r\n";
                out.write(text.getBytes(StandardCharsets.UTF_8));
                out.close();
            } catch (Throwable ignored) {
            }
        }

        // Do not auto-create onLoad.js. Broken old onLoad scripts are the most common
        // reason for launch freezes, so Malfix loads direct scripts such as shalk.js only.
    }

    public void reload() {
        reloadInternal(false);
    }

    public void reloadWithLegacyFolder() {
        reloadInternal(true);
    }

    private synchronized void reloadInternal(boolean includeLegacy) {
        this.includeLegacyScripts = includeLegacy;
        ensureScriptsDir();
        stopRepeatThreads();
        handlers.clear();
        loadedScripts.clear();
        scriptLoadErrors.clear();
        tickHandlerErrors = 0;
        messageHandlerErrors = 0;
        lastReloadAtMs = System.currentTimeMillis();

        sharedEngine = createEngine();
        if (sharedEngine == null) {
            lastStatus = "nashorn_engine_not_found dirs=" + dirsSummary();
            System.out.println("[MAB SCRIPT] Nashorn engine not found. dirs=" + dirsSummary());
            return;
        }

        sharedBindings = createSharedBindings();

        List<File> jsFiles = findAllJsFiles();
        if (jsFiles.isEmpty()) {
            lastStatus = "no_js_files dirs=" + dirsSummary();
            System.out.println("[MAB SCRIPT] no .js files. dirs=" + dirsSummary());
            return;
        }

        int ok = 0;
        int failed = 0;

        // Old loader runs onLoad first, then every other script.
        List<File> ordered = orderOnLoadFirst(jsFiles);
        for (int i = 0; i < ordered.size(); i++) {
            File file = ordered.get(i);
            if (loadOne(file)) {
                ok++;
                loadedScripts.add(displayName(file));
            } else {
                failed++;
            }
        }

        lastStatus = "loaded=" + ok + ", failed=" + failed + ", dirs=" + dirsSummary();
        System.out.println("[MAB SCRIPT] reload finished: " + lastStatus + ", scripts=" + loadedScripts + ", errors=" + scriptLoadErrors);
    }

    private Bindings createSharedBindings() {
        Bindings bindings = new SimpleBindings();
        ScriptEventBus eventBus = new ScriptEventBus(this);
        ScriptPrintBridge printBridge = new ScriptPrintBridge();
        ScriptChatBridge chatBridge = new ScriptChatBridge(client);

        bindings.put("on", eventBus);
        bindings.put("print", printBridge);
        bindings.put("chat", chatBridge);
        bindings.put("mabCompat", new ScriptCompatBridge(client));
        bindings.put("compat", bindings.get("mabCompat"));
        bindings.put("mc", MinecraftClient.getInstance());
        bindings.put("minecraft", MinecraftClient.getInstance());
        bindings.put("player", client == null ? null : client.player);
        bindings.put("keyboard", client == null ? null : client.keyboard);
        try {
            bindings.put("window", client == null ? null : client.getWindow());
        } catch (Throwable ignored) {
            bindings.put("window", null);
        }

        bindings.put("runScript", new BiConsumer<Object, Object>() {
            @Override
            public void accept(Object codeOrName, Object runByName) {
                handleRunScript(codeOrName, runByName);
            }
        });

        bindings.put("repeat", new BiConsumer<Object, Object>() {
            @Override
            public void accept(Object handler, Object delayMs) {
                startRepeat(handler, delayMs);
            }
        });

        return bindings;
    }

    private List<File> findAllJsFiles() {
        List<File> result = new ArrayList<File>();
        addJsFiles(result, primaryScriptsDir);
        if (includeLegacyScripts) {
            addJsFiles(result, legacyScriptsDir);
        }
        return result;
    }

    private void addJsFiles(List<File> out, File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        List<File> local = new ArrayList<File>();
        for (int i = 0; i < files.length; i++) {
            File f = files[i];
            if (f != null && f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".js")) {
                local.add(f);
            }
        }
        Collections.sort(local);
        out.addAll(local);
    }

    private List<File> orderOnLoadFirst(List<File> files) {
        List<File> ordered = new ArrayList<File>();
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            if ("onload.js".equals(f.getName().toLowerCase(Locale.ROOT))) {
                ordered.add(f);
            }
        }
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            if (!"onload.js".equals(f.getName().toLowerCase(Locale.ROOT))) {
                ordered.add(f);
            }
        }
        return ordered;
    }

    private boolean loadOne(File file) {
        if (sharedEngine == null) {
            return false;
        }

        try {
            String source = readUtf8(file);
            String rewritten = rewriteLegacyScriptSource(source, file == null ? "" : file.getName());
            sharedEngine.eval(rewritten, sharedBindings);

            System.out.println("[MAB SCRIPT] loaded " + displayName(file));
            return true;
        } catch (Throwable throwable) {
            String err = displayName(file) + ": " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            scriptLoadErrors.add(err);
            System.out.println("[MAB SCRIPT] load failed " + err);
            throwable.printStackTrace(System.out);
            return false;
        }
    }


    private String readUtf8(File file) throws Exception {
        FileInputStream input = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (true) {
                int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            try {
                input.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private String rewriteLegacyScriptSource(String source, String fileName) {
        if (source == null || source.isEmpty()) {
            return "";
        }

        String out = source;

        // Old shulker scripts often import Registry.class_2378 and then use the
        // removed Registry.ITEM field. In 1.21.4 item ids are read through Registries,
        // so we route getItemId through ScriptCompatBridge instead.
        out = out.replace("var Registry = Java.type(\"net.minecraft.class_2378\");", "var Registry = null; // rewritten by Malfix 1.21.4 compat");
        out = out.replace("var Registry = Java.type('net.minecraft.class_2378');", "var Registry = null; // rewritten by Malfix 1.21.4 compat");

        out = replaceFunction(out, "getItemId", "function getItemId(stack) {\n    return mabCompat.getItemId(stack);\n}\n");
        out = replaceFunction(out, "isShulkerBox", "function isShulkerBox(stack) {\n    return mabCompat.isShulkerBox(stack);\n}\n");
        out = replaceFunction(out, "selectHotbarSlot", "function selectHotbarSlot(slot) {\n    mabCompat.selectHotbarSlot(slot);\n}\n");
        out = replaceFunction(out, "rightClickMainHand", "function rightClickMainHand() {\n    mabCompat.rightClickMainHand();\n}\n");
        out = replaceFunction(out, "quickMoveSlot", "function quickMoveSlot(syncId, slotId) {\n    mabCompat.quickMoveSlot(syncId, slotId);\n}\n");
        out = replaceFunction(out, "isContainerOpen", "function isContainerOpen() {\n    return mabCompat.isContainerOpen();\n}\n");
        out = replaceFunction(out, "getContainerSlotsCount", "function getContainerSlotsCount() {\n    return mabCompat.getContainerSlotsCount();\n}\n");
        out = replaceFunction(out, "closeCurrentScreen", "function closeCurrentScreen() {\n    mabCompat.closeCurrentScreen();\n}\n");

        // Old scripts use mc.field_1755 as "any screen is open" and then close it.
        // On 1.21.4 this also catches Malfix/config/options screens, so a shulker
        // script can block every menu. Route those checks through a compat method
        // that returns true only for handled container screens used by shulkers/ec/ah.
        out = out.replace("mc.field_1755 != null", "mabCompat.isLegacyBlockingScreenOpen()");
        out = out.replace("mc.field_1755!=null", "mabCompat.isLegacyBlockingScreenOpen()");
        out = out.replace("mc.field_1755 == null", "!mabCompat.isLegacyBlockingScreenOpen()");
        out = out.replace("mc.field_1755==null", "!mabCompat.isLegacyBlockingScreenOpen()");

        // Do not let old storage scripts close /ah in a loop. The first attempt
        // inserted this guard at the top of startShulkerSequence/startEcSequence,
        // but that skipped the script's own initialization of shulkerSlots/currentMode.
        // Keep the initialization, then close /ah only after the script has found a
        // target shulker/EC path and paused AutoBuy.
        out = out.replace(
                "pauseAutoBuy();\n\n    if (mabCompat.isLegacyBlockingScreenOpen()) {\n        closeCurrentScreen();\n        state = \"WAIT_SCREEN_CLOSE\";",
                "pauseAutoBuy();\n\n    if (mabCompat.isAuctionScreenOpen()) {\n        mabCompat.closeAuctionScreenForStorage();\n        state = \"WAIT_SCREEN_CLOSE\";\n        nextActionAt = now() + OPEN_AUCTION_CLOSE_WAIT_MS;\n        return;\n    }\n\n    if (mabCompat.isLegacyBlockingScreenOpen()) {\n        closeCurrentScreen();\n        state = \"WAIT_SCREEN_CLOSE\";");
        out = out.replace(
                "pauseAutoBuy();\n\n    if (mabCompat.isLegacyBlockingScreenOpen()) {\n        closeCurrentScreen();\n        state = \"EC_WAIT_SCREEN_CLOSE\";",
                "pauseAutoBuy();\n\n    if (mabCompat.isAuctionScreenOpen()) {\n        mabCompat.closeAuctionScreenForStorage();\n        state = \"EC_WAIT_SCREEN_CLOSE\";\n        nextActionAt = now() + OPEN_AUCTION_CLOSE_WAIT_MS;\n        return;\n    }\n\n    if (mabCompat.isLegacyBlockingScreenOpen()) {\n        closeCurrentScreen();\n        state = \"EC_WAIT_SCREEN_CLOSE\";");

        // Old shulker scripts keep forceByFullInventoryMessage=true after a single
        // server message. Then every later /ah screen is treated as "inventory full"
        // and startShulkerSequence() instantly closes the auction. Reset the forced
        // flag as soon as the local inventory has free slots again.
        out = out.replace(
                "var invFullNow = forceByFullInventoryMessage || inventoryIsFullEnough();",
                "if (!inventoryIsFullEnough()) forceByFullInventoryMessage = false;\n            var invFullNow = forceByFullInventoryMessage || inventoryIsFullEnough();");
        out = out.replace(
                "var invFullNow=forceByFullInventoryMessage||inventoryIsFullEnough();",
                "if (!inventoryIsFullEnough()) forceByFullInventoryMessage = false;\n            var invFullNow = forceByFullInventoryMessage || inventoryIsFullEnough();");


        // Step 23.33: the uploaded shalk.js used a hard-coded ">= 3" full-shulker
        // threshold before trying /ec. Make it work with any number of hotbar
        // shulkers: when all detected hotbar shulkers are known full, switch to EC.
        out = out.replace(
                "countKnownFullShulkersInHotbar() >= 3 && !isEcBlocked()",
                "mabCompat.shouldUseEcPut(countKnownFullShulkersInHotbar(), 3) && !isEcBlocked()");

        if (!out.equals(source)) {
            System.out.println("[MAB SCRIPT] legacy compat rewrite applied: " + fileName);
        }
        return out;
    }

    private String replaceFunction(String source, String functionName, String replacement) {
        if (source == null || functionName == null || replacement == null) {
            return source;
        }
        String needle = "function " + functionName;
        int start = source.indexOf(needle);
        if (start < 0) {
            return source;
        }
        int open = source.indexOf('{', start + needle.length());
        if (open < 0) {
            return source;
        }
        int close = findMatchingBrace(source, open);
        if (close < 0) {
            return source;
        }
        return source.substring(0, start) + replacement + source.substring(close + 1);
    }

    private int findMatchingBrace(String source, int open) {
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : 0;

            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inSingle) {
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (c == '\'') {
                    inSingle = false;
                }
                continue;
            }
            if (inDouble) {
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (c == '"') {
                    inDouble = false;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '\'') {
                inSingle = true;
                continue;
            }
            if (c == '"') {
                inDouble = true;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private ScriptEngine createEngine() {
        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("Nashorn");
            if (engine != null) {
                return engine;
            }
        } catch (Throwable ignored) {
        }

        try {
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
            if (engine != null) {
                return engine;
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> factoryClass = Class.forName("org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory");
            Object factory = factoryClass.getConstructor().newInstance();
            if (factory instanceof ScriptEngineFactory) {
                return ((ScriptEngineFactory) factory).getScriptEngine();
            }
            Method method = factoryClass.getMethod("getScriptEngine");
            Object engine = method.invoke(factory);
            return engine instanceof ScriptEngine ? (ScriptEngine) engine : null;
        } catch (Throwable throwable) {
            System.out.println("[MAB SCRIPT] Nashorn factory unavailable: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }

        return null;
    }

    public void register(Object eventClassName, Object handler) {
        if (eventClassName == null || handler == null) {
            return;
        }
        String key = normalizeEvent(String.valueOf(eventClassName));
        List<Object> list = handlers.get(key);
        if (list == null) {
            list = new ArrayList<Object>();
            handlers.put(key, list);
        }
        list.add(handler);
        System.out.println("[MAB SCRIPT] handler registered: " + key + ", total=" + list.size());
    }

    public void firePlayerTick() {
        updateLiveBindings();
        // Let legacy shulker scripts react to a *real* full inventory while /ah is open.
        // Otherwise the script cannot move items into a shulker after the autobuy fills
        // the inventory. If /ah is open but inventory is not full, keep the old guard so
        // the script does not close the auction in a loop.
        try {
            ScriptCompatBridge compat = new ScriptCompatBridge(client);
            if (compat.isAuctionScreenOpen() && !compat.isInventoryFullEnough(0)) {
                return;
            }
        } catch (Throwable ignored) {
        }
        fire("eventplayertick", new ru.nedan.neverapi.event.impl.EventPlayerTick(), true);
    }

    public void fireServerMessage(String message) {
        updateLiveBindings();
        try {
            if (new ScriptCompatBridge(client).isAuctionScreenOpen()) {
                String lower = message == null ? "" : String.valueOf(message).toLowerCase(Locale.ROOT).replace('ё', 'е');
                if ((lower.contains("полный инвентарь") || lower.contains("full inventory") || lower.contains("inventory is full"))
                        && !new ScriptCompatBridge(client).isInventoryFullEnough(0)) {
                    // Ignore stale/full-inventory text only when the local inventory is not full.
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
        fire("eventmessage", new ru.nedan.neverapi.event.impl.EventMessage(message, false), false);
    }

    private void updateLiveBindings() {
        if (sharedBindings == null) {
            return;
        }
        try {
            sharedBindings.put("player", client == null ? null : client.player);
            sharedBindings.put("mc", MinecraftClient.getInstance());
            sharedBindings.put("minecraft", MinecraftClient.getInstance());
        } catch (Throwable ignored) {
        }
    }

    private void fire(String key, Object event, boolean tick) {
        List<Object> list = handlers.get(key);
        if (list == null || list.isEmpty()) {
            return;
        }

        List<Object> copy = new ArrayList<Object>(list);
        for (int i = 0; i < copy.size(); i++) {
            Object handler = copy.get(i);
            try {
                callHandler(handler, event);
            } catch (Throwable throwable) {
                if (tick) {
                    tickHandlerErrors++;
                } else {
                    messageHandlerErrors++;
                }
                if ((tick ? tickHandlerErrors : messageHandlerErrors) <= 10) {
                    System.out.println("[MAB SCRIPT] handler error: " + throwable.getClass().getSimpleName()
                            + ": " + throwable.getMessage());
                    throwable.printStackTrace(System.out);
                }
            }
        }
    }

    private void callHandler(Object handler, Object event) throws Exception {
        if (handler == null) {
            return;
        }
        Class<?> c = handler.getClass();

        try {
            Method call = c.getMethod("call", Object.class, Object[].class);
            call.invoke(handler, null, new Object[]{event});
            return;
        } catch (NoSuchMethodException ignored) {
        }

        Method[] methods = c.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            if (!"call".equals(m.getName())) {
                continue;
            }
            Class<?>[] types = m.getParameterTypes();
            if (types.length == 2 && types[1].isArray()) {
                m.invoke(handler, null, new Object[]{event});
                return;
            }
            if (types.length == 1) {
                m.invoke(handler, event);
                return;
            }
        }

        try {
            Method accept = c.getMethod("accept", Object.class);
            accept.invoke(handler, event);
            return;
        } catch (NoSuchMethodException ignored) {
        }

        throw new NoSuchMethodException("Cannot call JS handler: " + c.getName());
    }

    private void startRepeat(final Object handler, Object delayMs) {
        long delay = toLong(delayMs, 1000L);
        if (delay < 50L) {
            delay = 50L;
        } else if (delay > 60_000L) {
            delay = 60_000L;
        }

        final long finalDelay = delay;
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        callHandler(handler, null);
                        Thread.sleep(finalDelay);
                    } catch (InterruptedException interrupted) {
                        return;
                    } catch (Throwable throwable) {
                        System.out.println("[MAB SCRIPT] repeat error: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                        return;
                    }
                }
            }
        }, "MAB-Script-Repeat");
        thread.setDaemon(true);
        repeatThreads.add(thread);
        thread.start();
    }

    private void stopRepeatThreads() {
        for (int i = 0; i < repeatThreads.size(); i++) {
            Thread thread = repeatThreads.get(i);
            if (thread != null) {
                try {
                    thread.interrupt();
                } catch (Throwable ignored) {
                }
            }
        }
        repeatThreads.clear();
    }

    private void handleRunScript(Object codeOrName, Object runByName) {
        if (codeOrName == null || sharedEngine == null || sharedBindings == null) {
            return;
        }

        String text = String.valueOf(codeOrName);
        boolean byName = toBoolean(runByName);

        try {
            if (byName) {
                File file = findScriptByName(text);
                if (file != null) {
                    loadOne(file);
                } else {
                    System.out.println("[MAB SCRIPT] runScript file not found: " + text);
                }
            } else {
                sharedEngine.eval(text, sharedBindings);
            }
        } catch (Throwable throwable) {
            System.out.println("[MAB SCRIPT] runScript failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            throwable.printStackTrace(System.out);
        }
    }

    private File findScriptByName(String name) {
        if (name == null) {
            return null;
        }
        String target = name.toLowerCase(Locale.ROOT);
        List<File> files = findAllJsFiles();
        for (int i = 0; i < files.size(); i++) {
            File f = files.get(i);
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.equals(target) || n.equals(target + ".js")) {
                return f;
            }
        }
        return null;
    }

    private long toLong(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value == null) {
            return false;
        }
        String s = String.valueOf(value).toLowerCase(Locale.ROOT).trim();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "да".equals(s);
    }

    private String normalizeEvent(String eventClassName) {
        String s = eventClassName.toLowerCase(Locale.ROOT).trim();
        int dot = s.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < s.length()) {
            s = s.substring(dot + 1);
        }
        return s;
    }

    public boolean hasLoadedScripts() {
        return !loadedScripts.isEmpty();
    }

    public File getScriptsDir() {
        return primaryScriptsDir;
    }

    public String getAllScriptDirs() {
        return dirsSummary();
    }

    public String status() {
        return "scripts=" + loadedScripts
                + ", loadedCount=" + loadedScripts.size()
                + ", handlers=" + handlersSummary()
                + ", tickErrors=" + tickHandlerErrors
                + ", messageErrors=" + messageHandlerErrors
                + ", loadErrors=" + scriptLoadErrors
                + ", lastReloadAgoMs=" + (lastReloadAtMs <= 0L ? -1L : Math.max(0L, System.currentTimeMillis() - lastReloadAtMs))
                + ", loading=" + asyncReloadRunning
                + ", legacyIncluded=" + includeLegacyScripts
                + ", status=" + lastStatus
                + ", dirs=" + dirsSummary();
    }

    private String handlersSummary() {
        if (handlers.isEmpty()) {
            return "{}";
        }
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, List<Object>> e : handlers.entrySet()) {
            if (!first) {
                b.append(", ");
            }
            first = false;
            b.append(e.getKey()).append('=').append(e.getValue() == null ? 0 : e.getValue().size());
        }
        b.append('}');
        return b.toString();
    }

    private String dirsSummary() {
        return "safe=" + primaryScriptsDir.getAbsolutePath() + ", oldLegacyManual=" + legacyScriptsDir.getAbsolutePath();
    }

    private String displayName(File file) {
        if (file == null) {
            return "null";
        }
        try {
            return file.getParentFile().getName() + "/" + file.getName();
        } catch (Throwable ignored) {
            return file.getName();
        }
    }
}
