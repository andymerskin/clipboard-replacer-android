package dev.munchkin.clipboardreplacer

import android.app.NotificationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Brief focused activity used to legally read/write the clipboard on Android 10+.
 * Opened from the clipboard-change prompt notification for one-tap rewrite.
 */
class ClipboardFixActivity : ComponentActivity() {
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No UI — we wait for window focus, then finish immediately.
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || handled) return
        handled = true
        fixClipboard()
        finish()
    }

    private fun fixClipboard() {
        val original = ClipboardHelper.readText(this)
        if (original.isNullOrBlank()) {
            Toast.makeText(this, R.string.toast_clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }

        if (ClipboardHelper.isSelfWrite(original)) {
            return
        }

        val prefs = AppPrefs.get(this)
        val rewritten = UrlRewriter.rewriteText(
            original,
            AppPrefs.preferredXHost(prefs),
            AppPrefs.extraXHosts(prefs),
        )
        if (rewritten == original) {
            Toast.makeText(this, R.string.toast_nothing_to_fix, Toast.LENGTH_SHORT).show()
            return
        }

        ClipboardHelper.writeText(this, rewritten)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(ClipboardMonitorService.PROMPT_NOTIFICATION_ID)
        Toast.makeText(this, R.string.toast_fixed, Toast.LENGTH_SHORT).show()
    }
}
