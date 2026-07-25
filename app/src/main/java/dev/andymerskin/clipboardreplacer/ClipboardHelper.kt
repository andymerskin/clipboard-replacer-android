package dev.andymerskin.clipboardreplacer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardHelper {
    @Volatile
    private var lastWritten: String? = null

    @Volatile
    private var suppressPromptUntilMs: Long = 0

    fun readText(context: Context): String? {
        val clipboard = clipboard(context)
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()
    }

    fun writeText(context: Context, text: String) {
        lastWritten = text
        // Avoid re-prompting ourselves when our own write fires the clip listener.
        suppressPromptUntilMs = System.currentTimeMillis() + 1_500
        clipboard(context).setPrimaryClip(ClipData.newPlainText("fixed link", text))
    }

    fun shouldSuppressPrompt(): Boolean =
        System.currentTimeMillis() < suppressPromptUntilMs

    fun isSelfWrite(text: String): Boolean = text == lastWritten

    private fun clipboard(context: Context): ClipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
}
