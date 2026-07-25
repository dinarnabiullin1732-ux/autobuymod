package ru.malfix.autobuy.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import org.lwjgl.glfw.GLFW;
import ru.malfix.autobuy.auction.AuctionFingerprint;
import ru.malfix.autobuy.auction.AuctionScreenConfig;
import ru.malfix.autobuy.auction.AuctionSlot;
import ru.malfix.autobuy.auction.MinecraftAuctionView;
import ru.malfix.autobuy.core.AutoBuyRuntime;
import ru.malfix.autobuy.config.AutoBuyConfig;
import ru.malfix.autobuy.config.AutoBuyConfigManager;
import ru.malfix.autobuy.config.TargetConfig;
import ru.malfix.autobuy.config.ScriptItemCatalog;
import ru.malfix.autobuy.config.MalfixTimings;
import ru.malfix.autobuy.config.MalfixRuntimeSettings;
import ru.malfix.autobuy.gui.TargetsConfigScreen;
import ru.malfix.autobuy.gui.KeybindConfigScreen;
import ru.malfix.autobuy.gui.ParserConfigScreen;
import ru.malfix.autobuy.loop.LimitedAutoLoopController;
import ru.malfix.autobuy.loop.AutoLoopResult;
import ru.malfix.autobuy.cycle.OneCycleAutoBuyController;
import ru.malfix.autobuy.cycle.OneCycleResult;
import ru.malfix.autobuy.buy.BuyDryRunExecutor;
import ru.malfix.autobuy.buy.BuyDryRunResult;
import ru.malfix.autobuy.buy.BuyResult;
import ru.malfix.autobuy.buy.BuyResultDetector;
import ru.malfix.autobuy.buy.ControlledBuyClickExecutor;
import ru.malfix.autobuy.buy.ControlledBuyClickResult;
import ru.malfix.autobuy.scanner.AuctionScanner;
import ru.malfix.autobuy.scanner.ScanMode;
import ru.malfix.autobuy.refresh.RefreshCycleController;
import ru.malfix.autobuy.price.ParsedPrice;
import ru.malfix.autobuy.price.PriceParser;
import ru.malfix.autobuy.refresh.RefreshCycleResult;
import ru.malfix.autobuy.seller.SellerController;
import ru.malfix.autobuy.seller.SellerResult;
import ru.malfix.autobuy.shulker.ShulkerController;
import ru.malfix.autobuy.script.MalfixScriptManager;
import ru.malfix.autobuy.script.ScriptCompatBridge;
import ru.malfix.autobuy.unstack.UnstackController;
import ru.malfix.autobuy.render.PotatoMode;
import ru.malfix.autobuy.render.AutomationPerformance;
import ru.malfix.autobuy.profiler.MalfixProfiler;
import ru.malfix.autobuy.scanner.ScanCandidate;
import ru.malfix.autobuy.scanner.ScanResult;
import ru.malfix.autobuy.scanner.TargetItem;
import ru.malfix.autobuy.scanner.ItemMatcher;
import ru.malfix.autobuy.scanner.MatchResult;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ru.malfix.autobuy.mc.McChat;
import ru.malfix.autobuy.mc.McItemStacks;

public final class MalfixClientRuntime {

    private final MinecraftClient client;
    private final MinecraftAuctionView auctionView;
    private final AutoBuyConfigManager configManager;
    private AutoBuyConfig config;
    private final AuctionScanner scanner;
    private final AutoBuyRuntime runtime;
    private final RefreshCycleController refreshCycle;
    private final BuyDryRunExecutor buyDryRun;
    private final ControlledBuyClickExecutor controlledBuyClick;
    private final OneCycleAutoBuyController oneCycle;
    private final LimitedAutoLoopController limitedLoop;
    private final SellerController sellerController;
    private final BuyResultDetector buyResultDetector;
    private final ChatResultDeduplicator chatResultDeduplicator;
    private final ClientChat chat;
    private final ShulkerController shulkerController;
    private final MalfixScriptManager scriptManager;
    private final UnstackController unstackController;
    private final MalfixRuntimeSettings runtimeSettings;
    private final TelegramNotifier telegramNotifier;

    private final DecimalFormat moneyFormat;

    private long ticks = 0L;
    private long lastTickErrorLogAt = 0L;
    private long lastCompactLogAt = 0L;
    private long nextAutoShulkerCheckAtMs = 0L;
    private boolean legacyScriptPauseActive = false;
    // Step 22.40: Nashorn player-tick/message callbacks are expensive when 2-3 launchers
    // sell stacks at the same time. Keep scripts alive, but throttle them during heavy
    // automation instead of firing every render/client tick.
    private long nextLowLagScriptTickAtMs = 0L;
    private String legacyScriptPauseReason = "none";
    private long legacyScriptPauseChangedAtMs = 0L;
    private boolean legacyScriptResumeWaitingAuction = false;
    private boolean legacyScriptResumeAhSent = false;
    private long legacyScriptResumeStartedAtMs = 0L;
    private long legacyScriptResumeNextActionAtMs = 0L;
    private String legacyScriptResumeReason = "none";
    private boolean nativeShulkerPausedBaseRuntime = false;
    private boolean nativeShulkerBaseRuntimeWasEnabled = false;
    private long nativeShulkerPauseStartedAtMs = 0L;

    private boolean observerEnabled = false;
    private long lastObserverTickAt = 0L;
    private long lastObserverMessageAt = 0L;
    private int lastObserverFingerprint = 0;
    private int currentObserverFingerprint = 0;
    private boolean lastObserverAuctionOpen = false;
    private ScanResult lastObserverScanResult = ScanResult.notScanned();
    private BuyDryRunResult lastBuyDryRunResult = BuyDryRunResult.idle();
    private ControlledBuyClickResult lastControlledBuyClickResult = ControlledBuyClickResult.idle();
    private BuyResult lastBuyResult = BuyResult.none();
    private String lastBuyResultSource = "none";
    private boolean buyBlockedByNoMoney = false;
    private long buyBlockedNoMoneyAtMs = 0L;
    private long buyBlockedNoMoneyMinPrice = 0L;
    private String buyBlockedNoMoneyReason = "none";
    private String lastServerBuyMessage = "";
    private boolean lastServerBuyMessageDuplicate = false;
    private long lastServerBuyMessageAt = 0L;
    private OneCycleResult lastOneCycleResult = OneCycleResult.idle();
    private AutoLoopResult lastAutoLoopResult = AutoLoopResult.idle();
    private SellerResult lastSellerResult = SellerResult.idle();
    private String lastTelegramSoldFingerprint = "";
    private long lastTelegramSoldAtMs = 0L;
    private ScanCandidate lastTelegramBuyCandidate = null;
    private long lastTelegramBuyCandidateAtMs = 0L;

    private boolean sellerLoopEnabled = false;
    private int sellerLoopMaxSells = 0;
    private int sellerLoopSellsDone = 0;
    private long sellerLoopDelayMs = MalfixTimings.AUTOSELL_SELL_MS;
    private long sellerLoopNextAtMs = 0L;
    private String sellerLoopStopReason = "none";
    private boolean sellerLoopUntilRent = false;
    private boolean sellerUnstackPrepareActive = false;
    private long sellerUnstackPrepareAtMs = 0L;
    private String sellerUnstackPrepareReason = "none";
    private boolean sellerAwaitingServerResult = false;
    private long sellerAwaitingServerResultSinceMs = 0L;
    private String sellerAwaitingServerCommand = "";

    private boolean lastSellLimitDetected = false;
    private String lastSellLimitMessage = "";
    private String lastSellLimitReason = "none";
    private long lastSellLimitAtMs = 0L;
    private boolean fullAutoSkipNextPreSellStorage = false;
    private String fullAutoSkipNextPreSellStorageReason = "none";

    private boolean sellerReturnToAuctionPending = false;
    private long sellerReturnToAuctionAtMs = 0L;
    private String sellerReturnToAuctionReason = "none";
    private int sellerReturnToAuctionAttempts = 0;

    private boolean sellerCycleEnabled = false;
    private String sellerCycleLastReason = "none";

    private boolean cycleFullEnabled = false;
    private String cycleFullPhase = "idle";
    private int cycleFullBuyCycles = 0;
    private int cycleFullBuyMax = 0;
    private int cycleFullSellMax = 0;
    private long cycleFullSellDelayMs = MalfixTimings.AUTOSELL_SELL_MS;
    private long cycleFullStartedAtMs = 0L;
    private long cycleFullNextActionAtMs = 0L;
    private int cycleFullOpenAttempts = 0;
    private String cycleFullStopReason = "none";
    private int cycleFullStorageSlot = 47;
    private int cycleFullStorageTakeMax = 36;
    private int cycleFullStorageTaken = 0;
    private int cycleFullStorageAttempts = 0;
    private int cycleFullStorageNoItemChecks = 0;
    private boolean cycleFullStorageContinueAfterSell = false;
    private String cycleFullStorageStatus = "none";
    private boolean cycleFullTimedBuyMode = false;
    private long cycleFullBuyTimeMs = 0L;
    private boolean cycleFullSellUntilRent = false;
    private boolean cycleFullPreSellBeforeBuy = false;

    private boolean fullAutoTimedEnabled = false;
    private boolean fullAutoTimedWaitingForCycle = false;
    private int fullAutoTimedRoundsStarted = 0;
    private long fullAutoTimedStartedAtMs = 0L;
    private long fullAutoTimedNextStartAtMs = 0L;
    private String fullAutoTimedStopReason = "none";

    private boolean sellOnlyTimedEnabled = false;
    private String sellOnlyPhase = "idle";
    private int sellOnlyRoundsStarted = 0;
    private long sellOnlyStartedAtMs = 0L;
    private long sellOnlyNextActionAtMs = 0L;
    private long sellOnlyNextRoundAtMs = 0L;
    private String sellOnlyStopReason = "none";
    private int sellOnlyStorageTakeMax = MalfixTimings.SELL_ONLY_STORAGE_TAKE_MAX;
    private int sellOnlyStorageTaken = 0;
    private int sellOnlyStorageAttempts = 0;
    private int sellOnlyStorageNoItemChecks = 0;
    private boolean sellOnlyStorageContinueAfterSell = false;
    private String sellOnlyStorageStatus = "none";
    private long sellOnlySellDelayMs = MalfixTimings.AUTOSELL_SELL_MS;

    private boolean parserRunning = false;
    private List<TargetConfig> parserQueue = new ArrayList<TargetConfig>();
    private int parserIndex = 0;
    private String parserPhase = "idle";
    private long parserNextAtMs = 0L;
    private int parserUpdated = 0;
    private int parserSkipped = 0;
    private int parserRetries = 0;
    private String parserLastStatus = "none";
    private TargetConfig parserCurrentTarget = null;
    private TargetConfig parserDebugArmedTarget = null;
    private String parserDebugArmReason = "none";
    private long parserDebugArmedAtMs = 0L;
    private long parserDebugArmUntilMs = 0L;
    private long parserDebugNextCheckAtMs = 0L;
    private long parserDebugFirstContainerSeenAtMs = 0L;
    private String parserDebugLastSeenTitle = "";

    private boolean antiAfkRunning = false;
    private String antiAfkPhase = "idle";
    private long antiAfkNextAtMs = 0L;
    private long antiAfkPhaseUntilMs = 0L;
    private String antiAfkLastAnarchy = "";
    private String antiAfkLastReason = "none";
    private long antiAfkLastRunAtMs = 0L;
    private int antiAfkRuns = 0;
    private long antiAfkLastChatTriggerAtMs = 0L;
    private String antiAfkLastChatTriggerMessage = "";

    private boolean spamKickRecovering = false;
    private String spamKickPhase = "idle";
    private String spamKickAnarchy = "";
    private long spamKickNextAtMs = 0L;
    private long spamKickStartedAtMs = 0L;
    private long spamKickLastRunAtMs = 0L;
    private int spamKickRuns = 0;
    private int spamKickAhAttempts = 0;
    private String spamKickLastReason = "none";
    private String spamKickLastMessage = "";

    private boolean autoRejoinWaiting = false;
    private long autoRejoinAtMs = 0L;
    private long autoRejoinLastAttemptAtMs = 0L;
    private int autoRejoinAttempts = 0;
    private String autoRejoinLastReason = "none";
    private String autoRejoinLastScreen = "none";
    private Object autoRejoinServerInfo = null;

    private String autoAuthPendingCommand = "";
    private long autoAuthSendAtMs = 0L;
    private long autoAuthLastSentAtMs = 0L;
    private long autoAuthLastWarnAtMs = 0L;
    private String autoAuthLastReason = "none";

    private long runtimeSettingsNextReloadAtMs = 0L;

    private boolean cycleFullLoopEnabled = false;
    private boolean cycleFullLoopWaitingForCycle = false;
    private int cycleFullLoopMaxCycles = 0;
    private int cycleFullLoopCyclesStarted = 0;
    private int cycleFullLoopBuyCycles = 0;
    private int cycleFullLoopBuyMax = 0;
    private int cycleFullLoopSellMax = 0;
    private long cycleFullLoopSellDelayMs = MalfixTimings.AUTOSELL_SELL_MS;
    private long cycleFullLoopDelayMs = MalfixTimings.FULL_AUTO_LOOP_DELAY_MS;
    private long cycleFullLoopStartedAtMs = 0L;
    private long cycleFullLoopNextStartAtMs = 0L;
    private String cycleFullLoopStopReason = "none";
    private boolean cycleFullLoopSkipNextSeller = false;
    private String cycleFullLoopSkipSellerReason = "none";

    private boolean safeAutoRunEnabled = false;
    private long safeAutoRunStartedAt = 0L;
    private long safeAutoRunNextStartAt = 0L;
    private int safeAutoRunSessionsStarted = 0;
    private int safeAutoRunTotalBuys = 0;
    private int safeAutoRunMaxTotalBuys = 0;
    private String safeAutoRunStopReason = "none";
    private String safeAutoRunLastCountedBuySignature = "";

    private String lastBuyStatus = "NONE";
    private String lastBoughtItem = "none";
    private long lastBoughtUnitPrice = 0L;
    private long lastBoughtTotalPrice = 0L;
    private int lastBoughtSlot = -1;
    private long lastBoughtAtMs = 0L;

    private boolean debugKeyWasDown = false;
    private boolean fingerprintKeyWasDown = false;
    private boolean scanKeyWasDown = false;
    private boolean observerKeyWasDown = false;
    private boolean refreshKeyWasDown = false;
    private boolean buyDryRunKeyWasDown = false;
    private boolean buyClickKeyWasDown = false;
    private boolean oneCycleKeyWasDown = false;
    private boolean limitedLoopKeyWasDown = false;
    private boolean guiKeyWasDown = false;
    private boolean parserKeyWasDown = false;
    private boolean fullAutoKeyWasDown = false;
    private boolean sellOnlyKeyWasDown = false;

    private boolean autoPotatoApplied = false;
    private boolean autoPotatoPreviousState = false;

    private static final long OBSERVER_INTERVAL_MS = MalfixTimings.SMART_REOPEN_TICK_MS;
    private static final long OBSERVER_CHAT_COOLDOWN_MS = 1000L;

    public MalfixClientRuntime(MinecraftClient client) {
        this.client = client;
        this.auctionView = new MinecraftAuctionView(client, AuctionScreenConfig.defaultConfig());
        this.configManager = new AutoBuyConfigManager(client == null ? null : client.runDirectory);
        this.runtimeSettings = new MalfixRuntimeSettings(client == null ? null : client.runDirectory);
        this.telegramNotifier = new TelegramNotifier();
        this.telegramNotifier.setCommandHandler(new TelegramNotifier.CommandHandler() {
            @Override
            public String handle(String text) {
                return handleTelegramBotCommand(text);
            }
        });
        this.telegramNotifier.reload(this.runtimeSettings);
        this.config = configManager.loadOrCreate();
        applyRuntimeSettingsToConfig(false);
        if (ScriptItemCatalog.applyCatalogPatch(this.config) > 0) {
            configManager.save(this.config);
        }
        this.scanner = new AuctionScanner(config.toTargetItems(), null, null);
        this.scanner.setSettings(config.toScannerSettings());
        this.runtime = new AutoBuyRuntime(auctionView, scanner);
        this.refreshCycle = new RefreshCycleController(auctionView, scanner);
        this.refreshCycle.setTimeoutMs(config.getRefreshTimeoutMs());
        this.buyDryRun = new BuyDryRunExecutor(auctionView, scanner);
        this.controlledBuyClick = new ControlledBuyClickExecutor(auctionView, scanner);
        this.oneCycle = new OneCycleAutoBuyController(auctionView, scanner);
        this.oneCycle.setRefreshTimeoutMs(config.getRefreshTimeoutMs());
        this.limitedLoop = new LimitedAutoLoopController(auctionView, scanner);
        this.limitedLoop.setDelayBetweenCyclesMs(config.getLoopDelayMs());
        this.limitedLoop.setRefreshTimeoutMs(config.getRefreshTimeoutMs());
        this.limitedLoop.setMaxRefreshFailStreak(config.getMaxRefreshFailStreak());
        this.limitedLoop.setSuccessCooldownMs(MalfixTimings.AB_BUY_MS);
        this.sellerController = new SellerController(client, config.toTargetItems());
        this.sellerController.setSellerMarkupPercent(config.getSellerMarkupPercent());
        this.unstackController = new UnstackController(client, config.toTargetItems());
        this.unstackController.setDelayMs(MalfixTimings.UNSTACK_SELL_SPLIT_MS);
        this.buyResultDetector = new BuyResultDetector();
        this.chatResultDeduplicator = new ChatResultDeduplicator();
        this.chat = new ClientChat(client);
        this.shulkerController = new ShulkerController(client);
        this.scriptManager = new MalfixScriptManager(client);
        this.scriptManager.initAndLoad();

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        this.moneyFormat = new DecimalFormat("#,###", symbols);
        this.antiAfkNextAtMs = System.currentTimeMillis() + config.getAntiAfkIntervalMs();
    }

    public void onClientTick(MinecraftClient currentClient) {
        long profTickStart = MalfixProfiler.start();
        ticks++;

        if (currentClient == null || currentClient.player == null || currentClient.world == null) {
            AutomationPerformance.setAutomationActive(false);
            try {
                tickRuntimeSettingsReload();
                tickAutoRejoinEarly(currentClient);
            } catch (Throwable ignored) {
            }
            MalfixProfiler.recordClientTick(profTickStart);
            return;
        }

        try {
            MalfixProfiler.tickWindow();
            tickRuntimeSettingsReload();
            if (tickAutoRejoinEarly(currentClient)) {
                MalfixProfiler.recordClientTick(profTickStart);
                return;
            }
            tickAutoAuth();
            pollDebugKeys(currentClient);
            updateAutomationPerformanceMode();
            if (antiAfkRunning) {
                tickAntiAfk();
                return;
            }

            // Step 23.36: native storage must own the critical shulker/ec flow.
            // In 23.33 a loaded JS script stopped ShulkerController every tick; the
            // controller opened slot 0 and was immediately killed before WAIT_OPEN, so
            // scan/PUT never reached slots 1-2 and /ec fallback never ran.
            if (shulkerController.isRunning()) {
                shulkerController.tick();
                return;
            }
            tickNativeShulkerPauseResume();
            if (tickAutoShulkerRestack()) {
                return;
            }

            fireScriptTickLowLagAware();
            if (tickSpamKickRecovery()) {
                return;
            }
            if (tickLegacyScriptResume()) {
                return;
            }
            if (legacyScriptPauseActive) {
                if (tickLegacyScriptPauseWatchdog()) {
                    // Watchdog released a broken legacy script pause. Continue this tick.
                } else {
                    return;
                }
            }
            tickAntiAfk();
            if (antiAfkRunning) {
                return;
            }
            tickAutoParser();
            tickParserDebugArm();
            tickSafeAutoRun();
            tickLimitedLoop();
            tickOneCycle();
            tickRefreshCycle();
            tickControlledBuyClick();
            tickSellerLoop();
            tickSellerReturnToAuction();
            tickCycleFull();
            tickCycleFullLoop();
            tickFullAutoTimed();
            tickSellOnlyTimed();
            tickObserver();

            // Step 5: the dangerous automatic state loop is intentionally not started by .mab on.
            // It will be re-enabled later only after BuyExecutor and watchdog rules are designed.
            if (!runtime.controller().context().enabled) {
                return;
            }

            runtime.tick();
            logCompactStateSometimes();
        } catch (Throwable throwable) {
            long now = System.currentTimeMillis();
            if (now - lastTickErrorLogAt > 1000L) {
                lastTickErrorLogAt = now;
                System.out.println("[MAB] Tick error:");
                throwable.printStackTrace(System.out);
                chat.send("tick error: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            }
        } finally {
            MalfixProfiler.recordClientTick(profTickStart);
        }
    }

    public void sendTelegramCompat(String text) {
        if (telegramNotifier != null) {
            telegramNotifier.send(text);
        }
    }

    public boolean handleClientChatMessage(String message) {
        if (message == null) {
            return false;
        }

        String trimmed = message.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (lower.startsWith(".potatomode") || lower.startsWith("/potatomode")
                || lower.startsWith(".potato") || lower.startsWith("/potato")) {
            try {
                handlePotatoModeCommand(parseArguments(trimmed));
            } catch (Throwable throwable) {
                System.out.println("[MAB] Potato mode command error:");
                throwable.printStackTrace(System.out);
                chat.send("potato command error: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            }
            return true;
        }

        if (lower.startsWith(".nbt") || lower.startsWith("/nbt")) {
            try {
                dumpHandNbtCommand(parseArguments(trimmed));
            } catch (Throwable throwable) {
                System.out.println("[MAB] NBT command error:");
                throwable.printStackTrace(System.out);
                chat.send("nbt command error: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            }
            return true;
        }

        if (lower.startsWith(".cloud") || lower.startsWith("/cloud")) {
            try {
                handleCloudCommand(parseArguments(trimmed));
            } catch (Throwable throwable) {
                System.out.println("[MAB] Cloud command error:");
                throwable.printStackTrace(System.out);
                chat.send("cloud command error: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            }
            return true;
        }

        if (lower.startsWith(".script") || lower.startsWith("/script")) {
            try {
                handleDotScriptCommand(parseArguments(trimmed));
            } catch (Throwable throwable) {
                System.out.println("[MAB] Script command error:");
                throwable.printStackTrace(System.out);
                chat.send("script command error: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            }
            return true;
        }

        if (lower.startsWith(".tg") || lower.startsWith("/tg") || lower.startsWith(".telegram") || lower.startsWith("/telegram")) {
            try {
                handleTelegramCommand(parseArguments(trimmed));
            } catch (Throwable throwable) {
                System.out.println("[MAB] Telegram command error:");
                throwable.printStackTrace(System.out);
                chat.send("telegram command error: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            }
            return true;
        }

        if (!lower.startsWith(".mab") && !lower.startsWith("/mab")) {
            return false;
        }

        try {
            handleCommand(trimmed);
        } catch (Throwable throwable) {
            System.out.println("[MAB] Command error:");
            throwable.printStackTrace(System.out);
            chat.send("command error: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }

        return true;
    }

    private void updateAutomationPerformanceMode() {
        boolean active = isHeavyAutomationActive();
        AutomationPerformance.setAutomationActive(active);
        MovementLock.tick(client, active);

        // Step 22.42: do NOT auto-enable PotatoMode during autobuy/selling.
        // The user will enable .potatomode manually only when needed. Keep the
        // automation low-lag flag so heavy visual helpers can still pause, but do not
        // touch world rendering by force.
        autoPotatoApplied = false;
        autoPotatoPreviousState = PotatoMode.isEnabled();
    }

    private boolean isHeavyAutomationActive() {
        return fullAutoTimedEnabled
                || sellOnlyTimedEnabled
                || cycleFullEnabled
                || cycleFullLoopEnabled
                || safeAutoRunEnabled
                || sellerLoopEnabled
                || sellerCycleEnabled
                || sellerReturnToAuctionPending
                || sellerUnstackPrepareActive
                || parserRunning
                || shulkerController.isRunning()
                || limitedLoop.isRunning()
                || oneCycle.isPending()
                || controlledBuyClick.isPending()
                || refreshCycle.isPending();
    }

    private void fireScriptTickLowLagAware() {
        long now = System.currentTimeMillis();

        // If the native shulker task is running, do not tick legacy JS at the same
        // time. The old shalk.js closes/opens handled screens and can fight the
        // native storage controller tick-for-tick.
        if (shulkerController.isRunning()) {
            nextLowLagScriptTickAtMs = now + 250L;
            return;
        }

        // Legacy JS shulker scripts are allowed to drive only their own storage
        // containers. If the player opens any other GUI, do not tick the script;
        // otherwise old code that repeatedly calls closeCurrentScreen() can instantly
        // close every menu on every tick.
        if (isUserMenuOpenForLegacyScriptTick()) {
            nextLowLagScriptTickAtMs = now + 250L;
            return;
        }

        if (!AutomationPerformance.isLowLagActive()) {
            nextLowLagScriptTickAtMs = now;
            scriptManager.firePlayerTick();
            return;
        }

        // During selling the JS bridge is one of the biggest main-thread costs.
        // Old SpookyBuy did not run Nashorn handlers on every client tick while selling.
        // 200ms is still responsive enough for shulker/full-inventory helpers, but avoids
        // 20 calls per second per launcher while /ah sell commands are being sent.
        if (now < nextLowLagScriptTickAtMs) {
            return;
        }

        nextLowLagScriptTickAtMs = now + 200L;
        scriptManager.firePlayerTick();
    }

    private boolean isUserMenuOpenForLegacyScriptTick() {
        try {
            if (client == null || client.currentScreen == null) {
                return false;
            }
            Screen screen = client.currentScreen;
            if (screen instanceof ChatScreen) {
                return false;
            }
            ScriptCompatBridge compat = new ScriptCompatBridge(client);
            if (compat.isAuctionScreenOpen()) {
                return !compat.isInventoryFullEnough(0);
            }
            return !isLegacyScriptOwnedScreen(screen);
        } catch (Throwable throwable) {
            return false;
        }
    }

    private boolean isLegacyScriptOwnedScreen(Screen screen) {
        try {
            if (screen == null || screen instanceof ChatScreen) {
                return false;
            }
            if (!(screen instanceof GenericContainerScreen)) {
                return false;
            }
            String title = "";
            try {
                title = screen.getTitle() == null ? "" : screen.getTitle().getString();
            } catch (Throwable ignored) {
            }
            String lower = title.toLowerCase(Locale.ROOT).replace('ё', 'е');
            if (lower.contains("shulker") || lower.contains("шалкер")) {
                return true;
            }
            if (lower.contains("ender") || lower.contains("эндер") || lower.contains("эндер-сундук") || lower.contains("ender chest")) {
                return ScriptCompatBridge.isScriptEcOwnerActive();
            }
            return false;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private boolean detectAndHandleAfkBlockedMessage(String message) {
        if (!isAfkBlockedServerMessage(message)) {
            return false;
        }

        antiAfkLastChatTriggerMessage = cleanChatMessage(message);

        if (!config.isAntiAfkEnabled()) {
            antiAfkLastReason = "afk_block_detected_but_disabled";
            System.out.println("[MAB ANTI-AFK] AFK-block message ignored because anti-afk is disabled: " + antiAfkLastChatTriggerMessage);
            return true;
        }

        long now = System.currentTimeMillis();
        if (antiAfkRunning) {
            antiAfkLastReason = "afk_block_detected_already_running";
            return true;
        }

        if (spamKickRecovering) {
            antiAfkLastReason = "afk_block_detected_spam_rejoin_active";
            return true;
        }

        if (antiAfkLastChatTriggerAtMs > 0L
                && now - antiAfkLastChatTriggerAtMs < MalfixTimings.ANTI_AFK_CHAT_TRIGGER_COOLDOWN_MS) {
            antiAfkLastReason = "afk_block_trigger_cooldown";
            return true;
        }

        antiAfkLastChatTriggerAtMs = now;
        startAntiAfkReconnect("chat_afk_blocked");
        System.out.println("[MAB ANTI-AFK] AFK-block detected, rejoin started: " + antiAfkLastChatTriggerMessage);
        chat.send("anti-afk: обнаружен режим AFK по сообщению сервера, запускаю перезаход. " + buildAntiAfkCompact());
        return true;
    }

    private boolean isAfkBlockedServerMessage(String message) {
        String normalized = normalizeAfkBlockedMessage(message);
        if (normalized.isEmpty()) {
            return false;
        }

        if (normalized.contains("данная команда недоступна в режиме afk")
                || normalized.contains("команда недоступна в режиме afk")
                || normalized.contains("недопустимо нажимать в режиме afk")
                || normalized.contains("нельзя нажимать в режиме afk")
                || normalized.contains("недоступно в режиме afk")
                || normalized.contains("недоступна в режиме афк")
                || normalized.contains("недопустимо нажимать в режиме афк")) {
            return true;
        }

        boolean mentionsAfkMode = normalized.contains("режиме afk")
                || normalized.contains("режим afk")
                || normalized.contains("режиме афк")
                || normalized.contains("режим афк");

        if (!mentionsAfkMode) {
            return false;
        }

        return normalized.contains("недоступ")
                || normalized.contains("недопуст")
                || normalized.contains("нельзя")
                || normalized.contains("запрещ")
                || normalized.contains("команд")
                || normalized.contains("нажим");
    }

    private String normalizeAfkBlockedMessage(String message) {
        return cleanChatMessage(message).replace('ё', 'е').toLowerCase(Locale.ROOT);
    }

    private String cleanChatMessage(String message) {
        if (message == null) {
            return "";
        }
        return message.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }

    private boolean handleNativeShulkerStorageMessage(String message) {
        String normalized = cleanChatMessage(message).replace('ё', 'е').toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return false;
        }

        boolean storageTrigger = normalized.contains("у вас полный инвентарь")
                || normalized.contains("инвентарь полон")
                || normalized.contains("полный инвентарь")
                || normalized.contains("предмет перенесен в хранилище");
        boolean storageBlock = normalized.contains("/ah rent")
                || normalized.contains("не удалось выстав")
                || normalized.contains("освободите хранилище")
                || normalized.contains("лимит продаж")
                || normalized.contains("нет слотов продажи");

        if (!storageTrigger && !storageBlock) {
            return false;
        }

        // Step 23.34: native Never-style storage owns full-inventory messages even
        // when shalk.js is loaded, because the legacy script cannot reliably detect
        // generic shulker/ender screens on 1.21.4.

        try {
            boolean started = shulkerController.onServerChatMessage(message, shouldRestoreAuctionAfterShulker());
            if (started) {
                // Do not call setLegacyScriptPause(false) here: that method may try to
                // reopen /ah immediately. Native storage owns the flow now and will
                // restore /ah after the shulker move if needed.
                legacyScriptPauseActive = false;
                legacyScriptPauseReason = "native_shulker_started";
                legacyScriptPauseChangedAtMs = System.currentTimeMillis();
                legacyScriptResumeWaitingAuction = false;
                legacyScriptResumeAhSent = false;
                legacyScriptResumeReason = "none";
                System.out.println("[MAB] native shulker storage started from message: " + shulkerController.compact());
                return true;
            }
        } catch (Throwable throwable) {
            System.out.println("[MAB] native shulker storage message handler failed: "
                    + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }

        // Auction-rent/storage-block messages are consumed by the native controller
        // even when it only updates its cooldown. Full-inventory messages can still
        // fall back to legacy JS if native did not start, e.g. no hotbar shulker but
        // the old script wants to try /ec.
        return storageBlock;
    }

    private boolean shouldFireServerMessageToScripts(String message) {
        if (!AutomationPerformance.isLowLagActive()) {
            return true;
        }

        if (message == null) {
            return true;
        }

        String lower = message.toLowerCase(Locale.ROOT);

        // Keep the messages that scripts really need for safety/state transitions.
        if (lower.contains("/ah rent")
                || lower.contains("не удалось выстав")
                || lower.contains("освободите хранилище")
                || lower.contains("инвентарь")
                || lower.contains("inventory")
                || lower.contains("спам")
                || lower.contains("spam")
                || lower.contains("kick")
                || lower.contains("кик")) {
            return true;
        }

        // Normal sell confirmations can arrive for every /ah sell. Calling Nashorn
        // handlers for each one causes FPS drops with multiple launchers, and the native
        // seller already tracks rent/no-item conditions itself.
        if (sellerLoopEnabled || sellerCycleEnabled || sellOnlyTimedEnabled || cycleFullEnabled || cycleFullLoopEnabled) {
            if (lower.contains("вы выстав")
                    || lower.contains("вы успешно выстав")
                    || lower.contains("предмет выстав")
                    || lower.contains("на продаж")
                    || lower.contains(" выставлен")) {
                return false;
            }
        }

        return true;
    }


    private void detectAndScheduleAutoAuth(String message) {
        try {
            if (message == null || runtimeSettings == null || !runtimeSettings.isAuthEnabled()) {
                return;
            }
            String password = runtimeSettings.getAuthPassword();
            if (password == null || password.trim().isEmpty()) {
                if (looksLikeAuthRequest(message)) {
                    long now = System.currentTimeMillis();
                    if (now - autoAuthLastWarnAtMs > 10_000L) {
                        autoAuthLastWarnAtMs = now;
                        autoAuthLastReason = "auth_prompt_but_no_password";
                        chat.send("auth: server asks for password, set auth.password in runtime.properties");
                    }
                }
                return;
            }

            String normalized = cleanChatMessage(message).replace('ё', 'е').toLowerCase(Locale.ROOT);
            String template = null;
            String reason = "none";

            if (looksLikeRegisterRequestNormalized(normalized)) {
                template = runtimeSettings.getAuthRegisterCommand();
                reason = "register_prompt";
            } else if (looksLikeLoginRequestNormalized(normalized)) {
                template = runtimeSettings.getAuthLoginCommand();
                reason = "login_prompt";
            }

            if (template == null || template.trim().isEmpty()) {
                return;
            }

            long now = System.currentTimeMillis();
            if (now - autoAuthLastSentAtMs < runtimeSettings.getAuthCooldownMs()) {
                autoAuthLastReason = "cooldown:" + reason;
                return;
            }

            autoAuthPendingCommand = template.replace("{password}", password.trim()).trim();
            autoAuthSendAtMs = now + runtimeSettings.getAuthDelayMs();
            autoAuthLastReason = "scheduled:" + reason;
        } catch (Throwable throwable) {
            autoAuthLastReason = "error:" + throwable.getClass().getSimpleName();
        }
    }

    private boolean looksLikeAuthRequest(String message) {
        if (message == null) {
            return false;
        }
        String normalized = cleanChatMessage(message).replace('ё', 'е').toLowerCase(Locale.ROOT);
        return looksLikeLoginRequestNormalized(normalized) || looksLikeRegisterRequestNormalized(normalized);
    }

    private boolean looksLikeLoginRequestNormalized(String normalized) {
        if (normalized == null) {
            return false;
        }
        return (normalized.contains("/login") || normalized.contains("/l ") || normalized.contains(" login")
                || normalized.contains("войд") || normalized.contains("авториз") || normalized.contains("логин")
                || normalized.contains("парол") || normalized.contains("password"))
                && !looksLikeRegisterRequestNormalized(normalized);
    }

    private boolean looksLikeRegisterRequestNormalized(String normalized) {
        if (normalized == null) {
            return false;
        }
        return normalized.contains("/register") || normalized.contains("/reg") || normalized.contains("зарегистр")
                || normalized.contains("регистрац") || normalized.contains("register");
    }

    private void tickAutoAuth() {
        try {
            if (autoAuthPendingCommand == null || autoAuthPendingCommand.trim().isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now < autoAuthSendAtMs) {
                return;
            }
            String command = autoAuthPendingCommand.trim();
            autoAuthPendingCommand = "";
            autoAuthSendAtMs = 0L;
            autoAuthLastSentAtMs = now;
            autoAuthLastReason = "sent:" + maskAuthCommand(command);
            sendPlayerCommand(command);
        } catch (Throwable throwable) {
            autoAuthLastReason = "send_error:" + throwable.getClass().getSimpleName();
        }
    }

    private String maskAuthCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return "none";
        }
        String c = command.trim();
        int sp = c.indexOf(' ');
        if (sp < 0) {
            return c;
        }
        return c.substring(0, sp) + " ***";
    }

    public void handleServerChatMessage(String message) {
        if (detectAndHandleAfkBlockedMessage(message)) {
            return;
        }

        detectAndScheduleAutoAuth(message);

        boolean nativeShulkerHandled = handleNativeShulkerStorageMessage(message);

        if (!nativeShulkerHandled && shouldFireServerMessageToScripts(message)) {
            scriptManager.fireServerMessage(message);
        }

        if (detectAndStartSpamKickRecovery(message)) {
            return;
        }

        // Native shulker storage is handled above before legacy scripts. If it did not
        // start, legacy scripts can still receive the message as a fallback.

        if (detectAndHandleSellLimitMessage(message)) {
            return;
        }

        detectAndHandleSellerCommandResponse(message);

        if (detectAndNotifyTelegramSoldMessage(message)) {
            return;
        }

        BuyResult result = buyResultDetector.detect(message);
        if (result == null || !result.isDetected()) {
            return;
        }

        boolean duplicate = chatResultDeduplicator.isDuplicate(message, result.getType().name());
        lastServerBuyMessageDuplicate = duplicate;
        lastServerBuyMessage = message == null ? "" : message;
        lastServerBuyMessageAt = System.currentTimeMillis();

        if (duplicate) {
            System.out.println("[MAB] duplicate server buy result ignored: " + result.getType()
                    + ", message=" + lastServerBuyMessage);
            return;
        }

        lastBuyResult = result;
        lastBuyStatus = result.getType().name();
        boolean manualPendingBefore = controlledBuyClick.isPending();
        boolean oneCycleBuyPendingBefore = oneCycle.isWaitingBuyResult();
        boolean limitedLoopBuyPendingBefore = limitedLoop.isWaitingBuyResult();

        String source = detectBuyResultSource(manualPendingBefore, oneCycleBuyPendingBefore, limitedLoopBuyPendingBefore);
        lastBuyResultSource = source;

        ControlledBuyClickResult completed = controlledBuyClick.onBuyResult(result);

        chat.send("server buy result: " + result.getType()
                + ", reason=" + result.getReason()
                + ", source=" + source
                + ", manualPending=" + manualPendingBefore
                + ", oneCyclePending=" + oneCycleBuyPendingBefore
                + ", limitedLoopPending=" + limitedLoopBuyPendingBefore);

        handleGlobalNoMoneyResult(result, source);

        if (completed != null) {
            lastControlledBuyClickResult = completed;
            chat.send("manual buy done by chat: " + completed.compact());

            if (completed.getCandidate() != null) {
                chat.send("manual buy candidate: " + formatCandidateCompact(completed.getCandidate()));
            }
        }

        OneCycleResult oneCycleCompleted = oneCycle.onBuyResult(result);
        if (oneCycleCompleted != null) {
            lastOneCycleResult = oneCycleCompleted;
            chat.send("one-cycle done by chat: " + oneCycleCompleted.compact());

            if (oneCycleCompleted.getCandidate() != null) {
                chat.send("one-cycle candidate: " + formatCandidateCompact(oneCycleCompleted.getCandidate()));
            }
        }

        AutoLoopResult loopCompleted = limitedLoop.onBuyResult(result);
        if (loopCompleted != null) {
            lastAutoLoopResult = loopCompleted;
            chat.send("limited-loop event by chat: " + loopCompleted.compact());

            if (loopCompleted.getCandidate() != null) {
                chat.send("limited-loop candidate: " + formatCandidateCompact(loopCompleted.getCandidate()));
            }

            handleSafeAutoRunLoopEvent(loopCompleted);
            handleCycleFullBuyLoopEvent(loopCompleted);
        }

        rememberTelegramBuyCandidate(firstCandidate(completed, oneCycleCompleted, loopCompleted));
        notifyTelegramBuyResult(result, source, completed, oneCycleCompleted, loopCompleted);

        if (result.isHardStop() && (manualPendingBefore || oneCycleBuyPendingBefore || limitedLoopBuyPendingBefore)) {
            chat.send("hard-stop result detected: " + result.getType() + ". Safe-auto must pause here.");
        }
    }


    private void notifyTelegramBuyResult(
            BuyResult result,
            String source,
            ControlledBuyClickResult completed,
            OneCycleResult oneCycleCompleted,
            AutoLoopResult loopCompleted
    ) {
        try {
            if (telegramNotifier == null || result == null || !result.isDetected()) {
                return;
            }

            // Telegram must report only real completed deals. Do not send NO_MONEY,
            // ALREADY_SOLD, hard-stop, or any internal source/debug information.
            boolean success = result.getType() == ru.malfix.autobuy.buy.BuyResultType.BUY_SUCCESS;
            if (!success) {
                return;
            }

            ScanCandidate candidate = firstCandidate(completed, oneCycleCompleted, loopCompleted);
            long balance = readCurrentBalanceSafe();
            telegramNotifier.send(buildTelegramBuySuccessMessage(candidate, balance));
        } catch (Throwable throwable) {
            System.out.println("[MAB] telegram buy notify failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    private ScanCandidate firstCandidate(ControlledBuyClickResult completed, OneCycleResult oneCycleCompleted, AutoLoopResult loopCompleted) {
        if (completed != null && completed.getCandidate() != null) {
            return completed.getCandidate();
        }
        if (oneCycleCompleted != null && oneCycleCompleted.getCandidate() != null) {
            return oneCycleCompleted.getCandidate();
        }
        if (oneCycleCompleted != null && oneCycleCompleted.getBuyClickResult() != null && oneCycleCompleted.getBuyClickResult().getCandidate() != null) {
            return oneCycleCompleted.getBuyClickResult().getCandidate();
        }
        if (loopCompleted != null && loopCompleted.getCandidate() != null) {
            return loopCompleted.getCandidate();
        }
        if (loopCompleted != null && loopCompleted.getBuyClickResult() != null && loopCompleted.getBuyClickResult().getCandidate() != null) {
            return loopCompleted.getBuyClickResult().getCandidate();
        }
        if (lastControlledBuyClickResult != null && lastControlledBuyClickResult.getCandidate() != null) {
            return lastControlledBuyClickResult.getCandidate();
        }
        if (lastOneCycleResult != null && lastOneCycleResult.getCandidate() != null) {
            return lastOneCycleResult.getCandidate();
        }
        if (lastAutoLoopResult != null && lastAutoLoopResult.getCandidate() != null) {
            return lastAutoLoopResult.getCandidate();
        }
        if (lastTelegramBuyCandidate != null && System.currentTimeMillis() - lastTelegramBuyCandidateAtMs < 30_000L) {
            return lastTelegramBuyCandidate;
        }
        return null;
    }

    private void rememberTelegramBuyCandidate(ScanCandidate candidate) {
        if (candidate == null) {
            return;
        }
        try {
            if (candidate.getAuctionSlot() == null) {
                return;
            }
        } catch (Throwable ignored) {
            return;
        }
        lastTelegramBuyCandidate = candidate;
        lastTelegramBuyCandidateAtMs = System.currentTimeMillis();
    }

    private String buildTelegramBuySuccessMessage(ScanCandidate candidate, long balance) {
        StringBuilder b = new StringBuilder();
        b.append("✅ Купил предмет");
        if (candidate != null) {
            b.append("\nПредмет: ").append(telegramCandidateName(candidate));
            b.append("\nКол-во: ").append(telegramCandidateCount(candidate));
            if (candidate.getPrice() != null) {
                b.append("\nЦена: ").append(formatMoneyOrUnknown(candidate.getPrice().getTotalPrice()));
                b.append("\nЦена за шт: ").append(formatMoneyOrUnknown(candidate.getPrice().getUnitPrice()));
            }
        } else {
            String fallbackName = cleanTelegramText(lastBoughtItem);
            if (fallbackName.isEmpty() || "none".equalsIgnoreCase(fallbackName) || "unknown".equalsIgnoreCase(fallbackName)) {
                fallbackName = "unknown";
            }
            b.append("\nПредмет: ").append(fallbackName);
        }
        b.append("\nБаланс: ").append(formatMoneyOrUnknown(balance));
        return b.toString();
    }

    private boolean detectAndNotifyTelegramSoldMessage(String message) {
        try {
            if (message == null) {
                return false;
            }
            String clean = cleanChatMessage(message);
            String lower = clean.replace('ё', 'е').toLowerCase(Locale.ROOT);
            if (!looksLikeActualSoldMessage(lower)) {
                return false;
            }

            String fingerprint = lower.replaceAll("\\s+", " ").trim();
            long now = System.currentTimeMillis();
            if (fingerprint.equals(lastTelegramSoldFingerprint) && now - lastTelegramSoldAtMs < 3500L) {
                return true;
            }
            lastTelegramSoldFingerprint = fingerprint;
            lastTelegramSoldAtMs = now;

            if (telegramNotifier != null) {
                telegramNotifier.send(buildTelegramSoldMessage(clean, readCurrentBalanceSafe()));
            }
            return true;
        } catch (Throwable throwable) {
            System.out.println("[MAB] telegram sold notify failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            return false;
        }
    }

    private boolean looksLikeActualSoldMessage(String lower) {
        if (lower == null || lower.trim().isEmpty()) {
            return false;
        }

        // Explicitly ignore listing/placing-on-auction confirmations. They are not sales.
        if (lower.contains("выстав")
                || lower.contains("на продаж")
                || lower.contains("продаже")
                || lower.contains("не удалось")
                || lower.contains("ошибка")
                || lower.contains("нельзя")
                || lower.contains("не можете")) {
            return false;
        }

        return lower.contains("вы продали")
                || lower.contains("успешно продали")
                || lower.contains("продажа заверш")
                || lower.contains("предмет продан")
                || lower.contains("товар продан")
                || lower.contains("лот продан")
                || lower.contains("предмет продано")
                || lower.contains("товар продано")
                || lower.contains("лот продано")
                || lower.contains("был продан")
                || lower.contains("была продана")
                || lower.contains("было продано")
                || lower.contains("были проданы")
                || lower.contains("успешно продан")
                || lower.contains("успешно купили ваш")
                || lower.contains("купил ваш")
                || lower.contains("купили ваш")
                || lower.contains("у вас купили")
                || lower.contains("у тебя купили")
                || lower.contains("приобрел ваш")
                || lower.contains("приобрели ваш")
                || lower.contains("ваш товар куплен")
                || lower.contains("ваш лот куплен")
                || lower.contains("ваш предмет куплен")
                || lower.contains("с аукциона купили")
                || lower.contains("куплен с аукциона")
                || lower.contains("продан с аукциона")
                || (lower.contains("продан") && (lower.contains("монет") || lower.contains("$") || lower.contains("баланс") || lower.contains("за ")))
                || (lower.contains("получ") && (lower.contains("монет") || lower.contains("$") || lower.contains("баланс"))
                    && (lower.contains("продаж") || lower.contains("аукцион") || lower.contains("лот") || lower.contains("товар") || lower.contains("предмет")))
                || (lower.contains("ваш") && lower.contains("предмет") && (lower.contains("купили") || lower.contains("куплен") || lower.contains("продан") || lower.contains("продано")))
                || (lower.contains("ваш") && lower.contains("товар") && (lower.contains("купили") || lower.contains("куплен") || lower.contains("продан") || lower.contains("продано")))
                || (lower.contains("ваш") && lower.contains("лот") && (lower.contains("купили") || lower.contains("куплен") || lower.contains("продан") || lower.contains("продано")));
    }

    private String buildTelegramSoldMessage(String rawMessage, long balance) {
        SellerResult result = lastSellerResult;
        String itemName = parseSoldItemName(rawMessage);
        if (itemName.isEmpty()) {
            itemName = sellerTelegramItemName(result);
        }

        int count = parseSoldCount(rawMessage);
        if (count <= 0) {
            count = sellerTelegramCount(result);
        }

        long total = parseMoneyFromText(rawMessage);
        String totalText = total > 0L ? formatMoneyOrUnknown(total) : sellerTelegramTotal(result);
        String unitText;
        if (total > 0L && count > 0) {
            unitText = formatMoneyOrUnknown(total / Math.max(1, count));
        } else {
            unitText = sellerTelegramUnit(result);
        }

        StringBuilder b = new StringBuilder();
        b.append("✅ Продал предмет");
        b.append("\nПредмет: ").append(itemName);
        b.append("\nКол-во: ").append(count);
        b.append("\nЦена: ").append(totalText);
        b.append("\nЦена за шт: ").append(unitText);
        b.append("\nБаланс: ").append(formatMoneyOrUnknown(balance));
        return b.toString();
    }

    private String parseSoldItemName(String rawMessage) {
        String clean = cleanTelegramText(rawMessage);
        if (clean.isEmpty()) {
            return "";
        }

        String[] patterns = new String[] {
                "(?iu)у\\s+(?:вас|тебя)\\s+купили\\s+(.+?)\\s+(?:x|х)\\s*\\d{1,4}\\s+за",
                "(?iu)купили\\s+(.+?)\\s+(?:x|х)\\s*\\d{1,4}\\s+за",
                "(?iu)(?:предмет|товар|лот)\\s*[:\\-]\\s*[\\\"'«“]?([^\\\"'»”]+?)[\\\"'»”]?(?:\\s+(?:за|x|х|кол|кол-во|количество|был|была|куплен|купили|продан)|$)",
                "(?iu)[\\\"'«“]([^\\\"'»”]{2,64})[\\\"'»”]"
        };
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(clean);
            if (matcher.find()) {
                String value = cleanTelegramText(matcher.group(1)).trim();
                if (!value.isEmpty() && !value.matches("^[0-9\\s.,]+$")) {
                    return value;
                }
            }
        }
        return "";
    }

    private int parseSoldCount(String rawMessage) {
        String clean = cleanTelegramText(rawMessage);
        if (clean.isEmpty()) {
            return -1;
        }
        String[] patterns = new String[] {
                "(?iu)(?:кол-во|количество|кол\\.)\\s*[:=]?\\s*(\\d{1,3})",
                "(?iu)(?:x|х)\\s*(\\d{1,3})",
                "(?iu)(\\d{1,3})\\s*(?:шт|штук)"
        };
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern).matcher(clean);
            if (matcher.find()) {
                try {
                    return Math.max(1, Integer.parseInt(matcher.group(1)));
                } catch (Throwable ignored) {
                }
            }
        }
        return -1;
    }

    private long parseMoneyFromText(String rawMessage) {
        String clean = cleanTelegramText(rawMessage);
        if (clean.isEmpty()) {
            return -1L;
        }

        long best = -1L;
        Matcher matcher = Pattern.compile("(?iu)(?:за|цена|стоимость|сумма|получено|монет|\\$)\\s*[:=]?\\s*([0-9][0-9\\s.,]*)").matcher(clean);
        while (matcher.find()) {
            long value = parseLongDigits(matcher.group(1));
            if (value > best) {
                best = value;
            }
        }
        if (best > 0L) {
            return best;
        }

        matcher = Pattern.compile("([0-9][0-9\\s.,]{2,})").matcher(clean);
        while (matcher.find()) {
            long value = parseLongDigits(matcher.group(1));
            if (value > best) {
                best = value;
            }
        }
        return best;
    }

    private long parseLongDigits(String value) {
        if (value == null) {
            return -1L;
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return -1L;
        }
        try {
            return Long.parseLong(digits);
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private String handleTelegramBotCommand(String text) {
        String lower = text == null ? "" : text.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (lower.isEmpty()) {
            return "";
        }
        if ("/ping".equals(lower) || "ping".equals(lower)) {
            return "pong";
        }
        if ("/balance".equals(lower) || "balance".equals(lower) || "/bal".equals(lower) || "bal".equals(lower)
                || "/баланс".equals(lower) || "баланс".equals(lower)) {
            return buildTelegramBalanceMessage();
        }
        if ("/status".equals(lower) || "status".equals(lower) || "/статус".equals(lower) || "статус".equals(lower)) {
            return buildTelegramStatusMessage();
        }
        if ("/last".equals(lower) || "last".equals(lower) || "/последнее".equals(lower)) {
            return "Последняя покупка: " + buildLastBuyCompact()
                    + "\nПоследняя продажа: " + (lastSellerResult == null ? "none" : lastSellerResult.compact())
                    + "\nБаланс: " + formatMoneyOrUnknown(readCurrentBalanceSafe());
        }
        if ("/help".equals(lower) || "help".equals(lower) || "/помощь".equals(lower)) {
            return "Команды: /balance, /status, /last, /ping";
        }
        return "Неизвестная команда. Доступно: /balance, /status, /last, /ping";
    }

    private String buildTelegramBalanceMessage() {
        return "💰 Баланс: " + formatMoneyOrUnknown(readCurrentBalanceSafe());
    }

    private String buildTelegramStatusMessage() {
        return "MalfixAutoBuy online"
                + "\nБаланс: " + formatMoneyOrUnknown(readCurrentBalanceSafe())
                + "\nПоследняя покупка: " + buildLastBuyCompact()
                + "\nSeller: " + buildSellerLoopCompact()
                + "\nTelegram: " + (telegramNotifier == null ? "none" : telegramNotifier.status());
    }

    private long readCurrentBalanceSafe() {
        try {
            if (auctionView == null) {
                return -1L;
            }
            return auctionView.readPlayerBalance();
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private String telegramCandidateName(ScanCandidate candidate) {
        try {
            if (candidate == null) return "unknown";
            if (candidate.getAuctionSlot() != null) {
                String display = cleanTelegramText(candidate.getAuctionSlot().getDisplayName());
                if (!display.isEmpty() && !"unknown".equalsIgnoreCase(display)) {
                    return display;
                }
            }
            if (candidate.getTarget() != null && candidate.getTarget().getLabel() != null && !candidate.getTarget().getLabel().trim().isEmpty()) {
                String label = cleanTelegramText(candidate.getTarget().getLabel());
                if (!label.isEmpty() && !"unknown".equalsIgnoreCase(label)) {
                    return label;
                }
            }
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    private int telegramCandidateCount(ScanCandidate candidate) {
        try {
            if (candidate != null && candidate.getAuctionSlot() != null) {
                return Math.max(1, candidate.getAuctionSlot().getCount());
            }
        } catch (Throwable ignored) {
        }
        return 1;
    }

    private String sellerTelegramItemName(SellerResult result) {
        try {
            if (result != null && result.getTarget() != null && result.getTarget().getLabel() != null && !result.getTarget().getLabel().trim().isEmpty()) {
                return cleanTelegramText(result.getTarget().getLabel());
            }
            if (result != null && result.getItem() != null) {
                return cleanTelegramText(result.getItem().getDisplayName());
            }
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    private int sellerTelegramCount(SellerResult result) {
        try {
            if (result != null && result.getItem() != null) {
                return Math.max(1, result.getItem().getCount());
            }
        } catch (Throwable ignored) {
        }
        return 1;
    }

    private String sellerTelegramTotal(SellerResult result) {
        try {
            if (result != null) {
                return formatMoneyOrUnknown(result.getTotalPrice());
            }
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    private String sellerTelegramUnit(SellerResult result) {
        try {
            if (result != null) {
                return formatMoneyOrUnknown(result.getUnitPrice());
            }
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    private String formatMoneyOrUnknown(long value) {
        if (value < 0L) {
            return "unknown";
        }
        return moneyFormat == null ? String.valueOf(value) : moneyFormat.format(value);
    }

    private String safeText(String value) {
        String s = cleanTelegramText(value);
        return s.isEmpty() ? "unknown" : s;
    }

    private String cleanTelegramText(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }

    private String compactTelegramLine(String value) {
        String s = cleanTelegramText(value).replace('\n', ' ').replace('\r', ' ').trim();
        if (s.length() > 160) {
            return s.substring(0, 160) + "...";
        }
        return s;
    }

    private String detectBuyResultSource(boolean manualPending, boolean oneCyclePending, boolean limitedLoopPending) {
        if (limitedLoopPending) {
            return safeAutoRunEnabled ? "safe-auto" : "limited-loop";
        }

        if (oneCyclePending) {
            return "one-cycle";
        }

        if (manualPending) {
            return "manual";
        }

        return "unbound";
    }

    /**
     * Step 22.27: server-side no-money can arrive after the local buy executor already
     * finished as AUCTION_CHANGED_AFTER_CLICK. In that case the old code saw source=unbound
     * and the timed buy loop continued clicking expensive lots. Treat every real NO_MONEY
     * chat result as a global buy guard.
     */
    private void handleGlobalNoMoneyResult(BuyResult result, String source) {
        if (result == null || result.getType() != ru.malfix.autobuy.buy.BuyResultType.NO_MONEY) {
            return;
        }

        long minPrice = inferRecentBuyCandidateTotalPrice();
        buyBlockedByNoMoney = true;
        buyBlockedNoMoneyAtMs = System.currentTimeMillis();
        buyBlockedNoMoneyMinPrice = minPrice;
        buyBlockedNoMoneyReason = "server_no_money:" + (source == null ? "unknown" : source);
        ControlledBuyClickExecutor.rememberNoMoneyGuard(minPrice);

        if (limitedLoop.isRunning()) {
            limitedLoop.stop("server_no_money_global_guard");
            lastAutoLoopResult = limitedLoop.getLastResult();
        }
        if (oneCycle.isPending()) {
            oneCycle.cancel("server_no_money_global_guard");
            lastOneCycleResult = oneCycle.getLastResult();
        }
        controlledBuyClick.cancel("server_no_money_global_guard");

        if (cycleFullEnabled && "buy_loop".equals(cycleFullPhase)) {
            cycleFullPhase = "storage_open";
            cycleFullNextActionAtMs = System.currentTimeMillis() + MalfixTimings.AUTOSELL_UNSTACK_MS;
            cycleFullStorageTaken = 0;
            cycleFullStorageAttempts = 0;
            cycleFullStorageNoItemChecks = 0;
            cycleFullStorageContinueAfterSell = false;
            cycleFullStorageStatus = "scheduled_after_no_money";
            cycleFullStopReason = "no_money_buy_phase_finished";
            chat.send("cyclefull buy phase stopped by no-money guard; continue storage/seller. minPrice="
                    + (minPrice > 0L ? moneyFormat.format(minPrice) : "unknown"));
        } else if (safeAutoRunEnabled) {
            safeAutoRunEnabled = false;
            safeAutoRunStopReason = "server_no_money_global_guard";
            chat.send("safe-auto stopped by no-money guard.");
        }

        chat.send("buy blocked by no-money guard: minPrice="
                + (minPrice > 0L ? moneyFormat.format(minPrice) : "unknown")
                + ", source=" + (source == null ? "unknown" : source));
    }

    private long inferRecentBuyCandidateTotalPrice() {
        long best = 0L;
        try {
            best = Math.max(best, totalPriceOf(lastAutoLoopResult == null ? null : lastAutoLoopResult.getCandidate()));
        } catch (Throwable ignored) {
        }
        try {
            best = Math.max(best, totalPriceOf(lastOneCycleResult == null ? null : lastOneCycleResult.getCandidate()));
        } catch (Throwable ignored) {
        }
        try {
            best = Math.max(best, totalPriceOf(lastControlledBuyClickResult == null ? null : lastControlledBuyClickResult.getCandidate()));
        } catch (Throwable ignored) {
        }
        return best;
    }

    private long totalPriceOf(ScanCandidate candidate) {
        if (candidate == null || candidate.getPrice() == null) {
            return 0L;
        }
        long total = candidate.getPrice().getTotalPrice();
        return total > 0L ? total : 0L;
    }

    private boolean shouldBlockBuyByNoMoneyGuard(ScanCandidate candidate) {
        if (!buyBlockedByNoMoney) {
            return false;
        }

        long guardAge = Math.max(0L, System.currentTimeMillis() - buyBlockedNoMoneyAtMs);
        if (guardAge > 120_000L) {
            buyBlockedByNoMoney = false;
            buyBlockedNoMoneyMinPrice = 0L;
            buyBlockedNoMoneyReason = "expired";
            return false;
        }

        long candidateTotal = totalPriceOf(candidate);
        if (buyBlockedNoMoneyMinPrice > 0L && candidateTotal > 0L && candidateTotal < buyBlockedNoMoneyMinPrice) {
            return false;
        }

        return true;
    }

    private void handleCommand(String raw) {
        String[] parts = parseArguments(raw);
        String command = parts.length >= 2 ? parts[1].toLowerCase(Locale.ROOT) : "help";

        if ("help".equals(command) || "?".equals(command)) {
            sendHelp();
            return;
        }

        if ("on".equals(command) || "enable".equals(command) || "observer".equals(command)) {
            runtime.disable();
            observerEnabled = true;
            chat.send("observer enabled. Unlimited full-auto is disabled in Step 13.");
            chat.send("keys: бинды настраиваются в GUI: RShift + выбранная клавиша");
            return;
        }

        if ("off".equals(command) || "disable".equals(command)) {
            observerEnabled = false;
            runtime.disable();
            stopSafeAutoRun("disabled");
            stopCycleFullLoop("disabled");
            stopCycleFull("disabled");
            stopFullAutoTimed("disabled");
            stopAutoParser("disabled");
            antiAfkRunning = false;
            antiAfkPhase = "idle";
            stopSpamKickRecovery("disabled");
            setLegacyScriptPause(false, "disabled");
            shulkerController.stop("disabled");
            sellerCycleEnabled = false;
            sellerCycleLastReason = "disabled";
            stopSellerLoop("disabled");
            sellerReturnToAuctionPending = false;
            limitedLoop.stop("disabled");
            oneCycle.cancel("disabled");
            chat.send("disabled. Observer, one-cycle, limited-loop, seller-loop and safe-auto are off.");
            return;
        }

        if ("auto".equals(command) || "start".equals(command) || "full".equals(command) || "autorun".equals(command)) {
            startSafeAutoRun(parts);
            return;
        }

        if ("gui".equals(command) || "menu".equals(command)) {
            openTargetsGui();
            return;
        }

        if ("binds".equals(command) || "keybinds".equals(command) || "keys".equals(command)) {
            openKeybindGui(null);
            return;
        }

        if ("parser".equals(command) || "parsergui".equals(command) || "parsegui".equals(command)) {
            openParserGui(null);
            return;
        }

        if ("parseall".equals(command) || "autoparse".equals(command) || "autosetup".equals(command)) {
            if (parts.length >= 3 && "force".equalsIgnoreCase(parts[2])) {
                startAutoParserForAllForced();
            } else {
                startAutoParserForAll();
            }
            return;
        }

        if ("parse".equals(command) || "parseone".equals(command)) {
            if (parts.length < 3) {
                chat.send("usage: .mab parse \"Label\"");
                return;
            }
            startAutoParserForLabel(parts[2]);
            return;
        }

        if ("parsedebug".equals(command) || "parserdebug".equals(command) || "pdebug".equals(command) || "parsedump".equals(command)) {
            handleParserDebugCommand(parts);
            return;
        }

        if ("parserstop".equals(command) || "parsestop".equals(command)) {
            stopAutoParser("manual_stop");
            chat.send("Парсер остановлен: " + buildParserCompact());
            return;
        }

        if ("antiafk".equals(command) || "anti-afk".equals(command) || "afk".equals(command)) {
            handleAntiAfkCommand(parts);
            return;
        }

        if ("spamrejoin".equals(command) || "spamkick".equals(command) || "kickrejoin".equals(command)) {
            handleSpamKickCommand(parts);
            return;
        }

        if ("shulker".equals(command) || "shalk".equals(command) || "sh".equals(command)) {
            handleShulkerCommand(parts);
            return;
        }

        if ("scripts".equals(command) || "script".equals(command) || "js".equals(command)) {
            handleScriptsCommand(parts);
            return;
        }

        if ("nbt".equals(command) || "handnbt".equals(command) || "dumpnbt".equals(command) || "tag".equals(command)) {
            dumpHandNbtCommand(parts);
            return;
        }

        if ("scriptimport".equals(command) || "importscript".equals(command)) {
            int added = ScriptItemCatalog.mergeInto(config);
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("script targets imported: added=" + added + ", total=" + config.targetCount());
            return;
        }

        if ("scriptreset".equals(command) || "resetscript".equals(command)) {
            ScriptItemCatalog.resetInto(config);
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("script targets reset: total=" + config.targetCount());
            return;
        }

        if ("config".equals(command) || "cfg".equals(command)) {
            sendConfigSummary();
            return;
        }

        if ("runtime".equals(command) || "settings".equals(command) || "external".equals(command)) {
            handleRuntimeSettingsCommand(parts);
            return;
        }

        if ("autorejoin".equals(command) || "rejoin".equals(command)) {
            handleAutoRejoinCommand(parts);
            return;
        }

        if ("cloud".equals(command)) {
            handleCloudCommand(parts);
            return;
        }

        if ("set".equals(command)) {
            handleSetCommand(parts);
            return;
        }

        if ("blacklist".equals(command) || "bl".equals(command)) {
            handleBlacklistCommand(parts);
            return;
        }

        if ("save".equals(command)) {
            saveConfigAndReport();
            return;
        }

        if ("reload".equals(command)) {
            reloadConfigAndReport();
            return;
        }

        if ("targets".equals(command)) {
            listTargets();
            return;
        }

        if ("target".equals(command)) {
            handleTargetCommand(parts);
            return;
        }

        if ("selltest".equals(command) || "sellpreview".equals(command) || "seller".equals(command)) {
            runSellerPreview();
            return;
        }

        if ("unstack".equals(command) || "restack".equals(command) || "rasstack".equals(command) || "расстак".equals(command)) {
            runUnstackCommand(parts);
            return;
        }

        if ("sellreal".equals(command) || "sellnow".equals(command) || "realsell".equals(command)) {
            runSellerRealHandOnly();
            return;
        }

        if ("fullauto".equals(command) || "fa".equals(command) || "onekey".equals(command)) {
            toggleFullAutoOneKey();
            return;
        }

        if ("sellonly".equals(command) || "onlysell".equals(command) || "storageonly".equals(command) || "чистопродажа".equals(command)) {
            handleSellOnlyCommand(parts);
            return;
        }

        if ("potatomode".equals(command) || "potato".equals(command)) {
            handlePotatoModeCommand(parts);
            return;
        }

        if ("storagecycle".equals(command) || "relist".equals(command) || "resellstorage".equals(command)) {
            startStorageRelistCycle(parts);
            return;
        }

        if ("storageslot".equals(command) || "setstorage".equals(command)) {
            setStorageSlotCommand(parts);
            return;
        }

        if ("cyclefullloop".equals(command) || "fullcycleloop".equals(command) || "buyandsellloop".equals(command)) {
            startCycleFullLoop(parts);
            return;
        }

        if ("cyclefull".equals(command) || "fullcycle".equals(command) || "buyandsell".equals(command)) {
            startCycleFull(parts);
            return;
        }

        if ("sellcycle".equals(command) || "sellreturn".equals(command) || "sellandreturn".equals(command)) {
            startSellerCycle(parts);
            return;
        }

        if ("sellloop".equals(command) || "sellerloop".equals(command) || "autosell".equals(command)) {
            startSellerLoop(parts);
            return;
        }

        if ("sellstop".equals(command) || "stopsell".equals(command) || "stopseller".equals(command)) {
            stopCycleFullLoop("seller_manual_stop");
            stopCycleFull("seller_manual_stop");
            sellerCycleEnabled = false;
            sellerCycleLastReason = "manual_stop";
            stopSellerLoop("manual_stop");
            chat.send("seller-loop stopped: " + buildSellerLoopCompact());
            return;
        }

        if ("loop".equals(command) || "limited".equals(command) || "safe".equals(command)) {
            startLimitedLoop(parts);
            return;
        }

        if ("stop".equals(command) || "stoploop".equals(command) || "stopauto".equals(command)) {
            stopSafeAutoRun("manual_stop");
            stopCycleFullLoop("manual_stop");
            stopCycleFull("manual_stop");
            stopFullAutoTimed("manual_stop");
            stopSellOnlyTimed("manual_stop");
            stopAutoParser("manual_stop");
            antiAfkRunning = false;
            antiAfkPhase = "idle";
            setLegacyScriptPause(false, "manual_stop");
            shulkerController.stop("manual_stop");
            sellerCycleEnabled = false;
            sellerCycleLastReason = "manual_stop";
            stopSellerLoop("manual_stop");
            sellerReturnToAuctionPending = false;
            limitedLoop.stop("manual_stop");
            lastAutoLoopResult = limitedLoop.getLastResult();
            oneCycle.cancel("manual_stop");
            lastOneCycleResult = oneCycle.getLastResult();
            chat.send("stopped: safeAuto=" + safeAutoRunEnabled
                    + ", sellerLoop=" + buildSellerLoopCompact()
                    + ", loop=" + lastAutoLoopResult.compact());
            return;
        }

        if ("cycle".equals(command) || "once".equals(command) || "one".equals(command) || "onecycle".equals(command)) {
            startOneCycle();
            return;
        }

        if ("cancel".equals(command) || "stopcycle".equals(command)) {
            stopSafeAutoRun("manual_cancel");
            stopCycleFullLoop("manual_cancel");
            stopCycleFull("manual_cancel");
            stopFullAutoTimed("manual_cancel");
            stopSellOnlyTimed("manual_cancel");
            stopAutoParser("manual_cancel");
            antiAfkRunning = false;
            antiAfkPhase = "idle";
            setLegacyScriptPause(false, "manual_cancel");
            sellerCycleEnabled = false;
            sellerCycleLastReason = "manual_cancel";
            stopSellerLoop("manual_cancel");
            sellerReturnToAuctionPending = false;
            limitedLoop.stop("manual_cancel");
            lastAutoLoopResult = limitedLoop.getLastResult();
            oneCycle.cancel("manual_cancel");
            lastOneCycleResult = oneCycle.getLastResult();
            chat.send("cancelled: safeAuto=" + safeAutoRunEnabled
                    + ", sellerLoop=" + buildSellerLoopCompact()
                    + ", loop=" + lastAutoLoopResult.compact());
            return;
        }

        if ("prof".equals(command) || "profile".equals(command) || "profiler".equals(command)) {
            handleProfilerCommand(parts);
            return;
        }

        if ("debug".equals(command) || "state".equals(command)) {
            chat.sendDebugBlock("debug:", buildDebugBlock());
            return;
        }

        if ("result".equals(command) || "lastresult".equals(command) || "buyresult".equals(command)) {
            chat.send("last buy result: " + lastBuyResult.compact());
            return;
        }

        if ("scan".equals(command)) {
            runManualScan();
            return;
        }

        if ("refresh".equals(command) || "r".equals(command)) {
            startManualRefreshCycle();
            return;
        }

        if ("buy".equals(command) || "b".equals(command) || "drybuy".equals(command) || "ready".equals(command)) {
            runBuyDryRun();
            return;
        }

        if ("clickbuy".equals(command) || "realbuy".equals(command) || "click".equals(command)) {
            startControlledBuyClick();
            return;
        }

        if ("fp".equals(command) || "fingerprint".equals(command)) {
            runFingerprintCheck();
            return;
        }

        if ("open".equals(command)) {
            auctionView.requestOpenAuction();
            chat.send("requested /ah.");
            return;
        }

        if ("close".equals(command)) {
            auctionView.closeCurrentScreen();
            chat.send("closed current screen.");
            return;
        }

        chat.send("unknown command: " + command + ". Use .mab help");
    }

    private void handlePotatoModeCommand(String[] parts) {
        if (parts == null || parts.length < 2) {
            chat.sendInGame("§dMalfix AutoBuy §7» Использование: .potatomode <on/off/toggle/status>");
            return;
        }

        String value = parts[1] == null ? "" : parts[1].toLowerCase(Locale.ROOT);
        boolean enabled;

        if ("on".equals(value) || "enable".equals(value) || "1".equals(value) || "true".equals(value) || "вкл".equals(value)) {
            PotatoMode.setEnabled(true);
            enabled = true;
        } else if ("off".equals(value) || "disable".equals(value) || "0".equals(value) || "false".equals(value) || "выкл".equals(value)) {
            PotatoMode.setEnabled(false);
            enabled = false;
        } else if ("toggle".equals(value) || "switch".equals(value) || "переключить".equals(value)) {
            enabled = PotatoMode.toggle();
        } else if ("status".equals(value) || "state".equals(value) || "статус".equals(value)) {
            enabled = PotatoMode.isEnabled();
        } else {
            chat.sendInGame("§dMalfix AutoBuy §7» Использование: .potatomode <on/off/toggle/status>");
            return;
        }

        chat.sendInGame("§dMalfix AutoBuy §7» Potato mode " + (enabled ? "§aвключен" : "§cвыключен"));
    }

    private void handleProfilerCommand(String[] parts) {
        String action = parts.length >= 3 ? parts[2].toLowerCase(Locale.ROOT) : "status";

        if ("on".equals(action) || "enable".equals(action)) {
            MalfixProfiler.setEnabled(true);
            MalfixProfiler.setOverlayEnabled(false);
            chat.sendInGame("§dMalfix AutoBuy §7» profiler enabled, overlay off");
            chat.send("profiler enabled: overlay off. Run real scan/sell for 30-60 sec, then .mab prof status.");
            return;
        }

        if ("off".equals(action) || "disable".equals(action)) {
            MalfixProfiler.setEnabled(false);
            MalfixProfiler.setOverlayEnabled(false);
            chat.sendInGame("§dMalfix AutoBuy §7» profiler disabled");
            chat.send("profiler disabled.");
            return;
        }

        if ("overlay".equals(action)) {
            boolean value = parts.length < 4 || !"off".equals(parts[3].toLowerCase(Locale.ROOT));
            MalfixProfiler.setOverlayEnabled(value);
            chat.sendInGame("§dMalfix AutoBuy §7» profiler overlay=" + value);
            chat.send("profiler overlay=" + value);
            return;
        }

        if ("reset".equals(action) || "clear".equals(action)) {
            MalfixProfiler.reset();
            chat.sendInGame("§dMalfix AutoBuy §7» profiler counters reset");
            chat.send("profiler counters reset.");
            return;
        }

        if ("status".equals(action) || "summary".equals(action)) {
            chat.sendInGameBlock("profiler summary:", MalfixProfiler.summary());
            chat.sendDebugBlock("profiler full:", MalfixProfiler.debug());
            return;
        }

        if ("log".equals(action)) {
            chat.sendDebugBlock("profiler full:", MalfixProfiler.debug());
            chat.sendInGame("§dMalfix AutoBuy §7» profiler full block written to latest.log");
            return;
        }

        if ("debug".equals(action) || "full".equals(action)) {
            chat.sendInGameBlock("profiler full:", MalfixProfiler.debug());
            return;
        }

        chat.sendInGame("§dMalfix AutoBuy §7» usage: .mab prof on/off/status/full/log/reset/overlay on|off");
        chat.send("usage: .mab prof on/off/status/full/log/reset/overlay on|off");
    }

    private void sendHelp() {
        chat.send("commands:");
        chat.send(".mab on - enable observer mode only, no auto loop");
        chat.send(".mab off - disable observer and auto loop");
        chat.send(".mab debug - print observer + core/loop/chat state");
        chat.send(".mab prof on/off/status/full/log/reset/overlay on|off - scanner/seller profiler counters");
        chat.send(".mab scan - scan current auction once");
        chat.send(".mab refresh - click refresh, wait fingerprint change, then scan");
        chat.send(".mab buy - dry-run buy validation only, no click");
        chat.send(".mab clickbuy - REAL one-click buy after validation");
        chat.send(".mab result - show last detected server buy result");
        chat.send(".mab cycle - ONE automatic cycle: refresh -> scan -> click -> result -> stop");
        chat.send(".mab auto [maxTotalBuys] - safe auto-run until hard-stop/manual stop");
        chat.send(".mab cyclefull [buyCycles] [buyMax] [sellMax] [sellDelayMs] - buy -> sellcycle -> /ah");
        chat.send(".mab cyclefullloop [loops] [buyCycles] [buyMax] [sellMax] [sellDelayMs] [loopDelayMs]");
        chat.send(".mab fullauto - toggle timed full-auto: pre-sell storage -> 90s buy/refresh -> storage -> sell until /ah rent");
        chat.send(".mab sellonly on/off/status/now - every 30s storage -> sell only, auction closed while waiting");
        chat.send(".potatomode on/off/toggle/status - old SpookyBuy potato mode: disable world/entity/block/particle rendering");
        chat.send(".mab storagecycle [takeMax] [sellMax] [sellDelayMs] - storage relist -> sellcycle -> /ah");
        chat.send(".mab storageslot [slot] - set storage button slot for current session, default 47");
        chat.send("hotkeys: FullAuto default R, SellOnly default V. Change in GUI -> Бинды.");
        chat.send(".mab stop - stop safe-auto/loop/cycle");
        chat.send(".mab loop [cycles] [buys] - test limited-loop only");
        chat.send(".mab gui - open targets/price GUI");
        chat.send(".mab parser - открыть GUI настроек парсера процентов");
        chat.send(".mab parsedebug arm \"Label\" - поставить debug: открой /ah search, dump сам запишется в latest.log");
        chat.send(".mab parsedebug \"Label\" - manual dump текущей страницы, если чат доступен");
        chat.send(".mab parseall - parse targets with GUI 'Парсить' toggle enabled");
        chat.send(".mab parseall force - parse all targets ignoring 'Парсить'");
        chat.send(".mab parse \"Label\" - parse one target price");
        chat.send(".mab antiafk on/off/status/anarchy/test - anti-AFK /hub -> /an<current> -> /ah");
        chat.send(".mab autorejoin status/test/stop - reconnect if kicked/disconnected, then /an + /ah");
        chat.send(".mab runtime status/reload/dir - external anarchy/timings/telegram file");
        chat.send(".cloud save / .cloud load latest / .cloud list - local full config cloud");
        chat.send(".mab shulker status/test/take/scan/stop - shulker restack/take-back via hotbar shulkers");
        chat.send(".mab nbt [offhand] - dump item id/name/NBT from hand to latest.log and malfix_autobuy/nbt_dumps");
        chat.send(".mab config - show config path/current values");
        chat.send(".script reload - reload Never/Malfix scripts; .mab scriptimport / .mab scriptreset - item catalog");
        chat.send(".mab set delay/cycles/buys <value> - update config");
        chat.send(".mab set scan TOP9/TOP18/TOP27/ALL45 - scan rows");
        chat.send(".mab set requirePrice true/false - block targets without price");
        chat.send(".mab set allowUnlimited true/false - allow price=0 targets");
        chat.send(".mab set refreshTimeout <ms> - refresh timeout");
        chat.send(".mab set maxRefreshFails <count> - stop after refresh failures");
        chat.send(".mab blacklist add/remove/list/clear \"word\" - safety blacklist");
        chat.send(".mab selltest - safe seller preview, does not sell");
        chat.send(".mab sellreal - REAL sell from hand/hotbar/main-inv if hotbar has free slot");
        chat.send(".mab sellloop [max] [delayMs] - repeat safe seller, default max=10 delay=200");
        chat.send(".mab unstack status/once - split configured stacks before seller");
        chat.send(".mab sellcycle [max] [delayMs] - sell items, then return to /ah");
        chat.send(".mab sellstop - stop seller-loop");
        chat.send("seller-loop auto-stops on sell limit messages like /ah rent. Use .mab sellloop 0 300 for sell-until-rent mode.");
        chat.send("after sell limit, seller waits briefly and opens /ah again.");
        chat.send(".mab targets - list targets");
        chat.send(".mab target add \"Label\" <maxPrice> \"contains text\"");
        chat.send(".mab target price/enable/disable/remove \"Label\" [value]");
        chat.send(".mab save / .mab reload - save or reload config");
        chat.send(".mab cancel - cancel current loop/cycle");
        chat.send(".mab fp - show current slots fingerprint");
        chat.send(".mab open / .mab close - test auction open/close");
        chat.send("keys: настраиваются в .mab gui -> Бинды. FullAuto и SellOnly = одна клавиша, остальные = RightShift + клавиша.");
    }

    private void pollDebugKeys(MinecraftClient currentClient) {
        if (currentClient == null || currentClient.getWindow() == null) {
            return;
        }

        long handle = currentClient.getWindow().getHandle();
        boolean rightShift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean ctrl = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        boolean debugDown = rightShift && GLFW.glfwGetKey(handle, config.getKeyDebug()) == GLFW.GLFW_PRESS;
        boolean fingerprintDown = rightShift && GLFW.glfwGetKey(handle, config.getKeyFingerprint()) == GLFW.GLFW_PRESS;
        boolean scanDown = rightShift && GLFW.glfwGetKey(handle, config.getKeyScan()) == GLFW.GLFW_PRESS;
        boolean observerDown = rightShift && GLFW.glfwGetKey(handle, config.getKeyObserver()) == GLFW.GLFW_PRESS;
        boolean refreshDown = rightShift && GLFW.glfwGetKey(handle, config.getKeyRefresh()) == GLFW.GLFW_PRESS;
        boolean buyDryRunDown = rightShift && !ctrl && GLFW.glfwGetKey(handle, config.getKeyBuy()) == GLFW.GLFW_PRESS;
        boolean buyClickDown = rightShift && ctrl && GLFW.glfwGetKey(handle, config.getKeyBuy()) == GLFW.GLFW_PRESS;
        boolean oneCycleDown = rightShift && !ctrl && GLFW.glfwGetKey(handle, config.getKeyOneCycle()) == GLFW.GLFW_PRESS;
        boolean limitedLoopDown = rightShift && !ctrl && GLFW.glfwGetKey(handle, config.getKeyLimitedLoop()) == GLFW.GLFW_PRESS;

        boolean guiDown = false;
        int guiKey = config.getKeyGui();
        if (guiKey > 0 && shouldPollOneKeyBind(currentClient.currentScreen)) {
            guiDown = GLFW.glfwGetKey(handle, guiKey) == GLFW.GLFW_PRESS;
        }

        boolean parserDown = false;
        int parserKey = config.getKeyParser();
        if (parserKey > 0 && shouldPollOneKeyBind(currentClient.currentScreen)) {
            parserDown = GLFW.glfwGetKey(handle, parserKey) == GLFW.GLFW_PRESS;
        }

        boolean fullAutoDown = false;
        int fullAutoKey = config.getKeyFullAuto();
        if (fullAutoKey > 0 && shouldPollOneKeyBind(currentClient.currentScreen)) {
            fullAutoDown = GLFW.glfwGetKey(handle, fullAutoKey) == GLFW.GLFW_PRESS;
        }

        boolean sellOnlyDown = false;
        int sellOnlyKey = config.getKeySellOnly();
        if (sellOnlyKey > 0 && shouldPollOneKeyBind(currentClient.currentScreen)) {
            sellOnlyDown = GLFW.glfwGetKey(handle, sellOnlyKey) == GLFW.GLFW_PRESS;
        }

        if (debugDown && !debugKeyWasDown) {
            if (parserDebugArmedTarget != null && currentClient.currentScreen instanceof GenericContainerScreen) {
                List<AuctionSlot> slots = readCurrentContainerSlotsForParserDebug();
                TargetConfig target = parserDebugArmedTarget;
                ParserDebugStats stats = dumpParserDebugToLog("key_dump:" + parserDebugArmReason, target, slots, toParserTargetItem(target));
                clearParserDebugArm("key_dumped");
                chat.send("Parser debug key-dump записан в latest.log: target=" + safeTargetLabel(target)
                        + ", title=" + getCurrentScreenTitleSafe()
                        + ", slots=" + stats.totalSlots
                        + ", nonEmpty=" + stats.nonEmptySlots
                        + ", priced=" + stats.pricedSlots
                        + ", strictMatched=" + stats.strictMatchedSlots);
            } else {
                chat.sendDebugBlock("key debug:", buildDebugBlock());
            }
        }

        if (fingerprintDown && !fingerprintKeyWasDown) {
            runFingerprintCheck();
        }

        if (scanDown && !scanKeyWasDown) {
            runManualScan();
        }

        if (observerDown && !observerKeyWasDown) {
            toggleObserver();
        }

        if (refreshDown && !refreshKeyWasDown) {
            startManualRefreshCycle();
        }

        if (buyDryRunDown && !buyDryRunKeyWasDown) {
            runBuyDryRun();
        }

        if (buyClickDown && !buyClickKeyWasDown) {
            startControlledBuyClick();
        }

        if (oneCycleDown && !oneCycleKeyWasDown) {
            startOneCycle();
        }

        if (limitedLoopDown && !limitedLoopKeyWasDown) {
            toggleLimitedLoop();
        }

        if (guiDown && !guiKeyWasDown) {
            openTargetsGui();
        }

        if (parserDown && !parserKeyWasDown) {
            toggleParserKey();
        }

        if (fullAutoDown && !fullAutoKeyWasDown) {
            toggleFullAutoOneKey();
        }

        if (sellOnlyDown && !sellOnlyKeyWasDown) {
            toggleSellOnlyTimedFromKey();
        }

        debugKeyWasDown = debugDown;
        fingerprintKeyWasDown = fingerprintDown;
        scanKeyWasDown = scanDown;
        observerKeyWasDown = observerDown;
        refreshKeyWasDown = refreshDown;
        buyDryRunKeyWasDown = buyDryRunDown;
        buyClickKeyWasDown = buyClickDown;
        oneCycleKeyWasDown = oneCycleDown;
        limitedLoopKeyWasDown = limitedLoopDown;
        guiKeyWasDown = guiDown;
        parserKeyWasDown = parserDown;
        fullAutoKeyWasDown = fullAutoDown;
        sellOnlyKeyWasDown = sellOnlyDown;
    }

    private boolean shouldPollOneKeyBind(Screen screen) {
        // One-key binds must also work while the server auction/container is open.
        // Do not poll them in chat or in the mod config GUI, otherwise typing can toggle actions.
        return screen == null || screen instanceof GenericContainerScreen;
    }

    private void toggleParserKey() {
        if (parserRunning) {
            stopAutoParser("parser_key_stop");
            chat.send("Парсер остановлен клавишей: " + buildParserCompact());
            return;
        }

        startAutoParserForAll();
    }

    private void toggleFullAutoOneKey() {
        if (fullAutoTimedEnabled || cycleFullLoopEnabled || cycleFullEnabled || sellerLoopEnabled || sellerCycleEnabled || sellerReturnToAuctionPending || limitedLoop.isRunning()) {
            stopSafeAutoRun("full_auto_key_stop");
            stopFullAutoTimed("full_auto_key_stop");
            stopCycleFullLoop("full_auto_key_stop");
            stopCycleFull("full_auto_key_stop");
            stopSpamKickRecovery("full_auto_key_stop");
            setLegacyScriptPause(false, "full_auto_key_stop");
            shulkerController.stop("full_auto_key_stop");
            sellerCycleEnabled = false;
            sellerCycleLastReason = "full_auto_key_stop";
            stopSellerLoop("full_auto_key_stop");
            sellerReturnToAuctionPending = false;
            limitedLoop.stop("full_auto_key_stop");
            oneCycle.cancel("full_auto_key_stop");
            chat.send("full-auto stopped by key: fullAutoTimed=" + buildFullAutoTimedCompact()
                    + ", cycleFull=" + buildCycleFullCompact()
                    + ", sellerLoop=" + buildSellerLoopCompact());
            return;
        }

        startFullAutoTimed();
    }

    private void toggleObserver() {
        observerEnabled = !observerEnabled;
        runtime.disable();

        if (observerEnabled) {
            chat.send("observer enabled. No buying, no auto refresh.");
        } else {
            chat.send("observer disabled.");
        }
    }


    public AutoBuyConfig getConfig() {
        return config;
    }


    public void applyConfigToRuntime() {
        scanner.setTargets(config.toTargetItems());
        scanner.setSettings(config.toScannerSettings());

        refreshCycle.setTimeoutMs(config.getRefreshTimeoutMs());
        oneCycle.setRefreshTimeoutMs(config.getRefreshTimeoutMs());

        limitedLoop.setDelayBetweenCyclesMs(config.getLoopDelayMs());
        limitedLoop.setRefreshTimeoutMs(config.getRefreshTimeoutMs());
        limitedLoop.setMaxRefreshFailStreak(config.getMaxRefreshFailStreak());
        limitedLoop.setSuccessCooldownMs(MalfixTimings.AB_BUY_MS);

        sellerController.setTargets(config.toTargetItems());
        sellerController.setSellerMarkupPercent(config.getSellerMarkupPercent());
        unstackController.setTargets(config.toTargetItems());
        unstackController.setDelayMs(MalfixTimings.UNSTACK_SELL_SPLIT_MS);
    }

    public boolean saveConfig() {
        return configManager.save(config);
    }

    public void openTargetsGui() {
        if (client == null) {
            return;
        }

        client.setScreen(new TargetsConfigScreen(this, client.currentScreen));
    }

    public void openKeybindGui(net.minecraft.client.gui.screen.Screen parentScreen) {
        if (client == null) {
            return;
        }

        net.minecraft.client.gui.screen.Screen parent = parentScreen == null ? client.currentScreen : parentScreen;
        client.setScreen(new KeybindConfigScreen(this, parent));
    }

    public void openParserGui(net.minecraft.client.gui.screen.Screen parentScreen) {
        if (client == null) {
            return;
        }

        net.minecraft.client.gui.screen.Screen parent = parentScreen == null ? client.currentScreen : parentScreen;
        client.setScreen(new ParserConfigScreen(this, parent));
    }

    public void openAuctionSearchForTarget(TargetConfig target) {
        if (client == null || client.player == null || target == null) {
            return;
        }

        String query = buildAuctionSearchQuery(target);
        if (query.isEmpty()) {
            chat.send("ah search blocked: empty target name");
            return;
        }

        final String command = "/ah search " + query;

        try {
            client.setScreen(null);
        } catch (Throwable ignored) {
        }

        client.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (client.player != null) {
                        McChat.send(client, command);
                    }
                } catch (Throwable throwable) {
                    chat.send("ah search failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                }
            }
        });
    }

    private String buildAuctionSearchQuery(TargetConfig target) {
        if (target == null) {
            return "";
        }

        String label = normalizeSearchText(target.getLabel());
        if (!label.isEmpty()) {
            return label;
        }

        if (target.getContains() != null) {
            for (String value : target.getContains()) {
                String normalized = normalizeSearchText(value);
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            }
        }

        return "";
    }

    private boolean detectAndHandleSellLimitMessage(String message) {
        String reason = detectSellLimitReason(message);
        if ("none".equals(reason)) {
            return false;
        }

        lastSellLimitDetected = true;
        lastSellLimitMessage = message == null ? "" : message;
        lastSellLimitReason = reason;
        lastSellLimitAtMs = System.currentTimeMillis();

        // Step 22.77: /ah rent means the *current sell phase* cannot list more items.
        // Clear only immediate storage-continuation flags so we do not reopen storage
        // right after the failed sell. Do not block storage globally: the next post-buy
        // storage phase must still be able to drain items normally.
        cycleFullStorageContinueAfterSell = false;
        sellOnlyStorageContinueAfterSell = false;
        if (fullAutoTimedEnabled || cycleFullTimedBuyMode) {
            fullAutoSkipNextPreSellStorage = true;
            fullAutoSkipNextPreSellStorageReason = "sell_limit_detected:" + reason;
        }

        sellerAwaitingServerResult = false;
        sellerAwaitingServerResultSinceMs = 0L;
        sellerAwaitingServerCommand = "";

        if (sellerLoopEnabled) {
            stopSellerLoop("sell_limit_detected:" + reason);
            chat.send("seller-loop stopped: sell_limit_detected"
                    + ", reason=" + reason
                    + ", " + buildSellerLoopCompact());
        } else {
            chat.send("sell limit detected: reason=" + reason);
        }

        sellerCycleEnabled = false;
        sellerCycleLastReason = "sell_limit_detected:" + reason;

        if (sellOnlyTimedEnabled && isSellOnlyInSellPhase()) {
            sellOnlyStopReason = "sell_limit_detected:" + reason;
            sellerReturnToAuctionPending = false;
            finishSellOnlyRound("sell_limit_detected:" + reason);
            chat.send("sell-only sell limit detected: no /ah return, wait closed until next 30s round. reason=" + reason);
            return true;
        }

        if (cycleFullLoopEnabled) {
            cycleFullLoopSkipNextSeller = true;
            cycleFullLoopSkipSellerReason = "sell_limit_detected:" + reason;
            cycleFullLoopStopReason = "seller_paused_after_sell_limit:" + reason;
            chat.send("cyclefullloop seller pause armed: next full cycle will skip seller once, reason=" + reason);
        } else if (fullAutoTimedEnabled) {
            fullAutoTimedStopReason = "sell_limit_detected:" + reason;
            chat.send("full-auto timed sell limit detected: return /ah, then next 90s buy phase. reason=" + reason);
        }

        scheduleSellerReturnToAuction("sell_limit_detected:" + reason);
        return true;
    }

    private void detectAndHandleSellerCommandResponse(String message) {
        if (!sellerAwaitingServerResult || message == null) {
            return;
        }

        String lower = message.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replace("§0", "")
                .replace("§1", "")
                .replace("§2", "")
                .replace("§3", "")
                .replace("§4", "")
                .replace("§5", "")
                .replace("§6", "")
                .replace("§7", "")
                .replace("§8", "")
                .replace("§9", "")
                .replace("§a", "")
                .replace("§b", "")
                .replace("§c", "")
                .replace("§d", "")
                .replace("§e", "")
                .replace("§f", "")
                .replace("§k", "")
                .replace("§l", "")
                .replace("§m", "")
                .replace("§n", "")
                .replace("§o", "")
                .replace("§r", "");

        boolean saleAck = lower.contains("успешно выстав")
                || lower.contains("вы выстав")
                || lower.contains("предмет выстав")
                || lower.contains("выставлен") && lower.contains("продаж")
                || lower.contains("на продаж");

        boolean saleFailure = lower.contains("не удалось выстав")
                || lower.contains("ошибка") && (lower.contains("выстав") || lower.contains("продаж"))
                || lower.contains("нельзя") && (lower.contains("выстав") || lower.contains("продать"))
                || lower.contains("не можете") && (lower.contains("выстав") || lower.contains("продать"));

        if (!saleAck && !saleFailure) {
            return;
        }

        sellerAwaitingServerResult = false;
        sellerAwaitingServerResultSinceMs = 0L;
        sellerAwaitingServerCommand = "";
        sellerLoopNextAtMs = System.currentTimeMillis() + Math.max(MalfixTimings.AUTOSELL_SELL_MS, sellerLoopDelayMs);
        // Do not notify Telegram here: this is only the server acknowledgement that
        // the item was listed on /ah, not that it was actually sold.
    }

    private String detectSellLimitReason(String message) {
        if (message == null) {
            return "none";
        }

        String lower = message.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replace("§0", "")
                .replace("§1", "")
                .replace("§2", "")
                .replace("§3", "")
                .replace("§4", "")
                .replace("§5", "")
                .replace("§6", "")
                .replace("§7", "")
                .replace("§8", "")
                .replace("§9", "")
                .replace("§a", "")
                .replace("§b", "")
                .replace("§c", "")
                .replace("§d", "")
                .replace("§e", "")
                .replace("§f", "")
                .replace("§k", "")
                .replace("§l", "")
                .replace("§m", "")
                .replace("§n", "")
                .replace("§o", "")
                .replace("§r", "");

        if (lower.contains("/ah rent")) {
            return "ah_rent";
        }

        if (lower.contains("аренд") && (lower.contains("слот") || lower.contains("аукцион") || lower.contains("продаж"))) {
            return "rent_slots";
        }

        if (lower.contains("лимит") && (lower.contains("продаж") || lower.contains("слот") || lower.contains("аукцион") || lower.contains("выстав"))) {
            return "limit";
        }

        if ((lower.contains("достиг") || lower.contains("достигнут") || lower.contains("превыш"))
                && (lower.contains("лимит") || lower.contains("максим"))
                && (lower.contains("продаж") || lower.contains("слот") || lower.contains("аукцион") || lower.contains("выстав"))) {
            return "limit_reached";
        }

        if ((lower.contains("нельзя") || lower.contains("не можете") || lower.contains("невозможно"))
                && (lower.contains("выстав") || lower.contains("продать"))
                && (lower.contains("предмет") || lower.contains("аукцион") || lower.contains("слот") || lower.contains("продаж"))) {
            return "cannot_sell_more";
        }

        if ((lower.contains("нет") || lower.contains("законч"))
                && lower.contains("слот")
                && (lower.contains("продаж") || lower.contains("аукцион"))) {
            return "no_sell_slots";
        }

        if (lower.contains("максим") && lower.contains("количеств") && (lower.contains("продаж") || lower.contains("предмет"))) {
            return "max_sales";
        }

        return "none";
    }

    private String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace('"', ' ')
                .replace('\'', ' ')
                .replace("\r", " ")
                .replace("\n", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String[] parseArguments(String raw) {
        if (raw == null) {
            return new String[0];
        }

        List<String> result = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);

            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(c);
        }

        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result.toArray(new String[result.size()]);
    }

    private void sendConfigSummary() {
        chat.send("config: " + config.compact());
        chat.send("safety: " + config.safetySummary());
        chat.send("seller: " + config.sellerSummary());
        chat.send("парсер: " + config.parserSummary());
        chat.send("anti-afk: " + config.antiAfkSummary());
        chat.send("config path: " + configManager.getConfigPath());
        chat.send("runtime settings: " + runtimeSettings.compact());
        chat.send("scannerTargets=" + scanner.getTargetCount()
                + ", loopDelayMs=" + config.getLoopDelayMs()
                + ", defaultCycles=" + config.getDefaultLoopCycles()
                + ", defaultBuys=" + config.getDefaultLoopBuys()
                + ", refreshTimeoutMs=" + config.getRefreshTimeoutMs()
                + ", maxRefreshFails=" + config.getMaxRefreshFailStreak());
    }

    private void handleSetCommand(String[] parts) {
        if (parts.length < 4) {
            chat.send("usage: .mab set delay/cycles/buys/scan/requirePrice/allowUnlimited/refreshTimeout/maxRefreshFails/sellMarkup/parserBuy/parserSell/parserWait <value>");
            return;
        }

        String key = parts[2].toLowerCase(Locale.ROOT);

        if ("scan".equals(key) || "scanmode".equals(key)) {
            config.setScanMode(parts[3]);
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("set scanMode=" + config.getScanMode());
            return;
        }

        if ("requireprice".equals(key) || "requiremaxprice".equals(key)) {
            config.setRequireMaxPrice(parseBooleanArg(parts[3], config.isRequireMaxPrice()));
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("set requireMaxPrice=" + config.isRequireMaxPrice());
            return;
        }

        if ("allowunlimited".equals(key) || "allowunlimitedprice".equals(key) || "unlimited".equals(key)) {
            config.setAllowUnlimitedPrice(parseBooleanArg(parts[3], config.isAllowUnlimitedPrice()));
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("set allowUnlimitedPrice=" + config.isAllowUnlimitedPrice());
            return;
        }

        long value = parseLong(parts[3], -1L);

        if (value <= 0L) {
            chat.send("bad value: " + parts[3]);
            return;
        }

        if ("delay".equals(key) || "delayms".equals(key)) {
            config.setLoopDelayMs(value);
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("set delayMs=" + config.getLoopDelayMs());
            return;
        }

        if ("cycles".equals(key) || "maxcycles".equals(key)) {
            config.setDefaultLoopCycles((int) value);
            saveConfigAndReport();
            chat.send("set defaultLoopCycles=" + config.getDefaultLoopCycles());
            return;
        }

        if ("buys".equals(key) || "maxbuys".equals(key)) {
            config.setDefaultLoopBuys((int) value);
            saveConfigAndReport();
            chat.send("set defaultLoopBuys=" + config.getDefaultLoopBuys());
            return;
        }

        if ("refreshtimeout".equals(key) || "refreshtimeoutms".equals(key) || "timeout".equals(key)) {
            config.setRefreshTimeoutMs(value);
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("set refreshTimeoutMs=" + config.getRefreshTimeoutMs());
            return;
        }

        if ("maxrefreshfails".equals(key) || "maxrefreshfailstreak".equals(key) || "refreshfails".equals(key)) {
            config.setMaxRefreshFailStreak((int) value);
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("set maxRefreshFailStreak=" + config.getMaxRefreshFailStreak());
            return;
        }

        if ("sellmarkup".equals(key) || "sellmarkuppercent".equals(key) || "markup".equals(key) || "profit".equals(key)) {
            config.setSellerMarkupPercent((int) value);
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("set sellerMarkupPercent=" + config.getSellerMarkupPercent() + "%");
            return;
        }

        if ("parserbuy".equals(key) || "parserbuypercent".equals(key) || "buypercent".equals(key)) {
            config.setParserBuyPercent((int) value);
            saveConfigAndReport();
            chat.send("настройка парсера: процент покупки=" + config.getParserBuyPercent() + "%");
            return;
        }

        if ("parsersell".equals(key) || "parsersellpercent".equals(key) || "sellpercent".equals(key)) {
            config.setParserSellPercent((int) value);
            saveConfigAndReport();
            chat.send("настройка парсера: процент продажи=" + config.getParserSellPercent() + "%");
            return;
        }

        if ("parserwait".equals(key) || "parseropenwait".equals(key) || "parseropenwaitms".equals(key)) {
            config.setParserOpenWaitMs(value);
            saveConfigAndReport();
            chat.send("настройка парсера: ожидание открытия=" + config.getParserOpenWaitMs() + "ms");
            return;
        }

        chat.send("unknown set key: " + key);
    }

    private void handleBlacklistCommand(String[] parts) {
        if (parts.length < 3) {
            chat.send("usage: .mab blacklist list/add/remove/clear \"word\"");
            return;
        }

        String action = parts[2].toLowerCase(Locale.ROOT);

        if ("list".equals(action)) {
            chat.send("blacklist: " + config.getBlacklistKeywords());
            return;
        }

        if ("clear".equals(action)) {
            config.clearBlacklistKeywords();
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("blacklist cleared");
            return;
        }

        if (parts.length < 4) {
            chat.send("usage: .mab blacklist " + action + " \"word\"");
            return;
        }

        String keyword = parts[3];

        if ("add".equals(action)) {
            boolean added = config.addBlacklistKeyword(keyword);
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send(added ? "blacklist added: " + keyword : "blacklist already has/invalid: " + keyword);
            return;
        }

        if ("remove".equals(action) || "del".equals(action) || "delete".equals(action)) {
            boolean removed = config.removeBlacklistKeyword(keyword);
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send(removed ? "blacklist removed: " + keyword : "blacklist not found: " + keyword);
            return;
        }

        chat.send("unknown blacklist action: " + action);
    }

    private boolean parseBooleanArg(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);

        if ("true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized) || "1".equals(normalized) || "да".equals(normalized)) {
            return true;
        }

        if ("false".equals(normalized) || "no".equals(normalized) || "off".equals(normalized) || "0".equals(normalized) || "нет".equals(normalized)) {
            return false;
        }

        return fallback;
    }

    private void handleTargetCommand(String[] parts) {
        if (parts.length < 3) {
            chat.send("usage: .mab target list/add/remove/enable/disable/price/reset");
            return;
        }

        String action = parts[2].toLowerCase(Locale.ROOT);

        if ("list".equals(action)) {
            listTargets();
            return;
        }

        if ("reset".equals(action)) {
            config.resetTargets();
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("targets reset to defaults.");
            return;
        }

        if ("add".equals(action)) {
            addTarget(parts);
            return;
        }

        if (parts.length < 4) {
            chat.send("usage: .mab target " + action + " \"Label\" [value]");
            return;
        }

        String label = parts[3];
        TargetConfig target = config.findTarget(label);

        if (target == null) {
            chat.send("target not found: " + label);
            return;
        }

        if ("remove".equals(action) || "del".equals(action) || "delete".equals(action)) {
            config.removeTarget(label);
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("removed target: " + label);
            return;
        }

        if ("enable".equals(action) || "on".equals(action)) {
            target.setEnabled(true);
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("enabled target: " + label);
            return;
        }

        if ("disable".equals(action) || "off".equals(action)) {
            target.setEnabled(false);
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("disabled target: " + label);
            return;
        }

        if ("price".equals(action) || "max".equals(action) || "maxprice".equals(action)) {
            if (parts.length < 5) {
                chat.send("usage: .mab target price \"Label\" <maxUnitPrice>");
                return;
            }

            long price = parseLong(parts[4], -1L);
            if (price < 0L) {
                chat.send("bad price: " + parts[4]);
                return;
            }

            target.setMaxUnitPrice(price);
            applyConfigToRuntime();
            saveConfigAndReport();
            chat.send("updated target price: " + label + " maxUnitPrice=" + target.getMaxUnitPrice());
            return;
        }

        chat.send("unknown target action: " + action);
    }

    private void addTarget(String[] parts) {
        if (parts.length < 6) {
            chat.send("usage: .mab target add \"Label\" <maxUnitPrice> \"contains text\"");
            chat.send("example: .mab target add \"Talisman Yarosti\" 12000000 \"талисман ярости\"");
            return;
        }

        String label = parts[3];
        long maxUnitPrice = parseLong(parts[4], -1L);
        if (maxUnitPrice < 0L) {
            chat.send("bad maxUnitPrice: " + parts[4]);
            return;
        }

        List<String> contains = new ArrayList<String>();
        for (int i = 5; i < parts.length; i++) {
            String value = parts[i] == null ? "" : parts[i].trim();
            if (!value.isEmpty()) {
                contains.add(value);
            }
        }

        if (contains.isEmpty()) {
            chat.send("target add failed: contains list is empty.");
            return;
        }

        boolean added = config.addTarget(new TargetConfig(label, contains, "", "", maxUnitPrice, true));
        if (!added) {
            chat.send("target add failed. Maybe already exists: " + label);
            return;
        }

        applyConfigToRuntime();
        saveConfigAndReport();
        chat.send("added target: " + label + ", maxUnitPrice=" + maxUnitPrice + ", contains=" + contains);
    }

    private void listTargets() {
        List<TargetConfig> targets = config.getTargets();

        if (targets.isEmpty()) {
            chat.send("targets: empty");
            return;
        }

        chat.send("targets: " + targets.size());

        for (int i = 0; i < targets.size(); i++) {
            TargetConfig target = targets.get(i);
            chat.send("#" + (i + 1) + " " + target.compact());
        }
    }

    private void saveConfigAndReport() {
        boolean saved = configManager.save(config);
        if (!saved) {
            chat.send("config save failed: " + configManager.getConfigPath());
        }
    }

    private void reloadConfigAndReport() {
        config = configManager.loadOrCreate();
        if (ScriptItemCatalog.applyCatalogPatch(config) > 0) {
            configManager.save(config);
        }
        applyConfigToRuntime();
        chat.send("config reloaded: " + config.compact());
    }

    private long parseLong(String value, long fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            String normalized = value.replace(",", "").replace(" ", "").replace("_", "").trim();
            return Long.parseLong(normalized);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private void startSafeAutoRun(String[] parts) {
        if (safeAutoRunEnabled) {
            chat.send("safe-auto already running: " + buildSafeAutoRunCompact());
            return;
        }

        if (!auctionView.isAuctionOpen()) {
            chat.send("safe-auto blocked: auction is not open. Open /ah first.");
            return;
        }

        if (oneCycle.isPending()) {
            chat.send("safe-auto blocked: one-cycle is pending.");
            return;
        }

        if (refreshCycle.isPending()) {
            chat.send("safe-auto blocked: manual refresh is pending.");
            return;
        }

        if (controlledBuyClick.isPending()) {
            chat.send("safe-auto blocked: manual buy click is pending.");
            return;
        }

        if (limitedLoop.isRunning()) {
            chat.send("safe-auto blocked: limited-loop is already running.");
            return;
        }

        int maxTotalBuys = parsePositiveInt(parts, 2, 0);

        safeAutoRunEnabled = true;
        safeAutoRunStartedAt = System.currentTimeMillis();
        safeAutoRunNextStartAt = safeAutoRunStartedAt;
        safeAutoRunSessionsStarted = 0;
        safeAutoRunTotalBuys = 0;
        safeAutoRunMaxTotalBuys = Math.max(0, maxTotalBuys);
        safeAutoRunStopReason = "running";
        safeAutoRunLastCountedBuySignature = "";

        observerEnabled = false;
        runtime.disable();

        // Keep Anti-AFK from firing immediately if the client was idle before autobuy was enabled.
        armAntiAfkTimerFromNow("armed_by_safe_auto_timer_start");
        applyConfigToRuntime();

        chat.send("safe-auto started: maxTotalBuys="
                + (safeAutoRunMaxTotalBuys <= 0 ? "unlimited" : String.valueOf(safeAutoRunMaxTotalBuys))
                + ", batchCycles=" + config.getDefaultLoopCycles()
                + ", batchBuys=" + config.getDefaultLoopBuys()
                + ", successCooldownMs=" + limitedLoop.getSuccessCooldownMs()
                + ", safety={" + config.safetySummary() + "}");
    }

    private void tickSafeAutoRun() {
        if (!safeAutoRunEnabled) {
            return;
        }

        if (limitedLoop.isRunning()) {
            return;
        }

        if (safeAutoRunMaxTotalBuys > 0 && safeAutoRunTotalBuys >= safeAutoRunMaxTotalBuys) {
            stopSafeAutoRun("max_total_buys_reached");
            chat.send("safe-auto stopped: max total buys reached " + safeAutoRunTotalBuys + "/" + safeAutoRunMaxTotalBuys);
            return;
        }

        long now = System.currentTimeMillis();
        if (now < safeAutoRunNextStartAt) {
            return;
        }

        if (!auctionView.isAuctionOpen()) {
            stopSafeAutoRun("auction_closed");
            chat.send("safe-auto stopped: auction closed.");
            return;
        }

        applyConfigToRuntime();

        boolean started = limitedLoop.start(config.getDefaultLoopCycles(), config.getDefaultLoopBuys());
        lastAutoLoopResult = limitedLoop.getLastResult();

        if (!started) {
            stopSafeAutoRun("loop_start_failed");
            chat.send("safe-auto start failed: " + lastAutoLoopResult.compact());
            return;
        }

        safeAutoRunSessionsStarted++;
        chat.send("safe-auto batch started: session=" + safeAutoRunSessionsStarted
                + ", totalBuys=" + safeAutoRunTotalBuys
                + (safeAutoRunMaxTotalBuys > 0 ? "/" + safeAutoRunMaxTotalBuys : "")
                + ", cycles=" + config.getDefaultLoopCycles()
                + ", buys=" + config.getDefaultLoopBuys());
    }

    private void handleSafeAutoRunLoopEvent(AutoLoopResult result) {
        if (result == null) {
            return;
        }

        if (isLoopResultBuySuccess(result)) {
            updateLastBuyInfo(result);
        }

        if (!safeAutoRunEnabled) {
            return;
        }

        if (isLoopResultBuySuccess(result) && countSafeAutoBuyOnce(result)) {
            if (safeAutoRunMaxTotalBuys > 0 && safeAutoRunTotalBuys >= safeAutoRunMaxTotalBuys) {
                stopSafeAutoRun("max_total_buys_reached_after_buy");
                limitedLoop.stop("safe_auto_max_total_buys");
                lastAutoLoopResult = limitedLoop.getLastResult();
                chat.send("safe-auto stopped after buy limit: " + safeAutoRunTotalBuys + "/" + safeAutoRunMaxTotalBuys
                        + ", lastBuy=" + buildLastBuyCompact());
                return;
            }
        }

        if (result.isHardStop()) {
            stopSafeAutoRun("hard_stop_" + result.getStatus());
            chat.send("safe-auto stopped by hard-stop: " + result.getStatus());
            return;
        }

        if (result.getStatus() == AutoLoopResult.Status.MANUAL_STOP) {
            stopSafeAutoRun("manual_stop_from_loop");
            return;
        }

        if (result.getStatus() == AutoLoopResult.Status.LIMIT_REACHED_STOP || (!result.isRunning() && safeAutoRunEnabled)) {
            long delay = Math.max(MalfixTimings.AB_BUY_MS, config.getLoopDelayMs());

            if (isLoopResultBuySuccess(result)) {
                delay = Math.max(delay, MalfixTimings.AB_BUY_MS);
            }

            safeAutoRunNextStartAt = System.currentTimeMillis() + delay;
            chat.send("safe-auto next batch in " + delay + "ms. " + buildSafeAutoRunCompact());
        }
    }

    private boolean isLoopResultBuySuccess(AutoLoopResult result) {
        if (result == null) {
            return false;
        }

        if (result.getStatus() == AutoLoopResult.Status.BUY_SUCCESS_CONTINUE) {
            return true;
        }

        ControlledBuyClickResult click = result.getBuyClickResult();
        return click != null && click.getStatus() == ControlledBuyClickResult.Status.BUY_SUCCESS;
    }

    private boolean countSafeAutoBuyOnce(AutoLoopResult result) {
        String signature = buildBuySignature(result);

        if (signature.equals(safeAutoRunLastCountedBuySignature)) {
            return false;
        }

        safeAutoRunLastCountedBuySignature = signature;
        safeAutoRunTotalBuys++;
        return true;
    }

    private String buildBuySignature(AutoLoopResult result) {
        if (result == null || result.getCandidate() == null) {
            return "success@" + System.currentTimeMillis();
        }

        ScanCandidate candidate = result.getCandidate();
        return candidate.getAuctionSlot().getContainerSlotId()
                + "|" + candidate.getAuctionSlot().getDisplayName()
                + "|" + candidate.getPrice().getUnitPrice()
                + "|" + candidate.getPrice().getTotalPrice()
                + "|" + lastBuyResult.getRawMessage();
    }

    private void updateLastBuyInfo(AutoLoopResult result) {
        lastBuyStatus = "BUY_SUCCESS";
        lastBoughtAtMs = System.currentTimeMillis();

        ScanCandidate candidate = result == null ? null : result.getCandidate();

        if (candidate == null && result != null && result.getBuyClickResult() != null) {
            candidate = result.getBuyClickResult().getCandidate();
        }

        if (candidate == null) {
            lastBoughtItem = "unknown";
            lastBoughtUnitPrice = 0L;
            lastBoughtTotalPrice = 0L;
            lastBoughtSlot = -1;
            return;
        }

        lastBoughtItem = candidate.getAuctionSlot().getDisplayName();
        lastBoughtUnitPrice = candidate.getPrice().getUnitPrice();
        lastBoughtTotalPrice = candidate.getPrice().getTotalPrice();
        lastBoughtSlot = candidate.getAuctionSlot().getAuctionIndex();
    }

    private String buildLastBuyCompact() {
        return "status=" + lastBuyStatus
                + ", item=" + lastBoughtItem
                + ", unit=" + moneyFormat.format(lastBoughtUnitPrice)
                + ", total=" + moneyFormat.format(lastBoughtTotalPrice)
                + ", slot=" + lastBoughtSlot
                + ", atMs=" + lastBoughtAtMs;
    }

    private void stopSafeAutoRun(String reason) {
        safeAutoRunEnabled = false;
        safeAutoRunStopReason = reason == null ? "stopped" : reason;
        safeAutoRunNextStartAt = 0L;
    }

    private String buildSafeAutoRunCompact() {
        long elapsed = safeAutoRunStartedAt <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - safeAutoRunStartedAt);
        return "enabled=" + safeAutoRunEnabled
                + ", sessions=" + safeAutoRunSessionsStarted
                + ", totalBuys=" + safeAutoRunTotalBuys
                + (safeAutoRunMaxTotalBuys > 0 ? "/" + safeAutoRunMaxTotalBuys : "/unlimited")
                + ", elapsedMs=" + elapsed
                + ", nextStartInMs=" + Math.max(0L, safeAutoRunNextStartAt - System.currentTimeMillis())
                + ", stopReason=" + safeAutoRunStopReason;
    }

    private void tickLimitedLoop() {
        AutoLoopResult result = limitedLoop.tick();
        if (result == null) {
            return;
        }

        lastAutoLoopResult = result;
        chat.send((safeAutoRunEnabled ? "safe-auto event: " : "limited-loop event: ") + result.compact());

        if (result.getCandidate() != null) {
            chat.send((safeAutoRunEnabled ? "safe-auto candidate: " : "limited-loop candidate: ") + formatCandidateCompact(result.getCandidate()));
        }

        handleSafeAutoRunLoopEvent(result);
        handleCycleFullBuyLoopEvent(result);
    }

    private void toggleLimitedLoop() {
        if (safeAutoRunEnabled) {
            stopSafeAutoRun("key_toggle_stop");
            limitedLoop.stop("key_toggle_stop");
            lastAutoLoopResult = limitedLoop.getLastResult();
            chat.send("safe-auto stopped by key.");
            return;
        }

        if (limitedLoop.isRunning()) {
            limitedLoop.stop("key_toggle_stop");
            lastAutoLoopResult = limitedLoop.getLastResult();
            chat.send("limited-loop stopped: " + lastAutoLoopResult.compact());
            return;
        }

        startSafeAutoRun(new String[] { ".mab", "auto" });
    }

    private void startLimitedLoop(String[] parts) {
        if (safeAutoRunEnabled) {
            chat.send("limited-loop blocked: safe-auto is running. Use .mab stop first.");
            return;
        }

        if (!auctionView.isAuctionOpen()) {
            chat.send("limited-loop blocked: auction is not open. Open /ah and press RShift+L while GUI is open.");
            return;
        }

        if (oneCycle.isPending()) {
            chat.send("limited-loop blocked: one-cycle is pending.");
            return;
        }

        if (refreshCycle.isPending()) {
            chat.send("limited-loop blocked: manual refresh cycle is pending.");
            return;
        }

        if (controlledBuyClick.isPending()) {
            chat.send("limited-loop blocked: manual buy click is pending.");
            return;
        }

        if (limitedLoop.isRunning()) {
            chat.send("limited-loop already running. Use .mab stop or RShift+L.");
            return;
        }

        int cycles = parsePositiveInt(parts, 2, config.getDefaultLoopCycles());
        int buys = parsePositiveInt(parts, 3, config.getDefaultLoopBuys());
        limitedLoop.setDelayBetweenCyclesMs(config.getLoopDelayMs());

        boolean started = limitedLoop.start(cycles, buys);
        lastAutoLoopResult = limitedLoop.getLastResult();

        if (!started) {
            chat.send("limited-loop start failed: " + lastAutoLoopResult.compact());
            return;
        }

        chat.send("limited-loop started: cycles=" + cycles + ", buys=" + buys + ", delayMs=" + limitedLoop.getDelayBetweenCyclesMs());
    }

    private int parsePositiveInt(String[] parts, int index, int fallback) {
        if (parts == null || parts.length <= index) {
            return fallback;
        }

        try {
            int value = Integer.parseInt(parts[index]);
            return value <= 0 ? fallback : value;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private void tickOneCycle() {
        OneCycleResult result = oneCycle.tick();
        if (result == null) {
            return;
        }

        lastOneCycleResult = result;
        chat.send("one-cycle event: " + result.compact());

        if (result.getCandidate() != null) {
            chat.send("one-cycle candidate: " + formatCandidateCompact(result.getCandidate()));
        }
    }

    private void startOneCycle() {
        if (safeAutoRunEnabled) {
            chat.send("one-cycle blocked: safe-auto is running.");
            return;
        }

        if (limitedLoop.isRunning()) {
            chat.send("one-cycle blocked: limited-loop is running.");
            return;
        }

        if (!auctionView.isAuctionOpen()) {
            chat.send("one-cycle blocked: auction is not open. Open /ah and press RShift+A while GUI is open.");
            return;
        }

        if (refreshCycle.isPending()) {
            chat.send("one-cycle blocked: manual refresh cycle is pending.");
            return;
        }

        if (controlledBuyClick.isPending()) {
            chat.send("one-cycle blocked: manual buy click is pending.");
            return;
        }

        if (oneCycle.isPending()) {
            chat.send("one-cycle blocked: previous one-cycle is still pending.");
            return;
        }

        boolean started = oneCycle.start();
        lastOneCycleResult = oneCycle.getLastResult();

        if (!started) {
            chat.send("one-cycle start failed: " + lastOneCycleResult.compact());
            return;
        }

        chat.send("one-cycle started: refresh -> scan -> click -> result -> stop");
    }

    private void tickControlledBuyClick() {
        ControlledBuyClickResult completed = controlledBuyClick.tick();
        if (completed == null) {
            return;
        }

        lastControlledBuyClickResult = completed;
        rememberTelegramBuyCandidate(completed == null ? null : completed.getCandidate());
        chat.send("buy click done: " + completed.compact());

        ScanCandidate candidate = completed.getCandidate();
        if (candidate != null) {
            chat.send("buy click candidate: " + formatCandidateCompact(candidate));
        }
    }

    private void startControlledBuyClick() {
        if (safeAutoRunEnabled) {
            chat.send("real buy blocked: safe-auto is running.");
            return;
        }

        if (limitedLoop.isRunning()) {
            chat.send("real buy blocked: limited-loop is running.");
            return;
        }

        if (oneCycle.isPending()) {
            chat.send("real buy blocked: one-cycle is pending.");
            return;
        }

        if (!auctionView.isAuctionOpen()) {
            chat.send("real buy blocked: auction is not open. Open /ah and press RShift+Ctrl+B while GUI is open.");
            return;
        }

        if (refreshCycle.isPending()) {
            chat.send("real buy blocked: refresh cycle is pending.");
            return;
        }

        if (controlledBuyClick.isPending()) {
            chat.send("real buy blocked: previous buy click is still pending.");
            return;
        }

        boolean started = controlledBuyClick.start();
        ControlledBuyClickResult result = controlledBuyClick.getLastResult();
        lastControlledBuyClickResult = result;
        rememberTelegramBuyCandidate(result == null ? null : result.getCandidate());

        if (!started) {
            chat.send("real buy start failed: " + result.compact());
            return;
        }

        ScanCandidate candidate = result.getCandidate();
        chat.send("REAL BUY CLICK SENT: " + result.compact());

        if (candidate != null) {
            chat.send("clicked: slot=" + candidate.getAuctionSlot().getAuctionIndex()
                    + ", containerSlot=" + candidate.getAuctionSlot().getContainerSlotId()
                    + ", item=" + candidate.getAuctionSlot().getDisplayName());
            chat.send("target=" + candidate.getTarget().getLabel()
                    + ", unit=" + formatMoney(candidate.getPrice().getUnitPrice())
                    + ", total=" + formatMoney(candidate.getPrice().getTotalPrice()));
        }
    }

    private void tickRefreshCycle() {
        RefreshCycleResult completed = refreshCycle.tick();
        if (completed == null) {
            return;
        }

        chat.send("refresh done: " + completed.compact());

        if (completed.getScanResult() != null) {
            lastObserverScanResult = completed.getScanResult();
            lastObserverFingerprint = completed.getBeforeFingerprint();
            currentObserverFingerprint = completed.getAfterFingerprint();
            lastObserverAuctionOpen = auctionView.isAuctionOpen();
        }

        ScanCandidate best = completed.getBestCandidate();
        if (best == null) {
            chat.send("refresh best: none");
        } else {
            chat.send("refresh best: " + formatCandidateCompact(best));
        }
    }

    private void startManualRefreshCycle() {
        if (safeAutoRunEnabled) {
            chat.send("refresh blocked: safe-auto is running.");
            return;
        }

        if (limitedLoop.isRunning()) {
            chat.send("refresh blocked: limited-loop is running.");
            return;
        }

        if (oneCycle.isPending()) {
            chat.send("refresh blocked: one-cycle is pending.");
            return;
        }

        if (controlledBuyClick.isPending()) {
            chat.send("refresh blocked: buy click is pending.");
            return;
        }

        if (!auctionView.isAuctionOpen()) {
            chat.send("refresh blocked: auction is not open. Open /ah and press RShift+R while GUI is open.");
            return;
        }

        boolean started = refreshCycle.start();
        RefreshCycleResult result = refreshCycle.getLastResult();

        if (!started) {
            chat.send("refresh start failed: " + result.compact());
            return;
        }

        chat.send("refresh started: beforeFp=" + result.getBeforeFingerprint()
                + ", checked=" + result.getCheckedSlots()
                + ", timeoutMs=" + refreshCycle.getTimeoutMs());
    }

    private void tickObserver() {
        if (!observerEnabled) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastObserverTickAt < OBSERVER_INTERVAL_MS) {
            return;
        }
        lastObserverTickAt = now;

        boolean open = auctionView.isAuctionOpen();
        lastObserverAuctionOpen = open;

        if (!open) {
            lastObserverScanResult = ScanResult.noSlots();
            return;
        }

        List<AuctionSlot> slots = auctionView.readAuctionSlots();
        int fingerprint = AuctionFingerprint.compute(slots);
        boolean changed = fingerprint != currentObserverFingerprint;

        lastObserverFingerprint = currentObserverFingerprint;
        currentObserverFingerprint = fingerprint;
        lastObserverScanResult = scanner.scan(slots);

        if (isAutomationBusyForObserverChat()) {
            return;
        }

        if (changed && now - lastObserverMessageAt >= OBSERVER_CHAT_COOLDOWN_MS) {
            lastObserverMessageAt = now;
            chat.send("observer: auction slots changed, fp=" + fingerprint
                    + ", scan=" + lastObserverScanResult.getStatus()
                    + ", checked=" + lastObserverScanResult.getCheckedSlots());

            if (lastObserverScanResult.hasBestCandidate()) {
                chat.send("observer best: " + formatCandidateCompact(lastObserverScanResult.getBestCandidate()));
            }
        }
    }

    private boolean isAutomationBusyForObserverChat() {
        return safeAutoRunEnabled
                || sellOnlyTimedEnabled
                || limitedLoop.isRunning()
                || oneCycle.isPending()
                || controlledBuyClick.isPending()
                || refreshCycle.isPending();
    }

    private void startFullAutoTimed() {
        if (fullAutoTimedEnabled) {
            chat.send("full-auto timed already running: " + buildFullAutoTimedCompact());
            return;
        }

        if (sellOnlyTimedEnabled || cycleFullEnabled || cycleFullLoopEnabled || safeAutoRunEnabled || limitedLoop.isRunning()
                || oneCycle.isPending() || controlledBuyClick.isPending()
                || sellerLoopEnabled || sellerCycleEnabled || sellerReturnToAuctionPending) {
            chat.send("full-auto timed blocked: another action is active. cycleFull=" + buildCycleFullCompact()
                    + ", sellerLoop=" + buildSellerLoopCompact());
            return;
        }

        fullAutoTimedEnabled = true;
        fullAutoTimedWaitingForCycle = false;
        fullAutoTimedRoundsStarted = 0;
        fullAutoTimedStartedAtMs = System.currentTimeMillis();
        fullAutoTimedNextStartAtMs = 0L;
        fullAutoTimedStopReason = "running";
        fullAutoSkipNextPreSellStorage = false;
        fullAutoSkipNextPreSellStorageReason = "none";

        // Step 22.64: do NOT rejoin immediately when FullAuto starts.
        // Arm a fresh timer instead, so /hub -> /an -> /ah runs only after the configured interval.
        armAntiAfkTimerFromNow("armed_by_fullauto_timer_start");

        config.setLoopDelayMs(MalfixTimings.AB_UPDATE_MS);
        applyConfigToRuntime();
        saveConfig();

        chat.send("full-auto timed started: Anti-AFK timer armed -> pre-sell storage -> buy+refresh 90s -> storage -> sell until /ah rent/no items -> /ah -> repeat. Press same bind to stop.");
    }

    private void stopFullAutoTimed(String reason) {
        if (!fullAutoTimedEnabled && !fullAutoTimedWaitingForCycle) {
            fullAutoTimedStopReason = reason == null ? "stopped" : reason;
            return;
        }

        fullAutoTimedEnabled = false;
        fullAutoTimedWaitingForCycle = false;
        fullAutoTimedStopReason = reason == null ? "stopped" : reason;
        fullAutoSkipNextPreSellStorage = false;
        fullAutoSkipNextPreSellStorageReason = "none";
    }

    private void tickFullAutoTimed() {
        if (!fullAutoTimedEnabled) {
            return;
        }

        long now = System.currentTimeMillis();

        if (antiAfkRunning) {
            fullAutoTimedStopReason = "waiting_anti_afk";
            return;
        }

        if (cycleFullEnabled) {
            return;
        }

        if (fullAutoTimedWaitingForCycle) {
            fullAutoTimedWaitingForCycle = false;

            if (isFullAutoTimedTerminalStop(cycleFullStopReason)) {
                fullAutoTimedEnabled = false;
                fullAutoTimedStopReason = "terminal_cycle_stop:" + cycleFullStopReason;
                chat.send("full-auto timed stopped: " + buildFullAutoTimedCompact()
                        + ", lastCycle=" + buildCycleFullCompact());
                return;
            }

            fullAutoTimedNextStartAtMs = now + MalfixTimings.FULL_AUTO_LOOP_DELAY_MS;
            fullAutoTimedStopReason = "waiting_next_90s_buy_phase";
            chat.send("full-auto timed scheduled next 90s buy phase: " + buildFullAutoTimedCompact());
            return;
        }

        if (now < fullAutoTimedNextStartAtMs) {
            return;
        }

        if (safeAutoRunEnabled || limitedLoop.isRunning() || oneCycle.isPending() || controlledBuyClick.isPending()) {
            fullAutoTimedNextStartAtMs = now + MalfixTimings.AB_UPDATE_MS;
            fullAutoTimedStopReason = "waiting_buy_action_idle";
            return;
        }

        if (sellerLoopEnabled || sellerCycleEnabled || sellerReturnToAuctionPending) {
            fullAutoTimedNextStartAtMs = now + MalfixTimings.AUTOSELL_OPEN_MS;
            fullAutoTimedStopReason = "waiting_seller_idle";
            return;
        }

        startTimedCycleFullRound();
    }

    private boolean isFullAutoTimedTerminalStop(String reason) {
        if (reason == null) {
            return false;
        }

        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.contains("no_money")
                || lower.contains("open_ah_failed")
                || lower.contains("buy_loop_start_failed")
                || lower.contains("buy_error_no_buys")
                || lower.contains("full_auto_key_stop");
    }

    private void startTimedCycleFullRound() {
        if (!fullAutoTimedEnabled) {
            return;
        }

        if (cycleFullEnabled || cycleFullLoopEnabled || safeAutoRunEnabled || limitedLoop.isRunning()
                || oneCycle.isPending() || controlledBuyClick.isPending()
                || sellerLoopEnabled || sellerCycleEnabled || sellerReturnToAuctionPending) {
            fullAutoTimedNextStartAtMs = System.currentTimeMillis() + MalfixTimings.AB_UPDATE_MS;
            fullAutoTimedStopReason = "waiting_idle_before_round";
            return;
        }

        fullAutoTimedRoundsStarted++;
        fullAutoTimedWaitingForCycle = true;
        fullAutoTimedStopReason = "round_running";

        startCycleFullTimed(
                MalfixTimings.FULL_AUTO_BUY_TIME_MS,
                MalfixTimings.FULL_AUTO_STORAGE_TAKE_MAX,
                MalfixTimings.AUTOSELL_SELL_MS
        );

        if (!cycleFullEnabled) {
            fullAutoTimedWaitingForCycle = false;
            fullAutoTimedEnabled = false;
            fullAutoTimedStopReason = "round_start_failed:" + cycleFullStopReason;
            chat.send("full-auto timed stopped: " + buildFullAutoTimedCompact());
        }
    }

    private void startCycleFullTimed(long buyTimeMs, int storageTakeMax, long sellDelayMs) {
        if (cycleFullEnabled) {
            chat.send("cyclefull timed already running: " + buildCycleFullCompact());
            return;
        }

        cycleFullTimedBuyMode = true;
        cycleFullBuyTimeMs = Math.max(5_000L, Math.min(600_000L, buyTimeMs));
        cycleFullSellUntilRent = true;
        cycleFullPreSellBeforeBuy = true;
        if (fullAutoTimedEnabled && fullAutoSkipNextPreSellStorage) {
            cycleFullPreSellBeforeBuy = false;
            chat.send("cyclefull timed: pre-sell storage skipped once after /ah rent. reason="
                    + fullAutoSkipNextPreSellStorageReason);
            fullAutoSkipNextPreSellStorage = false;
            fullAutoSkipNextPreSellStorageReason = "none";
        }
        cycleFullBuyCycles = 0;
        cycleFullBuyMax = 0;
        cycleFullSellMax = MalfixTimings.FULL_AUTO_SELL_MAX;
        cycleFullStorageTakeMax = Math.max(1, Math.min(54, storageTakeMax));

        long parsedSellDelay = sellDelayMs;
        if (parsedSellDelay < MalfixTimings.AUTOSELL_SELL_MS) {
            parsedSellDelay = MalfixTimings.AUTOSELL_SELL_MS;
        } else if (parsedSellDelay > 5000L) {
            parsedSellDelay = 5000L;
        }

        config.setLoopDelayMs(MalfixTimings.AB_UPDATE_MS);
        applyConfigToRuntime();
        saveConfig();

        cycleFullSellDelayMs = parsedSellDelay;
        cycleFullEnabled = true;
        cycleFullPhase = cycleFullPreSellBeforeBuy ? "pre_sell_open_ah" : "open_ah";
        cycleFullStartedAtMs = System.currentTimeMillis();
        cycleFullNextActionAtMs = 0L;
        cycleFullOpenAttempts = 0;
        cycleFullStopReason = "running";
        cycleFullStorageTaken = 0;
        cycleFullStorageAttempts = 0;
        cycleFullStorageNoItemChecks = 0;
        cycleFullStorageContinueAfterSell = false;
        cycleFullStorageStatus = "none";

        chat.send("cyclefull timed started: pre-sell storage first -> buy. buyTimeMs=" + cycleFullBuyTimeMs
                + ", storageTakeMax=" + cycleFullStorageTakeMax
                + ", sell=until_/ah_rent/no_items"
                + ", sellDelayMs=" + cycleFullSellDelayMs);

        if (cycleFullPreSellBeforeBuy) {
            cycleFullNextActionAtMs = 0L;
        } else {
            requestCycleFullAuctionOpen("timed_fullauto_start");
        }
    }

    private String buildFullAutoTimedCompact() {
        long elapsed = fullAutoTimedStartedAtMs <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - fullAutoTimedStartedAtMs);
        long delayLeft = Math.max(0L, fullAutoTimedNextStartAtMs - System.currentTimeMillis());
        return "enabled=" + fullAutoTimedEnabled
                + ", waitingCycle=" + fullAutoTimedWaitingForCycle
                + ", rounds=" + fullAutoTimedRoundsStarted
                + ", buyTimeMs=" + MalfixTimings.FULL_AUTO_BUY_TIME_MS
                + ", nextStartInMs=" + delayLeft
                + ", skipPreSellStorage=" + fullAutoSkipNextPreSellStorage
                + ", skipReason=" + fullAutoSkipNextPreSellStorageReason
                + ", rentStorageBlockLeftMs=" + getSellLimitStorageBlockLeftMs()
                + ", elapsedMs=" + elapsed
                + ", stopReason=" + fullAutoTimedStopReason;
    }


    public boolean isSellOnlyTimedEnabled() {
        return sellOnlyTimedEnabled;
    }

    public String getSellOnlyTimedCompact() {
        return buildSellOnlyTimedCompact();
    }

    private void toggleSellOnlyTimedFromKey() {
        if (sellOnlyTimedEnabled) {
            stopSellOnlyTimed("sellonly_key_stop");
            chat.send("sell-only disabled by key: " + buildSellOnlyTimedCompact());
            return;
        }

        startSellOnlyTimed(false, "sellonly_key_start");
    }

    private void handleSellOnlyCommand(String[] parts) {
        String action = parts != null && parts.length >= 3 ? parts[2].toLowerCase(Locale.ROOT) : "toggle";

        if ("status".equals(action) || "state".equals(action)) {
            chat.send("sell-only: " + buildSellOnlyTimedCompact());
            return;
        }

        if ("off".equals(action) || "stop".equals(action) || "disable".equals(action)) {
            stopSellOnlyTimed("manual_off");
            chat.send("sell-only disabled: " + buildSellOnlyTimedCompact());
            return;
        }

        if ("now".equals(action) || "test".equals(action) || "run".equals(action)) {
            if (!sellOnlyTimedEnabled) {
                startSellOnlyTimed(true, "manual_now");
            } else {
                sellOnlyPhase = "waiting";
                sellOnlyNextRoundAtMs = 0L;
                sellOnlyStopReason = "manual_now";
                chat.send("sell-only run now armed: " + buildSellOnlyTimedCompact());
            }
            return;
        }

        if ("on".equals(action) || "start".equals(action) || "enable".equals(action)) {
            startSellOnlyTimed(false, "manual_on");
            return;
        }

        if (sellOnlyTimedEnabled) {
            stopSellOnlyTimed("manual_toggle_off");
            chat.send("sell-only disabled: " + buildSellOnlyTimedCompact());
            return;
        }

        startSellOnlyTimed(false, "manual_toggle_on");
    }

    private void startSellOnlyTimed(boolean runNow, String reason) {
        if (sellOnlyTimedEnabled) {
            chat.send("sell-only already running: " + buildSellOnlyTimedCompact());
            return;
        }

        if (fullAutoTimedEnabled || cycleFullEnabled || cycleFullLoopEnabled || safeAutoRunEnabled
                || limitedLoop.isRunning() || oneCycle.isPending() || controlledBuyClick.isPending()
                || sellerLoopEnabled || sellerCycleEnabled || sellerReturnToAuctionPending) {
            chat.send("sell-only blocked: another action is active. fullAuto=" + buildFullAutoTimedCompact()
                    + ", cycleFull=" + buildCycleFullCompact()
                    + ", sellerLoop=" + buildSellerLoopCompact());
            return;
        }

        sellOnlyTimedEnabled = true;
        sellOnlyPhase = "waiting";
        sellOnlyRoundsStarted = 0;
        sellOnlyStartedAtMs = System.currentTimeMillis();
        sellOnlyNextActionAtMs = 0L;
        sellOnlyNextRoundAtMs = runNow ? 0L : sellOnlyStartedAtMs + MalfixTimings.SELL_ONLY_INTERVAL_MS;
        sellOnlyStopReason = reason == null ? "running" : reason;
        sellOnlyStorageTakeMax = MalfixTimings.SELL_ONLY_STORAGE_TAKE_MAX;
        sellOnlyStorageTaken = 0;
        sellOnlyStorageAttempts = 0;
        sellOnlyStorageNoItemChecks = 0;
        sellOnlyStorageContinueAfterSell = false;
        sellOnlyStorageStatus = "none";
        sellOnlySellDelayMs = MalfixTimings.AUTOSELL_SELL_MS;

        config.setAntiAfkEnabled(true);
        if (antiAfkNextAtMs <= 0L || antiAfkNextAtMs < System.currentTimeMillis()) {
            antiAfkNextAtMs = System.currentTimeMillis() + config.getAntiAfkIntervalMs();
        }
        saveConfig();

        closeScreenQuietly();
        chat.send("sell-only enabled: waits with auction closed, every 30s opens storage -> takes items -> sells only. "
                + "sellDelayMs=" + sellOnlySellDelayMs
                + ", nextRoundInMs=" + Math.max(0L, sellOnlyNextRoundAtMs - System.currentTimeMillis())
                + ", antiAfk=on");
    }

    private void stopSellOnlyTimed(String reason) {
        if (!sellOnlyTimedEnabled && "idle".equals(sellOnlyPhase)) {
            sellOnlyStopReason = reason == null ? "stopped" : reason;
            return;
        }

        sellOnlyTimedEnabled = false;
        sellOnlyPhase = "idle";
        sellOnlyNextActionAtMs = 0L;
        sellOnlyNextRoundAtMs = 0L;
        sellOnlyStopReason = reason == null ? "stopped" : reason;
        sellOnlyStorageTaken = 0;
        sellOnlyStorageAttempts = 0;
        sellOnlyStorageNoItemChecks = 0;
        sellOnlyStorageContinueAfterSell = false;
        sellOnlyStorageStatus = "none";

        if (sellerLoopEnabled) {
            stopSellerLoop("sell_only_stopped:" + sellOnlyStopReason);
        }
        sellerReturnToAuctionPending = false;
        closeScreenQuietly();
    }

    private void tickSellOnlyTimed() {
        if (!sellOnlyTimedEnabled) {
            return;
        }

        long now = System.currentTimeMillis();

        if (antiAfkRunning) {
            sellOnlyStopReason = "waiting_antiafk";
            return;
        }

        if (safeAutoRunEnabled || limitedLoop.isRunning() || oneCycle.isPending() || controlledBuyClick.isPending()
                || fullAutoTimedEnabled || cycleFullLoopEnabled) {
            sellOnlyStopReason = "waiting_other_action";
            sellOnlyNextActionAtMs = now + MalfixTimings.AUTOSELL_SELL_MS;
            return;
        }

        if ("waiting".equals(sellOnlyPhase)) {
            if (client != null && client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
                closeScreenQuietly();
            }

            if (sellOnlyNextRoundAtMs <= 0L) {
                sellOnlyNextRoundAtMs = now;
            }

            if (now < sellOnlyNextRoundAtMs) {
                return;
            }

            startSellOnlyRound(now);
            return;
        }

        if ("open_ah".equals(sellOnlyPhase)) {
            tickSellOnlyOpenAuction(now);
            return;
        }

        if ("storage_open".equals(sellOnlyPhase)) {
            tickSellOnlyStorageOpen(now);
            return;
        }

        if ("storage_take".equals(sellOnlyPhase)) {
            tickSellOnlyStorageTake(now);
            return;
        }

        if ("prepare_sell".equals(sellOnlyPhase)) {
            tickSellOnlyPrepareSell(now);
            return;
        }

        if ("sell_loop".equals(sellOnlyPhase)) {
            if (sellerLoopEnabled) {
                return;
            }
            if (sellOnlyStorageContinueAfterSell && sellerLoopSellsDone > 0) {
                sellOnlyStorageContinueAfterSell = false;
                sellOnlyPhase = "storage_open";
                sellOnlyStorageAttempts = 0;
                sellOnlyStorageNoItemChecks = 0;
                sellOnlyStorageTaken = 0;
                sellOnlyStorageStatus = "continue_storage_after_selling:" + sellerLoopStopReason;
                sellOnlyNextActionAtMs = now + MalfixTimings.AUTOSELL_OPEN_MS;
                chat.send("sell-only storage: continue after seller, storage probably still has items. sold="
                        + sellerLoopSellsDone + ", reason=" + sellerLoopStopReason);
                return;
            }
            finishSellOnlyRound("seller_finished:" + sellerLoopStopReason);
            return;
        }

        if ("closing".equals(sellOnlyPhase)) {
            finishSellOnlyRound(sellOnlyStopReason);
        }
    }

    private void startSellOnlyRound(long now) {
        sellOnlyRoundsStarted++;
        sellOnlyPhase = "open_ah";
        sellOnlyNextActionAtMs = 0L;
        sellOnlyStorageTaken = 0;
        sellOnlyStorageAttempts = 0;
        sellOnlyStorageNoItemChecks = 0;
        sellOnlyStorageContinueAfterSell = false;
        sellOnlyStorageStatus = "round_start";
        sellOnlyStopReason = "round_running";
        closeScreenQuietly();
        chat.send("sell-only round started: round=" + sellOnlyRoundsStarted
                + ", storageTakeMax=" + sellOnlyStorageTakeMax
                + ", sell=until_/ah_rent/no_items"
                + ", sellDelayMs=" + sellOnlySellDelayMs);
    }

    private void tickSellOnlyOpenAuction(long now) {
        if (now < sellOnlyNextActionAtMs) {
            return;
        }

        if (client == null || client.player == null) {
            sellOnlyNextActionAtMs = now + MalfixTimings.AUTOSELL_OPEN_MS;
            return;
        }

        if (auctionView.isAuctionOpen()) {
            sellOnlyPhase = "storage_open";
            sellOnlyNextActionAtMs = now;
            return;
        }

        if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
            closeScreenQuietly();
        }

        try {
            auctionView.requestOpenAuction();
            sellOnlyStopReason = "opening_ah";
            chat.send("sell-only opening /ah before storage: round=" + sellOnlyRoundsStarted);
        } catch (Throwable throwable) {
            chat.send("sell-only /ah open failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }

        sellOnlyNextActionAtMs = now + MalfixTimings.AUTOSELL_OPEN_MS;
    }

    private void tickSellOnlyStorageOpen(long now) {
        if (now < sellOnlyNextActionAtMs) {
            return;
        }

        if (isStorageScreenOpen()) {
            sellOnlyPhase = "storage_take";
            sellOnlyNextActionAtMs = now + MalfixTimings.STORAGE_TAKE_WAIT_MS;
            sellOnlyStorageStatus = "storage_already_open";
            return;
        }

        if (!auctionView.isAuctionOpen()) {
            sellOnlyPhase = "open_ah";
            sellOnlyNextActionAtMs = now;
            return;
        }

        int slotToClick = getStorageOpenSlotForAttempt(sellOnlyStorageAttempts);
        boolean clicked = auctionView.clickContainerSlot(slotToClick, 0);
        if (clicked) {
            MalfixProfiler.recordClick("storage_open");
        }
        sellOnlyStorageAttempts++;
        sellOnlyStorageStatus = clicked ? "storage_slot_clicked" : "storage_slot_click_failed";

        chat.send("sell-only storage: open clicked=" + clicked
                + ", slot=" + slotToClick
                + ", attempt=" + sellOnlyStorageAttempts
                + ", title=" + getCurrentScreenTitleSafe());

        if (!clicked && sellOnlyStorageAttempts >= 4) {
            sellOnlyStorageStatus = "storage_open_failed";
            sellOnlyPhase = "prepare_sell";
            sellOnlyNextActionAtMs = now + MalfixTimings.AUTOSELL_SELL_MS;
            return;
        }

        sellOnlyPhase = "storage_take";
        sellOnlyNextActionAtMs = now + MalfixTimings.STORAGE_OPEN_WAIT_MS;
    }

    private void tickSellOnlyStorageTake(long now) {
        if (now < sellOnlyNextActionAtMs) {
            return;
        }

        if (isStorageScreenOpen()) {
            StorageTakeStep step = takeOneStorageItemLikeOldSpooky(sellOnlyStorageTakeMax, sellOnlyStorageTaken);
            sellOnlyStorageStatus = step.status;

            if (step.moved) {
                sellOnlyStorageTaken++;
                sellOnlyStorageNoItemChecks = 0;
                sellOnlyStorageStatus = "took_one:slot=" + step.slot + ", total=" + sellOnlyStorageTaken;
                sellOnlyNextActionAtMs = now + MalfixTimings.STORAGE_ONE_TAKE_MS;
                return;
            }

            if (step.inventoryFull) {
                sellOnlyStorageContinueAfterSell = true;
                sellOnlyStorageStatus = "inventory_full_continue_after_sell:free=" + step.freeSlots
                        + ", total=" + sellOnlyStorageTaken;
                chat.send("sell-only storage: inventory filled, close storage -> unstack/sell -> reopen storage after selling. "
                        + sellOnlyStorageStatus);
                closeHandledScreenForInventoryClicks();
                sellOnlyPhase = "prepare_sell";
                sellOnlyNextActionAtMs = now + MalfixTimings.UNSTACK_PREPARE_WAIT_MS;
                return;
            }

            if (step.emptyStorage) {
                sellOnlyStorageNoItemChecks++;
                if (sellOnlyStorageNoItemChecks < MalfixTimings.STORAGE_EMPTY_RECHECKS && sellOnlyStorageTaken > 0) {
                    sellOnlyStorageStatus = "storage_empty_recheck:" + sellOnlyStorageNoItemChecks;
                    sellOnlyNextActionAtMs = now + MalfixTimings.STORAGE_EMPTY_RECHECK_MS;
                    return;
                }

                sellOnlyStorageContinueAfterSell = false;
                sellOnlyStorageStatus = "storage_empty:total=" + sellOnlyStorageTaken;
                chat.send("sell-only storage: empty, close storage -> sell. totalTaken=" + sellOnlyStorageTaken);
                closeHandledScreenForInventoryClicks();
                sellOnlyPhase = "prepare_sell";
                sellOnlyNextActionAtMs = now + MalfixTimings.UNSTACK_PREPARE_WAIT_MS;
                return;
            }

            sellOnlyNextActionAtMs = now + MalfixTimings.STORAGE_ONE_TAKE_MS;
            return;
        }

        if (sellOnlyStorageAttempts < 4) {
            sellOnlyPhase = "storage_open";
            sellOnlyNextActionAtMs = now + MalfixTimings.AB_RESELL_ITEM_MS;
            sellOnlyStorageStatus = "storage_screen_not_open_retry";
            return;
        }

        sellOnlyStorageStatus = "storage_screen_not_open";
        sellOnlyPhase = "prepare_sell";
        sellOnlyNextActionAtMs = now + MalfixTimings.AUTOSELL_SELL_MS;
    }

    private void tickSellOnlyPrepareSell(long now) {
        if (now < sellOnlyNextActionAtMs) {
            return;
        }

        if (client != null && client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
            closeHandledScreenForInventoryClicks();
            sellOnlyNextActionAtMs = now + MalfixTimings.UNSTACK_PREPARE_WAIT_MS;
            return;
        }

        if (sellerLoopEnabled || sellerCycleEnabled || sellerReturnToAuctionPending) {
            sellOnlyNextActionAtMs = now + MalfixTimings.AUTOSELL_SELL_MS;
            return;
        }

        String[] sellParts = new String[] {
                ".mab",
                "sellloop",
                "0",
                String.valueOf(sellOnlySellDelayMs)
        };

        chat.send("sell-only starting seller: sell=until_/ah_rent/no_items"
                + ", sellDelayMs=" + sellOnlySellDelayMs
                + ", storageStatus=" + sellOnlyStorageStatus
                + ", moved=" + sellOnlyStorageTaken);

        startSellerLoop(sellParts);

        if (!sellerLoopEnabled) {
            finishSellOnlyRound("seller_not_started:" + sellerLoopStopReason);
            return;
        }

        sellOnlyPhase = "sell_loop";
    }

    private boolean isSellOnlyInSellPhase() {
        return sellOnlyTimedEnabled && ("prepare_sell".equals(sellOnlyPhase) || "sell_loop".equals(sellOnlyPhase));
    }

    private boolean isSellOnlyCycleActive() {
        return sellOnlyTimedEnabled && !("waiting".equals(sellOnlyPhase) || "idle".equals(sellOnlyPhase));
    }

    private void finishSellOnlyRound(String reason) {
        if (!sellOnlyTimedEnabled) {
            return;
        }

        if (sellerLoopEnabled) {
            stopSellerLoop("sell_only_finish:" + reason);
        }

        sellerCycleEnabled = false;
        sellerCycleLastReason = "sell_only_finish";
        sellerReturnToAuctionPending = false;

        closeScreenQuietly();
        sellOnlyPhase = "waiting";
        sellOnlyNextRoundAtMs = System.currentTimeMillis() + MalfixTimings.SELL_ONLY_INTERVAL_MS;
        sellOnlyNextActionAtMs = 0L;
        sellOnlyStopReason = reason == null ? "round_done" : reason;

        chat.send("sell-only round done: " + buildSellOnlyTimedCompact());
    }

    private String buildSellOnlyTimedCompact() {
        long now = System.currentTimeMillis();
        long elapsed = sellOnlyStartedAtMs <= 0L ? 0L : Math.max(0L, now - sellOnlyStartedAtMs);
        long nextRound = Math.max(0L, sellOnlyNextRoundAtMs - now);
        long nextAction = Math.max(0L, sellOnlyNextActionAtMs - now);
        return "enabled=" + sellOnlyTimedEnabled
                + ", phase=" + sellOnlyPhase
                + ", rounds=" + sellOnlyRoundsStarted
                + ", intervalMs=" + MalfixTimings.SELL_ONLY_INTERVAL_MS
                + ", nextRoundInMs=" + nextRound
                + ", nextActionInMs=" + nextAction
                + ", sellDelayMs=" + sellOnlySellDelayMs
                + ", storageTaken=" + sellOnlyStorageTaken
                + ", storageStatus=" + sellOnlyStorageStatus
                + ", storageContinueAfterSell=" + sellOnlyStorageContinueAfterSell
                + ", elapsedMs=" + elapsed
                + ", reason=" + sellOnlyStopReason;
    }

    private String formatLoopLimit(int value) {
        return value == Integer.MAX_VALUE ? "time_mode" : String.valueOf(value);
    }

    private void startCycleFullLoop(String[] parts) {
        if (cycleFullLoopEnabled) {
            chat.send("cyclefullloop already running: " + buildCycleFullLoopCompact());
            return;
        }

        if (sellOnlyTimedEnabled || cycleFullEnabled || safeAutoRunEnabled || limitedLoop.isRunning() || oneCycle.isPending() || controlledBuyClick.isPending()) {
            chat.send("cyclefullloop blocked: cycle/buy/sell-only action is active. cycleFull=" + buildCycleFullCompact() + ", sellOnly=" + buildSellOnlyTimedCompact());
            return;
        }

        if (sellerLoopEnabled || sellerCycleEnabled || sellerReturnToAuctionPending) {
            chat.send("cyclefullloop blocked: seller/return is active. sellerLoop=" + buildSellerLoopCompact()
                    + ", sellerCycle=" + buildSellerCycleCompact()
                    + ", sellerReturn=" + buildSellerReturnCompact());
            return;
        }

        int loops = parsePositiveInt(parts, 2, 5);
        if (loops > 120) {
            loops = 120;
        }

        cycleFullLoopBuyCycles = parsePositiveInt(parts, 3, config.getDefaultLoopCycles());
        cycleFullLoopBuyMax = parsePositiveInt(parts, 4, config.getDefaultLoopBuys());
        cycleFullLoopSellMax = parsePositiveInt(parts, 5, 10);

        long sellDelay = parts != null && parts.length >= 7 ? parseLong(parts[6], MalfixTimings.AUTOSELL_SELL_MS) : MalfixTimings.AUTOSELL_SELL_MS;
        if (sellDelay < MalfixTimings.AUTOSELL_SELL_MS) {
            sellDelay = MalfixTimings.AUTOSELL_SELL_MS;
        } else if (sellDelay > 5000L) {
            sellDelay = 5000L;
        }

        long loopDelay = parts != null && parts.length >= 8 ? parseLong(parts[7], MalfixTimings.FULL_AUTO_LOOP_DELAY_MS) : MalfixTimings.FULL_AUTO_LOOP_DELAY_MS;
        if (loopDelay < MalfixTimings.AB_UPDATE_MS) {
            loopDelay = MalfixTimings.AB_UPDATE_MS;
        } else if (loopDelay > 30000L) {
            loopDelay = 30000L;
        }

        cycleFullLoopEnabled = true;
        cycleFullLoopWaitingForCycle = false;
        cycleFullLoopMaxCycles = Math.max(1, loops);
        cycleFullLoopCyclesStarted = 0;
        cycleFullLoopSellDelayMs = sellDelay;
        cycleFullLoopDelayMs = loopDelay;
        cycleFullLoopStartedAtMs = System.currentTimeMillis();
        cycleFullLoopNextStartAtMs = 0L;
        cycleFullLoopStopReason = "running";
        cycleFullLoopSkipNextSeller = false;
        cycleFullLoopSkipSellerReason = "none";

        config.setLoopDelayMs(MalfixTimings.AB_UPDATE_MS);
        applyConfigToRuntime();
        saveConfig();

        chat.send("cyclefullloop started: loops=" + cycleFullLoopMaxCycles
                + ", buyCycles=" + cycleFullLoopBuyCycles
                + ", buyMax=" + cycleFullLoopBuyMax
                + ", sellMax=" + cycleFullLoopSellMax
                + ", sellDelayMs=" + cycleFullLoopSellDelayMs
                + ", loopDelayMs=" + cycleFullLoopDelayMs
                + ", auctionRefreshMs=" + config.getLoopDelayMs()
                + ", timings=stable_jar");
    }

    private void stopCycleFullLoop(String reason) {
        if (!cycleFullLoopEnabled && !cycleFullLoopWaitingForCycle) {
            cycleFullLoopStopReason = reason == null ? "stopped" : reason;
            return;
        }

        cycleFullLoopEnabled = false;
        cycleFullLoopWaitingForCycle = false;
        cycleFullLoopSkipNextSeller = false;
        cycleFullLoopSkipSellerReason = "none";
        cycleFullLoopStopReason = reason == null ? "stopped" : reason;
    }

    private void tickCycleFullLoop() {
        if (!cycleFullLoopEnabled) {
            return;
        }

        long now = System.currentTimeMillis();

        if (cycleFullEnabled) {
            return;
        }

        if (cycleFullLoopWaitingForCycle) {
            cycleFullLoopWaitingForCycle = false;

            if (isCycleFullLoopTerminalStop(cycleFullStopReason)) {
                cycleFullLoopEnabled = false;
                cycleFullLoopStopReason = "terminal_cycle_stop:" + cycleFullStopReason;
                chat.send("cyclefullloop stopped: " + buildCycleFullLoopCompact()
                        + ", lastCycle=" + buildCycleFullCompact());
                return;
            }

            if (cycleFullLoopCyclesStarted >= cycleFullLoopMaxCycles) {
                cycleFullLoopEnabled = false;
                cycleFullLoopStopReason = "max_loops_reached";
                chat.send("cyclefullloop done: " + buildCycleFullLoopCompact());
                return;
            }

            cycleFullLoopNextStartAtMs = now + cycleFullLoopDelayMs;
            cycleFullLoopStopReason = "waiting_next_cycle";
            chat.send("cyclefullloop scheduled next cycle: " + buildCycleFullLoopCompact());
            return;
        }

        if (cycleFullLoopCyclesStarted >= cycleFullLoopMaxCycles) {
            cycleFullLoopEnabled = false;
            cycleFullLoopStopReason = "max_loops_reached";
            chat.send("cyclefullloop done: " + buildCycleFullLoopCompact());
            return;
        }

        if (now < cycleFullLoopNextStartAtMs) {
            return;
        }

        startNextCycleFullLoopCycle();
    }

    private void startNextCycleFullLoopCycle() {
        if (!cycleFullLoopEnabled) {
            return;
        }

        if (safeAutoRunEnabled || limitedLoop.isRunning() || oneCycle.isPending() || controlledBuyClick.isPending()) {
            cycleFullLoopEnabled = false;
            cycleFullLoopStopReason = "buy_action_active";
            chat.send("cyclefullloop stopped: " + buildCycleFullLoopCompact());
            return;
        }

        if (sellerLoopEnabled || sellerCycleEnabled || sellerReturnToAuctionPending) {
            cycleFullLoopNextStartAtMs = System.currentTimeMillis() + MalfixTimings.AUTOSELL_OPEN_MS;
            cycleFullLoopStopReason = "waiting_seller_idle";
            return;
        }

        cycleFullLoopCyclesStarted++;
        cycleFullLoopWaitingForCycle = true;
        cycleFullLoopStopReason = "cycle_running";

        String[] cycleParts = new String[] {
                ".mab",
                "cyclefull",
                String.valueOf(cycleFullLoopBuyCycles),
                String.valueOf(cycleFullLoopBuyMax),
                String.valueOf(cycleFullLoopSellMax),
                String.valueOf(cycleFullLoopSellDelayMs)
        };

        chat.send("cyclefullloop starting cycle " + cycleFullLoopCyclesStarted + "/" + cycleFullLoopMaxCycles
                + ": buyCycles=" + cycleFullLoopBuyCycles
                + ", buyMax=" + cycleFullLoopBuyMax
                + ", sellMax=" + cycleFullLoopSellMax
                + ", sellDelayMs=" + cycleFullLoopSellDelayMs);

        startCycleFull(cycleParts);

        if (!cycleFullEnabled) {
            cycleFullLoopWaitingForCycle = false;
            cycleFullLoopEnabled = false;
            cycleFullLoopStopReason = "cycle_start_failed:" + cycleFullStopReason;
            chat.send("cyclefullloop stopped: " + buildCycleFullLoopCompact());
        }
    }

    private boolean isCycleFullLoopTerminalStop(String reason) {
        if (reason == null) {
            return false;
        }

        String lower = reason.toLowerCase(Locale.ROOT);

        return lower.contains("no_money")
                || lower.contains("open_ah_failed")
                || lower.contains("buy_error_no_buys")
                || lower.contains("buy_loop_start_failed")
                || lower.contains("manual")
                || lower.contains("disabled")
                || lower.contains("cancel")
                || lower.contains("autobuy_action")
                || lower.contains("buy_action_active")
                || lower.contains("terminal");
    }

    private String buildCycleFullLoopCompact() {
        long elapsed = cycleFullLoopStartedAtMs <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - cycleFullLoopStartedAtMs);
        long delayLeft = Math.max(0L, cycleFullLoopNextStartAtMs - System.currentTimeMillis());

        return "enabled=" + cycleFullLoopEnabled
                + ", waitingCycle=" + cycleFullLoopWaitingForCycle
                + ", cycles=" + cycleFullLoopCyclesStarted + "/" + cycleFullLoopMaxCycles
                + ", buyCycles=" + cycleFullLoopBuyCycles
                + ", buyMax=" + cycleFullLoopBuyMax
                + ", sellMax=" + cycleFullLoopSellMax
                + ", sellDelayMs=" + cycleFullLoopSellDelayMs
                + ", loopDelayMs=" + cycleFullLoopDelayMs
                + ", skipNextSeller=" + cycleFullLoopSkipNextSeller
                + ", skipSellerReason=" + cycleFullLoopSkipSellerReason
                + ", delayLeftMs=" + delayLeft
                + ", elapsedMs=" + elapsed
                + ", stopReason=" + cycleFullLoopStopReason;
    }

    private void startCycleFull(String[] parts) {
        if (cycleFullEnabled) {
            chat.send("cyclefull already running: " + buildCycleFullCompact());
            return;
        }

        if (sellOnlyTimedEnabled || safeAutoRunEnabled || limitedLoop.isRunning() || oneCycle.isPending() || controlledBuyClick.isPending()) {
            chat.send("cyclefull blocked: autobuy/sell-only action is active. sellOnly=" + buildSellOnlyTimedCompact());
            return;
        }

        if (sellerLoopEnabled || sellerCycleEnabled) {
            chat.send("cyclefull blocked: seller is already running. sellerLoop=" + buildSellerLoopCompact()
                    + ", sellerCycle=" + buildSellerCycleCompact());
            return;
        }

        cycleFullTimedBuyMode = false;
        cycleFullBuyTimeMs = 0L;
        cycleFullSellUntilRent = false;
        cycleFullPreSellBeforeBuy = false;

        cycleFullBuyCycles = parsePositiveInt(parts, 2, config.getDefaultLoopCycles());
        cycleFullBuyMax = parsePositiveInt(parts, 3, config.getDefaultLoopBuys());
        cycleFullSellMax = parsePositiveInt(parts, 4, 10);

        long parsedSellDelay = parts != null && parts.length >= 6
                ? parseLong(parts[5], MalfixTimings.AUTOSELL_SELL_MS)
                : MalfixTimings.AUTOSELL_SELL_MS;

        if (parsedSellDelay < MalfixTimings.AUTOSELL_SELL_MS) {
            parsedSellDelay = MalfixTimings.AUTOSELL_SELL_MS;
        } else if (parsedSellDelay > 5000L) {
            parsedSellDelay = 5000L;
        }

        config.setLoopDelayMs(MalfixTimings.AB_UPDATE_MS);
        applyConfigToRuntime();
        saveConfig();

        cycleFullSellDelayMs = parsedSellDelay;
        cycleFullEnabled = true;
        cycleFullPhase = "open_ah";
        cycleFullStartedAtMs = System.currentTimeMillis();
        cycleFullNextActionAtMs = 0L;
        cycleFullOpenAttempts = 0;
        cycleFullStopReason = "running";

        chat.send("cyclefull started: buyCycles=" + cycleFullBuyCycles
                + ", buyMax=" + cycleFullBuyMax
                + ", sellMax=" + cycleFullSellMax
                + ", sellDelayMs=" + cycleFullSellDelayMs);

        requestCycleFullAuctionOpen("start");
    }

    private void stopCycleFull(String reason) {
        if (!cycleFullEnabled && "idle".equals(cycleFullPhase)) {
            cycleFullStopReason = reason == null ? "stopped" : reason;
            return;
        }

        cycleFullEnabled = false;
        cycleFullPhase = "stopped";
        cycleFullStopReason = reason == null ? "stopped" : reason;
        cycleFullTimedBuyMode = false;
        cycleFullBuyTimeMs = 0L;
        cycleFullSellUntilRent = false;
        cycleFullPreSellBeforeBuy = false;
    }

    private void tickCycleFull() {
        if (!cycleFullEnabled) {
            return;
        }

        long now = System.currentTimeMillis();

        if ("pre_sell_open_ah".equals(cycleFullPhase)) {
            tickCycleFullPreSellOpenAuction(now);
            return;
        }

        if ("open_ah".equals(cycleFullPhase)) {
            if (auctionView.isAuctionOpen()) {
                startCycleFullBuyLoop();
                return;
            }

            if (now >= cycleFullNextActionAtMs) {
                requestCycleFullAuctionOpen("retry");
            }

            return;
        }

        if ("buy_loop".equals(cycleFullPhase)) {
            if (!limitedLoop.isRunning() && lastAutoLoopResult != null && !lastAutoLoopResult.isRunning()) {
                handleCycleFullBuyLoopEvent(lastAutoLoopResult);
            }
            return;
        }

        if ("storage_open".equals(cycleFullPhase)) {
            tickCycleFullStorageOpen(now);
            return;
        }

        if ("storage_take".equals(cycleFullPhase)) {
            tickCycleFullStorageTake(now);
            return;
        }

        if ("prepare_sell".equals(cycleFullPhase)) {
            if (now < cycleFullNextActionAtMs) {
                return;
            }

            if (client != null && client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
                try {
                    client.setScreen(null);
                } catch (Throwable ignored) {
                }
                cycleFullNextActionAtMs = now + MalfixTimings.AUTOSELL_SELL_MS;
                return;
            }

            startCycleFullSellCycle();
            return;
        }

        if ("sell_cycle".equals(cycleFullPhase)) {
            if (!sellerLoopEnabled && !sellerCycleEnabled && !sellerReturnToAuctionPending) {
                if (cycleFullStorageContinueAfterSell && sellerLoopSellsDone > 0) {
                    if (wasSellLimitDetectedForCurrentCycle()) {
                        cycleFullStorageContinueAfterSell = false;
                        cycleFullStorageStatus = "skip_continue_storage_after_current_sell_limit:" + lastSellLimitReason;
                        chat.send("cyclefull storage: skip immediate continue after /ah rent. sold="
                                + sellerLoopSellsDone + ", reason=" + sellerLoopStopReason
                                + ", lastSellLimit=" + lastSellLimitReason);
                    } else {
                        cycleFullStorageContinueAfterSell = false;
                        cycleFullPhase = "storage_open";
                        cycleFullStorageAttempts = 0;
                        cycleFullStorageNoItemChecks = 0;
                        cycleFullStorageTaken = 0;
                        cycleFullStorageStatus = "continue_storage_after_selling:" + sellerLoopStopReason;
                        cycleFullNextActionAtMs = System.currentTimeMillis() + MalfixTimings.AUTOSELL_OPEN_MS;
                        chat.send("cyclefull storage: continue after seller, storage probably still has items. sold="
                                + sellerLoopSellsDone + ", reason=" + sellerLoopStopReason);
                        return;
                    }
                }
                if (cycleFullPreSellBeforeBuy) {
                    cycleFullPreSellBeforeBuy = false;
                    cycleFullPhase = "open_ah";
                    cycleFullOpenAttempts = 0;
                    cycleFullNextActionAtMs = 0L;
                    cycleFullStopReason = "pre_sell_done_start_buy";
                    chat.send("cyclefull pre-sell done: starting buy phase. " + buildCycleFullCompact());
                    return;
                }

                cycleFullEnabled = false;
                cycleFullPhase = "done";
                cycleFullStopReason = "done";
                chat.send("cyclefull done: " + buildCycleFullCompact());
            }
        }
    }

    private void tickCycleFullPreSellOpenAuction(long now) {
        if (!cycleFullEnabled) {
            return;
        }

        if (auctionView.isAuctionOpen()) {
            cycleFullPhase = "storage_open";
            cycleFullNextActionAtMs = 0L;
            cycleFullStorageTaken = 0;
            cycleFullStorageAttempts = 0;
            cycleFullStorageNoItemChecks = 0;
            cycleFullStorageContinueAfterSell = false;
            cycleFullStorageStatus = "pre_sell_storage_before_buy";
            return;
        }

        if (now < cycleFullNextActionAtMs) {
            return;
        }

        if (client == null || client.player == null) {
            cycleFullNextActionAtMs = now + MalfixTimings.AUTOSELL_OPEN_MS;
            return;
        }

        if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
            try {
                closeHandledScreenForInventoryClicks();
            } catch (Throwable ignored) {
            }
            cycleFullNextActionAtMs = now + MalfixTimings.AUTOSELL_UNSTACK_MS;
            return;
        }

        cycleFullOpenAttempts++;
        cycleFullNextActionAtMs = now + MalfixTimings.AUTOSELL_OPEN_MS;

        try {
            auctionView.requestOpenAuction();
            chat.send("cyclefull pre-sell opening /ah before buy: attempt=" + cycleFullOpenAttempts);
        } catch (Throwable throwable) {
            chat.send("cyclefull pre-sell /ah open failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }

        if (cycleFullOpenAttempts >= 8) {
            cycleFullPreSellBeforeBuy = false;
            cycleFullPhase = "open_ah";
            cycleFullOpenAttempts = 0;
            cycleFullNextActionAtMs = 0L;
            cycleFullStopReason = "pre_sell_open_failed_start_buy_anyway";
            chat.send("cyclefull pre-sell skipped: could not open /ah, starting buy anyway.");
        }
    }

    private void requestCycleFullAuctionOpen(String reason) {
        if (!cycleFullEnabled) {
            return;
        }

        if (client == null || client.player == null) {
            cycleFullNextActionAtMs = System.currentTimeMillis() + MalfixTimings.AUTOSELL_OPEN_MS;
            return;
        }

        if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen) && !auctionView.isAuctionOpen()) {
            try {
                client.setScreen(null);
            } catch (Throwable ignored) {
            }
        }

        if (auctionView.isAuctionOpen()) {
            cycleFullPhase = "open_ah";
            cycleFullNextActionAtMs = 0L;
            return;
        }

        cycleFullOpenAttempts++;
        cycleFullNextActionAtMs = System.currentTimeMillis() + MalfixTimings.AUTOSELL_OPEN_MS;

        try {
            auctionView.requestOpenAuction();
            chat.send("cyclefull opening /ah: attempt=" + cycleFullOpenAttempts + ", reason=" + reason);
        } catch (Throwable throwable) {
            chat.send("cyclefull /ah open failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }

        if (cycleFullOpenAttempts >= 8) {
            stopCycleFull("open_ah_failed");
            chat.send("cyclefull stopped: " + buildCycleFullCompact());
        }
    }

    private void startCycleFullBuyLoop() {
        if (!cycleFullEnabled) {
            return;
        }

        if (!auctionView.isAuctionOpen()) {
            cycleFullPhase = "open_ah";
            requestCycleFullAuctionOpen("auction_not_open_before_buy");
            return;
        }

        if (limitedLoop.isRunning()) {
            cycleFullPhase = "buy_loop";
            return;
        }

        limitedLoop.setDelayBetweenCyclesMs(config.getLoopDelayMs());
        if (cycleFullLoopEnabled || cycleFullTimedBuyMode || fullAutoTimedEnabled) {
            limitedLoop.setRefreshTimeoutMs(MalfixTimings.FULL_AUTO_REFRESH_TIMEOUT_MS);
            limitedLoop.setMaxRefreshFailStreak(MalfixTimings.FULL_AUTO_MAX_REFRESH_FAIL_STREAK);
        } else {
            limitedLoop.setRefreshTimeoutMs(config.getRefreshTimeoutMs());
            limitedLoop.setMaxRefreshFailStreak(config.getMaxRefreshFailStreak());
        }

        boolean started;
        if (cycleFullTimedBuyMode) {
            started = limitedLoop.startTimed(cycleFullBuyTimeMs);
        } else {
            started = limitedLoop.start(cycleFullBuyCycles, cycleFullBuyMax);
        }
        lastAutoLoopResult = limitedLoop.getLastResult();

        if (!started) {
            cycleFullEnabled = false;
            cycleFullPhase = "stopped";
            cycleFullStopReason = "buy_loop_start_failed";
            chat.send("cyclefull buy-loop start failed: " + lastAutoLoopResult.compact());
            scheduleSellerReturnToAuction("cyclefull:buy_loop_start_failed");
            return;
        }

        cycleFullPhase = "buy_loop";
        if (cycleFullTimedBuyMode) {
            chat.send("cyclefull timed buy-loop started: buyTimeMs=" + cycleFullBuyTimeMs
                    + ", delayMs=" + limitedLoop.getDelayBetweenCyclesMs());
        } else {
            chat.send("cyclefull buy-loop started: cycles=" + cycleFullBuyCycles
                    + ", buys=" + cycleFullBuyMax
                    + ", delayMs=" + limitedLoop.getDelayBetweenCyclesMs());
        }
    }

    private void handleCycleFullBuyLoopEvent(AutoLoopResult result) {
        if (!cycleFullEnabled || !"buy_loop".equals(cycleFullPhase) || result == null) {
            return;
        }

        if (result.isRunning() || limitedLoop.isRunning()) {
            return;
        }

        if (result.getStatus() == AutoLoopResult.Status.NO_MONEY_STOP) {
            cycleFullEnabled = false;
            cycleFullPhase = "stopped";
            cycleFullStopReason = "no_money";
            chat.send("cyclefull stopped: no money. " + result.compact());
            return;
        }

        if (result.getStatus() == AutoLoopResult.Status.ERROR_STOP && result.getBuysDone() <= 0) {
            cycleFullEnabled = false;
            cycleFullPhase = "stopped";
            cycleFullStopReason = "buy_error_no_buys";
            chat.send("cyclefull stopped: buy error before buys. " + result.compact());
            scheduleSellerReturnToAuction("cyclefull:buy_error_no_buys");
            return;
        }

        if (cycleFullLoopEnabled && cycleFullLoopSkipNextSeller) {
            cycleFullLoopSkipNextSeller = false;
            cycleFullPhase = "done";
            cycleFullStopReason = "seller_skipped_after_sell_limit";
            chat.send("cyclefull seller skipped after sell limit: buyStatus=" + result.getStatus()
                    + ", buys=" + result.getBuysDone() + "/" + formatLoopLimit(result.getMaxBuys())
                    + ", reason=" + cycleFullLoopSkipSellerReason);

            scheduleSellerReturnToAuction("cyclefull:seller_skipped_after_sell_limit");
            return;
        }

        cycleFullPhase = "storage_open";
        cycleFullNextActionAtMs = System.currentTimeMillis() + MalfixTimings.AUTOSELL_UNSTACK_MS;
        cycleFullStorageTaken = 0;
        cycleFullStorageAttempts = 0;
        cycleFullStorageNoItemChecks = 0;
        cycleFullStorageContinueAfterSell = false;
        cycleFullStorageStatus = "scheduled_after_buy";

        chat.send("cyclefull preparing storage relist before seller: buyStatus=" + result.getStatus()
                + ", buys=" + result.getBuysDone() + "/" + formatLoopLimit(result.getMaxBuys())
                + ", storageSlot=" + cycleFullStorageSlot
                + ", takeMax=" + cycleFullStorageTakeMax
                + ", then sell=" + (cycleFullSellUntilRent ? "until_/ah_rent" : String.valueOf(cycleFullSellMax)));
    }

    private void startStorageRelistCycle(String[] parts) {
        if (sellOnlyTimedEnabled || cycleFullEnabled || cycleFullLoopEnabled || limitedLoop.isRunning() || oneCycle.isPending()
                || controlledBuyClick.isPending() || sellerLoopEnabled || sellerCycleEnabled) {
            chat.send("storagecycle blocked: another cycle/action is active. sellOnly=" + buildSellOnlyTimedCompact());
            return;
        }

        int takeMax = parsePositiveInt(parts, 2, 36);
        int sellMax = parsePositiveInt(parts, 3, 10);
        long sellDelay = parts != null && parts.length >= 5 ? parseLong(parts[4], MalfixTimings.AUTOSELL_SELL_MS) : MalfixTimings.AUTOSELL_SELL_MS;

        if (sellDelay < MalfixTimings.AUTOSELL_SELL_MS) {
            sellDelay = MalfixTimings.AUTOSELL_SELL_MS;
        } else if (sellDelay > 5000L) {
            sellDelay = 5000L;
        }

        cycleFullTimedBuyMode = false;
        cycleFullBuyTimeMs = 0L;
        cycleFullSellUntilRent = false;
        cycleFullPreSellBeforeBuy = false;
        cycleFullStorageTakeMax = Math.max(1, Math.min(54, takeMax));
        cycleFullSellMax = Math.max(1, Math.min(64, sellMax));
        cycleFullSellDelayMs = sellDelay;
        cycleFullEnabled = true;
        cycleFullPhase = "storage_open";
        cycleFullStartedAtMs = System.currentTimeMillis();
        cycleFullNextActionAtMs = 0L;
        cycleFullOpenAttempts = 0;
        cycleFullStorageTaken = 0;
        cycleFullStorageAttempts = 0;
        cycleFullStorageNoItemChecks = 0;
        cycleFullStorageContinueAfterSell = false;
        cycleFullStorageStatus = "manual_storagecycle";
        cycleFullStopReason = "storagecycle_running";

        chat.send("storagecycle started: takeMax=" + cycleFullStorageTakeMax
                + ", sellMax=" + cycleFullSellMax
                + ", sellDelayMs=" + cycleFullSellDelayMs
                + ", storageSlot=" + cycleFullStorageSlot);
    }

    private boolean isRecentSellLimitBlockingStorage() {
        return false;
    }

    private long getSellLimitStorageBlockLeftMs() {
        return 0L;
    }

    private boolean wasSellLimitDetectedForCurrentCycle() {
        if (lastSellLimitAtMs <= 0L || cycleFullStartedAtMs <= 0L) {
            return false;
        }
        if (lastSellLimitAtMs < cycleFullStartedAtMs) {
            return false;
        }
        String reason = lastSellLimitReason == null ? "" : lastSellLimitReason.toLowerCase(Locale.ROOT);
        return reason.contains("rent") || reason.contains("limit") || reason.contains("slot");
    }

    private void tickCycleFullStorageOpen(long now) {
        if (now < cycleFullNextActionAtMs) {
            return;
        }

        // Step 22.77: do not use the Step 22.74 global sell-limit storage block here.
        // A /ah rent message means "stop the current sell continuation", not "disable
        // storage draining for the next real storage phase". The current continuation is
        // stopped in detectAndHandleSellLimitMessage() by clearing
        // cycleFullStorageContinueAfterSell/sellOnlyStorageContinueAfterSell. Blocking
        // tickCycleFullStorageOpen() itself caused FullAuto to stop taking storage items.

        if (client == null || client.player == null) {
            cycleFullNextActionAtMs = now + MalfixTimings.AUTOSELL_OPEN_MS;
            return;
        }

        if (isStorageScreenOpen()) {
            cycleFullPhase = "storage_take";
            cycleFullNextActionAtMs = now + MalfixTimings.STORAGE_TAKE_WAIT_MS;
            cycleFullStorageStatus = "storage_already_open";
            return;
        }

        if (!auctionView.isAuctionOpen()) {
            requestCycleFullAuctionOpen("storage_relist_needs_auction");
            cycleFullNextActionAtMs = now + MalfixTimings.AUTOSELL_OPEN_MS;
            return;
        }

        int slotToClick = getStorageOpenSlotForAttempt(cycleFullStorageAttempts);
        boolean clicked = auctionView.clickContainerSlot(slotToClick, 0);
        if (clicked) {
            MalfixProfiler.recordClick("storage_open");
        }
        cycleFullStorageAttempts++;
        cycleFullStorageStatus = clicked ? "storage_slot_clicked" : "storage_slot_click_failed";

        chat.send("storage relist: open storage clicked=" + clicked
                + ", slot=" + slotToClick
                + ", baseSlot=" + cycleFullStorageSlot
                + ", attempt=" + cycleFullStorageAttempts
                + ", title=" + getCurrentScreenTitleSafe());

        if (!clicked && cycleFullStorageAttempts >= 4) {
            chat.send("storage relist skipped: storage slot cannot be clicked, continue seller. title=" + getCurrentScreenTitleSafe());
            goCycleFullPrepareSell("storage_open_failed");
            return;
        }

        cycleFullPhase = "storage_take";
        cycleFullNextActionAtMs = now + MalfixTimings.STORAGE_OPEN_WAIT_MS;
    }


    private void tickCycleFullStorageTake(long now) {
        if (now < cycleFullNextActionAtMs) {
            return;
        }

        if (isStorageScreenOpen()) {
            StorageTakeStep step = takeOneStorageItemLikeOldSpooky(cycleFullStorageTakeMax, cycleFullStorageTaken);
            cycleFullStorageStatus = step.status;

            if (step.moved) {
                cycleFullStorageTaken++;
                cycleFullStorageNoItemChecks = 0;
                cycleFullStorageStatus = "took_one:slot=" + step.slot + ", total=" + cycleFullStorageTaken;
                cycleFullNextActionAtMs = now + MalfixTimings.STORAGE_ONE_TAKE_MS;
                return;
            }

            if (step.inventoryFull) {
                cycleFullStorageContinueAfterSell = true;
                cycleFullStorageStatus = "inventory_full_continue_after_sell:free=" + step.freeSlots
                        + ", total=" + cycleFullStorageTaken;
                chat.send("storage relist: inventory filled, close storage -> unstack/sell -> reopen storage after selling. "
                        + cycleFullStorageStatus);
                closeHandledScreenForInventoryClicks();
                goCycleFullPrepareSell("storage_inventory_full_continue");
                return;
            }

            if (step.emptyStorage) {
                cycleFullStorageNoItemChecks++;
                if (cycleFullStorageNoItemChecks < MalfixTimings.STORAGE_EMPTY_RECHECKS && cycleFullStorageTaken > 0) {
                    cycleFullStorageStatus = "storage_empty_recheck:" + cycleFullStorageNoItemChecks;
                    cycleFullNextActionAtMs = now + MalfixTimings.STORAGE_EMPTY_RECHECK_MS;
                    return;
                }

                cycleFullStorageContinueAfterSell = false;
                cycleFullStorageStatus = "storage_empty:total=" + cycleFullStorageTaken;
                chat.send("storage relist: empty, close storage -> sell. totalTaken=" + cycleFullStorageTaken);
                closeHandledScreenForInventoryClicks();
                goCycleFullPrepareSell("storage_empty");
                return;
            }

            cycleFullNextActionAtMs = now + MalfixTimings.STORAGE_ONE_TAKE_MS;
            return;
        }

        if (cycleFullStorageAttempts < 4) {
            cycleFullPhase = "storage_open";
            cycleFullNextActionAtMs = now + MalfixTimings.AB_RESELL_ITEM_MS;
            cycleFullStorageStatus = "storage_screen_not_open_retry";
            return;
        }

        chat.send("storage relist skipped: storage screen did not open. title=" + getCurrentScreenTitleSafe());
        goCycleFullPrepareSell("storage_screen_not_open");
    }

    private void goCycleFullPrepareSell(String reason) {
        cycleFullPhase = "prepare_sell";
        cycleFullNextActionAtMs = System.currentTimeMillis() + MalfixTimings.AUTOSELL_SELL_MS;
        cycleFullStorageStatus = reason == null ? cycleFullStorageStatus : reason;

        if (client != null && client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
            closeHandledScreenForInventoryClicks();
        }

        chat.send("cyclefull preparing sellcycle: storageStatus=" + cycleFullStorageStatus
                + ", moved=" + cycleFullStorageTaken
                + ", sellMax=" + cycleFullSellMax
                + ", sellDelayMs=" + cycleFullSellDelayMs);
    }

    private boolean isStorageScreenOpen() {
        if (client == null || client.currentScreen == null) {
            return false;
        }

        if (!(client.currentScreen instanceof GenericContainerScreen)) {
            return false;
        }

        String lower = getCurrentScreenTitleLower();
        return isStorageLikeTitle(lower);
    }

    private String getCurrentScreenTitleSafe() {
        if (client == null || client.currentScreen == null) {
            return "none";
        }

        try {
            return client.currentScreen.getTitle() == null ? "" : client.currentScreen.getTitle().getString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String getCurrentScreenTitleLower() {
        return getCurrentScreenTitleSafe().toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private boolean isStorageLikeTitle(String lowerTitle) {
        if (lowerTitle == null) {
            return false;
        }

        return lowerTitle.contains("храни")
                || lowerTitle.contains("storage")
                || lowerTitle.contains("склад")
                || lowerTitle.contains("vault")
                || lowerTitle.contains("мои предмет")
                || lowerTitle.contains("предметы на продаж")
                || lowerTitle.contains("предмет")
                || lowerTitle.contains("мои товар")
                || lowerTitle.contains("товары")
                || lowerTitle.contains("продаж");
    }

    private int getStorageOpenSlotForAttempt(int attempt) {
        if (attempt <= 0) {
            return cycleFullStorageSlot;
        }
        if (attempt == 1) {
            return Math.max(0, cycleFullStorageSlot - 1);
        }
        if (attempt == 2) {
            return cycleFullStorageSlot + 1;
        }
        return cycleFullStorageSlot;
    }

    private void setStorageSlotCommand(String[] parts) {
        int slot = parsePositiveInt(parts, 2, cycleFullStorageSlot);
        if (slot < 0) {
            slot = 0;
        }
        if (slot > 100) {
            slot = 100;
        }
        cycleFullStorageSlot = slot;
        chat.send("storage slot set for current session: " + cycleFullStorageSlot
                + ". Try: .mab storagecycle 36 10 300");
    }


    private StorageTakeStep takeOneStorageItemLikeOldSpooky(int maxItems, int alreadyTaken) {
        if (client == null || client.player == null || client.interactionManager == null) {
            return StorageTakeStep.waiting("client_not_ready");
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler.slots == null) {
            return StorageTakeStep.waiting("handler_not_ready");
        }

        if (maxItems > 0 && alreadyTaken >= maxItems) {
            return StorageTakeStep.inventoryFull("take_limit_reached", countPlayerInventoryEmptySlots());
        }

        int freeSlots = countPlayerInventoryEmptySlots();
        if (shouldStopStorageTakingForSell(freeSlots)) {
            return StorageTakeStep.inventoryFull("inventory_ready_for_sell", freeSlots);
        }

        int slotId = findFirstStorageItemSlot(handler);
        if (slotId < 0) {
            return StorageTakeStep.empty("storage_first_slots_empty");
        }

        try {
            // Old SpookyBuy resell used a normal PICKUP click on one storage slot at a time,
            // not a bulk QUICK_MOVE over the whole storage. The server storage then gives
            // exactly one stored resource/stack per click and shifts the next item into slot 0.
            if (client.player.networkHandler == null) {
                return StorageTakeStep.waiting("network_not_ready");
            }

            if (client.interactionManager == null) {
                return StorageTakeStep.waiting("interaction_manager_not_ready");
            }
            client.interactionManager.clickSlot(
                    handler.syncId,
                    slotId,
                    0,
                    SlotActionType.PICKUP,
                    client.player
            );
            MalfixProfiler.recordClick("storage_take");
            return StorageTakeStep.moved(slotId, freeSlots);
        } catch (Throwable throwable) {
            return StorageTakeStep.waiting("click_failed:" + throwable.getClass().getSimpleName());
        }
    }

    private boolean shouldStopStorageTakingForSell(int freeSlots) {
        if (freeSlots <= 0) {
            return true;
        }
        // If unstacking is enabled, keep one free destination slot for the old Spooky
        // unstack chain. Otherwise storage can fill all free slots before selling.
        return hasAnyUnstackTargetEnabled() && freeSlots <= 1;
    }

    private int findFirstStorageItemSlot(ScreenHandler handler) {
        if (handler == null || handler.slots == null) {
            return -1;
        }

        int maxSlot = getStorageItemSlotLimit(handler);
        for (int slotId = 0; slotId < maxSlot; slotId++) {
            try {
                Slot slot = handler.slots.get(slotId);
                if (slot == null || slot.getStack() == null || slot.getStack().isEmpty()) {
                    continue;
                }
                return slotId;
            } catch (Throwable ignored) {
            }
        }
        return -1;
    }

    private int getStorageItemSlotLimit(ScreenHandler handler) {
        int containerSlots = Math.max(0, handler.slots.size() - 36);
        int rows = 0;

        try {
            if (handler instanceof net.minecraft.screen.GenericContainerScreenHandler) {
                rows = ((net.minecraft.screen.GenericContainerScreenHandler) handler).getRows();
                containerSlots = rows * 9;
            }
        } catch (Throwable ignored) {
        }

        if (containerSlots <= 0 || containerSlots > handler.slots.size()) {
            containerSlots = Math.min(54, handler.slots.size());
        }

        // Last row is normally navigation/control buttons in /ah storage.
        if (rows >= 2) {
            return Math.min(Math.max(0, (rows - 1) * 9), handler.slots.size());
        }
        if (containerSlots >= 18) {
            return Math.min(Math.max(0, containerSlots - 9), handler.slots.size());
        }
        return Math.min(containerSlots, handler.slots.size());
    }

    private int quickMoveStorageItemsToInventory(int maxItems) {
        int moved = 0;
        for (int i = 0; i < Math.max(0, maxItems); i++) {
            StorageTakeStep step = takeOneStorageItemLikeOldSpooky(maxItems, moved);
            if (!step.moved) {
                break;
            }
            moved++;
        }
        return moved;
    }

    private static final class StorageTakeStep {
        final boolean moved;
        final boolean inventoryFull;
        final boolean emptyStorage;
        final int slot;
        final int freeSlots;
        final String status;

        private StorageTakeStep(boolean moved, boolean inventoryFull, boolean emptyStorage, int slot, int freeSlots, String status) {
            this.moved = moved;
            this.inventoryFull = inventoryFull;
            this.emptyStorage = emptyStorage;
            this.slot = slot;
            this.freeSlots = freeSlots;
            this.status = status == null ? "none" : status;
        }

        static StorageTakeStep moved(int slot, int freeSlots) {
            return new StorageTakeStep(true, false, false, slot, freeSlots, "moved");
        }

        static StorageTakeStep inventoryFull(String status, int freeSlots) {
            return new StorageTakeStep(false, true, false, -1, freeSlots, status);
        }

        static StorageTakeStep empty(String status) {
            return new StorageTakeStep(false, false, true, -1, 0, status);
        }

        static StorageTakeStep waiting(String status) {
            return new StorageTakeStep(false, false, false, -1, 0, status);
        }
    }

    private boolean hasAnyUnstackTargetEnabled() {
        try {
            if (config == null || config.getTargets() == null) {
                return false;
            }
            for (TargetConfig target : config.getTargets()) {
                if (target != null && target.isUnstack() && target.getUnstackAmount() > 0) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private int countPlayerInventoryEmptySlots() {
        try {
            if (client == null || client.player == null || client.player.getInventory() == null) {
                return 0;
            }
            int empty = 0;
            for (int i = 0; i < 36; i++) {
                if (client.player.getInventory().getStack(i).isEmpty()) {
                    empty++;
                }
            }
            return empty;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void startCycleFullSellCycle() {
        if (!cycleFullEnabled || !"prepare_sell".equals(cycleFullPhase)) {
            return;
        }

        if (hasBlockingScreenOpen()) {
            cycleFullNextActionAtMs = System.currentTimeMillis() + MalfixTimings.AUTOSELL_SELL_MS;
            chat.send("cyclefull waiting before sellcycle: close GUI first. screen=" + currentScreenName());
            return;
        }

        cycleFullPhase = "sell_cycle";

        String[] sellParts = new String[] {
                ".mab",
                "sellcycle",
                cycleFullSellUntilRent ? "0" : String.valueOf(cycleFullSellMax),
                String.valueOf(cycleFullSellDelayMs)
        };

        chat.send("cyclefull starting sellcycle: sell=" + (cycleFullSellUntilRent ? "until_/ah_rent" : String.valueOf(cycleFullSellMax))
                + ", sellDelayMs=" + cycleFullSellDelayMs);

        startSellerCycle(sellParts);

        if (!sellerLoopEnabled && !sellerCycleEnabled) {
            scheduleSellerReturnToAuction("cyclefull:sellcycle_not_started");
        }
    }

    private String buildCycleFullCompact() {
        long elapsed = cycleFullStartedAtMs <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - cycleFullStartedAtMs);
        long delayLeft = Math.max(0L, cycleFullNextActionAtMs - System.currentTimeMillis());

        return "enabled=" + cycleFullEnabled
                + ", phase=" + cycleFullPhase
                + ", timedBuy=" + cycleFullTimedBuyMode
                + ", preSellBeforeBuy=" + cycleFullPreSellBeforeBuy
                + ", buyTimeMs=" + cycleFullBuyTimeMs
                + ", buyCycles=" + cycleFullBuyCycles
                + ", buyMax=" + cycleFullBuyMax
                + ", sell=" + (cycleFullSellUntilRent ? "until_/ah_rent" : String.valueOf(cycleFullSellMax))
                + ", sellDelayMs=" + cycleFullSellDelayMs
                + ", storageSlot=" + cycleFullStorageSlot
                + ", storageTaken=" + cycleFullStorageTaken
                + ", storageStatus=" + cycleFullStorageStatus
                + ", storageContinueAfterSell=" + cycleFullStorageContinueAfterSell
                + ", rentStorageBlockLeftMs=" + getSellLimitStorageBlockLeftMs()
                + ", openAttempts=" + cycleFullOpenAttempts
                + ", delayLeftMs=" + delayLeft
                + ", elapsedMs=" + elapsed
                + ", stopReason=" + cycleFullStopReason;
    }

    private void scheduleSellerReturnToAuction(String reason) {
        sellerReturnToAuctionPending = true;
        sellerReturnToAuctionAtMs = System.currentTimeMillis() + MalfixTimings.SELLER_RETURN_AUCTION_MS;
        sellerReturnToAuctionReason = reason == null ? "unknown" : reason;
        sellerReturnToAuctionAttempts = 0;
        chat.send("seller return scheduled: /ah in " + MalfixTimings.SELLER_RETURN_AUCTION_MS + "ms, reason=" + sellerReturnToAuctionReason);
    }

    private void tickSellerReturnToAuction() {
        if (!sellerReturnToAuctionPending) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < sellerReturnToAuctionAtMs) {
            return;
        }

        if (client == null || client.player == null) {
            sellerReturnToAuctionAtMs = now + MalfixTimings.AUTOSELL_OPEN_MS;
            return;
        }

        if (safeAutoRunEnabled || limitedLoop.isRunning() || oneCycle.isPending() || controlledBuyClick.isPending()) {
            sellerReturnToAuctionPending = false;
            chat.send("seller return cancelled: autobuy action became active, reason=" + sellerReturnToAuctionReason);
            return;
        }

        if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
            try {
                client.setScreen(null);
            } catch (Throwable ignored) {
            }

            sellerReturnToAuctionAttempts++;
            sellerReturnToAuctionAtMs = now + MalfixTimings.AUTOSELL_SELL_MS;

            if (sellerReturnToAuctionAttempts <= 3) {
                return;
            }
        }

        try {
            McChat.send(client, "/ah");
            sellerReturnToAuctionPending = false;
            chat.send("seller returned to auction: /ah sent, reason=" + sellerReturnToAuctionReason);
        } catch (Throwable throwable) {
            sellerReturnToAuctionAttempts++;
            if (sellerReturnToAuctionAttempts >= 5) {
                sellerReturnToAuctionPending = false;
                chat.send("seller return failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            } else {
                sellerReturnToAuctionAtMs = now + MalfixTimings.AUTOSELL_OPEN_MS;
            }
        }
    }

    private String buildSellerReturnCompact() {
        long delayLeft = Math.max(0L, sellerReturnToAuctionAtMs - System.currentTimeMillis());
        return "pending=" + sellerReturnToAuctionPending
                + ", delayLeftMs=" + delayLeft
                + ", attempts=" + sellerReturnToAuctionAttempts
                + ", reason=" + sellerReturnToAuctionReason;
    }

    private void startSellerCycle(String[] parts) {
        if (sellerLoopEnabled) {
            chat.send("sellcycle blocked: seller-loop is already running. " + buildSellerLoopCompact());
            return;
        }

        sellerCycleEnabled = true;
        sellerCycleLastReason = "running";
        startSellerLoop(parts);

        if (!sellerLoopEnabled) {
            sellerCycleEnabled = false;
            sellerCycleLastReason = "sellloop_not_started";
            return;
        }

        chat.send("sellcycle started: sell first, then return to /ah. " + buildSellerLoopCompact());
    }

    private void finishSellerCycleAfterLoop(String reason) {
        if (!sellerCycleEnabled) {
            return;
        }

        sellerCycleEnabled = false;
        sellerCycleLastReason = reason == null ? "finished" : reason;
        scheduleSellerReturnToAuction("sellcycle:" + sellerCycleLastReason);
    }

    private String buildSellerCycleCompact() {
        return "enabled=" + sellerCycleEnabled
                + ", lastReason=" + sellerCycleLastReason;
    }

    private void startSellerLoop(String[] parts) {
        int maxSells = 10;
        boolean untilRent = false;
        long delayMs = MalfixTimings.AUTOSELL_SELL_MS;

        if (parts.length >= 3) {
            long parsed = parseLong(parts[2], maxSells);
            if (parsed <= 0L) {
                untilRent = true;
                maxSells = Integer.MAX_VALUE;
            } else {
                maxSells = (int) Math.min(512L, parsed);
            }
        }

        if (parts.length >= 4) {
            long parsedDelay = parseLong(parts[3], delayMs);
            // Never-style sell cycle: use AUTOSELL_SELL_MS as the lower action gate.
            // Values below it can spam /ah sell and trigger server anti-spam.
            if (parsedDelay >= MalfixTimings.AUTOSELL_SELL_MS) {
                delayMs = Math.min(5000L, parsedDelay);
            }
        }

        if (safeAutoRunEnabled || limitedLoop.isRunning() || oneCycle.isPending() || controlledBuyClick.isPending()) {
            chat.send("seller-loop blocked: autobuy action is active.");
            return;
        }

        if (hasBlockingScreenOpen()) {
            chat.send("seller-loop blocked: close current GUI first. screen=" + currentScreenName());
            return;
        }

        // Step 22.34: apply GUI/config once at seller start instead of rebuilding
        // target/scanner objects on every sell tick. This reduces CPU spikes when
        // several launchers are selling at the same time.
        applyConfigToRuntime();

        sellerLoopEnabled = true;
        sellerLoopUntilRent = untilRent;
        sellerLoopMaxSells = untilRent ? Integer.MAX_VALUE : Math.max(1, maxSells);
        sellerLoopSellsDone = 0;
        sellerLoopDelayMs = Math.max(MalfixTimings.AUTOSELL_UNSTACK_MS, delayMs);
        sellerLoopNextAtMs = 0L;
        sellerLoopStopReason = "running";
        sellerUnstackPrepareActive = false;
        sellerUnstackPrepareAtMs = 0L;
        sellerUnstackPrepareReason = "none";
        sellerAwaitingServerResult = false;
        sellerAwaitingServerResultSinceMs = 0L;
        sellerAwaitingServerCommand = "";

        chat.send("seller-loop started: max=" + (sellerLoopUntilRent ? "until_/ah_rent" : String.valueOf(sellerLoopMaxSells)) + ", delayMs=" + sellerLoopDelayMs);
    }

    private void stopSellerLoop(String reason) {
        if (!sellerLoopEnabled && sellerLoopSellsDone == 0) {
            sellerLoopStopReason = reason == null ? "stopped" : reason;
            sellerLoopUntilRent = false;
            sellerUnstackPrepareActive = false;
            sellerUnstackPrepareAtMs = 0L;
            sellerAwaitingServerResult = false;
            sellerAwaitingServerResultSinceMs = 0L;
            sellerAwaitingServerCommand = "";
            return;
        }

        sellerLoopEnabled = false;
        sellerLoopStopReason = reason == null ? "stopped" : reason;
        sellerLoopUntilRent = false;
        sellerUnstackPrepareActive = false;
        sellerUnstackPrepareAtMs = 0L;
        sellerAwaitingServerResult = false;
        sellerAwaitingServerResultSinceMs = 0L;
        sellerAwaitingServerCommand = "";
    }

    private void tickSellerLoop() {
        if (!sellerLoopEnabled) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < sellerLoopNextAtMs) {
            return;
        }

        if (sellerAwaitingServerResult) {
            long waited = Math.max(0L, now - sellerAwaitingServerResultSinceMs);
            if (waited < MalfixTimings.SELLER_RESULT_WAIT_TIMEOUT_MS) {
                sellerLoopNextAtMs = now + 100L;
                return;
            }

            sellerAwaitingServerResult = false;
            sellerAwaitingServerCommand = "";
            sellerLoopNextAtMs = now + sellerLoopDelayMs;
            return;
        }

        if (safeAutoRunEnabled || limitedLoop.isRunning() || oneCycle.isPending() || controlledBuyClick.isPending()) {
            stopSellerLoop("autobuy_action_active");
            chat.send("seller-loop stopped: " + buildSellerLoopCompact());
            finishSellerCycleAfterLoop("autobuy_action_active");
            return;
        }

        if (!sellerLoopUntilRent && sellerLoopSellsDone >= sellerLoopMaxSells) {
            stopSellerLoop("max_sells_reached");
            chat.send("seller-loop done: " + buildSellerLoopCompact());
            finishSellerCycleAfterLoop("max_sells_reached");
            return;
        }

        if (tickSellerUnstackBeforeSell(now)) {
            return;
        }

        if (hasBlockingScreenOpen()) {
            stopSellerLoop("blocking_screen:" + currentScreenName());
            chat.send("seller-loop stopped: " + buildSellerLoopCompact());
            finishSellerCycleAfterLoop("blocking_screen");
            return;
        }

        SellerResult preview = sellerController.previewNextSell();
        lastSellerResult = preview;

        if (preview == null || !preview.hasFoundItem()) {
            stopSellerLoop("no_matching_item");
            chat.send("seller-loop stopped: " + buildSellerLoopCompact());
            finishSellerCycleAfterLoop("no_matching_item");
            return;
        }

        if (preview.getTarget() == null || preview.getTarget().getSellUnitPrice() <= 0L) {
            stopSellerLoop("sell_price_zero");
            chat.send("seller-loop stopped: " + buildSellerLoopCompact());
            finishSellerCycleAfterLoop("sell_price_zero");
            return;
        }

        // Step 22.42: old SpookyBuy does not require an empty hotbar slot for main
        // inventory selling. It uses ClientPlayerInteractionManager.pickFromInventory
        // (method_2916) and then sends /ah sell. Requiring an empty hotbar forced
        // extra SWAP clicks and caused visible hand/inventory lag.

        sellerLoopSellsDone++;
        sellerLoopNextAtMs = now + sellerLoopDelayMs;

        if (sellerVerboseLogs()) {
            chat.send("seller-loop step: " + sellerLoopSellsDone + "/" + (sellerLoopUntilRent ? "until_/ah_rent" : String.valueOf(sellerLoopMaxSells))
                    + ", item=" + preview.getItem().getDisplayName()
                    + ", unit=" + moneyFormat.format(preview.getUnitPrice())
                    + ", total=" + moneyFormat.format(preview.getTotalPrice())
                    + ", handPlan=" + preview.getHandPlan());
        }

        runSellerRealHandOnly(preview);
    }

    /**
     * Old SpookyBuy did not split stacks directly from the auction/storage phase.
     * It first leaves every container GUI, waits a short safe delay, and only then clicks
     * the player inventory handler (syncId 0). Without this preparation the server
     * can ignore the pickup/right-click sequence and the seller immediately continues.
     */
    private boolean tickSellerUnstackBeforeSell(long now) {
        boolean needs = unstackController.needsUnstack();
        if (!needs) {
            sellerUnstackPrepareActive = false;
            sellerUnstackPrepareAtMs = 0L;
            sellerUnstackPrepareReason = "none";
            return false;
        }

        if (!unstackController.canUnstackNow()) {
            sellerUnstackPrepareActive = false;
            sellerUnstackPrepareAtMs = 0L;
            sellerLoopNextAtMs = now + Math.max(300L, MalfixTimings.UNSTACK_SELL_SPLIT_MS);
            chat.send("seller-loop waiting empty slot for unstack: " + unstackController.compact());
            return true;
        }

        if (client != null && client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
            String screenBeforeClose = currentScreenName();
            closeHandledScreenForInventoryClicks();
            sellerUnstackPrepareActive = true;
            sellerUnstackPrepareReason = "closed_screen:" + screenBeforeClose;
            sellerUnstackPrepareAtMs = now + MalfixTimings.UNSTACK_PREPARE_WAIT_MS;
            sellerLoopNextAtMs = now + Math.max(MalfixTimings.AUTOSELL_UNSTACK_MS, 200L);
            chat.send("seller-loop preparing old unstack: close handled GUI first. " + unstackController.compact());
            return true;
        }

        if (!sellerUnstackPrepareActive) {
            sellerUnstackPrepareActive = true;
            sellerUnstackPrepareReason = "old_spooky_wait_before_clicks";
            sellerUnstackPrepareAtMs = now + MalfixTimings.UNSTACK_PREPARE_WAIT_MS;
            sellerLoopNextAtMs = now + Math.max(MalfixTimings.AUTOSELL_UNSTACK_MS, 200L);
            chat.send("seller-loop preparing old unstack: wait_before_clicks=600ms. " + unstackController.compact());
            return true;
        }

        if (now < sellerUnstackPrepareAtMs) {
            sellerLoopNextAtMs = Math.min(sellerUnstackPrepareAtMs, now + Math.max(MalfixTimings.AUTOSELL_UNSTACK_MS, 200L));
            return true;
        }

        boolean waitForUnstack = unstackController.tick();
        sellerLoopNextAtMs = now + Math.max(MalfixTimings.AUTOSELL_UNSTACK_MS, MalfixTimings.UNSTACK_SELL_SPLIT_MS);
        chat.send("seller-loop old unstack tick: running=" + waitForUnstack
                + ", prepare=" + sellerUnstackPrepareReason
                + ", " + unstackController.compact());

        if (!unstackController.needsUnstack()) {
            sellerUnstackPrepareActive = false;
            sellerUnstackPrepareAtMs = 0L;
            sellerUnstackPrepareReason = "done";
        }

        return waitForUnstack || unstackController.needsUnstack();
    }

    private boolean sellerVerboseLogs() {
        // Step 22.39: old Spooky-style selling keeps the hot path quiet.
        // Start/stop/errors still print, but per-item progress is disabled to avoid
        // stdout/formatting FPS spikes when several launchers sell at once.
        return false;
    }

    private String buildSellerLoopCompact() {
        long delayLeft = Math.max(0L, sellerLoopNextAtMs - System.currentTimeMillis());
        return "enabled=" + sellerLoopEnabled
                + ", sells=" + sellerLoopSellsDone + "/" + (sellerLoopUntilRent ? "until_/ah_rent" : String.valueOf(sellerLoopMaxSells))
                + ", delayMs=" + sellerLoopDelayMs
                + ", delayLeftMs=" + delayLeft
                + ", awaitingServer=" + sellerAwaitingServerResult
                + ", awaitingLeftMs=" + (sellerAwaitingServerResult ? Math.max(0L, MalfixTimings.SELLER_RESULT_WAIT_TIMEOUT_MS - (System.currentTimeMillis() - sellerAwaitingServerResultSinceMs)) : 0L)
                + ", unstackPrepare=" + sellerUnstackPrepareActive
                + ", unstackPrepareLeftMs=" + Math.max(0L, sellerUnstackPrepareAtMs - System.currentTimeMillis())
                + ", unstackReason=" + sellerUnstackPrepareReason
                + ", stopReason=" + sellerLoopStopReason;
    }

    private void runSellerRealHandOnly() {
        runSellerRealHandOnly(null);
    }

    private void runSellerRealHandOnly(SellerResult precheckedResult) {
        if (safeAutoRunEnabled || limitedLoop.isRunning() || oneCycle.isPending() || controlledBuyClick.isPending()) {
            chat.send("sellreal blocked: autobuy action is active.");
            return;
        }

        if (hasBlockingScreenOpen()) {
            chat.send("sellreal blocked: close current GUI first. screen=" + currentScreenName());
            return;
        }

        // Step 22.34: sell-loop already scanned the inventory once. Reuse that result
        // instead of scanning all 36 slots twice per item.
        lastSellerResult = precheckedResult == null ? sellerController.previewNextSell() : precheckedResult;

        if (!validateSellerResultForReal(lastSellerResult)) {
            return;
        }

        if (lastSellerResult.isSelectedHandMatches() && "READY_IN_HAND".equals(lastSellerResult.getHandPlan())) {
            sendSellerCommand(lastSellerResult, "hand-ready");
            return;
        }

        if (lastSellerResult.isDirectHotbarSelectPossible() && lastSellerResult.getItem() != null) {
            selectHotbarAndSell(lastSellerResult);
            return;
        }

        if (lastSellerResult.getItem() != null
                && lastSellerResult.getItem().getInventorySlot() >= 9
                && lastSellerResult.getItem().getInventorySlot() <= 35) {
            moveMainInventoryToHotbarAndSell(lastSellerResult);
            return;
        }

        chat.send("sellreal blocked: item is not in selected hand/hotbar/main-inventory. handPlan=" + lastSellerResult.getHandPlan()
                + ", slot=" + lastSellerResult.getItem().getInventorySlot()
                + ", selectedHotbarSlot=" + lastSellerResult.getSelectedHotbarSlot());
    }

    private boolean validateSellerResultForReal(SellerResult result) {
        if (result == null || !result.hasFoundItem()) {
            chat.send("sellreal blocked: " + (result == null ? "null_result" : result.compact()));
            return false;
        }

        if (result.getTarget() == null || result.getTarget().getSellUnitPrice() <= 0L) {
            chat.send("sellreal blocked: sell price is 0. Set lower price field in GUI first.");
            return false;
        }

        if (result.getUnitPrice() <= 0L || result.getTotalPrice() <= 0L) {
            chat.send("sellreal blocked: bad sell price. " + result.compact());
            return false;
        }

        if (result.getCommand() == null || result.getCommand().trim().isEmpty()) {
            chat.send("sellreal blocked: empty sell command.");
            return false;
        }

        if (result.getItem() == null) {
            chat.send("sellreal blocked: empty item result.");
            return false;
        }

        return true;
    }

    private void selectHotbarAndSell(final SellerResult result) {
        final int slot = result.getItem().getInventorySlot();

        if (slot < 0 || slot > 8) {
            chat.send("sellreal blocked: selected item is not hotbar slot. slot=" + slot);
            return;
        }

        if (sellerVerboseLogs()) {
            chat.send("sellreal hotbar select: slot=" + slot
                    + ", item=" + result.getItem().getDisplayName()
                    + ", command=" + result.getCommand());
        }

        // Step 22.39: old SpookyBuy does not rescan the whole inventory after selecting
        // a hotbar slot. It trusts the already matched item and sends /ah sell directly.
        try {
            if (client == null || client.player == null || client.player.getInventory() == null) {
                chat.send("sellreal hotbar failed: player/inventory is null");
                return;
            }

            if (!selectHotbarSlot(slot)) {
                chat.send("sellreal hotbar failed: could not select slot=" + slot);
                return;
            }
            MalfixProfiler.recordClick("seller_hotbar_select");

            sendSellerCommand(result, "hotbar-selected-fast");
        } catch (Throwable throwable) {
            chat.send("sellreal hotbar failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    private void moveMainInventoryToHotbarAndSell(final SellerResult result) {
        if (result == null || result.getItem() == null) {
            chat.send("sellreal move blocked: result/item is null");
            return;
        }

        final int sourceInventorySlot = result.getItem().getInventorySlot();

        if (sourceInventorySlot < 9 || sourceInventorySlot > 35) {
            chat.send("sellreal move blocked: source is not main inventory. slot=" + sourceInventorySlot);
            return;
        }

        if (sellerVerboseLogs()) {
            chat.send("sellreal old-spooky pick: invSlot=" + sourceInventorySlot
                    + ", item=" + result.getItem().getDisplayName());
        }

        // Step 22.42: donor SpookyBuy sells main-inventory items through
        // ClientPlayerInteractionManager.pickFromInventory(int) / intermediary
        // method_2916(int). This is lighter than our previous SWAP-to-empty-hotbar
        // path and avoids the lag spike that happened exactly when the item was put
        // into the visible hand.
        try {
            if (client == null || client.player == null || client.player.getInventory() == null || client.interactionManager == null) {
                chat.send("sellreal pick failed: client/player/inventory/interactionManager is null");
                return;
            }

            closeHandledScreenForInventoryClicks();

            boolean picked = oldSpookyPickFromInventory(sourceInventorySlot);
            if (picked) {
                MalfixProfiler.recordClick("seller_pick_from_inventory");
            }

            // In 1.21.4 some clients expose pickFromInventory/method_2916 but it may
            // not put the target stack into the selected hand reliably after the port.
            // If the selected hand still does not look like the matched stack, use a
            // direct player-inventory SWAP fallback. This fixes the sell cycle stopping
            // after hotbar items: main inventory slots 9-35 are moved into the selected
            // hotbar slot and then /ah sell is sent for that stack.
            if (!selectedHandLooksLikeSellerResult(result)) {
                if (!swapMainInventorySlotToSelectedHotbar(sourceInventorySlot)) {
                    chat.send("sellreal pick failed: cannot move main inventory slot to hand. slot=" + sourceInventorySlot);
                    return;
                }
                MalfixProfiler.recordClick("seller_swap_main_inventory_to_hotbar");
            }

            if (!selectedHandLooksLikeSellerResult(result)) {
                chat.send("sellreal pick failed: selected hand did not become target item. slot=" + sourceInventorySlot
                        + ", expected=" + result.getItem().compact());
                return;
            }

            sendSellerCommand(result, picked ? "main-inventory-old-spooky-pick-verified" : "main-inventory-swap-fallback");
        } catch (Throwable throwable) {
            chat.send("sellreal pick failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    private boolean oldSpookyPickFromInventory(int inventorySlot) {
        if (client == null || client.interactionManager == null) {
            return false;
        }
        if (inventorySlot < 0 || inventorySlot > 35) {
            return false;
        }

        String[] methodNames = new String[] {"pickFromInventory", "method_2916"};
        for (String methodName : methodNames) {
            try {
                java.lang.reflect.Method method = client.interactionManager.getClass().getMethod(methodName, int.class);
                method.setAccessible(true);
                method.invoke(client.interactionManager, inventorySlot);
                return true;
            } catch (NoSuchMethodException ignored) {
                // Try the next runtime name. Dev runs may expose named mappings, while
                // normal Fabric launches expose intermediary method_2916.
            } catch (Throwable throwable) {
                if (sellerVerboseLogs()) {
                    chat.send("sellreal pick warning: " + methodName + " failed: "
                            + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                }
            }
        }

        return false;
    }
    private boolean selectedHandLooksLikeSellerResult(SellerResult result) {
        try {
            if (result == null || result.getItem() == null || client == null || client.player == null || client.player.getInventory() == null) {
                return false;
            }

            int selected = client.player.getInventory().selectedSlot;
            if (selected < 0 || selected > 8) {
                return false;
            }

            ItemStack stack = client.player.getInventory().getStack(selected);
            if (stack == null || stack.isEmpty()) {
                return false;
            }

            String actualId = safeItemId(stack);
            String expectedId = result.getItem().getItemId();
            if (expectedId != null && !expectedId.isEmpty() && !expectedId.equals(actualId)) {
                return false;
            }

            // Count can change after unstacking or after server-side stack normalization,
            // so do not require exact equality. Only reject impossible empty stacks above.
            String expectedName = normalizeLooseName(result.getItem().getDisplayName());
            String actualName = normalizeLooseName(stack.getName().getString());
            if (!expectedName.isEmpty() && !actualName.isEmpty() && !actualName.contains(expectedName) && !expectedName.contains(actualName)) {
                // Names are only a safety hint. For custom items, item id plus target
                // matching can still be enough, so do not fail hard here.
            }

            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String normalizeLooseName(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("(?i)§[0-9A-FK-OR]", "")
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .trim();
    }

    private boolean swapMainInventorySlotToSelectedHotbar(int inventorySlot) {
        try {
            if (client == null || client.player == null || client.interactionManager == null) {
                return false;
            }
            if (inventorySlot < 9 || inventorySlot > 35) {
                return false;
            }

            int selectedHotbar = client.player.getInventory().selectedSlot;
            if (selectedHotbar < 0 || selectedHotbar > 8) {
                selectedHotbar = 0;
                selectHotbarSlot(selectedHotbar);
            }

            ScreenHandler handler = client.player.currentScreenHandler;
            if (handler == null) {
                return false;
            }

            int sourceScreenSlot = inventorySlotToPlayerScreenSlot(inventorySlot);
            if (sourceScreenSlot < 0) {
                return false;
            }

            client.interactionManager.clickSlot(
                    handler.syncId,
                    sourceScreenSlot,
                    selectedHotbar,
                    SlotActionType.SWAP,
                    client.player
            );
            return true;
        } catch (Throwable throwable) {
            if (sellerVerboseLogs()) {
                chat.send("seller swap fallback failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            }
            return false;
        }
    }


    private boolean selectHotbarSlot(int slot) {
        if (client == null || client.player == null || client.player.getInventory() == null) {
            return false;
        }

        if (slot < 0 || slot > 8) {
            return false;
        }

        client.player.getInventory().selectedSlot = slot;

        try {
            if (client.player.networkHandler != null) {
                client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }
        } catch (Throwable packetThrowable) {
            chat.send("sellreal warning: selected-slot packet failed: "
                    + packetThrowable.getClass().getSimpleName() + ": " + packetThrowable.getMessage());
        }

        return true;
    }

    private int findEmptyHotbarSlot() {
        if (client == null || client.player == null || client.player.getInventory() == null) {
            return -1;
        }

        for (int slot = 0; slot <= 8; slot++) {
            try {
                if (client.player.getInventory().getStack(slot) == null || client.player.getInventory().getStack(slot).isEmpty()) {
                    return slot;
                }
            } catch (Throwable ignored) {
            }
        }

        return -1;
    }

    private int inventorySlotToPlayerScreenSlot(int inventorySlot) {
        if (inventorySlot >= 9 && inventorySlot <= 35) {
            return inventorySlot;
        }

        if (inventorySlot >= 0 && inventorySlot <= 8) {
            return 36 + inventorySlot;
        }

        return -1;
    }

    private void sendSellerCommand(final SellerResult result, String mode) {
        if (!validateSellerResultForReal(result)) {
            return;
        }

        final String command = result.getCommand();

        if (sellerVerboseLogs()) {
            final String itemName = result.getItem().getDisplayName();
            final int count = result.getItem().getCount();
            final long unitPrice = result.getUnitPrice();
            final long totalPrice = result.getTotalPrice();
            final String finalMode = mode == null ? "unknown" : mode;
            chat.send("sellreal sending: mode=" + finalMode
                    + ", " + command
                    + ", item=" + itemName
                    + ", count=" + count
                    + ", unit=" + moneyFormat.format(unitPrice)
                    + ", total=" + moneyFormat.format(totalPrice));
        }

        sendSellerCommandPacket(command);
    }

    private void sendSellerCommandPacket(final String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }

        try {
            if (client != null && client.player != null) {
                // Step 22.41: rollback Step 22.40 direct packet sending.
                // Old SpookyBuy sends /ah sell through the normal player chat path and
                // lets the server/chat pipeline pace the command. Direct packets at 400ms
                // caused hard freezes and spam-kicks with several launchers.
                McChat.send(client, command);
                MalfixProfiler.recordClick("seller_command");
                sellerAwaitingServerResult = true;
                sellerAwaitingServerResultSinceMs = System.currentTimeMillis();
                sellerAwaitingServerCommand = command;
            }
        } catch (Throwable throwable) {
            sellerAwaitingServerResult = false;
            sellerAwaitingServerCommand = "";
            chat.send("sellreal failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
    }

    private boolean hasBlockingScreenOpen() {
        if (client == null || client.currentScreen == null) {
            return false;
        }

        // Allow command execution while the user typed .mab sellreal in chat.
        // In dev mappings this is ChatScreen, but at runtime logs may show obfuscated net.minecraft.class_408.
        if (client.currentScreen instanceof ChatScreen) {
            return false;
        }

        String name = currentScreenName().toLowerCase(Locale.ROOT);
        if (name.contains("chatscreen") || name.contains("chat") || name.contains("class_408")) {
            return false;
        }

        return true;
    }

    private String currentScreenName() {
        if (client == null || client.currentScreen == null) {
            return "none";
        }

        try {
            return client.currentScreen.getClass().getName();
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private void closeHandledScreenForInventoryClicks() {
        try {
            if (client != null && client.player != null) {
                client.player.closeHandledScreen();
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            if (client != null) {
                client.setScreen(null);
            }
        } catch (Throwable ignored) {
        }
    }

    public void startAutoParserForAll() {
        if (isParserBlockedByAutomation()) {
            chat.send("Парсер заблокирован: сначала останови full-auto/seller/buy loop. active=" + buildBusyCompactForParser());
            return;
        }

        List<TargetConfig> targets = new ArrayList<TargetConfig>();
        for (TargetConfig target : config.getTargets()) {
            if (target != null && target.isParserEnabled()) {
                targets.add(target);
            }
        }
        startAutoParserQueue(targets, "parser_enabled_targets");
    }

    public void startAutoParserForAllForced() {
        if (isParserBlockedByAutomation()) {
            chat.send("Парсер заблокирован: сначала останови full-auto/seller/buy loop. active=" + buildBusyCompactForParser());
            return;
        }

        startAutoParserQueue(new ArrayList<TargetConfig>(config.getTargets()), "all_targets_forced");
    }

    public void startAutoParserForLabel(String label) {
        if (isParserBlockedByAutomation()) {
            chat.send("Парсер заблокирован: сначала останови full-auto/seller/buy loop. active=" + buildBusyCompactForParser());
            return;
        }

        TargetConfig target = config.findTarget(label);
        if (target == null) {
            chat.send("цель парсера не найдена: " + label);
            return;
        }

        List<TargetConfig> targets = new ArrayList<TargetConfig>();
        targets.add(target);
        startAutoParserQueue(targets, "target=" + target.getLabel());
    }

    private void startAutoParserQueue(List<TargetConfig> targets, String reason) {
        if (parserRunning) {
            chat.send("Парсер уже работает: " + buildParserCompact());
            return;
        }

        if (targets == null || targets.isEmpty()) {
            chat.send("Парсер заблокирован: нет целей. Включи 'Парсить' в GUI предметов или используй .mab parseall force");
            return;
        }

        parserQueue = new ArrayList<TargetConfig>();
        for (TargetConfig target : targets) {
            if (target != null && !buildAuctionSearchQuery(target).isEmpty()) {
                parserQueue.add(target);
            }
        }

        if (parserQueue.isEmpty()) {
            chat.send("Парсер заблокирован: нет целей с поисковым запросом");
            return;
        }

        parserRunning = true;
        parserIndex = 0;
        parserPhase = "start_item";
        parserNextAtMs = 0L;
        parserUpdated = 0;
        parserSkipped = 0;
        parserRetries = 0;
        parserCurrentTarget = null;
        parserLastStatus = "started:" + reason;

        chat.send("Парсер запущен: целей=" + parserQueue.size()
                + ", buyPercent=" + config.getParserBuyPercent()
                + ", sellPercent=" + config.getParserSellPercent()
                + ", openWaitMs=" + config.getParserOpenWaitMs());
    }

    private void stopAutoParser(String reason) {
        parserRunning = false;
        parserPhase = "idle";
        parserNextAtMs = 0L;
        parserCurrentTarget = null;
        parserLastStatus = reason == null ? "stopped" : reason;
    }

    private void tickAutoParser() {
        if (!parserRunning) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < parserNextAtMs) {
            return;
        }

        if (parserIndex >= parserQueue.size()) {
            stopAutoParser("done");
            saveConfigAndReport();
            applyConfigToRuntime();
            chat.send("Парсер завершён: обновлено=" + parserUpdated + ", пропущено=" + parserSkipped
                    + ", settings={" + config.parserSummary() + "}");
            return;
        }

        if ("start_item".equals(parserPhase)) {
            parserCurrentTarget = parserQueue.get(parserIndex);
            parserRetries = 0;
            String query = buildAuctionSearchQuery(parserCurrentTarget);
            if (query.isEmpty()) {
                parserSkipped++;
                parserLastStatus = "skip_empty_query:" + safeTargetLabel(parserCurrentTarget);
                parserIndex++;
                parserNextAtMs = now + config.getParserBetweenItemsMs();
                return;
            }

            sendPlayerCommand("/ah search " + query);
            parserPhase = "wait_auction";
            parserNextAtMs = now + parserOpenWaitMs();
            parserLastStatus = "search_sent:" + parserCurrentTarget.getLabel();
            return;
        }

        if ("wait_auction".equals(parserPhase)) {
            if (!isParserSearchResultScreenReady()) {
                if (parserRetries < MalfixTimings.PARSER_MAX_RETRIES) {
                    parserRetries++;
                    sendPlayerCommand("/ah search " + buildAuctionSearchQuery(parserCurrentTarget));
                    parserNextAtMs = now + parserOpenWaitMs();
                    parserLastStatus = "retry_search_open:" + parserRetries
                            + ":title=" + getCurrentScreenTitleSafe()
                            + ":" + safeTargetLabel(parserCurrentTarget);
                    return;
                }

                parserSkipped++;
                parserLastStatus = "skip_no_search_screen:title=" + getCurrentScreenTitleSafe()
                        + ":" + safeTargetLabel(parserCurrentTarget);
                dumpParserDebugToLog("auto_skip_no_search_screen", parserCurrentTarget, null, null);
                parserIndex++;
                parserPhase = "start_item";
                parserNextAtMs = now + config.getParserBetweenItemsMs();
                return;
            }

            List<AuctionSlot> slots = readAuctionSearchSlotsForParser();
            TargetItem targetItem = toParserTargetItem(parserCurrentTarget);

            // Parser follows old SpookyBuy AutoSetup logic: /ah search already filters the
            // item, so for setting prices we must take the cheapest priced visible result.
            // Exact NBT/enchant matching is intentionally only a fallback here; otherwise
            // strict armor enchant checks can make parser skip valid search pages.
            ScanCandidate cheapest = findCheapestPricedLotOnSearchPage(slots, targetItem);
            if (cheapest == null) {
                cheapest = scanner.findCheapestForTarget(slots, targetItem);
            }

            if (cheapest == null) {
                if (parserRetries < MalfixTimings.PARSER_MAX_RETRIES) {
                    parserRetries++;
                    sendPlayerCommand("/ah search " + buildAuctionSearchQuery(parserCurrentTarget));
                    parserNextAtMs = now + parserOpenWaitMs();
                    parserLastStatus = "retry_no_price:" + parserRetries + ":" + safeTargetLabel(parserCurrentTarget);
                    return;
                }

                parserSkipped++;
                parserLastStatus = "skip_no_price_on_search:" + safeTargetLabel(parserCurrentTarget);
                dumpParserDebugToLog("auto_skip_no_price_on_search", parserCurrentTarget, slots, targetItem);
                chat.send("Парсер пропустил: " + safeTargetLabel(parserCurrentTarget) + " — нет лота с ценой на странице /ah search. Диагностика записана в latest.log");
                parserIndex++;
                parserPhase = "start_item";
                parserNextAtMs = now + config.getParserBetweenItemsMs();
                return;
            }

            long baseUnit = cheapest.getPrice().getUnitPrice();
            long buyPrice = percentPrice(baseUnit, config.getParserBuyPercent());
            long sellPrice = percentPrice(baseUnit, config.getParserSellPercent());

            parserCurrentTarget.setMaxUnitPrice(buyPrice);
            parserCurrentTarget.setSellUnitPrice(sellPrice);
            if (buyPrice > 0L) {
                parserCurrentTarget.setEnabled(true);
            }

            parserUpdated++;
            parserLastStatus = "updated:" + safeTargetLabel(parserCurrentTarget)
                    + ":base=" + baseUnit + ":buy=" + buyPrice + ":sell=" + sellPrice;

            saveConfigAndReport();
            applyConfigToRuntime();

            chat.send("Парсер обновил: " + parserCurrentTarget.getLabel()
                    + ", baseUnit=" + formatMoney(baseUnit)
                    + ", buy=" + formatMoney(buyPrice) + " (" + config.getParserBuyPercent() + "%)"
                    + ", sell=" + formatMoney(sellPrice) + " (" + config.getParserSellPercent() + "%)"
                    + ", source=" + cheapest.getAuctionSlot().getDisplayName());

            chat.sendInGame("§dПарсер §8» §f" + parserCurrentTarget.getLabel()
                    + " §7| самый дешевый предмет: §e" + formatMoney(baseUnit)
                    + " §7| покупка: §a" + formatMoney(buyPrice)
                    + " §7| продажа: §6" + formatMoney(sellPrice));

            parserIndex++;
            parserPhase = "start_item";
            parserNextAtMs = now + config.getParserBetweenItemsMs();
        }
    }


    private void handleParserDebugCommand(String[] parts) {
        String action = parts != null && parts.length >= 3 && parts[2] != null
                ? parts[2].toLowerCase(Locale.ROOT)
                : "";

        if ("cancel".equals(action) || "off".equals(action) || "stop".equals(action)) {
            TargetConfig oldTarget = parserDebugArmedTarget;
            clearParserDebugArm("manual_cancel");
            chat.send("Parser debug arm отменён" + (oldTarget == null ? "" : ": " + safeTargetLabel(oldTarget)));
            return;
        }

        if ("status".equals(action) || "state".equals(action)) {
            chat.send("Parser debug arm: " + buildParserDebugArmCompact());
            return;
        }

        if ("arm".equals(action) || "watch".equals(action) || "next".equals(action) || "auto".equals(action)) {
            String label = joinArguments(parts, 3);
            TargetConfig target = label.isEmpty() ? null : findTargetLoose(label);
            if (target == null && parserCurrentTarget != null) {
                target = parserCurrentTarget;
            }
            if (target == null) {
                chat.send("usage: .mab parsedebug arm \"Label\". Потом открой /ah search — dump сам запишется в latest.log.");
                return;
            }

            armParserDebug(target, "manual_arm", 45_000L);
            chat.send("Parser debug arm включён на 45s: " + safeTargetLabel(target)
                    + ". Теперь открой нужную /ah search страницу; писать команду поверх GUI не нужно.");
            return;
        }

        String label = joinArguments(parts, 2);
        TargetConfig target = null;

        if (!label.isEmpty()) {
            target = findTargetLoose(label);
        }
        if (target == null && parserCurrentTarget != null) {
            target = parserCurrentTarget;
        }

        if (target == null) {
            chat.send("usage: .mab parsedebug arm \"Label\" или .mab parsedebug \"Label\"");
            return;
        }

        List<AuctionSlot> slots = auctionView.readAuctionSlots();
        ParserDebugStats stats = dumpParserDebugToLog("manual", target, slots, toParserTargetItem(target));
        chat.send("Parser debug записан в latest.log: target=" + safeTargetLabel(target)
                + ", title=" + getCurrentScreenTitleSafe()
                + ", slots=" + stats.totalSlots
                + ", nonEmpty=" + stats.nonEmptySlots
                + ", priced=" + stats.pricedSlots
                + ", strictMatched=" + stats.strictMatchedSlots
                + ", parserCheapest=" + (stats.parserCheapestUnitPrice > 0L ? formatMoney(stats.parserCheapestUnitPrice) : "none")
                + ", strictCheapest=" + (stats.strictCheapestUnitPrice > 0L ? formatMoney(stats.strictCheapestUnitPrice) : "none"));
    }

    private void armParserDebug(TargetConfig target, String reason, long ttlMs) {
        parserDebugArmedTarget = target;
        parserDebugArmReason = reason == null || reason.trim().isEmpty() ? "armed" : reason.trim();
        parserDebugArmedAtMs = System.currentTimeMillis();
        parserDebugArmUntilMs = parserDebugArmedAtMs + Math.max(5_000L, ttlMs);
        parserDebugNextCheckAtMs = 0L;
        parserDebugFirstContainerSeenAtMs = 0L;
        parserDebugLastSeenTitle = "";
    }

    private void clearParserDebugArm(String reason) {
        parserDebugArmedTarget = null;
        parserDebugArmReason = reason == null ? "none" : reason;
        parserDebugArmedAtMs = 0L;
        parserDebugArmUntilMs = 0L;
        parserDebugNextCheckAtMs = 0L;
        parserDebugFirstContainerSeenAtMs = 0L;
        parserDebugLastSeenTitle = "";
    }

    private String buildParserDebugArmCompact() {
        if (parserDebugArmedTarget == null) {
            return "off";
        }
        long now = System.currentTimeMillis();
        long left = Math.max(0L, parserDebugArmUntilMs - now);
        return "target=" + safeTargetLabel(parserDebugArmedTarget)
                + ", reason=" + parserDebugArmReason
                + ", leftMs=" + left
                + ", title=" + getCurrentScreenTitleSafe();
    }

    private void tickParserDebugArm() {
        if (parserDebugArmedTarget == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now > parserDebugArmUntilMs) {
            TargetConfig expired = parserDebugArmedTarget;
            clearParserDebugArm("expired");
            chat.send("Parser debug arm истёк без dump: " + safeTargetLabel(expired));
            return;
        }

        if (now < parserDebugNextCheckAtMs) {
            return;
        }
        parserDebugNextCheckAtMs = now + 250L;

        if (client == null || !(client.currentScreen instanceof GenericContainerScreen)) {
            return;
        }
        if (client.currentScreen instanceof ChatScreen) {
            return;
        }
        if (isClearlyNonAuctionDebugScreen()) {
            return;
        }

        String title = getCurrentScreenTitleSafe();
        if (parserDebugFirstContainerSeenAtMs <= 0L || !title.equals(parserDebugLastSeenTitle)) {
            parserDebugFirstContainerSeenAtMs = now;
            parserDebugLastSeenTitle = title;
        }

        List<AuctionSlot> slots = readCurrentContainerSlotsForParserDebug();
        int nonEmpty = 0;
        if (slots != null) {
            for (AuctionSlot slot : slots) {
                if (slot != null && !slot.isEmpty()) {
                    nonEmpty++;
                }
            }
        }

        // Step 22.70: do not use auctionView.readAuctionSlots() here. It returns empty when
        // the search result title is not recognised by isAuctionOpen(), which is exactly what
        // parser-debug is supposed to diagnose. Dump the raw container once it has items; if it
        // stays empty for a short time, dump anyway so the log still shows the real title/slot state.
        if (nonEmpty <= 0 && now - parserDebugFirstContainerSeenAtMs < 1_200L) {
            return;
        }

        TargetConfig target = parserDebugArmedTarget;
        ParserDebugStats stats = dumpParserDebugToLog("armed:" + parserDebugArmReason + ":rawContainer", target, slots, toParserTargetItem(target));
        clearParserDebugArm("dumped");
        chat.send("Parser debug auto-dump записан в latest.log: target=" + safeTargetLabel(target)
                + ", title=" + getCurrentScreenTitleSafe()
                + ", slots=" + stats.totalSlots
                + ", nonEmpty=" + stats.nonEmptySlots
                + ", priced=" + stats.pricedSlots
                + ", strictMatched=" + stats.strictMatchedSlots
                + ", parserCheapest=" + (stats.parserCheapestUnitPrice > 0L ? formatMoney(stats.parserCheapestUnitPrice) : "none")
                + ", strictCheapest=" + (stats.strictCheapestUnitPrice > 0L ? formatMoney(stats.strictCheapestUnitPrice) : "none"));
    }

    private boolean isClearlyNonAuctionDebugScreen() {
        String lower = getCurrentScreenTitleLower();
        if (lower.isEmpty()) {
            return false;
        }

        return lower.contains("храни")
                || lower.contains("storage")
                || lower.contains("склад")
                || lower.contains("vault")
                || lower.contains("мои предмет")
                || lower.contains("предметы на продаж")
                || lower.contains("мои товар")
                || lower.contains("товары")
                || lower.contains("продаж");
    }


    private List<AuctionSlot> readCurrentContainerSlotsForParserDebug() {
        if (client == null || client.player == null || !(client.currentScreen instanceof GenericContainerScreen)) {
            return java.util.Collections.emptyList();
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler.slots == null || handler.slots.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        int max = Math.min(54, handler.slots.size());
        List<AuctionSlot> result = new ArrayList<AuctionSlot>(max);

        for (int i = 0; i < max; i++) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getStack();
            final int containerSlot = i;

            if (stack == null || stack.isEmpty()) {
                result.add(AuctionSlot.empty(i, containerSlot));
                continue;
            }

            final ItemStack capturedStack = stack.copy();
            result.add(new AuctionSlot(
                    i,
                    containerSlot,
                    false,
                    safeItemId(capturedStack),
                    safeStackName(capturedStack),
                    safeStackCount(capturedStack),
                    new java.util.function.Supplier<List<String>>() {
                        @Override
                        public List<String> get() {
                            return readStackTooltipForParserDebug(capturedStack);
                        }
                    },
                    new java.util.function.Supplier<String>() {
                        @Override
                        public String get() {
                            return readStackNbtForParserDebug(capturedStack);
                        }
                    }
            ));
        }

        return result;
    }

    private String safeItemId(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()) {
                return "";
            }
            return McItemStacks.itemId(stack);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String safeStackName(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()) {
                return "";
            }
            return stack.getName().getString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private int safeStackCount(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty()) {
                return 0;
            }
            return stack.getCount();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private List<String> readStackTooltipForParserDebug(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        return McItemStacks.tooltip(stack, client);
    }

    private String readStackNbtForParserDebug(ItemStack stack) {
        try {
            return McItemStacks.componentString(stack);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private ParserDebugStats dumpParserDebugToLog(String reason, TargetConfig target, List<AuctionSlot> providedSlots, TargetItem targetItem) {
        ParserDebugStats stats = new ParserDebugStats();
        long startedAt = System.currentTimeMillis();
        List<AuctionSlot> slots = providedSlots != null ? providedSlots : auctionView.readAuctionSlots();
        if (targetItem == null) {
            targetItem = toParserTargetItem(target);
        }

        PriceParser priceParser = new PriceParser();
        ItemMatcher matcher = new ItemMatcher();
        List<TargetItem> oneTarget = new ArrayList<TargetItem>();
        oneTarget.add(targetItem);

        ScanCandidate parserCheapest = findCheapestPricedLotOnSearchPage(slots, targetItem);
        ScanCandidate strictCheapest = scanner.findCheapestForTarget(slots, targetItem);
        stats.totalSlots = slots == null ? 0 : slots.size();
        stats.parserCheapestUnitPrice = parserCheapest == null || parserCheapest.getPrice() == null ? 0L : parserCheapest.getPrice().getUnitPrice();
        stats.strictCheapestUnitPrice = strictCheapest == null || strictCheapest.getPrice() == null ? 0L : strictCheapest.getPrice().getUnitPrice();

        System.out.println("========== [MAB PARSER DEBUG BEGIN] ==========");
        System.out.println("reason=" + safeLog(reason));
        System.out.println("screenTitle=" + safeLog(getCurrentScreenTitleSafe())
                + ", isAuctionOpen=" + auctionView.isAuctionOpen()
                + ", isSearchResult=" + isAuctionSearchResultScreenOpen()
                + ", parserRunning=" + parserRunning
                + ", parserPhase=" + parserPhase);
        System.out.println("target=" + (target == null ? "null" : safeLog(target.compact())));
        System.out.println("query=" + safeLog(buildAuctionSearchQuery(target)));
        System.out.println("targetItem label=" + safeLog(targetItem == null ? "null" : targetItem.getLabel())
                + ", itemId=" + safeLog(targetItem == null ? "" : targetItem.getItemId())
                + ", tag=" + safeLog(targetItem == null ? "" : targetItem.getTagContains())
                + ", contains=" + safeLog(targetItem == null ? "" : String.valueOf(targetItem.getNameContains())));
        System.out.println("parserCheapest=" + formatDebugCandidate(parserCheapest));
        System.out.println("strictCheapest=" + formatDebugCandidate(strictCheapest));

        if (slots == null || slots.isEmpty()) {
            System.out.println("slots=<empty/null from readAuctionSlots>");
        } else {
            int max = Math.min(54, slots.size());
            for (int i = 0; i < max; i++) {
                AuctionSlot slot = slots.get(i);
                if (slot == null) {
                    System.out.println("slot[" + i + "]=null");
                    continue;
                }

                if (slot.isEmpty()) {
                    System.out.println("slot[" + i + "] empty container=" + slot.getContainerSlotId());
                    continue;
                }

                stats.nonEmptySlots++;
                ParsedPrice price = priceParser.parse(slot.getTooltipLines(), slot.getCount());
                if (price != null && price.isFound() && price.getUnitPrice() > 0L) {
                    stats.pricedSlots++;
                }

                MatchResult match = matcher.match(slot, oneTarget);
                if (match != null && match.isMatched()) {
                    stats.strictMatchedSlots++;
                }

                String debugMatch = matcher.debugMatch(slot, targetItem);
                System.out.println("slot[" + i + "] auctionIndex=" + slot.getAuctionIndex()
                        + ", container=" + slot.getContainerSlotId()
                        + ", itemId=" + safeLog(slot.getItemId())
                        + ", name=" + safeLog(slot.getDisplayName())
                        + ", count=" + slot.getCount()
                        + ", price=" + formatDebugPrice(price)
                        + ", match=" + (match != null && match.isMatched())
                        + ", matchReason=" + safeLog(match == null ? "null" : match.getReason())
                        + ", debug=" + safeLog(debugMatch));

                System.out.println("slot[" + i + "] tooltip=" + compactTooltip(slot.getTooltipLines(), 900));
                String nbt = slot.getNbtString();
                System.out.println("slot[" + i + "] nbt=" + compactLogText(nbt, 900));
            }
        }

        stats.elapsedMs = System.currentTimeMillis() - startedAt;
        System.out.println("summary totalSlots=" + stats.totalSlots
                + ", nonEmpty=" + stats.nonEmptySlots
                + ", priced=" + stats.pricedSlots
                + ", strictMatched=" + stats.strictMatchedSlots
                + ", parserCheapestUnit=" + stats.parserCheapestUnitPrice
                + ", strictCheapestUnit=" + stats.strictCheapestUnitPrice
                + ", elapsedMs=" + stats.elapsedMs);
        System.out.println("========== [MAB PARSER DEBUG END] ==========");
        return stats;
    }

    private TargetConfig findTargetLoose(String label) {
        TargetConfig exact = config.findTarget(label);
        if (exact != null) {
            return exact;
        }

        String wanted = normalizeForLooseCompare(label);
        if (wanted.isEmpty()) {
            return null;
        }

        TargetConfig best = null;
        for (TargetConfig target : config.getTargets()) {
            if (target == null) {
                continue;
            }
            String targetLabel = normalizeForLooseCompare(target.getLabel());
            if (targetLabel.equals(wanted) || targetLabel.contains(wanted) || wanted.contains(targetLabel)) {
                return target;
            }
            for (String phrase : target.getContains()) {
                String normalizedPhrase = normalizeForLooseCompare(phrase);
                if (!normalizedPhrase.isEmpty()
                        && (normalizedPhrase.equals(wanted) || normalizedPhrase.contains(wanted) || wanted.contains(normalizedPhrase))) {
                    best = target;
                }
            }
        }
        return best;
    }

    private String joinArguments(String[] parts, int start) {
        if (parts == null || start >= parts.length) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (parts[i] == null || parts[i].trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(parts[i].trim());
        }
        return builder.toString();
    }

    private String normalizeForLooseCompare(String value) {
        return normalizeSearchText(value).toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private String formatDebugCandidate(ScanCandidate candidate) {
        if (candidate == null) {
            return "none";
        }
        AuctionSlot slot = candidate.getAuctionSlot();
        ParsedPrice price = candidate.getPrice();
        return "slot=" + (slot == null ? -1 : slot.getAuctionIndex())
                + ", container=" + (slot == null ? -1 : slot.getContainerSlotId())
                + ", itemId=" + safeLog(slot == null ? "" : slot.getItemId())
                + ", name=" + safeLog(slot == null ? "" : slot.getDisplayName())
                + ", price=" + formatDebugPrice(price);
    }

    private String formatDebugPrice(ParsedPrice price) {
        if (price == null || !price.isFound()) {
            return "missing";
        }
        return "total=" + price.getTotalPrice()
                + ", unit=" + price.getUnitPrice()
                + ", source=" + safeLog(price.getSourceLine());
    }

    private String compactTooltip(List<String> lines, int maxLen) {
        if (lines == null || lines.isEmpty()) {
            return "<empty>";
        }
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(line);
        }
        return compactLogText(builder.toString(), maxLen);
    }

    private String compactLogText(String value, int maxLen) {
        if (value == null || value.isEmpty()) {
            return "<empty>";
        }
        String s = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        while (s.contains("  ")) {
            s = s.replace("  ", " ");
        }
        if (maxLen > 0 && s.length() > maxLen) {
            return s.substring(0, maxLen) + "...<truncated len=" + s.length() + ">";
        }
        return s;
    }

    private String safeLog(String value) {
        if (value == null) {
            return "null";
        }
        return compactLogText(value, 600);
    }

    private static final class ParserDebugStats {
        private int totalSlots;
        private int nonEmptySlots;
        private int pricedSlots;
        private int strictMatchedSlots;
        private long parserCheapestUnitPrice;
        private long strictCheapestUnitPrice;
        private long elapsedMs;
    }

    private TargetItem toParserTargetItem(TargetConfig target) {
        if (target == null) {
            return new TargetItem("", new ArrayList<String>(), "", "", 0L, 0L, true, false, 1);
        }

        return new TargetItem(
                target.getLabel(),
                target.getContains(),
                target.getItemId(),
                target.getTagContains(),
                Long.MAX_VALUE,
                target.getSellUnitPrice(),
                true,
                target.isUnstack(),
                target.getUnstackAmount(),
                target.getPotionDragMinSourceCount()
        );
    }

    private ScanCandidate findCheapestPricedLotOnSearchPage(List<AuctionSlot> slots, TargetItem targetItem) {
        if (slots == null || slots.isEmpty()) {
            return null;
        }

        PriceParser parser = new PriceParser();
        ScanCandidate best = null;
        int max = Math.min(45, slots.size());

        for (int i = 0; i < max; i++) {
            AuctionSlot slot = slots.get(i);
            if (slot == null || slot.isEmpty()) {
                continue;
            }

            ParsedPrice price = parser.parse(slot.getTooltipLines(), slot.getCount());
            if (price == null || !price.isFound() || price.getUnitPrice() <= 0L) {
                continue;
            }

            ScanCandidate candidate = new ScanCandidate(slot, targetItem, price);
            if (best == null
                    || candidate.getPrice().getUnitPrice() < best.getPrice().getUnitPrice()
                    || (candidate.getPrice().getUnitPrice() == best.getPrice().getUnitPrice()
                    && candidate.getAuctionSlot().getAuctionIndex() < best.getAuctionSlot().getAuctionIndex())) {
                best = candidate;
            }
        }

        return best;
    }

    private boolean isAuctionSearchResultScreenOpen() {
        if (client == null || client.currentScreen == null) {
            return false;
        }

        String lower = getCurrentScreenTitleLower();
        if (isClearlyNotAuctionSearchTitle(lower)) {
            return false;
        }

        if (lower.contains("поиск")
                || lower.contains("search")
                || lower.contains("найден")
                || lower.contains("результат")
                || isPagedSearchTitle(lower)) {
            return true;
        }

        // /ah search on this server can return the same title as the normal auction.
        // During parser wait_auction we know that the current screen was opened by
        // our just-sent /ah search command, so accept the auction title here too.
        return parserRunning
                && parserCurrentTarget != null
                && "wait_auction".equals(parserPhase)
                && (lower.contains("аукцион") || lower.contains("auction") || lower.contains("ah"));
    }

    private boolean isParserSearchResultScreenReady() {
        if (client == null || !(client.currentScreen instanceof GenericContainerScreen)) {
            return false;
        }

        String lower = getCurrentScreenTitleLower();
        if (isClearlyNotAuctionSearchTitle(lower)) {
            return false;
        }

        if (isAuctionSearchResultScreenOpen()) {
            return true;
        }

        // Step 22.71: titles like "☃ П: Нагрудник Крушителя [1/4]" were not recognised
        // as auction/search by the old path, even though raw debug showed priced+matched lots.
        // While auto-parser is waiting for our own /ah search result, accept a populated 54-slot
        // GenericContainerScreen as a search page. Do not use this outside wait_auction.
        if (!parserRunning || parserCurrentTarget == null || !"wait_auction".equals(parserPhase)) {
            return false;
        }

        List<AuctionSlot> slots = readCurrentContainerSlotsForParserDebug();
        if (slots == null || slots.isEmpty()) {
            return false;
        }

        PriceParser parser = new PriceParser();
        int max = Math.min(45, slots.size());
        int nonEmpty = 0;
        int priced = 0;
        for (int i = 0; i < max; i++) {
            AuctionSlot slot = slots.get(i);
            if (slot == null || slot.isEmpty()) {
                continue;
            }
            nonEmpty++;
            ParsedPrice price = parser.parse(slot.getTooltipLines(), slot.getCount());
            if (price != null && price.isFound() && price.getUnitPrice() > 0L) {
                priced++;
                break;
            }
        }
        return nonEmpty > 0 && priced > 0;
    }

    private List<AuctionSlot> readAuctionSearchSlotsForParser() {
        if (auctionView != null && auctionView.isAuctionOpen()) {
            List<AuctionSlot> normal = auctionView.readAuctionSlots();
            if (normal != null && !normal.isEmpty()) {
                return normal;
            }
        }
        return readCurrentContainerSlotsForParserDebug();
    }

    private boolean isClearlyNotAuctionSearchTitle(String lower) {
        if (lower == null) {
            return false;
        }
        return lower.contains("храни")
                || lower.contains("storage")
                || lower.contains("склад")
                || lower.contains("vault")
                || lower.contains("мои предмет")
                || lower.contains("предметы на продаж")
                || lower.contains("мои товар")
                || lower.contains("товары")
                || lower.contains("продаж");
    }

    private boolean isPagedSearchTitle(String lower) {
        if (lower == null) {
            return false;
        }
        String s = lower.replace('ё', 'е').trim();
        boolean hasPageMarker = s.contains("[") && s.contains("/") && s.contains("]");
        boolean hasSearchPrefix = s.startsWith("☃ п:") || s.startsWith("п:") || s.contains(" п:");
        return hasPageMarker && hasSearchPrefix;
    }


    private long parserOpenWaitMs() {
        // Heavy NBT items like Crusher armor and enchanted golden apple search pages can
        // arrive one tick later than simple resources. A 650ms wait was enough for powder,
        // but it sometimes parsed the old/empty page for armor/apples. Keep user setting,
        // but never below 1200ms during auto-parse.
        long configured = config == null ? MalfixTimings.PARSER_OPEN_WAIT_MS : config.getParserOpenWaitMs();
        return Math.max(1200L, configured);
    }

    private long percentPrice(long baseUnit, int percent) {
        if (baseUnit <= 0L) {
            return 0L;
        }

        long safePercent = Math.max(1L, Math.min(500L, (long) percent));
        long result = (baseUnit * safePercent + 50L) / 100L;
        return Math.max(1L, result);
    }

    private boolean isParserBlockedByAutomation() {
        return fullAutoTimedEnabled
                || cycleFullEnabled
                || cycleFullLoopEnabled
                || safeAutoRunEnabled
                || limitedLoop.isRunning()
                || oneCycle.isPending()
                || controlledBuyClick.isPending()
                || sellerLoopEnabled
                || sellerCycleEnabled
                || sellerReturnToAuctionPending
                || shulkerController.isRunning();
    }

    private String buildBusyCompactForParser() {
        return "fullAuto=" + fullAutoTimedEnabled
                + ", cycleFull=" + cycleFullEnabled
                + ", limitedLoop=" + limitedLoop.isRunning()
                + ", sellerLoop=" + sellerLoopEnabled
                + ", sellerCycle=" + sellerCycleEnabled;
    }

    private String buildParserCompact() {
        return "running=" + parserRunning
                + ", phase=" + parserPhase
                + ", index=" + parserIndex + "/" + (parserQueue == null ? 0 : parserQueue.size())
                + ", updated=" + parserUpdated
                + ", skipped=" + parserSkipped
                + ", target=" + safeTargetLabel(parserCurrentTarget)
                + ", lastStatus=" + parserLastStatus;
    }

    private String safeTargetLabel(TargetConfig target) {
        return target == null ? "none" : target.getLabel();
    }


    private boolean tickAutoShulkerRestack() {
        // Step 23.34: the old JS shalk.js is kept for compatibility, but storage is
        // handled by the native Never-style controller. This fixes generic-title
        // shulkers, scan state, and /ec fallback while keeping user menus protected.
        return tickAutoShulkerRestackNative();
    }

    private boolean tickAutoShulkerRestackNative() {
        long now = System.currentTimeMillis();
        if (now < nextAutoShulkerCheckAtMs) {
            return false;
        }
        nextAutoShulkerCheckAtMs = now + MalfixTimings.SHULKER_AUTO_CHECK_MS;

        if (isEnderStorageScreenOpen()) {
            return false;
        }

        if (!shulkerController.isPlayerInventoryFullEnough() && shouldTakeBackFromShulkerNow()) {
            prepareNativeShulkerStorageStart();
            boolean started = shulkerController.startTake("auto_take_back_free_slots", shouldRestoreAuctionAfterShulker());
            if (!started) {
                rollbackNativeShulkerStorageStartIfNeeded();
            }
            if (started) {
                legacyScriptPauseActive = false;
                legacyScriptPauseReason = "native_shulker_take_started";
                legacyScriptPauseChangedAtMs = System.currentTimeMillis();
                legacyScriptResumeWaitingAuction = false;
                legacyScriptResumeAhSent = false;
                legacyScriptResumeReason = "none";
                chat.send("auto shulker take-back started: " + shulkerController.compact());
                return true;
            }
        }

        // Step 23.18: the manual `.mab shulker test` proves the 1.21.4 open/right-click
        // path is working. The remaining failure mode is the automatic trigger being
        // blocked by buy-result waits, legacy JS pause, or runtime state while /ah is open.
        // When inventory is actually full and there is a hotbar shulker, native storage
        // must take priority over the buy loop and close /ah once for storage.
        if (!shulkerController.isPlayerInventoryFullEnough()) {
            return false;
        }

        boolean unstackStorageEmergency = shouldUnstackStorageEmergencyNow();
        boolean forceFullInventoryStorage = unstackStorageEmergency || shouldForceAutoRestackShulkerNow();
        if (!forceFullInventoryStorage && !shouldAutoRestackShulkerNow()) {
            return false;
        }

        if (forceFullInventoryStorage) {
            shulkerController.clearNoMoveBlock(unstackStorageEmergency
                    ? "unstack_full_inventory_clear_no_move_block"
                    : "force_auto_full_inventory_clear_no_move_block");
        }
        if (unstackStorageEmergency) {
            resetSellerUnstackPreparationForNativeStorage(now);
        }
        prepareNativeShulkerStorageStart();
        String startReason = unstackStorageEmergency
                ? "unstack_full_inventory_storage"
                : (forceFullInventoryStorage ? "force_full_inventory_auto" : "auto_inventory_full");
        boolean started = shulkerController.startPut(
                startReason,
                shouldRestoreAuctionAfterShulkerForStart(unstackStorageEmergency));
        if (!started) {
            rollbackNativeShulkerStorageStartIfNeeded();
        }
        if (started) {
            legacyScriptPauseActive = false;
            legacyScriptPauseReason = "native_shulker_force_started";
            legacyScriptPauseChangedAtMs = System.currentTimeMillis();
            legacyScriptResumeWaitingAuction = false;
            legacyScriptResumeAhSent = false;
            legacyScriptResumeReason = "none";
            chat.send("auto shulker restack started: " + shulkerController.compact());
            return true;
        }

        return false;
    }

    private void prepareNativeShulkerStorageStart() {
        try {
            controlledBuyClick.cancel("native_shulker_storage_start");
        } catch (Throwable ignored) {
        }
        try {
            oneCycle.cancel("native_shulker_storage_start");
        } catch (Throwable ignored) {
        }
        try {
            nativeShulkerBaseRuntimeWasEnabled = runtime != null
                    && runtime.controller() != null
                    && runtime.controller().context() != null
                    && runtime.controller().context().enabled;
            if (nativeShulkerBaseRuntimeWasEnabled) {
                runtime.controller().pause("native_shulker_storage");
                nativeShulkerPausedBaseRuntime = true;
                nativeShulkerPauseStartedAtMs = System.currentTimeMillis();
            }
        } catch (Throwable ignored) {
            nativeShulkerPausedBaseRuntime = false;
            nativeShulkerBaseRuntimeWasEnabled = false;
            nativeShulkerPauseStartedAtMs = 0L;
        }
    }

    private void rollbackNativeShulkerStorageStartIfNeeded() {
        if (!nativeShulkerPausedBaseRuntime) {
            return;
        }
        try {
            if (nativeShulkerBaseRuntimeWasEnabled && runtime != null && runtime.controller() != null) {
                runtime.controller().resume();
            }
        } catch (Throwable ignored) {
        }
        nativeShulkerPausedBaseRuntime = false;
        nativeShulkerBaseRuntimeWasEnabled = false;
        nativeShulkerPauseStartedAtMs = 0L;
    }

    private void tickNativeShulkerPauseResume() {
        if (!nativeShulkerPausedBaseRuntime) {
            return;
        }
        if (shulkerController.isRunning()) {
            return;
        }
        try {
            if (nativeShulkerBaseRuntimeWasEnabled && runtime != null && runtime.controller() != null) {
                runtime.controller().resume();
            }
        } catch (Throwable ignored) {
        }
        nativeShulkerPausedBaseRuntime = false;
        nativeShulkerBaseRuntimeWasEnabled = false;
        nativeShulkerPauseStartedAtMs = 0L;
    }

    private boolean isEnderStorageScreenOpen() {
        try {
            if (client == null || client.currentScreen == null) {
                return false;
            }
            String title = client.currentScreen.getTitle() == null ? "" : client.currentScreen.getTitle().getString();
            String lower = title.toLowerCase(Locale.ROOT).replace('ё', 'е');
            return lower.contains("ender") || lower.contains("эндер") || lower.contains("эндер-сундук") || lower.contains("ec");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean shouldTakeBackFromShulkerNow() {
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        if (antiAfkRunning || parserRunning || shulkerController.isRunning()) {
            return false;
        }
        if (client.currentScreen instanceof ChatScreen) {
            return false;
        }
        // Match the uploaded shalk.js behavior: TAKE runs only from a clean player
        // screen after PUT finished and the inventory has more than 20 free slots.
        // Do not close /ah or user menus just to take items back.
        if (client.currentScreen != null) {
            return false;
        }
        if (sellerLoopEnabled || sellerCycleEnabled || sellerReturnToAuctionPending
                || sellOnlyTimedEnabled || sellerAwaitingServerResult) {
            return false;
        }
        if (controlledBuyClick.isPending() || oneCycle.isPending()) {
            return false;
        }
        if (limitedLoop.isWaitingBuyResult()) {
            return false;
        }
        if (!shulkerController.shouldTakeBackFromStorage()) {
            return false;
        }
        if (!shulkerController.hasTakeCandidates()) {
            return false;
        }
        boolean automationContext = fullAutoTimedEnabled
                || cycleFullEnabled
                || cycleFullLoopEnabled
                || safeAutoRunEnabled
                || limitedLoop.isRunning()
                || runtime.controller().context().enabled;
        return automationContext;
    }

    private boolean shouldUnstackStorageEmergencyNow() {
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        if (antiAfkRunning || parserRunning || shulkerController.isRunning()) {
            return false;
        }
        if (client.currentScreen instanceof ChatScreen) {
            return false;
        }
        if (!shulkerController.isPlayerInventoryFullEnough()) {
            return false;
        }
        if (shulkerController.countHotbarShulkers() <= 0) {
            return false;
        }
        if (sellerAwaitingServerResult) {
            return false;
        }

        boolean sellerOrSellPhase = sellerLoopEnabled
                || sellerCycleEnabled
                || sellOnlyTimedEnabled
                || sellerUnstackPrepareActive
                || (cycleFullEnabled && isCycleFullInSellPhase());
        if (!sellerOrSellPhase) {
            return false;
        }

        boolean unstackRelevant = sellerUnstackPrepareActive || hasAnyUnstackTargetEnabled();
        try {
            unstackRelevant = unstackRelevant || (unstackController != null && unstackController.needsUnstack());
        } catch (Throwable ignored) {
        }
        if (!unstackRelevant) {
            return false;
        }

        // During old-Spooky-style unstack, a full inventory means right-click splitting
        // cannot continue because there is no destination slot. The shulker storage
        // task must preempt the seller/unstack loop. Allow only a clean player screen
        // or the auction screen; do not steal focus from user menus.
        if (client.currentScreen == null) {
            return true;
        }
        try {
            return auctionView != null && auctionView.isAuctionOpen();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void resetSellerUnstackPreparationForNativeStorage(long now) {
        sellerUnstackPrepareActive = false;
        sellerUnstackPrepareAtMs = 0L;
        sellerUnstackPrepareReason = "native_shulker_storage_full_inventory";
        if (sellerLoopEnabled) {
            sellerLoopNextAtMs = now + Math.max(300L, MalfixTimings.SHULKER_CLOSE_WAIT_MS);
        }
    }

    private boolean shouldRestoreAuctionAfterShulkerForStart(boolean unstackStorageEmergency) {
        if (unstackStorageEmergency) {
            return false;
        }
        return shouldRestoreAuctionAfterShulker();
    }

    private boolean shouldForceAutoRestackShulkerNow() {
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        if (antiAfkRunning || parserRunning || shulkerController.isRunning()) {
            return false;
        }
        if (sellerLoopEnabled || sellerCycleEnabled || sellerReturnToAuctionPending
                || sellOnlyTimedEnabled || cycleFullEnabled && isCycleFullInSellPhase()) {
            return false;
        }
        if (shulkerController.countHotbarShulkers() <= 0) {
            return false;
        }
        if (client.currentScreen == null) {
            return true;
        }
        try {
            return auctionView != null && auctionView.isAuctionOpen();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isCycleFullInSellPhase() {
        try {
            if (!cycleFullEnabled) {
                return false;
            }
            String phase = cycleFullPhase == null ? "" : cycleFullPhase;
            return !"open_ah".equals(phase) && !"buy_loop".equals(phase);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean shouldAutoRestackShulkerNow() {
        if (client == null || client.player == null || client.world == null) {
            return false;
        }

        if (antiAfkRunning || parserRunning || shulkerController.isRunning()) {
            return false;
        }

        if (client.currentScreen instanceof ChatScreen) {
            return false;
        }

        // Do not interrupt selling, storage relist or explicit buy-click confirmation.
        if (sellerLoopEnabled || sellerCycleEnabled || sellerReturnToAuctionPending) {
            return false;
        }
        if (controlledBuyClick.isPending() || oneCycle.isPending()) {
            return false;
        }
        if (limitedLoop.isWaitingBuyResult()) {
            return false;
        }

        if (cycleFullEnabled) {
            // The shulker system is for the auction buy phase only. Storage/sell phases must not be interrupted.
            if (!"open_ah".equals(cycleFullPhase) && !"buy_loop".equals(cycleFullPhase)) {
                return false;
            }
        }

        boolean autobuyContext = fullAutoTimedEnabled
                || cycleFullEnabled
                || cycleFullLoopEnabled
                || safeAutoRunEnabled
                || limitedLoop.isRunning()
                || runtime.controller().context().enabled;

        if (!autobuyContext) {
            return false;
        }

        // Start only from a clean player screen or auction screen. This prevents moving items while another GUI is open.
        if (client.currentScreen == null) {
            return true;
        }
        if (auctionView != null && auctionView.isAuctionOpen()) {
            return true;
        }
        try {
            return new ScriptCompatBridge(client).isAuctionScreenOpen();
        } catch (Throwable ignored) {
            return false;
        }
    }


    public boolean isAutomationActiveForLegacyScript() {
        return fullAutoTimedEnabled
                || cycleFullEnabled
                || cycleFullLoopEnabled
                || safeAutoRunEnabled
                || limitedLoop.isRunning()
                || oneCycle.isPending()
                || controlledBuyClick.isPending()
                || runtime.controller().context().enabled;
    }

    public void setLegacyScriptPause(boolean active, String reason) {
        String safeReason = reason == null ? "none" : reason;
        long now = System.currentTimeMillis();

        if (active) {
            legacyScriptPauseActive = true;
            legacyScriptPauseReason = safeReason;
            legacyScriptPauseChangedAtMs = now;
            legacyScriptResumeWaitingAuction = false;
            legacyScriptResumeAhSent = false;
            legacyScriptResumeReason = "none";
            System.out.println("[MAB SCRIPT] legacy script pause=true, reason=" + legacyScriptPauseReason);
            return;
        }

        if (shouldRestoreAuctionAfterLegacyScript()) {
            legacyScriptPauseActive = true;
            legacyScriptPauseReason = "resume_wait_auction:" + safeReason;
            legacyScriptPauseChangedAtMs = now;
            legacyScriptResumeWaitingAuction = true;
            legacyScriptResumeAhSent = false;
            legacyScriptResumeStartedAtMs = now;
            legacyScriptResumeNextActionAtMs = now;
            legacyScriptResumeReason = safeReason;
            System.out.println("[MAB SCRIPT] legacy script resume requested; waiting /ah before unpause, reason=" + safeReason);
            return;
        }

        legacyScriptPauseActive = false;
        legacyScriptPauseReason = safeReason;
        legacyScriptPauseChangedAtMs = now;
        legacyScriptResumeWaitingAuction = false;
        legacyScriptResumeAhSent = false;
        legacyScriptResumeReason = "none";
        System.out.println("[MAB SCRIPT] legacy script pause=false, reason=" + legacyScriptPauseReason);
    }


    private boolean tickLegacyScriptPauseWatchdog() {
        if (!legacyScriptPauseActive || legacyScriptResumeWaitingAuction) {
            return false;
        }
        if (client == null || client.player == null || client.world == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long age = legacyScriptPauseChangedAtMs <= 0L ? 0L : now - legacyScriptPauseChangedAtMs;
        if (age < 12_000L) {
            return false;
        }

        // A healthy shulker script should keep opening/closing handled screens while it
        // owns the pause. If the player has been sitting on the normal screen for many
        // seconds, the old JS script most likely failed before resumeAutoBuy().
        if (client.currentScreen != null) {
            return false;
        }

        legacyScriptPauseActive = false;
        legacyScriptPauseReason = "script_pause_watchdog:" + legacyScriptPauseReason;
        legacyScriptPauseChangedAtMs = now;
        legacyScriptResumeWaitingAuction = false;
        legacyScriptResumeAhSent = false;
        legacyScriptResumeReason = "none";
        System.out.println("[MAB SCRIPT] legacy script pause watchdog released stuck pause");
        return true;
    }

    private boolean shouldRestoreAuctionAfterLegacyScript() {
        if (client == null || client.player == null || client.world == null) {
            return false;
        }

        if (sellerLoopEnabled || sellerCycleEnabled || sellerReturnToAuctionPending) {
            return false;
        }

        return fullAutoTimedEnabled
                || cycleFullEnabled
                || cycleFullLoopEnabled
                || safeAutoRunEnabled
                || limitedLoop.isRunning()
                || oneCycle.isPending()
                || controlledBuyClick.isPending()
                || runtime.controller().context().enabled;
    }

    private boolean tickLegacyScriptResume() {
        if (!legacyScriptResumeWaitingAuction) {
            return false;
        }

        if (client == null || client.player == null || client.world == null) {
            return true;
        }

        long now = System.currentTimeMillis();

        if (auctionView != null && auctionView.isAuctionOpen()) {
            legacyScriptPauseActive = false;
            legacyScriptPauseReason = "resumed_after_ah:" + legacyScriptResumeReason;
            legacyScriptPauseChangedAtMs = now;
            legacyScriptResumeWaitingAuction = false;
            legacyScriptResumeAhSent = false;
            System.out.println("[MAB SCRIPT] legacy script pause=false, auction restored, reason=" + legacyScriptResumeReason);
            return false;
        }

        if (now - legacyScriptResumeStartedAtMs > 8000L) {
            legacyScriptPauseActive = false;
            legacyScriptPauseReason = "resume_timeout:" + legacyScriptResumeReason;
            legacyScriptPauseChangedAtMs = now;
            legacyScriptResumeWaitingAuction = false;
            legacyScriptResumeAhSent = false;
            System.out.println("[MAB SCRIPT] legacy resume timeout; unpausing anyway, reason=" + legacyScriptResumeReason);
            return false;
        }

        if (now < legacyScriptResumeNextActionAtMs) {
            return true;
        }

        try {
            if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
                if (isLegacyScriptUserMenuOpen()) {
                    legacyScriptPauseActive = false;
                    legacyScriptPauseReason = "resume_cancel_user_gui:" + legacyScriptResumeReason;
                    legacyScriptPauseChangedAtMs = now;
                    legacyScriptResumeWaitingAuction = false;
                    legacyScriptResumeAhSent = false;
                    legacyScriptResumeReason = "none";
                    System.out.println("[MAB SCRIPT] legacy resume cancelled: user GUI is open");
                    return false;
                }
                if (isLegacyScriptOwnedScreen(client.currentScreen)) {
                    closeScreenQuietly();
                    legacyScriptResumeNextActionAtMs = now + 250L;
                    return true;
                }
                legacyScriptPauseActive = false;
                legacyScriptPauseReason = "resume_cancel_non_owned_gui:" + legacyScriptResumeReason;
                legacyScriptPauseChangedAtMs = now;
                legacyScriptResumeWaitingAuction = false;
                legacyScriptResumeAhSent = false;
                legacyScriptResumeReason = "none";
                System.out.println("[MAB SCRIPT] legacy resume cancelled: non-owned GUI is open");
                return false;
            }
        } catch (Throwable ignored) {
        }

        try {
            McChat.send(client, "/ah");
            legacyScriptResumeAhSent = true;
            legacyScriptResumeNextActionAtMs = now + 700L;
            System.out.println("[MAB SCRIPT] legacy resume sent /ah, reason=" + legacyScriptResumeReason);
        } catch (Throwable throwable) {
            legacyScriptResumeNextActionAtMs = now + 700L;
            System.out.println("[MAB SCRIPT] legacy resume /ah failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }

        return true;
    }


    private boolean isLegacyScriptUserMenuOpen() {
        try {
            if (client == null || client.currentScreen == null) {
                return false;
            }
            Screen screen = client.currentScreen;
            if (screen instanceof ChatScreen) {
                return false;
            }
            // Only narrow storage/auction containers are owned by the legacy script.
            // Generic server menus, player inventory, options, Malfix GUI and other
            // screens are user UI and must not be closed during script resume.
            return !isLegacyScriptOwnedScreen(screen);
        } catch (Throwable throwable) {
            return false;
        }
    }


    private void tickRuntimeSettingsReload() {
        long now = System.currentTimeMillis();
        if (now < runtimeSettingsNextReloadAtMs) {
            return;
        }
        runtimeSettingsNextReloadAtMs = now + 2_000L;
        runtimeSettings.reloadIfChanged();
        applyRuntimeSettingsToConfig(false);
        if (telegramNotifier != null) {
            telegramNotifier.reload(runtimeSettings);
        }
    }

    private void applyRuntimeSettingsToConfig(boolean save) {
        try {
            if (runtimeSettings == null || config == null) {
                return;
            }
            String anarchy = runtimeSettings.getAnarchy();
            if (anarchy != null && !anarchy.trim().isEmpty() && !anarchy.trim().equals(config.getAntiAfkAnarchy())) {
                config.setAntiAfkAnarchy(anarchy.trim());
                if (save) {
                    configManager.save(config);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean tickAutoRejoinEarly(MinecraftClient mc) {
        if (mc == null || runtimeSettings == null || !runtimeSettings.isAutoRejoinEnabled()) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (mc.player != null && mc.world != null) {
            if (autoRejoinWaiting) {
                autoRejoinWaiting = false;
                autoRejoinLastReason = "connected";
                if (runtimeSettings.isAutoRejoinRestoreAuction()) {
                    try {
                        startAntiAfkReconnect("autorejoin_connected");
                    } catch (Throwable ignored) {
                    }
                }
                return true;
            }
            return false;
        }

        Screen screen = mc.currentScreen;
        if (!isDisconnectedScreen(screen)) {
            return false;
        }

        autoRejoinLastScreen = screen == null ? "none" : screen.getClass().getName();
        if (!autoRejoinWaiting) {
            autoRejoinWaiting = true;
            autoRejoinAtMs = now + runtimeSettings.getAutoRejoinDelayMs();
            autoRejoinServerInfo = reflectCurrentServerInfo(mc);
            autoRejoinLastReason = autoRejoinServerInfo == null ? "waiting_no_server_info" : "waiting_disconnect";
            return true;
        }

        if (now < autoRejoinAtMs) {
            return true;
        }

        if (now - autoRejoinLastAttemptAtMs < Math.max(1_000L, runtimeSettings.getAutoRejoinDelayMs())) {
            return true;
        }

        autoRejoinLastAttemptAtMs = now;
        autoRejoinAttempts++;
        boolean ok = tryReconnectReflective(mc, screen, autoRejoinServerInfo);
        autoRejoinLastReason = ok ? "connect_invoked" : "connect_failed";
        autoRejoinAtMs = now + runtimeSettings.getAutoRejoinDelayMs();
        return true;
    }

    private boolean isDisconnectedScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        String name = screen.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("disconnect") || name.contains("disconnected") || name.contains("disconnection");
    }

    private Object reflectCurrentServerInfo(MinecraftClient mc) {
        try {
            java.lang.reflect.Method m = mc.getClass().getMethod("getCurrentServerEntry");
            return m.invoke(mc);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean tryReconnectReflective(MinecraftClient mc, Screen parent, Object serverInfo) {
        if (mc == null || serverInfo == null) {
            return false;
        }
        try {
            Object address = null;
            String addressText = reflectServerAddress(serverInfo);
            if (!addressText.isEmpty()) {
                address = makeServerAddress(addressText);
            }

            Class<?> connectScreenClass = Class.forName("net.minecraft.client.gui.screen.ConnectScreen");
            java.lang.reflect.Method[] methods = connectScreenClass.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                java.lang.reflect.Method method = methods[i];
                if (!"connect".equals(method.getName())) {
                    continue;
                }
                int modifiers = method.getModifiers();
                if (!java.lang.reflect.Modifier.isStatic(modifiers)) {
                    continue;
                }
                Class<?>[] types = method.getParameterTypes();
                Object[] args = new Object[types.length];
                boolean possible = true;
                for (int j = 0; j < types.length; j++) {
                    Class<?> t = types[j];
                    String tn = t.getName();
                    if (t.isAssignableFrom(Screen.class) || "net.minecraft.client.gui.screen.Screen".equals(tn)) {
                        args[j] = parent;
                    } else if (t.isAssignableFrom(MinecraftClient.class) || "net.minecraft.client.MinecraftClient".equals(tn)) {
                        args[j] = mc;
                    } else if (serverInfo != null && t.isAssignableFrom(serverInfo.getClass())) {
                        args[j] = serverInfo;
                    } else if (tn.endsWith("ServerAddress")) {
                        if (address == null) {
                            possible = false;
                            break;
                        }
                        args[j] = address;
                    } else if (t == boolean.class || t == Boolean.class) {
                        args[j] = Boolean.FALSE;
                    } else {
                        args[j] = null;
                    }
                }
                if (!possible) {
                    continue;
                }
                method.setAccessible(true);
                method.invoke(null, args);
                return true;
            }
        } catch (Throwable throwable) {
            System.out.println("[MAB] autorejoin connect failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }
        return false;
    }

    private String reflectServerAddress(Object serverInfo) {
        if (serverInfo == null) {
            return "";
        }
        try {
            Field f = serverInfo.getClass().getField("address");
            Object v = f.get(serverInfo);
            return v == null ? "" : String.valueOf(v).trim();
        } catch (Throwable ignored) {
        }
        try {
            Field[] fields = serverInfo.getClass().getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field f = fields[i];
                if (f.getType() == String.class && f.getName().toLowerCase(Locale.ROOT).contains("address")) {
                    f.setAccessible(true);
                    Object v = f.get(serverInfo);
                    return v == null ? "" : String.valueOf(v).trim();
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private Object makeServerAddress(String addressText) {
        try {
            Class<?> cls = Class.forName("net.minecraft.client.network.ServerAddress");
            java.lang.reflect.Method[] methods = cls.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                java.lang.reflect.Method method = methods[i];
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (method.getParameterTypes().length == 1 && method.getParameterTypes()[0] == String.class
                        && method.getReturnType().getName().endsWith("ServerAddress")) {
                    method.setAccessible(true);
                    return method.invoke(null, addressText);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void handleRuntimeSettingsCommand(String[] parts) {
        String action = parts.length >= 3 ? parts[2].toLowerCase(Locale.ROOT) : "status";
        if ("status".equals(action) || "info".equals(action)) {
            runtimeSettings.reloadIfChanged();
            applyRuntimeSettingsToConfig(false);
            chat.send("runtime: " + runtimeSettings.compact());
            return;
        }
        if ("reload".equals(action) || "load".equals(action)) {
            runtimeSettings.reload(true);
            applyRuntimeSettingsToConfig(true);
            if (telegramNotifier != null) {
                telegramNotifier.reload(runtimeSettings);
            }
            applyConfigToRuntime();
            chat.send("runtime reloaded: " + runtimeSettings.compact());
            return;
        }
        if ("dir".equals(action) || "path".equals(action) || "folder".equals(action)) {
            chat.send("runtime file: " + runtimeSettings.getFile().getAbsolutePath());
            return;
        }
        chat.send("unknown runtime action. Use: .mab runtime status/reload/dir");
    }

    private void handleAutoRejoinCommand(String[] parts) {
        String action = parts.length >= 3 ? parts[2].toLowerCase(Locale.ROOT) : "status";
        if ("status".equals(action) || "info".equals(action)) {
            chat.send("autorejoin: enabled=" + runtimeSettings.isAutoRejoinEnabled()
                    + ", waiting=" + autoRejoinWaiting
                    + ", nextInMs=" + Math.max(0L, autoRejoinAtMs - System.currentTimeMillis())
                    + ", attempts=" + autoRejoinAttempts
                    + ", screen=" + autoRejoinLastScreen
                    + ", reason=" + autoRejoinLastReason
                    + ", runtime=" + runtimeSettings.compact());
            return;
        }
        if ("stop".equals(action) || "cancel".equals(action) || "off".equals(action)) {
            autoRejoinWaiting = false;
            autoRejoinLastReason = "manual_stop";
            chat.send("autorejoin stopped");
            return;
        }
        if ("test".equals(action) || "now".equals(action)) {
            autoRejoinWaiting = true;
            autoRejoinAtMs = System.currentTimeMillis();
            autoRejoinServerInfo = reflectCurrentServerInfo(client);
            autoRejoinLastReason = "manual_test";
            chat.send("autorejoin test armed: serverInfo=" + (autoRejoinServerInfo == null ? "none" : autoRejoinServerInfo.getClass().getSimpleName()));
            return;
        }
        chat.send("unknown autorejoin action. Use: .mab autorejoin status/test/stop");
    }

    private void handleDotScriptCommand(String[] parts) {
        String action = parts.length >= 2 ? parts[1].toLowerCase(Locale.ROOT) : "status";
        if ("reload".equals(action) || "load".equals(action) || "restart".equals(action)) {
            setLegacyScriptPause(false, "dot_script_reload");
            scriptManager.reloadAsync(false);
            chat.send("script reload started: " + scriptManager.status());
            return;
        }
        if ("legacyreload".equals(action) || "reloadlegacy".equals(action)) {
            setLegacyScriptPause(false, "dot_script_legacy_reload");
            scriptManager.reloadAsync(true);
            chat.send("script legacy reload started: " + scriptManager.status());
            return;
        }
        if ("dir".equals(action) || "folder".equals(action) || "path".equals(action)) {
            scriptManager.ensureScriptsDir();
            chat.send("script folders: " + scriptManager.getAllScriptDirs());
            return;
        }
        chat.send("script: " + scriptManager.status() + ". Use .script reload");
    }

    private void handleTelegramCommand(String[] parts) {
        String action = parts.length >= 2 ? parts[1].toLowerCase(Locale.ROOT) : "status";
        if ("status".equals(action) || "info".equals(action)) {
            runtimeSettings.reloadIfChanged();
            if (telegramNotifier != null) {
                telegramNotifier.reload(runtimeSettings);
                chat.send("telegram: " + telegramNotifier.status());
            } else {
                chat.send("telegram: not_initialized");
            }
            return;
        }
        if ("reload".equals(action)) {
            runtimeSettings.reload(true);
            if (telegramNotifier != null) telegramNotifier.reload(runtimeSettings);
            chat.send("telegram reloaded: " + (telegramNotifier == null ? "none" : telegramNotifier.status()));
            return;
        }
        if ("test".equals(action) || "sendtest".equals(action) || "ping".equals(action)) {
            runtimeSettings.reloadIfChanged();
            if (telegramNotifier != null) telegramNotifier.reload(runtimeSettings);
            boolean queued = telegramNotifier != null && telegramNotifier.test();
            chat.send((queued ? "telegram test queued: " : "telegram test NOT queued: ") + (telegramNotifier == null ? "none" : telegramNotifier.status()));
            return;
        }
        if ("balance".equals(action) || "bal".equals(action) || "баланс".equals(action)) {
            chat.send(buildTelegramBalanceMessage());
            if (telegramNotifier != null) {
                telegramNotifier.send(buildTelegramBalanceMessage());
                chat.send("telegram balance queued: " + telegramNotifier.status());
            }
            return;
        }
        chat.send("unknown telegram action. Use: .tg status/reload/test/balance. Configure runtime.properties telegram.*");
    }

    private void handleCloudCommand(String[] parts) {
        int offset = parts.length > 0 && (".cloud".equalsIgnoreCase(parts[0]) || "/cloud".equalsIgnoreCase(parts[0])) ? 1 : 2;
        String action = parts.length > offset ? parts[offset].toLowerCase(Locale.ROOT) : "status";
        File dir = cloudDir();

        if ("status".equals(action) || "dir".equals(action) || "folder".equals(action) || "path".equals(action)) {
            if (!dir.exists()) dir.mkdirs();
            chat.send("cloud dir: " + dir.getAbsolutePath() + ". Use .cloud save, .cloud load latest, .cloud list");
            return;
        }
        if ("list".equals(action) || "ls".equals(action)) {
            if (!dir.exists()) dir.mkdirs();
            File[] files = dir.listFiles();
            StringBuilder b = new StringBuilder("cloud saves:");
            int count = 0;
            if (files != null) {
                java.util.Arrays.sort(files);
                for (int i = 0; i < files.length; i++) {
                    File f = files[i];
                    if (f != null && f.isDirectory() && new File(f, "config.json").exists()) {
                        b.append(count == 0 ? " " : ", ").append(f.getName());
                        count++;
                    }
                }
            }
            if (count == 0) b.append(" empty");
            chat.send(b.toString());
            return;
        }
        if ("save".equals(action) || "export".equals(action)) {
            if (!dir.exists()) dir.mkdirs();
            configManager.save(config);
            String stamp = cloudTimestampName();
            File out = new File(dir, stamp);
            File latest = new File(dir, "latest");
            try {
                saveCloudSnapshot(out);
                saveCloudSnapshot(latest);
                chat.send("cloud saved latest + " + stamp + ": " + latest.getAbsolutePath());
            } catch (Throwable throwable) {
                chat.send("cloud save failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            }
            return;
        }
        if ("load".equals(action) || "import".equals(action) || "apply".equals(action)) {
            String name = parts.length > offset + 1 ? safeCloudName(parts[offset + 1]) : "latest";
            File in = new File(dir, name);
            if (!in.exists() && name.endsWith(".json")) {
                in = new File(dir, name.substring(0, name.length() - 5));
            }
            if (!in.exists()) {
                chat.send("cloud load failed: not found " + in.getAbsolutePath());
                return;
            }
            try {
                loadCloudSnapshot(in);
                chat.send("cloud loaded " + name + ": targets=" + config.targetCount() + ", runtime=" + runtimeSettings.compact());
            } catch (Throwable throwable) {
                chat.send("cloud load failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            }
            return;
        }
        chat.send("unknown cloud action. Use: .cloud save, .cloud load latest, .cloud list, .cloud dir");
    }

    private void saveCloudSnapshot(File outDir) throws java.io.IOException {
        if (outDir == null) return;
        if (!outDir.exists()) outDir.mkdirs();
        Files.copy(configManager.getConfigFile().toPath(), new File(outDir, "config.json").toPath(), StandardCopyOption.REPLACE_EXISTING);
        if (runtimeSettings != null && runtimeSettings.getFile() != null && runtimeSettings.getFile().exists()) {
            Files.copy(runtimeSettings.getFile().toPath(), new File(outDir, "runtime.properties").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(new File(outDir, "README_RU.txt")), StandardCharsets.UTF_8);
        try {
            out.write("Malfix local cloud snapshot.\n");
            out.write("Contains config.json with prices/targets/parser/settings and runtime.properties with anarchy/timings/telegram/autorejoin.\n");
            out.write("Load with: .cloud load " + outDir.getName() + "\n");
        } finally {
            out.close();
        }
    }

    private void loadCloudSnapshot(File inDir) throws java.io.IOException {
        File configFile = inDir.isDirectory() ? new File(inDir, "config.json") : inDir;
        if (!configFile.exists()) {
            throw new java.io.FileNotFoundException(configFile.getAbsolutePath());
        }
        Files.copy(configFile.toPath(), configManager.getConfigFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
        File runtimeFile = inDir.isDirectory() ? new File(inDir, "runtime.properties") : null;
        if (runtimeFile != null && runtimeFile.exists() && runtimeSettings != null && runtimeSettings.getFile() != null) {
            Files.copy(runtimeFile.toPath(), runtimeSettings.getFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
            runtimeSettings.reload(true);
            if (telegramNotifier != null) telegramNotifier.reload(runtimeSettings);
        }
        config = configManager.loadOrCreate();
        ScriptItemCatalog.applyCatalogPatch(config);
        applyRuntimeSettingsToConfig(false);
        applyConfigToRuntime();
        configManager.save(config);
    }

    private String cloudTimestampName() {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.ROOT);
        return fmt.format(new java.util.Date());
    }


    private File cloudDir() {
        File root = client == null || client.runDirectory == null ? new File(".") : client.runDirectory;
        return new File(new File(root, "malfix_autobuy"), "cloud");
    }

    private String safeCloudName(String raw) {
        String value = raw == null ? "default" : raw.trim();
        if (value.isEmpty()) value = "default";
        value = value.replaceAll("[^A-Za-z0-9А-Яа-я._-]", "_");
        if (value.length() > 64) value = value.substring(0, 64);
        return value;
    }

    private void dumpHandNbtCommand(String[] parts) {
        if (client == null || client.player == null) {
            chat.send("hand nbt failed: player is not ready");
            return;
        }

        boolean offhand = false;
        if (parts != null) {
            for (int i = 1; i < parts.length; i++) {
                String arg = parts[i] == null ? "" : parts[i].toLowerCase(Locale.ROOT);
                if ("nbt".equals(arg) || "handnbt".equals(arg) || "dumpnbt".equals(arg) || "tag".equals(arg)) {
                    continue;
                }
                if ("off".equals(arg) || "offhand".equals(arg) || "2".equals(arg) || "левая".equals(arg) || "вторая".equals(arg)) {
                    offhand = true;
                }
            }
        }

        ItemStack stack;
        try {
            stack = client.player.getStackInHand(offhand ? Hand.OFF_HAND : Hand.MAIN_HAND);
        } catch (Throwable throwable) {
            chat.send("hand nbt failed: cannot read hand stack: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            return;
        }

        if (stack == null || stack.isEmpty()) {
            chat.send("hand nbt: hand is empty");
            return;
        }

        String itemId = safeItemIdForDump(stack);
        String name = safeItemNameForDump(stack);
        int count = safeItemCountForDump(stack);
        String nbt = safeNbtForDump(stack);
        String tagContains = buildTagContainsSuggestion(nbt);

        StringBuilder builder = new StringBuilder();
        builder.append("=== Malfix hand NBT dump ===\r\n");
        builder.append("hand=").append(offhand ? "OFF_HAND" : "MAIN_HAND").append("\r\n");
        builder.append("itemId=").append(itemId).append("\r\n");
        builder.append("name=").append(name).append("\r\n");
        builder.append("count=").append(count).append("\r\n");
        builder.append("nbt=").append(nbt.isEmpty() ? "<no nbt>" : nbt).append("\r\n");
        builder.append("\r\n");
        builder.append("Для GUI/autobuy обычно нужно:\r\n");
        builder.append("itemId: ").append(itemId).append("\r\n");
        builder.append("tagContains: ").append(tagContains.isEmpty() ? "<empty/no nbt>" : tagContains).append("\r\n");

        File outFile = null;
        try {
            File runDir = client.runDirectory == null ? new File(".") : client.runDirectory;
            File dir = new File(new File(runDir, "malfix_autobuy"), "nbt_dumps");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            outFile = new File(dir, "hand_nbt_" + System.currentTimeMillis() + ".txt");
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8);
            try {
                writer.write(builder.toString());
            } finally {
                writer.close();
            }
        } catch (Throwable throwable) {
            chat.send("hand nbt file write failed: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        }

        System.out.println("[MAB NBT BEGIN]");
        System.out.print(builder.toString());
        System.out.println("[MAB NBT END]");
        chat.send("hand nbt dumped: itemId=" + itemId + ", name=" + name + ", file=" + (outFile == null ? "write_failed" : outFile.getAbsolutePath()));
    }

    private String safeItemIdForDump(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty() || stack.getItem() == null) {
                return "";
            }
            return McItemStacks.itemId(stack);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String safeItemNameForDump(ItemStack stack) {
        try {
            if (stack == null || stack.isEmpty() || stack.getName() == null) {
                return "";
            }
            return stack.getName().getString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private int safeItemCountForDump(ItemStack stack) {
        try {
            return stack == null || stack.isEmpty() ? 0 : stack.getCount();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private String safeNbtForDump(ItemStack stack) {
        try {
            return McItemStacks.componentString(stack);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String buildTagContainsSuggestion(String nbt) {
        if (nbt == null) {
            return "";
        }
        String trimmed = nbt.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        String[] preferredMarkers = new String[] {
                "spookystash:",
                "spooky-item",
                "spookyevents:",
                "PublicBukkitValues",
                "SkullOwner",
                "display:{Name",
                "spookystash"
        };

        for (int i = 0; i < preferredMarkers.length; i++) {
            String marker = preferredMarkers[i];
            int index = trimmed.indexOf(marker);
            if (index >= 0) {
                int end = Math.min(trimmed.length(), index + 140);
                return trimmed.substring(index, end);
            }
        }

        return trimmed.length() <= 180 ? trimmed : trimmed.substring(0, 180);
    }

    private void handleScriptsCommand(String[] parts) {
        String action = parts.length >= 3 ? parts[2].toLowerCase(Locale.ROOT) : "status";

        if ("status".equals(action) || "info".equals(action)) {
            chat.send("scripts: " + scriptManager.status()
                    + ", legacyPause=" + legacyScriptPauseActive
                    + ", pauseReason=" + legacyScriptPauseReason);
            return;
        }

        if ("reload".equals(action) || "load".equals(action) || "restart".equals(action)) {
            setLegacyScriptPause(false, "scripts_reload");
            scriptManager.reloadAsync(false);
            chat.send("scripts reload started: " + scriptManager.status());
            return;
        }

        if ("legacyreload".equals(action) || "reloadlegacy".equals(action) || "oldreload".equals(action)) {
            setLegacyScriptPause(false, "scripts_legacy_reload");
            scriptManager.reloadAsync(true);
            chat.send("legacy scripts reload started: " + scriptManager.status());
            return;
        }

        if ("dir".equals(action) || "folder".equals(action) || "path".equals(action)) {
            scriptManager.ensureScriptsDir();
            chat.send("scripts folders: " + scriptManager.getAllScriptDirs());
            return;
        }

        if ("pauseoff".equals(action) || "resume".equals(action) || "unpause".equals(action)) {
            setLegacyScriptPause(false, "manual_script_pauseoff");
            chat.send("legacy script pause disabled");
            return;
        }

        chat.send("unknown scripts action: " + action + ". Use: .mab scripts status/reload/legacyreload/dir/pauseoff");
    }

    private void handleShulkerCommand(String[] parts) {
        String action = parts.length >= 3 ? parts[2].toLowerCase(Locale.ROOT) : "status";

        if ("status".equals(action) || "info".equals(action)) {
            chat.send("shulker: " + shulkerController.compact());
            return;
        }

        if ("stop".equals(action) || "off".equals(action) || "cancel".equals(action)) {
            shulkerController.stop("manual_stop");
            chat.send("shulker stopped: " + shulkerController.compact());
            return;
        }

        if ("reset".equals(action) || "clear".equals(action)) {
            shulkerController.resetKnownFullShulkers();
            chat.send("shulker known-full/empty cache reset: " + shulkerController.compact());
            return;
        }

        if ("scan".equals(action) || "rescan".equals(action)) {
            if (!isSafeIdleForManualShulkerTest()) {
                chat.send("shulker scan blocked: wait until buy/sell/storage is idle. " + buildBusyCompactForParser()
                        + ", shulker=" + shulkerController.compact());
                return;
            }
            boolean started = shulkerController.startScan("manual_scan", false);
            chat.send("shulker scan started=" + started + ": " + shulkerController.compact());
            return;
        }

        if ("test".equals(action) || "now".equals(action) || "put".equals(action)) {
            if (!isSafeIdleForManualShulkerTest()) {
                chat.send("shulker test blocked: wait until buy/sell/storage is idle. " + buildBusyCompactForParser()
                        + ", shulker=" + shulkerController.compact());
                return;
            }

            boolean started = shulkerController.startPut("manual_test_full_rescan", auctionView.isAuctionOpen());
            chat.send("shulker test started=" + started + ": " + shulkerController.compact());
            return;
        }

        if ("take".equals(action) || "back".equals(action) || "pull".equals(action) || "get".equals(action)) {
            if (!isSafeIdleForManualShulkerTest()) {
                chat.send("shulker take blocked: wait until buy/sell/storage is idle. " + buildBusyCompactForParser()
                        + ", shulker=" + shulkerController.compact());
                return;
            }

            boolean started = shulkerController.startTake("manual_take_back", auctionView.isAuctionOpen());
            chat.send("shulker take started=" + started + ": " + shulkerController.compact());
            return;
        }

        chat.send("unknown shulker action: " + action + ". Use: .mab shulker status/test/take/scan/stop");
    }

    private boolean shouldRestoreAuctionAfterShulker() {
        return auctionView.isAuctionOpen()
                || fullAutoTimedEnabled
                || cycleFullEnabled
                || cycleFullLoopEnabled
                || limitedLoop.isRunning()
                || oneCycle.isPending()
                || controlledBuyClick.isPending();
    }

    private boolean isSafeIdleForManualShulkerTest() {
        return !antiAfkRunning
                && !parserRunning
                && !safeAutoRunEnabled
                && !limitedLoop.isRunning()
                && !oneCycle.isPending()
                && !controlledBuyClick.isPending()
                && !sellerLoopEnabled
                && !sellerCycleEnabled
                && !sellerReturnToAuctionPending
                && !cycleFullEnabled
                && !cycleFullLoopEnabled
                && !shulkerController.isRunning();
    }

    private boolean detectAndStartSpamKickRecovery(String message) {
        if (message == null) {
            return false;
        }

        String normalized = message.replace('ё', 'е').toLowerCase(Locale.ROOT);
        boolean spamKick = normalized.contains("kicked for spamming")
                || normalized.contains("kick for spamming")
                || normalized.contains("кикнуты с сервера")
                || normalized.contains("кикнут с сервера")
                || (normalized.contains("spamming") && (normalized.contains("kick") || normalized.contains("кик")))
                || (normalized.contains("спам") && (normalized.contains("kick") || normalized.contains("кик")));

        if (!spamKick) {
            return false;
        }

        String anarchy = extractAnarchyFromKickMessage(message);
        if (anarchy == null || anarchy.trim().isEmpty()) {
            anarchy = config.getAntiAfkAnarchy();
        }
        if (anarchy == null || anarchy.trim().isEmpty()) {
            anarchy = detectCurrentAnarchyNumber();
        }

        startSpamKickRecovery(anarchy, "chat_spam_kick", message);
        return true;
    }

    private String extractAnarchyFromKickMessage(String message) {
        if (message == null) {
            return "";
        }

        String normalized = message.replace('ё', 'е').toLowerCase(Locale.ROOT);
        Matcher matcher = Pattern.compile("(?:\\ban|\\bан)\\s*[-_:#№]*\\s*([0-9]{1,4})", Pattern.CASE_INSENSITIVE).matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }

        matcher = Pattern.compile("(?:сервер[ае]?|анарх(?:ия|ии|ию)?)\\D{0,24}([0-9]{1,4})", Pattern.CASE_INSENSITIVE).matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }

        matcher = Pattern.compile("([0-9]{1,4})").matcher(normalized);
        if (matcher.find() && (normalized.contains("кик") || normalized.contains("kick"))) {
            return matcher.group(1);
        }

        return "";
    }

    private void startSpamKickRecovery(String anarchy, String reason, String message) {
        String safeAnarchy = anarchy == null ? "" : anarchy.trim();
        if (safeAnarchy.startsWith("an") || safeAnarchy.startsWith("ан")) {
            safeAnarchy = safeAnarchy.replaceAll("[^0-9]", "");
        }

        if (safeAnarchy.isEmpty()) {
            spamKickLastReason = "no_anarchy_detected:" + (reason == null ? "none" : reason);
            spamKickLastMessage = message == null ? "" : message;
            System.out.println("[MAB] spam-kick recovery skipped: cannot detect anarchy, message=" + spamKickLastMessage);
            return;
        }

        long now = System.currentTimeMillis();
        if (spamKickRecovering) {
            spamKickAnarchy = safeAnarchy;
            spamKickNextAtMs = now + runtimeSettings.getSpamKickInitialWaitMs();
            spamKickLastReason = "restarted:" + (reason == null ? "none" : reason);
            spamKickLastMessage = message == null ? spamKickLastMessage : message;
            System.out.println("[MAB] spam-kick recovery restarted: an" + spamKickAnarchy);
            return;
        }

        spamKickRecovering = true;
        spamKickPhase = "wait_before_join";
        spamKickAnarchy = safeAnarchy;
        spamKickNextAtMs = now + runtimeSettings.getSpamKickInitialWaitMs();
        spamKickStartedAtMs = now;
        spamKickAhAttempts = 0;
        spamKickLastReason = reason == null ? "chat_spam_kick" : reason;
        spamKickLastMessage = message == null ? "" : message;

        config.setAntiAfkAnarchy(safeAnarchy);
        saveConfig();

        if (isAutomationActiveForLegacyScript() || sellerLoopEnabled || sellerCycleEnabled || sellerReturnToAuctionPending || cycleFullEnabled || fullAutoTimedEnabled) {
            setLegacyScriptPause(true, "spam_kick_recovery");
        }

        try {
            if (client != null && client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
                closeScreenQuietly();
            }
        } catch (Throwable ignored) {
        }

        System.out.println("[MAB] spam-kick recovery started: /an" + spamKickAnarchy + ", reason=" + spamKickLastReason);
    }

    private boolean tickSpamKickRecovery() {
        if (!spamKickRecovering) {
            return false;
        }

        if (client == null || client.player == null || client.world == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (now < spamKickNextAtMs) {
            return true;
        }

        if (now - spamKickStartedAtMs > runtimeSettings.getSpamKickTotalTimeoutMs()) {
            finishSpamKickRecovery("timeout", false);
            return false;
        }

        if ("wait_before_join".equals(spamKickPhase)) {
            try {
                if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
                    closeScreenQuietly();
                    spamKickNextAtMs = now + 250L;
                    return true;
                }
            } catch (Throwable ignored) {
            }

            sendPlayerCommand("/an" + spamKickAnarchy);
            spamKickPhase = "wait_after_join";
            spamKickNextAtMs = now + runtimeSettings.getSpamKickJoinWaitMs();
            spamKickLastReason = "join_sent:/an" + spamKickAnarchy;
            System.out.println("[MAB] spam-kick recovery sent /an" + spamKickAnarchy);
            return true;
        }

        if ("wait_after_join".equals(spamKickPhase)) {
            try {
                if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) {
                    closeScreenQuietly();
                    spamKickNextAtMs = now + 250L;
                    return true;
                }
            } catch (Throwable ignored) {
            }

            sendPlayerCommand("/ah");
            spamKickAhAttempts++;
            spamKickPhase = "wait_auction";
            spamKickNextAtMs = now + runtimeSettings.getSpamKickAhWaitMs();
            spamKickLastReason = "auction_restore_sent";
            System.out.println("[MAB] spam-kick recovery sent /ah");
            return true;
        }

        if ("wait_auction".equals(spamKickPhase)) {
            if (auctionView != null && auctionView.isAuctionOpen()) {
                finishSpamKickRecovery("auction_restored", true);
                return false;
            }

            if (spamKickAhAttempts < runtimeSettings.getSpamKickAhMaxAttempts()) {
                sendPlayerCommand("/ah");
                spamKickAhAttempts++;
                spamKickNextAtMs = now + runtimeSettings.getSpamKickAhRetryMs();
                spamKickLastReason = "auction_restore_retry_" + spamKickAhAttempts;
                System.out.println("[MAB] spam-kick recovery retry /ah attempt=" + spamKickAhAttempts);
                return true;
            }

            finishSpamKickRecovery("auction_restore_failed", false);
            return false;
        }

        finishSpamKickRecovery("unknown_phase:" + spamKickPhase, false);
        return false;
    }

    private void finishSpamKickRecovery(String reason, boolean restoredAuction) {
        long now = System.currentTimeMillis();
        String safeReason = reason == null ? "done" : reason;

        spamKickRecovering = false;
        spamKickPhase = "idle";
        spamKickNextAtMs = 0L;
        spamKickLastRunAtMs = now;
        spamKickRuns++;
        spamKickLastReason = safeReason;

        if (legacyScriptPauseActive || legacyScriptResumeWaitingAuction) {
            legacyScriptPauseActive = false;
            legacyScriptPauseReason = "spam_kick_done:" + safeReason;
            legacyScriptPauseChangedAtMs = now;
            legacyScriptResumeWaitingAuction = false;
            legacyScriptResumeAhSent = false;
            legacyScriptResumeReason = "none";
        }

        if (!restoredAuction && isAutomationActiveForLegacyScript()) {
            fullAutoTimedNextStartAtMs = now + MalfixTimings.AUTOSELL_OPEN_MS;
            fullAutoTimedStopReason = "waiting_after_spam_kick_restore_failed";
        }

        System.out.println("[MAB] spam-kick recovery finished: reason=" + safeReason
                + ", restoredAuction=" + restoredAuction
                + ", an" + spamKickAnarchy);
    }

    private void stopSpamKickRecovery(String reason) {
        if (!spamKickRecovering) {
            spamKickLastReason = reason == null ? "stopped" : reason;
            return;
        }

        spamKickRecovering = false;
        spamKickPhase = "idle";
        spamKickNextAtMs = 0L;
        spamKickLastReason = reason == null ? "stopped" : reason;
        System.out.println("[MAB] spam-kick recovery stopped: " + spamKickLastReason);
    }

    private String buildSpamKickCompact() {
        long now = System.currentTimeMillis();
        return "recovering=" + spamKickRecovering
                + ", phase=" + spamKickPhase
                + ", anarchy=" + (spamKickAnarchy == null || spamKickAnarchy.isEmpty() ? "none" : spamKickAnarchy)
                + ", nextInMs=" + Math.max(0L, spamKickNextAtMs - now)
                + ", attemptsAh=" + spamKickAhAttempts
                + ", runs=" + spamKickRuns
                + ", lastReason=" + spamKickLastReason;
    }

    private void handleSpamKickCommand(String[] parts) {
        String action = parts.length >= 3 ? parts[2].toLowerCase(Locale.ROOT) : "status";

        if ("status".equals(action) || "info".equals(action)) {
            chat.send("spam-rejoin: " + buildSpamKickCompact());
            return;
        }

        if ("stop".equals(action) || "cancel".equals(action)) {
            stopSpamKickRecovery("manual_stop");
            if (legacyScriptPauseActive && "spam_kick_recovery".equals(legacyScriptPauseReason)) {
                setLegacyScriptPause(false, "manual_spam_rejoin_stop");
            }
            chat.send("spam-rejoin stopped: " + buildSpamKickCompact());
            return;
        }

        if ("test".equals(action) || "now".equals(action)) {
            String anarchy = parts.length >= 4 ? parts[3] : config.getAntiAfkAnarchy();
            if (anarchy == null || anarchy.trim().isEmpty()) {
                anarchy = detectCurrentAnarchyNumber();
            }
            startSpamKickRecovery(anarchy, "manual_test", "manual_test");
            chat.send("spam-rejoin test started: " + buildSpamKickCompact());
            return;
        }

        chat.send("unknown spam-rejoin action. Use: .mab spamrejoin status | test [305] | stop");
    }

    private void handleAntiAfkCommand(String[] parts) {
        if (parts.length < 3 || "status".equalsIgnoreCase(parts[2])) {
            chat.send("anti-afk: " + buildAntiAfkCompact());
            chat.send("usage: .mab antiafk on/off | interval <ms> | anarchy <505> | test. FullAuto/autobuy: таймер с нуля; test/now = перезаход сразу.");
            return;
        }

        String action = parts[2].toLowerCase(Locale.ROOT);
        if ("on".equals(action) || "enable".equals(action)) {
            armAntiAfkTimerFromNow("manual_enabled_timer_start");
            saveConfigAndReport();
            chat.send("anti-afk включён: таймер запущен с нуля, перезаход будет только после интервала. " + buildAntiAfkCompact());
            return;
        }

        if ("off".equals(action) || "disable".equals(action)) {
            config.setAntiAfkEnabled(false);
            antiAfkRunning = false;
            antiAfkPhase = "idle";
            antiAfkLastChatTriggerAtMs = 0L;
            antiAfkLastChatTriggerMessage = "";
            saveConfigAndReport();
            chat.send("anti-afk disabled: " + buildAntiAfkCompact());
            return;
        }

        if ("interval".equals(action) || "delay".equals(action)) {
            if (parts.length < 4) {
                chat.send("usage: .mab antiafk interval <ms>");
                return;
            }
            long value = parseLong(parts[3], -1L);
            if (value <= 0L) {
                chat.send("bad anti-afk interval: " + parts[3]);
                return;
            }
            config.setAntiAfkIntervalMs(value);
            antiAfkNextAtMs = System.currentTimeMillis() + config.getAntiAfkIntervalMs();
            saveConfigAndReport();
            chat.send("anti-afk interval set: " + buildAntiAfkCompact());
            return;
        }

        if ("anarchy".equals(action) || "an".equals(action) || "server".equals(action)) {
            if (parts.length < 4) {
                chat.send("usage: .mab antiafk anarchy <505>. Empty/auto = autodetect");
                return;
            }
            if ("auto".equalsIgnoreCase(parts[3]) || "none".equalsIgnoreCase(parts[3]) || "clear".equalsIgnoreCase(parts[3])) {
                config.setAntiAfkAnarchy("");
            } else {
                config.setAntiAfkAnarchy(parts[3]);
            }
            saveConfigAndReport();
            chat.send("anti-afk anarchy set: " + buildAntiAfkCompact());
            return;
        }

        if ("test".equals(action) || "now".equals(action)) {
            if (antiAfkRunning) {
                chat.send("anti-afk already running: " + buildAntiAfkCompact());
                return;
            }
            if (!isSafeIdleForAntiAfk()) {
                chat.send("anti-afk test blocked: wait until buy/sell/storage is idle. " + buildBusyCompactForParser());
                return;
            }
            startAntiAfkReconnect("manual_test");
            chat.send("anti-afk test started: " + buildAntiAfkCompact());
            return;
        }

        chat.send("unknown anti-afk action: " + action);
    }

    private void tickAntiAfk() {
        long now = System.currentTimeMillis();

        if (antiAfkRunning) {
            tickAntiAfkRunning(now);
            return;
        }

        if (!config.isAntiAfkEnabled()) {
            return;
        }

        if (!isAntiAfkContextActive()) {
            // Do not let an old idle timer expire while autobuy/fullauto is off.
            // Otherwise enabling autobuy later can trigger an instant /hub -> /an -> /ah.
            antiAfkNextAtMs = now + config.getAntiAfkIntervalMs();
            antiAfkLastReason = "idle_timer_armed";
            return;
        }

        if (antiAfkNextAtMs <= 0L) {
            antiAfkNextAtMs = now + config.getAntiAfkIntervalMs();
            return;
        }

        if (now < antiAfkNextAtMs) {
            return;
        }

        if (!isSafeIdleForAntiAfk()) {
            antiAfkLastReason = "waiting_safe_idle";
            return;
        }

        startAntiAfkReconnect("timer");
    }

    private void armAntiAfkTimerFromNow(String reason) {
        config.setAntiAfkEnabled(true);
        config.setAntiAfkIntervalMs(MalfixTimings.ANTI_AFK_INTERVAL_MS);
        antiAfkRunning = false;
        antiAfkPhase = "idle";
        antiAfkPhaseUntilMs = 0L;
        antiAfkNextAtMs = System.currentTimeMillis() + config.getAntiAfkIntervalMs();
        antiAfkLastReason = reason == null ? "timer_armed" : reason;
    }

    private boolean isAntiAfkContextActive() {
        return fullAutoTimedEnabled || sellOnlyTimedEnabled || cycleFullLoopEnabled || safeAutoRunEnabled || runtime.controller().context().enabled;
    }

    private boolean isSafeIdleForAntiAfk() {
        return !cycleFullEnabled
                && !isSellOnlyCycleActive()
                && !cycleFullLoopWaitingForCycle
                && !safeAutoRunEnabled
                && !limitedLoop.isRunning()
                && !oneCycle.isPending()
                && !controlledBuyClick.isPending()
                && !sellerLoopEnabled
                && !sellerCycleEnabled
                && !sellerReturnToAuctionPending
                && !parserRunning
                && !shulkerController.isRunning();
    }

    private void startAntiAfkReconnect(String reason) {
        String anarchy = runtimeSettings.getAnarchy();
        if (anarchy == null || anarchy.trim().isEmpty()) {
            anarchy = config.getAntiAfkAnarchy();
        }
        if (anarchy == null || anarchy.trim().isEmpty()) {
            anarchy = detectCurrentAnarchyNumber();
        }

        if (anarchy == null || anarchy.trim().isEmpty() || "none".equalsIgnoreCase(anarchy)) {
            antiAfkLastReason = "no_anarchy_detected:" + reason;
            antiAfkNextAtMs = System.currentTimeMillis() + MalfixTimings.ANTI_AFK_INTERVAL_MS;
            chat.send("anti-afk skipped: cannot detect anarchy. Set manually: .mab antiafk anarchy 505");
            return;
        }

        antiAfkRunning = true;
        antiAfkPhase = "send_hub";
        long now = System.currentTimeMillis();
        antiAfkPhaseUntilMs = reason != null && reason.toLowerCase(Locale.ROOT).contains("autorejoin")
                ? now + runtimeSettings.getAutoRejoinPostLoginWaitMs()
                : 0L;
        antiAfkLastAnarchy = anarchy;
        antiAfkLastReason = "running:" + reason;

        // The AFK-block trigger can happen while seller/buy/storage state machines are active.
        // Do not let them keep sending /ah sell, /ah, click packets, or storage actions while
        // the rejoin sequence is in progress; onClientTick returns while antiAfkRunning=true.
    }

    private void tickAntiAfkRunning(long now) {
        if (now < antiAfkPhaseUntilMs) {
            if ("finish".equals(antiAfkPhase) && auctionView != null && auctionView.isAuctionOpen()) {
                antiAfkPhaseUntilMs = 0L;
            } else {
                return;
            }
        }

        if ("send_hub".equals(antiAfkPhase)) {
            closeScreenQuietly();
            sendPlayerCommand(resolveRuntimeCommand(runtimeSettings.getAutoRejoinHubCommand(), antiAfkLastAnarchy));
            antiAfkPhase = "send_join";
            antiAfkPhaseUntilMs = now + runtimeSettings.getAntiAfkHubWaitMs();
            antiAfkLastReason = "hub_sent";
            return;
        }

        if ("send_join".equals(antiAfkPhase)) {
            sendPlayerCommand(resolveRuntimeCommand(runtimeSettings.getAutoRejoinAnarchyCommand(), antiAfkLastAnarchy));
            antiAfkPhase = "restore_auction";
            antiAfkPhaseUntilMs = now + runtimeSettings.getAntiAfkJoinWaitMs();
            antiAfkLastReason = "join_sent:/an" + antiAfkLastAnarchy;
            return;
        }

        if ("restore_auction".equals(antiAfkPhase)) {
            if (sellOnlyTimedEnabled) {
                closeScreenQuietly();
                antiAfkPhase = "finish";
                antiAfkPhaseUntilMs = now + runtimeSettings.getAntiAfkAuctionRestoreWaitMs();
                antiAfkLastReason = "sellonly_no_auction_restore";
                return;
            }

            if (auctionView != null && auctionView.isAuctionOpen()) {
                antiAfkPhase = "finish";
                antiAfkPhaseUntilMs = 0L;
                antiAfkLastReason = "auction_already_restored";
                return;
            }

            sendPlayerCommand(resolveRuntimeCommand(runtimeSettings.getAutoRejoinAuctionCommand(), antiAfkLastAnarchy));
            antiAfkPhase = "finish";
            antiAfkPhaseUntilMs = now + runtimeSettings.getAntiAfkAuctionRestoreWaitMs();
            antiAfkLastReason = "auction_restore_sent";
            return;
        }

        if ("finish".equals(antiAfkPhase) && auctionView != null && auctionView.isAuctionOpen()) {
            antiAfkPhaseUntilMs = 0L;
        }

        antiAfkRunning = false;
        antiAfkPhase = "idle";
        antiAfkLastRunAtMs = now;
        antiAfkRuns++;
        antiAfkNextAtMs = now + config.getAntiAfkIntervalMs();
        antiAfkLastReason = "done";
        chat.send("anti-afk done: /hub -> /an" + antiAfkLastAnarchy
                + (sellOnlyTimedEnabled ? " -> closed screen" : " -> /ah")
                + ". nextInMs=" + config.getAntiAfkIntervalMs() + " (5:00)");
    }

    private String detectCurrentAnarchyNumber() {
        List<String> texts = new ArrayList<String>();
        collectScoreboardTexts(texts);
        collectTextFields(client == null ? null : client.inGameHud, texts, 0);

        for (String text : texts) {
            String parsed = parseAnarchyNumber(text);
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }

        return "";
    }

    private void collectScoreboardTexts(List<String> out) {
        if (client == null || client.world == null || out == null) {
            return;
        }

        try {
            Scoreboard scoreboard = client.world.getScoreboard();
            if (scoreboard == null) {
                return;
            }

            for (ScoreboardDisplaySlot slot : ScoreboardDisplaySlot.values()) {
                ScoreboardObjective objective = scoreboard.getObjectiveForSlot(slot);
                if (objective == null) {
                    continue;
                }

                if (objective.getDisplayName() != null) {
                    out.add(objective.getDisplayName().getString());
                }

                for (ScoreboardEntry score : scoreboard.getScoreboardEntries(objective)) {
                    if (score == null) {
                        continue;
                    }
                    if (score.owner() != null) {
                        out.add(score.owner());
                    }
                    try {
                        if (score.name() != null) {
                            out.add(score.name().getString());
                        }
                        if (score.display() != null) {
                            out.add(score.display().getString());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void collectTextFields(Object object, List<String> out, int depth) {
        if (object == null || out == null || depth > 2) {
            return;
        }

        try {
            Field[] fields = object.getClass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(object);
                if (value == null) {
                    continue;
                }

                if (value instanceof Text) {
                    out.add(((Text) value).getString());
                } else if (depth < 1 && !isPrimitiveLike(value)) {
                    collectTextFields(value, out, depth + 1);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean isPrimitiveLike(Object value) {
        if (value == null) {
            return true;
        }

        Class<?> type = value.getClass();
        return type.isPrimitive()
                || type.getName().startsWith("java.lang")
                || type.getName().startsWith("java.util")
                || type.isEnum();
    }

    private String parseAnarchyNumber(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text.replace('ё', 'е').toLowerCase(Locale.ROOT);
        if (!normalized.contains("анарх") && !normalized.contains("anarchy")) {
            return "";
        }

        Matcher matcher = Pattern.compile("(?:анархия|анарх|anarchy)[^0-9]{0,32}([0-9]{1,4})", Pattern.CASE_INSENSITIVE).matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }

        matcher = Pattern.compile("([0-9]{1,4})").matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return "";
    }

    private String buildAntiAfkCompact() {
        long now = System.currentTimeMillis();
        return "enabled=" + config.isAntiAfkEnabled()
                + ", running=" + antiAfkRunning
                + ", phase=" + antiAfkPhase
                + ", nextInMs=" + Math.max(0L, antiAfkNextAtMs - now)
                + ", intervalMs=" + config.getAntiAfkIntervalMs()
                + ", anarchy=" + (config.getAntiAfkAnarchy() == null || config.getAntiAfkAnarchy().isEmpty() ? "auto" : config.getAntiAfkAnarchy())
                + ", lastAnarchy=" + antiAfkLastAnarchy
                + ", runs=" + antiAfkRuns
                + ", chatTriggerAgoMs=" + (antiAfkLastChatTriggerAtMs <= 0L ? -1L : Math.max(0L, now - antiAfkLastChatTriggerAtMs))
                + ", lastReason=" + antiAfkLastReason;
    }


    private String resolveRuntimeCommand(String template, String anarchy) {
        String command = template == null || template.trim().isEmpty() ? "" : template.trim();
        if (command.isEmpty()) {
            return command;
        }
        String safeAnarchy = anarchy == null ? "" : anarchy.trim();
        command = command.replace("{anarchy}", safeAnarchy);
        command = command.replace("%anarchy%", safeAnarchy);
        command = command.replace("{an}", safeAnarchy);
        return command;
    }

    private void closeScreenQuietly() {
        try {
            if (client != null) {
                client.setScreen(null);
            }
        } catch (Throwable ignored) {
        }
    }

    private void sendPlayerCommand(final String command) {
        if (command == null || command.trim().isEmpty() || client == null) {
            return;
        }

        client.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (client.player != null) {
                        McChat.send(client, command);
                    }
                } catch (Throwable throwable) {
                    chat.send("command send failed: " + command + ", " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                }
            }
        });
    }

    private void runUnstackCommand(String[] parts) {
        applyConfigToRuntime();

        String action = parts.length >= 3 && parts[2] != null ? parts[2].toLowerCase(Locale.ROOT) : "once";

        if ("status".equals(action) || "debug".equals(action)) {
            chat.send("unstack: " + unstackController.compact());
            return;
        }

        if (hasBlockingScreenOpen()) {
            chat.send("unstack blocked: close current GUI first. screen=" + currentScreenName());
            return;
        }

        if (!unstackController.needsUnstack()) {
            chat.send("unstack: nothing_to_unstack. " + unstackController.compact());
            return;
        }

        boolean running = unstackController.tick();
        chat.send("unstack once: running=" + running + ", " + unstackController.compact());
    }

    private void runSellerPreview() {
        if (safeAutoRunEnabled || limitedLoop.isRunning() || oneCycle.isPending() || controlledBuyClick.isPending()) {
            chat.send("selltest blocked: autobuy action is active.");
            return;
        }

        applyConfigToRuntime();
        lastSellerResult = sellerController.previewNextSell();

        chat.send("seller preview: " + lastSellerResult.compact());

        if (lastSellerResult.hasFoundItem()) {
            chat.send("seller found: slot=" + lastSellerResult.getItem().getInventorySlot()
                    + ", item=" + lastSellerResult.getItem().getDisplayName()
                    + ", count=" + lastSellerResult.getItem().getCount()
                    + ", target=" + lastSellerResult.getTarget().getLabel()
                    + ", buyMaxUnitPrice=" + moneyFormat.format(lastSellerResult.getTarget().getMaxUnitPrice())
                    + ", sellUnitPrice(GUI)=" + moneyFormat.format(lastSellerResult.getUnitPrice())
                    + ", totalPrice=" + moneyFormat.format(lastSellerResult.getTotalPrice()));
            chat.send("seller hand dry-run: " + lastSellerResult.getHandPlan()
                    + ", selectedHotbarSlot=" + lastSellerResult.getSelectedHotbarSlot()
                    + ", itemInHotbar=" + lastSellerResult.isItemInHotbar()
                    + ", selectedHandMatches=" + lastSellerResult.isSelectedHandMatches());
            chat.send("seller command preview: " + lastSellerResult.getCommand());
        }
    }

    private void runManualScan() {
        if (!auctionView.isAuctionOpen()) {
            chat.send("auction is not open. Open /ah first. For GUI tests use RShift+S while auction is open.");
            return;
        }

        List<AuctionSlot> slots = auctionView.readAuctionSlots();
        int fingerprint = AuctionFingerprint.compute(slots);
        ScanResult result = scanner.scan(slots);

        lastObserverAuctionOpen = true;
        lastObserverFingerprint = currentObserverFingerprint;
        currentObserverFingerprint = fingerprint;
        lastObserverScanResult = result;

        chat.send("manual scan: slots=" + slots.size()
                + ", fingerprint=" + fingerprint
                + ", status=" + result.getStatus()
                + ", checked=" + result.getCheckedSlots());

        ScanCandidate best = result.getBestCandidate();
        if (best == null) {
            chat.send("best: none");
            return;
        }

        chat.send("best: slot=" + best.getAuctionSlot().getAuctionIndex()
                + ", containerSlot=" + best.getAuctionSlot().getContainerSlotId());
        chat.send("item: " + best.getAuctionSlot().getDisplayName());
        chat.send("target: " + best.getTarget().getLabel()
                + ", total=" + formatMoney(best.getPrice().getTotalPrice())
                + ", unit=" + formatMoney(best.getPrice().getUnitPrice()));
    }

    private void runBuyDryRun() {
        if (!auctionView.isAuctionOpen()) {
            chat.send("buy dry-run blocked: auction is not open. Open /ah and press RShift+B while GUI is open.");
            return;
        }

        BuyDryRunResult result = buyDryRun.dryRun();
        lastBuyDryRunResult = result;

        chat.send("buy dry-run: " + result.compact());

        ScanCandidate candidate = result.getCandidate();
        if (candidate == null) {
            chat.send("READY_TO_BUY=false, reason=" + result.getMessage());
            return;
        }

        chat.send("READY_TO_BUY=" + result.isReady()
                + ", slot=" + candidate.getAuctionSlot().getAuctionIndex()
                + ", containerSlot=" + candidate.getAuctionSlot().getContainerSlotId()
                + ", item=" + candidate.getAuctionSlot().getDisplayName());

        chat.send("target=" + candidate.getTarget().getLabel()
                + ", unit=" + formatMoney(candidate.getPrice().getUnitPrice())
                + ", total=" + formatMoney(candidate.getPrice().getTotalPrice())
                + ", REAL_CLICK=false");
    }

    private void runFingerprintCheck() {
        boolean open = auctionView.isAuctionOpen();

        if (!open) {
            chat.send("auctionOpen=false, screen=" + currentScreenInfo() + ", slots=0, fingerprint=0");
            return;
        }

        List<AuctionSlot> slots = auctionView.readAuctionSlots();
        int fingerprint = AuctionFingerprint.compute(slots);

        lastObserverAuctionOpen = true;
        lastObserverFingerprint = currentObserverFingerprint;
        currentObserverFingerprint = fingerprint;

        chat.send("auctionOpen=true, screen=" + currentScreenInfo()
                + ", slots=" + slots.size()
                + ", fingerprint=" + fingerprint
                + ", changed=" + (fingerprint != lastObserverFingerprint));
    }

    private String buildDebugBlock() {
        ScanCandidate best = lastObserverScanResult == null ? null : lastObserverScanResult.getBestCandidate();

        StringBuilder builder = new StringBuilder();
        builder.append("observerEnabled=").append(observerEnabled).append('\n');
        builder.append("observerAuctionOpen=").append(lastObserverAuctionOpen).append('\n');
        builder.append("screen=").append(currentScreenInfo()).append('\n');
        builder.append("observerLastFp=").append(lastObserverFingerprint).append('\n');
        builder.append("observerCurrentFp=").append(currentObserverFingerprint).append('\n');
        builder.append("observerFpChanged=").append(currentObserverFingerprint != lastObserverFingerprint).append('\n');
        builder.append("observerScan=").append(lastObserverScanResult == null ? "null" : lastObserverScanResult.getStatus()).append('\n');
        builder.append("observerChecked=").append(lastObserverScanResult == null ? 0 : lastObserverScanResult.getCheckedSlots()).append('\n');
        builder.append("observerBest=").append(best == null ? "none" : formatCandidateCompact(best)).append('\n');
        RefreshCycleResult refresh = refreshCycle.getLastResult();
        builder.append("refreshPending=").append(refreshCycle.isPending()).append('\n');
        builder.append("refreshStatus=").append(refresh == null ? "null" : refresh.getStatus()).append('\n');
        builder.append("refreshElapsedMs=").append(refresh == null ? 0 : refresh.getElapsedMs()).append('\n');
        builder.append("refreshBeforeFp=").append(refresh == null ? 0 : refresh.getBeforeFingerprint()).append('\n');
        builder.append("refreshAfterFp=").append(refresh == null ? 0 : refresh.getAfterFingerprint()).append('\n');
        builder.append("refreshChanged=").append(refresh != null && refresh.isChanged()).append('\n');
        builder.append("refreshChecked=").append(refresh == null ? 0 : refresh.getCheckedSlots()).append('\n');
        builder.append("refreshBest=").append(refresh == null || refresh.getBestCandidate() == null ? "none" : formatCandidateCompact(refresh.getBestCandidate())).append('\n');
        BuyDryRunResult buy = lastBuyDryRunResult;
        builder.append("buyDryRunStatus=").append(buy == null ? "null" : buy.getStatus()).append('\n');
        builder.append("buyDryRunReady=").append(buy != null && buy.isReady()).append('\n');
        builder.append("buyDryRunBeforeFp=").append(buy == null ? 0 : buy.getBeforeFingerprint()).append('\n');
        builder.append("buyDryRunAfterFp=").append(buy == null ? 0 : buy.getAfterFingerprint()).append('\n');
        builder.append("buyDryRunChanged=").append(buy != null && buy.isChanged()).append('\n');
        builder.append("buyDryRunCandidate=").append(buy == null || buy.getCandidate() == null ? "none" : formatCandidateCompact(buy.getCandidate())).append('\n');
        ControlledBuyClickResult click = lastControlledBuyClickResult;
        builder.append("buyClickPending=").append(controlledBuyClick.isPending()).append('\n');
        builder.append("buyClickStatus=").append(click == null ? "null" : click.getStatus()).append('\n');
        builder.append("buyClickElapsedMs=").append(click == null ? 0 : click.getElapsedMs()).append('\n');
        builder.append("buyClickBeforeFp=").append(click == null ? 0 : click.getBeforeFingerprint()).append('\n');
        builder.append("buyClickAfterFp=").append(click == null ? 0 : click.getAfterFingerprint()).append('\n');
        builder.append("buyClickChanged=").append(click != null && click.isChanged()).append('\n');
        builder.append("buyClickCandidate=").append(click == null || click.getCandidate() == null ? "none" : formatCandidateCompact(click.getCandidate())).append('\n');
        builder.append("lastBuyResultType=").append(lastBuyResult == null ? "null" : lastBuyResult.getType()).append('\n');
        builder.append("lastBuyResultReason=").append(lastBuyResult == null ? "null" : lastBuyResult.getReason()).append('\n');
        builder.append("lastBuyResultMessage=").append(lastBuyResult == null ? "null" : lastBuyResult.getRawMessage()).append('\n');
        builder.append("lastBuyResultSource=").append(lastBuyResultSource).append('\n');
        builder.append("lastBuy=").append(buildLastBuyCompact()).append('\n');
        builder.append("lastServerBuyMessageDuplicate=").append(lastServerBuyMessageDuplicate).append('\n');
        builder.append("lastServerBuyMessageAt=").append(lastServerBuyMessageAt).append('\n');
        builder.append("dedupDuplicateCount=").append(chatResultDeduplicator.getDuplicateCount()).append('\n');
        builder.append("dedupWindowMs=").append(chatResultDeduplicator.getWindowMs()).append('\n');
        builder.append("configPath=").append(configManager.getConfigPath()).append('\n');
        builder.append("configSummary=").append(config.compact()).append('\n');
        builder.append("safetySummary=").append(config.safetySummary()).append('\n');
        builder.append("scannerSettings=").append(scanner.getSettings().compact()).append('\n');
        builder.append("blacklistKeywords=").append(config.getBlacklistKeywords()).append('\n');
        builder.append("refreshTimeoutMs=").append(refreshCycle.getTimeoutMs()).append('\n');
        builder.append("limitedLoopRefreshFails=").append(limitedLoop.getRefreshFailStreak()).append('/').append(limitedLoop.getMaxRefreshFailStreak()).append('\n');
        builder.append("limitedLoopNoChangeStreak=").append(limitedLoop.getNoChangeRefreshStreak()).append('\n');
        builder.append("limitedLoopSmartReopenReason=").append(limitedLoop.getLastSmartReopenReason()).append('\n');
        builder.append("limitedLoopSmartReopenAgoMs=").append(limitedLoop.getLastSmartReopenAtMs() <= 0L ? -1L : Math.max(0L, System.currentTimeMillis() - limitedLoop.getLastSmartReopenAtMs())).append('\n');
        builder.append("limitedLoopSuccessCooldownMs=").append(limitedLoop.getSuccessCooldownMs()).append('\n');
        builder.append("limitedLoopTimeMode=").append(limitedLoop.isTimeLimitMode()).append('\n');
        builder.append("limitedLoopTimeLeftMs=").append(limitedLoop.getRemainingRuntimeMs()).append('\n');
        builder.append("keybinds=").append(config.keySummary()).append('\n');
        builder.append("scannerTargetCount=").append(scanner.getTargetCount()).append('\n');
        builder.append("manualBuyPending=").append(controlledBuyClick.isPending()).append('\n');
        builder.append("oneCycleBuyPending=").append(oneCycle.isWaitingBuyResult()).append('\n');
        builder.append("limitedLoopBuyPending=").append(limitedLoop.isWaitingBuyResult()).append('\n');
        OneCycleResult cycle = lastOneCycleResult;
        builder.append("oneCyclePending=").append(oneCycle.isPending()).append('\n');
        builder.append("oneCycleStatus=").append(cycle == null ? "null" : cycle.getStatus()).append('\n');
        builder.append("oneCycleElapsedMs=").append(cycle == null ? 0 : cycle.getElapsedMs()).append('\n');
        builder.append("oneCycleHardStop=").append(cycle != null && cycle.isHardStop()).append('\n');
        builder.append("oneCycleNeedsRefreshNext=").append(cycle != null && cycle.needsRefreshNext()).append('\n');
        builder.append("oneCycleCandidate=").append(cycle == null || cycle.getCandidate() == null ? "none" : formatCandidateCompact(cycle.getCandidate())).append('\n');
        AutoLoopResult loop = lastAutoLoopResult;
        builder.append("safeAutoRun=").append(buildSafeAutoRunCompact()).append('\n');
        builder.append("fullAutoTimed=").append(buildFullAutoTimedCompact()).append('\n');
        builder.append("sellOnlyTimed=").append(buildSellOnlyTimedCompact()).append('\n');
        builder.append("spamKickRecovery=").append(buildSpamKickCompact()).append('\n');
        builder.append("shulker=").append(shulkerController.compact()).append('\n');
        builder.append("scripts=").append(scriptManager.status()).append('\n');
        builder.append("legacyScriptPause=").append(legacyScriptPauseActive)
                .append(", reason=").append(legacyScriptPauseReason)
                .append(", changedAgoMs=").append(legacyScriptPauseChangedAtMs <= 0L ? -1L : Math.max(0L, System.currentTimeMillis() - legacyScriptPauseChangedAtMs))
                .append(", resumeWaitingAh=").append(legacyScriptResumeWaitingAuction)
                .append(", resumeAhSent=").append(legacyScriptResumeAhSent)
                .append(", resumeReason=").append(legacyScriptResumeReason)
                .append('\n');
        builder.append("autoAuth=").append(autoAuthLastReason)
                .append(", pending=").append(autoAuthPendingCommand == null || autoAuthPendingCommand.isEmpty() ? "none" : maskAuthCommand(autoAuthPendingCommand))
                .append(", nextInMs=").append(Math.max(0L, autoAuthSendAtMs - System.currentTimeMillis()))
                .append('\n');
        builder.append("sellerLoop=").append(buildSellerLoopCompact()).append('\n');
        builder.append("sellerCycle=").append(buildSellerCycleCompact()).append('\n');
        builder.append("cycleFull=").append(buildCycleFullCompact()).append('\n');
        builder.append("cycleFullLoop=").append(buildCycleFullLoopCompact()).append('\n');
        builder.append("lastSellLimitDetected=").append(lastSellLimitDetected).append('\n');
        builder.append("lastSellLimitReason=").append(lastSellLimitReason).append('\n');
        builder.append("lastSellLimitAtMs=").append(lastSellLimitAtMs).append('\n');
        builder.append("lastSellLimitMessage=").append(lastSellLimitMessage).append('\n');
        builder.append("sellLimitStorageBlockLeftMs=").append(getSellLimitStorageBlockLeftMs()).append('\n');
        builder.append("fullAutoSkipNextPreSellStorage=").append(fullAutoSkipNextPreSellStorage).append('\n');
        builder.append("fullAutoSkipNextPreSellStorageReason=").append(fullAutoSkipNextPreSellStorageReason).append('\n');
        builder.append("sellerReturnToAuction=").append(buildSellerReturnCompact()).append('\n');
        builder.append("sellerMarkupPercent=").append(config.getSellerMarkupPercent()).append('\n');
        builder.append("sellerLast=").append(lastSellerResult.compact()).append('\n');
        builder.append("limitedLoopRunning=").append(limitedLoop.isRunning()).append('\n');
        builder.append("limitedLoopStatus=").append(loop == null ? "null" : loop.getStatus()).append('\n');
        builder.append("limitedLoopCycles=").append(loop == null ? "0/0" : (loop.getCyclesStarted() + "/" + loop.getMaxCycles())).append('\n');
        builder.append("limitedLoopBuys=").append(loop == null ? "0/0" : (loop.getBuysDone() + "/" + loop.getMaxBuys())).append('\n');
        builder.append("limitedLoopHardStop=").append(loop != null && loop.isHardStop()).append('\n');
        builder.append("limitedLoopDelayLeftMs=").append(loop == null ? 0 : loop.getDelayLeftMs()).append('\n');
        builder.append("limitedLoopCandidate=").append(loop == null || loop.getCandidate() == null ? "none" : formatCandidateCompact(loop.getCandidate())).append('\n');
        builder.append("profilerBelow=").append('\n').append(MalfixProfiler.debug());
        builder.append("autoLoopEnabled=").append(runtime.controller().context().enabled).append('\n');
        builder.append("coreState=").append(runtime.controller().context().state).append('\n');
        builder.append("coreReason=").append(runtime.controller().context().reason).append('\n');
        builder.append("coreDebugBelow=").append('\n');
        builder.append(runtime.debug());
        return builder.toString();
    }

    private String currentScreenInfo() {
        if (client == null || client.currentScreen == null) {
            return "none";
        }

        Screen screen = client.currentScreen;
        String className = screen.getClass().getSimpleName();
        String title = "";

        try {
            title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        } catch (Throwable ignored) {
            title = "";
        }

        if (title == null || title.trim().isEmpty()) {
            return className;
        }

        return className + " title='" + title + "'";
    }

    private void logCompactStateSometimes() {
        long now = System.currentTimeMillis();
        if (now - lastCompactLogAt < 1000L) {
            return;
        }

        lastCompactLogAt = now;

        System.out.println("[MAB] state=" + runtime.controller().context().state
                + ", reason=" + runtime.controller().context().reason
                + ", fpChanged=" + runtime.controller().context().lastFingerprintChanged
                + ", scan=" + runtime.controller().context().lastScanResult.getStatus());
    }

    private String formatCandidateCompact(ScanCandidate candidate) {
        if (candidate == null) {
            return "none";
        }

        return "slot=" + candidate.getAuctionSlot().getAuctionIndex()
                + ", item=" + candidate.getAuctionSlot().getDisplayName()
                + ", target=" + candidate.getTarget().getLabel()
                + ", unit=" + formatMoney(candidate.getPrice().getUnitPrice())
                + ", total=" + formatMoney(candidate.getPrice().getTotalPrice());
    }

    private String formatMoney(long value) {
        return moneyFormat.format(value);
    }

    public AutoBuyRuntime coreRuntime() {
        return runtime;
    }
}
