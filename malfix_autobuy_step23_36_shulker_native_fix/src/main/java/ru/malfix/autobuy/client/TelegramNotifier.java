package ru.malfix.autobuy.client;

import ru.malfix.autobuy.config.MalfixRuntimeSettings;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight Never-style Telegram sender/poller.
 * Step 23.29: chatId is stored as String, so negative Telegram ids (-100...)
 * and @channel usernames work. Status now explains exactly why test/send failed.
 */
public final class TelegramNotifier {
    public interface CommandHandler {
        String handle(String text);
    }

    private final HttpClient httpClient;
    private final Queue<String> sendQueue = new ConcurrentLinkedQueue<String>();
    private final ScheduledExecutorService executor;
    private volatile boolean enabled;
    private volatile boolean pollingEnabled;
    private volatile String token = "";
    private volatile String chatId = "";
    private volatile long updateOffset = 0L;
    private volatile String lastStatus = "not_started";
    private volatile CommandHandler commandHandler;

    public TelegramNotifier() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10L))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.executor = Executors.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "MAB-Telegram");
                thread.setDaemon(true);
                return thread;
            }
        });
        this.executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                flushOne();
            }
        }, 1L, 3L, TimeUnit.SECONDS);
        this.executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                pollOnce();
            }
        }, 3L, 5L, TimeUnit.SECONDS);
    }

    public void setCommandHandler(CommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    public void reload(MalfixRuntimeSettings settings) {
        if (settings == null) {
            enabled = false;
            pollingEnabled = false;
            token = "";
            chatId = "";
            lastStatus = "settings_null";
            return;
        }
        enabled = settings.isTelegramEnabled();
        pollingEnabled = settings.isTelegramPollingEnabled();
        token = normalizeToken(settings.getTelegramToken());
        chatId = normalizeChatId(settings.getTelegramChatId());
        lastStatus = "enabled=" + enabled
                + ", polling=" + pollingEnabled
                + ", token=" + (!token.isEmpty())
                + ", chatId=" + (chatId.isEmpty() ? "empty" : chatId)
                + diagnosticsSuffix();
    }

    public void send(String text) {
        if (!isConfigured()) {
            lastStatus = "send_skipped" + diagnosticsSuffix();
            return;
        }
        String safe = text == null ? "" : text.trim();
        if (safe.isEmpty()) {
            lastStatus = "send_skipped_empty_text";
            return;
        }
        sendQueue.offer(safe);
        lastStatus = "queued, queue=" + sendQueue.size();
    }

    public boolean test() {
        if (!isConfigured()) {
            lastStatus = "test_not_configured" + diagnosticsSuffix();
            return false;
        }
        send("MalfixAutoBuy Telegram test OK");
        return true;
    }

    public String status() {
        return lastStatus + ", queue=" + sendQueue.size() + ", offset=" + updateOffset;
    }

    public boolean isConfigured() {
        return enabled && !token.isEmpty() && !chatId.isEmpty();
    }

    private String diagnosticsSuffix() {
        return " [enabled=" + enabled
                + ", token=" + (!token.isEmpty())
                + ", chatId=" + (chatId.isEmpty() ? "empty" : chatId)
                + "]";
    }

    private void flushOne() {
        String text = sendQueue.poll();
        if (text == null) {
            return;
        }
        if (!isConfigured()) {
            lastStatus = "send_drop_not_configured" + diagnosticsSuffix();
            return;
        }
        try {
            String body = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                    + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                    + "&disable_web_page_preview=true";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                    .timeout(Duration.ofSeconds(15L))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            lastStatus = "send_http=" + response.statusCode() + ", body=" + shortBody(response.body());
        } catch (Throwable throwable) {
            lastStatus = "send_error=" + throwable.getClass().getSimpleName() + ":" + throwable.getMessage();
        }
    }

    private void pollOnce() {
        if (!isConfigured() || !pollingEnabled) {
            return;
        }
        try {
            String url = "https://api.telegram.org/bot" + token + "/getUpdates?timeout=0&offset=" + updateOffset;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15L))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            updateOffset = Math.max(updateOffset, extractNextOffset(body));
            String text = extractLastMessageText(body);
            if (text != null && !text.isEmpty()) {
                handleTelegramCommand(text);
            }
            lastStatus = "poll_http=" + response.statusCode() + ", body=" + shortBody(body);
        } catch (Throwable throwable) {
            lastStatus = "poll_error=" + throwable.getClass().getSimpleName() + ":" + throwable.getMessage();
        }
    }

    private void handleTelegramCommand(String text) {
        String safe = text == null ? "" : text.trim();
        if (safe.isEmpty()) {
            return;
        }

        CommandHandler handler = commandHandler;
        if (handler != null) {
            try {
                String reply = handler.handle(safe);
                if (reply != null && !reply.trim().isEmpty()) {
                    send(reply);
                    return;
                }
            } catch (Throwable throwable) {
                send("Malfix Telegram command error: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
                return;
            }
        }

        String lower = safe.toLowerCase(java.util.Locale.ROOT);
        if ("/status".equals(lower) || "status".equals(lower)) {
            send("MalfixAutoBuy online. " + status());
        } else if ("/ping".equals(lower) || "ping".equals(lower)) {
            send("pong");
        } else if ("/help".equals(lower) || "help".equals(lower) || "помощь".equals(lower)) {
            send("Commands: /status, /balance, /ping");
        }
    }

    private long extractNextOffset(String json) {
        long best = updateOffset;
        int from = 0;
        while (json != null) {
            int pos = json.indexOf("\"update_id\"", from);
            if (pos < 0) break;
            int colon = json.indexOf(':', pos);
            if (colon < 0) break;
            int i = colon + 1;
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            int start = i;
            while (i < json.length() && Character.isDigit(json.charAt(i))) i++;
            try {
                long id = Long.parseLong(json.substring(start, i));
                best = Math.max(best, id + 1L);
            } catch (Throwable ignored) {
            }
            from = Math.max(i, pos + 1);
        }
        return best;
    }

    private String extractLastMessageText(String json) {
        if (json == null || json.isEmpty()) {
            return "";
        }
        int pos = json.lastIndexOf("\"text\"");
        if (pos < 0) {
            return "";
        }
        int colon = json.indexOf(':', pos);
        if (colon < 0) {
            return "";
        }
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean esc = false;
        for (int i = q1 + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (esc) {
                if (c == 'n') out.append('\n');
                else if (c == 'r') out.append('\r');
                else if (c == 't') out.append('\t');
                else out.append(c);
                esc = false;
                continue;
            }
            if (c == '\\') {
                esc = true;
                continue;
            }
            if (c == '"') {
                break;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String normalizeToken(String value) {
        String s = safe(value);
        if (s.startsWith("bot")) {
            s = s.substring(3).trim();
        }
        return s;
    }

    private static String normalizeChatId(String value) {
        String s = safe(value);
        if (s.equals("0")) return "";
        return s;
    }

    private static String shortBody(String body) {
        String s = body == null ? "" : body.replace('\n', ' ').replace('\r', ' ').trim();
        if (s.length() > 160) {
            s = s.substring(0, 160) + "...";
        }
        return s;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
