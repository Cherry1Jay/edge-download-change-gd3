package io.github.lswlc33.edge_download_change;

/** Shared constants for the module app and the injected (Edge) side. */
final class BuildInfo {

    /** Module version, shown in the UI and reported by the injected side. */
    static final String VERSION = "2.12-gd3.3";

    /** The app whose download flow is hooked. */
    static final String TARGET_PACKAGE = "com.microsoft.emmx";

    /**
     * Settings push channel (module app -> hooked process). A broadcast is used because it is
     * the only direction that survives Android 11+ package visibility: the hooked browser
     * cannot resolve this module's provider/service at all, while the module app can still
     * address the browser by package name.
     */
    static final String ACTION_SETTINGS = "io.github.lswlc33.edge_download_change.action.SETTINGS";

    /**
     * Edge-side copy of the last pushed settings, stored in the browser's own data directory
     * so the injected code never has to talk to this module app on a cold start.
     */
    static final String PREFS_CACHE = "edge_download_change_cache";

    /** SharedPreferences file used for settings and as remote-preference fallback. */
    static final String PREFS_SETTINGS = "settings";
    static final String KEY_ENABLED = "intercept_enabled";
    static final String KEY_DOWNLOADER = "downloader_id";
    static final String KEY_CUSTOM_PACKAGE = "downloader_custom_package";

    /** ContentProvider channel between the module app and the injected process. */
    static final String AUTHORITY = "io.github.lswlc33.edge_download_change.settings";
    static final String METHOD_SETTINGS = "settings";
    static final String METHOD_REPORT = "report";

    /** Log file name inside the module app's files directory. */
    static final String LOG_FILE = "module.log";
    static final int LOG_MAX_BYTES = 96 * 1024;

    private BuildInfo() {}
}
