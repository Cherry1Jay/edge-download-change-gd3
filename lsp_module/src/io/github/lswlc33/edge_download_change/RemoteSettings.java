package io.github.lswlc33.edge_download_change;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;

/**
 * Injected side channel to the module app.
 *
 * Settings arrive in three independent ways, in order of reliability on Android 11+:
 *
 * <ol>
 *   <li><b>Local cache</b> - the values last pushed by the module app, stored in the browser's
 *       own data directory. No IPC at all, so it always works, including on a cold start.</li>
 *   <li><b>Settings broadcast</b> - the module app pushes on every change (and on every resume
 *       of its UI); received by the receiver registered below.</li>
 *   <li><b>ContentProvider</b> - only reachable if the browser can see this module's package,
 *       which Android 11+ normally forbids for non-system apps; used as a best-effort
 *       refresh, and it is also how log lines and status are reported back.</li>
 * </ol>
 */
final class RemoteSettings {

    private static final long CACHE_MS = 3_000L;
    private static final long FLUSH_DELAY_MS = 1_200L;
    private static final long HEARTBEAT_MS = 60_000L;
    /** First heartbeat goes out soon so the app shows the activation without waiting a minute. */
    private static final long FIRST_HEARTBEAT_MS = 3_000L;
    private static final long STATE_RETRY_MS = 2_000L;
    private static final int MAX_BUFFER_LINES = 400;

    private static final ArrayDeque<String> BUFFER = new ArrayDeque<String>();
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private static volatile boolean sEnabled = true;
    private static volatile String sDownloader = Downloaders.ID_SYSTEM;
    private static volatile String sCustomPackage = "";
    private static volatile long sFetchedAt;
    private static volatile long sLastAsyncRefresh;
    private static volatile boolean sFlushScheduled;
    private static volatile boolean sHeartbeatStarted;
    private static volatile boolean sStateRetryScheduled;
    private static volatile String sPendingStateEvent;
    private static volatile String sPendingStateDetail;
    private static volatile long sProviderBrokenAt;
    private static volatile boolean sCacheLoaded;
    private static volatile boolean sReceiverRegistered;
    /** Retry the provider after this long even if it failed before (app may have been started). */
    private static final long PROVIDER_RETRY_MS = 30_000L;

    private RemoteSettings() {}

    // ------------------------------------------------------------------ settings

    static boolean enabled() {
        refreshIfStale();
        return sEnabled;
    }

    static String downloaderId() {
        refreshIfStale();
        return sDownloader;
    }

    static String customPackage() {
        refreshIfStale();
        return sCustomPackage;
    }

    /** Human readable name of the currently selected download target. */
    static String downloaderLabel() {
        return Downloaders.labelOf(downloaderId(), customPackage());
    }

    private static void refreshIfStale() {
        if (SystemClock.elapsedRealtime() - sFetchedAt < CACHE_MS) return;
        sFetchedAt = SystemClock.elapsedRealtime();
        // 1. Local cache: instant, no IPC, survives a browser restart.
        loadCache();
        registerSettingsReceiver();
        // 2. The framework's remote preferences, which mirror the module app's own
        // SharedPreferences file. Only populated when LSPosed redirects that file, so it is
        // treated as authoritative only when it actually carries a value.
        boolean loaded = false;
        try {
            XposedInterface api = DownloadHooks.api();
            if (api != null) {
                SharedPreferences prefs = api.getRemotePreferences(BuildInfo.PREFS_SETTINGS);
                if (prefs.contains(BuildInfo.KEY_DOWNLOADER)) {
                    sEnabled = prefs.getBoolean(BuildInfo.KEY_ENABLED, sEnabled);
                    String downloader = prefs.getString(BuildInfo.KEY_DOWNLOADER, sDownloader);
                    if (downloader != null && downloader.length() > 0) sDownloader = downloader;
                    String custom = prefs.getString(BuildInfo.KEY_CUSTOM_PACKAGE, sCustomPackage);
                    if (custom != null) sCustomPackage = custom;
                    loaded = true;
                    saveCache();
                }
            }
        } catch (Throwable ignored) {
        }
        // 3. Calibrate through the provider off the main thread; the answer also carries the
        // settings on every log/heartbeat report, so changes converge quickly when reachable.
        if (!loaded || SystemClock.elapsedRealtime() - sLastAsyncRefresh > CACHE_MS) {
            sLastAsyncRefresh = SystemClock.elapsedRealtime();
            Bg.run(new Runnable() {
                @Override
                public void run() {
                    Bundle values = queryProvider();
                    if (values != null) {
                        applySettings(values);
                        sFetchedAt = SystemClock.elapsedRealtime();
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ local cache

    private static void loadCache() {
        if (sCacheLoaded) return;
        Context context = DownloadHooks.application();
        if (context == null) return;
        sCacheLoaded = true;
        try {
            SharedPreferences prefs = context.getSharedPreferences(
                    BuildInfo.PREFS_CACHE, Context.MODE_PRIVATE);
            sEnabled = prefs.getBoolean("enabled", sEnabled);
            String downloader = prefs.getString("downloader", null);
            if (downloader != null && downloader.length() > 0) sDownloader = downloader;
            String custom = prefs.getString("custom", null);
            if (custom != null) sCustomPackage = custom;
        } catch (Throwable ignored) {
        }
    }

    private static void saveCache() {
        Context context = DownloadHooks.application();
        if (context == null) return;
        try {
            context.getSharedPreferences(BuildInfo.PREFS_CACHE, Context.MODE_PRIVATE).edit()
                    .putBoolean("enabled", sEnabled)
                    .putString("downloader", sDownloader)
                    .putString("custom", sCustomPackage)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Listens for the module app's settings push. Registered on Edge's application context
     * (exported, because the sender is another app) and re-registered lazily in case the first
     * refresh happened before a context existed.
     */
    private static void registerSettingsReceiver() {
        if (sReceiverRegistered) return;
        Context context = DownloadHooks.application();
        if (context == null) return;
        sReceiverRegistered = true;
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    Bundle values = new Bundle();
                    values.putBoolean("enabled", intent.getBooleanExtra("enabled", sEnabled));
                    String downloader = intent.getStringExtra("downloader");
                    if (downloader != null) values.putString("downloader", downloader);
                    String custom = intent.getStringExtra("custom");
                    if (custom != null) values.putString("custom", custom);
                    applySettings(values);
                    sFetchedAt = SystemClock.elapsedRealtime();
                    DownloadHooks.log(4, "settings pushed by module app: " + sDownloader);
                }
            };
            IntentFilter filter = new IntentFilter(BuildInfo.ACTION_SETTINGS);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
        } catch (Throwable t) {
            sReceiverRegistered = false;
            DownloadHooks.log(5, "settings receiver registration failed", t);
        }
    }

    private static void applySettings(Bundle values) {
        if (values == null) return;
        sEnabled = values.getBoolean("enabled", sEnabled);
        String downloader = values.getString("downloader");
        if (downloader != null && downloader.length() > 0) sDownloader = downloader;
        String custom = values.getString("custom");
        if (custom != null) sCustomPackage = custom;
        saveCache();
    }

    private static Bundle queryProvider() {
        Context context = DownloadHooks.application();
        if (context == null) return null;
        try {
            return context.getContentResolver().call(
                    Uri.parse("content://" + BuildInfo.AUTHORITY), BuildInfo.METHOD_SETTINGS, null, null);
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ reporting

    /** Reports the injected process state (drives the app's activation indicator). */
    static void reportState(String event, String detail) {
        Context context = DownloadHooks.application();
        if (context == null) {
            // Edge's Application does not exist yet during early startup; keep the report
            // and retry as soon as a context is available instead of losing it.
            sPendingStateEvent = event;
            sPendingStateDetail = detail;
            DownloadHooks.log(4, "state report deferred (no context yet): " + event);
            scheduleStateRetry();
            return;
        }
        sendState(context, event, detail);
    }

    /** Called when Edge's application context first becomes available. */
    static void onContextAvailable() {
        retryPendingState();
        flushNow();
    }

    private static void scheduleStateRetry() {
        if (sStateRetryScheduled) return;
        sStateRetryScheduled = true;
        Bg.postDelayed(new Runnable() {
            @Override
            public void run() {
                sStateRetryScheduled = false;
                retryPendingState();
            }
        }, STATE_RETRY_MS);
    }

    private static void retryPendingState() {
        final String event = sPendingStateEvent;
        if (event == null) return;
        final Context context = DownloadHooks.application();
        if (context == null) {
            scheduleStateRetry();
            return;
        }
        sPendingStateEvent = null;
        final String detail = sPendingStateDetail;
        sPendingStateDetail = null;
        sendState(context, event, detail);
    }

    private static void sendState(final Context context, final String event, final String detail) {
        final String process = DownloadHooks.processName;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                send(context, event, detail, process, frameworkString());
            }
        });
    }

    static void startHeartbeat() {
        if (sHeartbeatStarted) return;
        sHeartbeatStarted = true;
        // First beat soon after startup (so the app shows the activation quickly), then
        // once a minute.
        Bg.postDelayed(new Runnable() {
            @Override
            public void run() {
                reportState("heartbeat", "alive");
                Bg.postDelayed(this, HEARTBEAT_MS);
            }
        }, FIRST_HEARTBEAT_MS);
    }

    private static String frameworkString() {
        try {
            XposedInterface api = DownloadHooks.api();
            if (api != null) {
                return api.getFrameworkName() + " " + api.getFrameworkVersion()
                        + " (API " + api.getApiVersion() + ")";
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** Queues a log line; batched delivery keeps the provider calls cheap. */
    static void log(String message) {
        synchronized (BUFFER) {
            while (BUFFER.size() >= MAX_BUFFER_LINES) BUFFER.pollFirst();
            BUFFER.addLast(TIME.format(new Date()) + "  " + message);
        }
        if (sFlushScheduled) return;
        sFlushScheduled = true;
        Bg.postDelayed(new Runnable() {
            @Override
            public void run() {
                sFlushScheduled = false;
                flushNow();
            }
        }, FLUSH_DELAY_MS);
    }

    /** Flushes buffered log lines immediately (used when a context becomes available). */
    static void flushNow() {
        final String payload = takePending();
        if (payload == null) return;
        Bg.run(new Runnable() {
            @Override
            public void run() {
                deliver(payload);
            }
        });
    }

    private static String takePending() {
        synchronized (BUFFER) {
            if (BUFFER.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            while (!BUFFER.isEmpty()) {
                sb.append(BUFFER.pollFirst()).append('\n');
            }
            return sb.toString();
        }
    }

    private static void deliver(String payload) {
        final Context context = DownloadHooks.application();
        if (context == null) {
            // Very early in Edge's startup there is no context yet; keep the lines.
            requeue(payload);
            return;
        }
        if (isProviderBroken()) {
            requeue(payload);
            return;
        }
        if (!send(context, "log", payload, DownloadHooks.processName, frameworkString())) {
            requeue(payload);
        }
    }

    private static void requeue(String payload) {
        String[] lines = payload.split("\n");
        synchronized (BUFFER) {
            for (int i = Math.max(0, lines.length - MAX_BUFFER_LINES); i < lines.length; i++) {
                if (lines[i].length() > 0) BUFFER.addLast(lines[i]);
            }
        }
    }

    private static boolean isProviderBroken() {
        long brokenAt = sProviderBrokenAt;
        return brokenAt != 0 && SystemClock.elapsedRealtime() - brokenAt < PROVIDER_RETRY_MS;
    }

    private static boolean send(Context context, String event, String text, String process,
            String framework) {
        if (isProviderBroken()) return false;
        try {
            Bundle extras = new Bundle();
            extras.putString("event", event);
            if (text != null) extras.putString("text", text);
            if (process != null) extras.putString("process", process);
            if (framework != null) extras.putString("framework", framework);
            Bundle response = context.getContentResolver().call(
                    Uri.parse("content://" + BuildInfo.AUTHORITY), BuildInfo.METHOD_REPORT, null, extras);
            sProviderBrokenAt = 0;
            // The provider answers with the current settings, keeping this side in sync
            // without an extra round trip.
            applySettings(response);
            sFetchedAt = SystemClock.elapsedRealtime();
            return true;
        } catch (Throwable t) {
            // The module app may be force-stopped; back off instead of retrying every line.
            sProviderBrokenAt = SystemClock.elapsedRealtime();
            return false;
        }
    }
}
