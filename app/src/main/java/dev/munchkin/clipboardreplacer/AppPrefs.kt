package dev.munchkin.clipboardreplacer

import android.content.Context
import android.content.SharedPreferences

object AppPrefs {
    const val NAME = "clipboard_replacer"
    const val KEY_MONITORING = "monitoring_enabled"
    const val KEY_CUSTOM_X_DOMAIN = "custom_x_domain"
    const val KEY_LAST_X_REWRITE_HOST = "last_x_rewrite_host"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun extraXHosts(prefs: SharedPreferences): Set<String> = buildSet {
        UrlRewriter.normalizeCustomXHost(
            prefs.getString(KEY_CUSTOM_X_DOMAIN, "").orEmpty(),
        )?.let(::add)
        prefs.getString(KEY_LAST_X_REWRITE_HOST, null)?.let(::add)
    }

    /** Preferred host for automatic X rewrites: last used, else FixVx. */
    fun preferredXHost(prefs: SharedPreferences): String =
        prefs.getString(KEY_LAST_X_REWRITE_HOST, null) ?: XFixHost.FixVx.host
}
