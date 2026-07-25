package dev.andymerskin.clipboardreplacer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipboardHelperTest {

    private lateinit var context: Context
    private lateinit var clipboard: ClipboardManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    }

    @Test
    fun writeTextAndReadTextRoundtrip() {
        ClipboardHelper.writeText(context, "https://fixvx.com/user/status/1")
        assertEquals(
            "https://fixvx.com/user/status/1",
            ClipboardHelper.readText(context),
        )
    }

    @Test
    fun isSelfWriteTrueAfterWrite() {
        ClipboardHelper.writeText(context, "fixed")
        assertTrue(ClipboardHelper.isSelfWrite("fixed"))
        assertFalse(ClipboardHelper.isSelfWrite("other"))
    }

    @Test
    fun shouldSuppressPromptImmediatelyAfterWrite() {
        ClipboardHelper.writeText(context, "fixed")
        assertTrue(ClipboardHelper.shouldSuppressPrompt())
    }

    @Test
    fun readTextReturnsNullWhenClipboardCleared() {
        clipboard.clearPrimaryClip()
        assertNull(ClipboardHelper.readText(context))
    }
}
