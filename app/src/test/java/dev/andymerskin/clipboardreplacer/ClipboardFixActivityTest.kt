package dev.andymerskin.clipboardreplacer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.edit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipboardFixActivityTest {

    private lateinit var context: Context
    private lateinit var clipboard: ClipboardManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        AppPrefs.get(context).edit { clear() }
    }

    @Test
    fun rewritesXLinkUsingPreferredHostFromPrefs() {
        AppPrefs.get(context).edit {
            putString(AppPrefs.KEY_LAST_X_REWRITE_HOST, XFixHost.FixUpX.host)
        }
        setClipboard("https://x.com/user/status/123?s=20")

        Robolectric.buildActivity(ClipboardFixActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .windowFocusChanged(true)
            .get()

        assertEquals(
            "https://fixupx.com/user/status/123",
            ClipboardHelper.readText(context),
        )
    }

    @Test
    fun emptyClipboardStaysEmpty() {
        clipboard.clearPrimaryClip()

        Robolectric.buildActivity(ClipboardFixActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .windowFocusChanged(true)
            .get()

        assertNull(ClipboardHelper.readText(context))
    }

    @Test
    fun alreadyFixedLinkStaysUnchanged() {
        setClipboard("https://fixvx.com/user/status/123")

        Robolectric.buildActivity(ClipboardFixActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .windowFocusChanged(true)
            .get()

        assertEquals(
            "https://fixvx.com/user/status/123",
            ClipboardHelper.readText(context),
        )
    }

    @Test
    fun selfWriteIsNoOp() {
        val fixed = "https://fixvx.com/user/status/123"
        ClipboardHelper.writeText(context, fixed)
        setClipboard(fixed)

        Robolectric.buildActivity(ClipboardFixActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .windowFocusChanged(true)
            .get()

        assertEquals(fixed, ClipboardHelper.readText(context))
    }

    private fun setClipboard(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("test", text))
    }
}
