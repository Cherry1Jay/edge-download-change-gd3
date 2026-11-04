package io.github.lswlc33.edge_download_change;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Pushes the current settings into the hooked browser process.
 *
 * <p>Why a broadcast: the injected code runs inside Edge, and Android 11+ package visibility
 * prevents that process from resolving this module's ContentProvider or Service at all
 * (an explicit {@code bindService}/{@code ContentResolver.call} is refused before it ever
 * reaches us). The reverse direction is not affected - this app knows the browser's package
 * name, so it can address it directly and let Edge's already registered receiver pick the
 * values up. Edge then keeps a copy in its own data directory, so even a cold start does not
 * need any conversation with this app.</p>
 */
final class SettingsPush {

    private static final String TAG = "EdgeSysDL";

    private SettingsPush() {}

    /** Sends the current settings to the hooked browser; safe to call from the UI thread. */
    static void push(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(BuildInfo.ACTION_SETTINGS);
            // Addressing the target explicitly keeps the broadcast from being filtered by
            // package visibility on the receiving side.
            intent.setPackage(BuildInfo.TARGET_PACKAGE);
            intent.putExtra("enabled", ModulePrefs.isEnabled(context));
            intent.putExtra("downloader", ModulePrefs.downloader(context));
            intent.putExtra("custom", ModulePrefs.customPackage(context));
            context.sendBroadcast(intent);
            Log.i(TAG, "settings pushed: " + ModulePrefs.downloader(context));
        } catch (Throwable t) {
            Log.w(TAG, "settings push failed", t);
        }
    }
}
