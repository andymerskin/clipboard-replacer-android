package dev.munchkin.clipboardreplacer

import android.net.Uri
import androidx.core.net.toUri

enum class XFixHost(val host: String) {
    FixVx("fixvx.com"),
    FixUpX("fixupx.com"),
}

enum class LinkKind {
    X,
    YOUTUBE,
}

/**
 * Rewrites clipboard-friendly URLs:
 * - x.com / twitter.com / fixvx / fixupx → chosen fix host, strip query/fragment
 * - YouTube → youtu.be, drop `si`, keep timestamp (`t` / `start`)
 */
object UrlRewriter {
    private val urlRegex = Regex(
        """https?://[^\s<>"')\]]+""",
        RegexOption.IGNORE_CASE,
    )

    private val xHosts = setOf(
        "x.com",
        "www.x.com",
        "twitter.com",
        "www.twitter.com",
        "mobile.twitter.com",
        "m.twitter.com",
        "fixvx.com",
        "www.fixvx.com",
        "fixupx.com",
        "www.fixupx.com",
    )

    private val youtubeHosts = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be",
        "www.youtu.be",
    )

    fun detectKinds(
        text: String,
        extraXHosts: Collection<String> = emptyList(),
    ): Set<LinkKind> {
        if (text.isBlank()) return emptySet()

        val knownXHosts = knownXHosts(extraXHosts)
        val kinds = mutableSetOf<LinkKind>()
        urlRegex.findAll(text).forEach { match ->
            val uri = runCatching { trimUrl(match.value).toUri() }.getOrNull()
                ?: return@forEach
            val host = uri.host?.lowercase() ?: return@forEach
            when {
                isXUri(uri, knownXHosts) -> kinds += LinkKind.X
                host in youtubeHosts -> kinds += LinkKind.YOUTUBE
            }
        }
        return kinds
    }

    private val customHostRegex = Regex(
        """^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$""",
        RegexOption.IGNORE_CASE,
    )

    /** True for a bare hostname like `cunnyx.com` (no scheme, path, port, or spaces). */
    fun isValidCustomXHost(host: String): Boolean =
        normalizeCustomXHost(host) != null

    /** Returns a trimmed lowercase hostname, or null if invalid. */
    fun normalizeCustomXHost(host: String): String? {
        val trimmed = host.trim().lowercase()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains("://") ||
            trimmed.contains('/') ||
            trimmed.contains(':') ||
            trimmed.contains(' ')
        ) {
            return null
        }
        return trimmed.takeIf { customHostRegex.matches(it) }
    }

    fun rewriteText(
        text: String,
        xHost: XFixHost = XFixHost.FixVx,
        extraXHosts: Collection<String> = emptyList(),
    ): String = rewriteText(text, xHost.host, extraXHosts)

    fun rewriteText(
        text: String,
        xHost: String,
        extraXHosts: Collection<String> = emptyList(),
    ): String {
        if (text.isBlank()) return text

        return urlRegex.replace(text) { match ->
            rewriteUrl(match.value, xHost, extraXHosts) ?: match.value
        }
    }

    fun rewriteTextYoutubeOnly(text: String): String {
        if (text.isBlank()) return text

        return urlRegex.replace(text) { match ->
            val uri = runCatching { trimUrl(match.value).toUri() }.getOrNull()
                ?: return@replace match.value
            val host = uri.host?.lowercase() ?: return@replace match.value
            if (host !in youtubeHosts) return@replace match.value
            rewriteYoutube(uri) ?: match.value
        }
    }

    fun rewriteTextXOnly(
        text: String,
        xHost: XFixHost,
        extraXHosts: Collection<String> = emptyList(),
    ): String = rewriteTextXOnly(text, xHost.host, extraXHosts)

    fun rewriteTextXOnly(
        text: String,
        xHost: String,
        extraXHosts: Collection<String> = emptyList(),
    ): String {
        if (text.isBlank()) return text

        val knownXHosts = knownXHosts(extraXHosts)
        return urlRegex.replace(text) { match ->
            val uri = runCatching { trimUrl(match.value).toUri() }.getOrNull()
                ?: return@replace match.value
            if (!isXUri(uri, knownXHosts)) return@replace match.value
            rewriteX(uri, xHost)
        }
    }

    fun rewriteUrl(
        raw: String,
        xHost: XFixHost = XFixHost.FixVx,
        extraXHosts: Collection<String> = emptyList(),
    ): String? = rewriteUrl(raw, xHost.host, extraXHosts)

    fun rewriteUrl(
        raw: String,
        xHost: String,
        extraXHosts: Collection<String> = emptyList(),
    ): String? {
        val trimmed = trimUrl(raw)
        val uri = runCatching { trimmed.toUri() }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val knownXHosts = knownXHosts(extraXHosts)

        return when {
            isXUri(uri, knownXHosts) -> rewriteX(uri, xHost)
            host in youtubeHosts -> rewriteYoutube(uri)
            else -> null
        }?.takeIf { it != trimmed }
    }

    // Tweet-like path: /user/status/123 or /i/status/123 (not bare "/status/" on arbitrary sites).
    private val xStatusPathRegex = Regex("""/[^/]+/status/\d+""")

    /**
     * True when [uri] is a known X/Twitter/fix host, an explicitly allowed extra host,
     * or a host with a tweet-like `/user/status/<id>` path (so already-rewritten custom
     * domains remain swappable without treating every `/status/` URL as X).
     */
    private fun isXUri(uri: Uri, knownXHosts: Set<String>): Boolean {
        val host = uri.host?.lowercase() ?: return false
        if (host in youtubeHosts) return false
        if (host in knownXHosts) return true
        return xStatusPathRegex.containsMatchIn(uri.encodedPath.orEmpty())
    }

    private fun knownXHosts(extraXHosts: Collection<String>): Set<String> {
        if (extraXHosts.isEmpty()) return xHosts

        val hosts = xHosts.toMutableSet()
        for (raw in extraXHosts) {
            val host = normalizeCustomXHost(raw) ?: continue
            hosts += host
            hosts += "www.$host"
        }
        return hosts
    }

    private fun rewriteX(uri: Uri, host: String): String {
        return Uri.Builder()
            .scheme(uri.scheme?.takeIf { it.isNotBlank() } ?: "https")
            .authority(host)
            .encodedPath(uri.encodedPath?.takeIf { it.isNotBlank() } ?: "/")
            .build()
            .toString()
    }

    private fun rewriteYoutube(uri: Uri): String? {
        val videoId = extractYoutubeVideoId(uri) ?: return null
        val timestamp = extractTimestamp(uri)

        val builder = Uri.Builder()
            .scheme("https")
            .authority("youtu.be")
            .appendPath(videoId)

        if (!timestamp.isNullOrBlank()) {
            builder.appendQueryParameter("t", timestamp)
        }

        return builder.build().toString()
    }

    private fun extractYoutubeVideoId(uri: Uri): String? {
        val host = uri.host?.lowercase() ?: return null
        val path = uri.path.orEmpty().trim('/')

        return when {
            "youtu.be" in host -> path.substringBefore('/').takeIf { it.isNotBlank() }
            path.startsWith("watch") -> uri.getQueryParameter("v")
            path.startsWith("shorts/") -> path.removePrefix("shorts/").substringBefore('/')
            path.startsWith("embed/") -> path.removePrefix("embed/").substringBefore('/')
            path.startsWith("live/") -> path.removePrefix("live/").substringBefore('/')
            else -> uri.getQueryParameter("v")
        }?.takeIf { it.matches(Regex("""[\w-]{11}""")) }
    }

    private fun extractTimestamp(uri: Uri): String? {
        val t = uri.getQueryParameter("t")?.takeIf { it.isNotBlank() }
        if (t != null) return normalizeTimestamp(t)

        val start = uri.getQueryParameter("start")?.takeIf { it.isNotBlank() }
        return start?.let { normalizeTimestamp(it) }
    }

    private fun normalizeTimestamp(value: String): String = value.trim()

    private fun trimUrl(raw: String): String =
        raw.trimEnd('.', ',', ';', '!', '?', ')', ']', '"', '\'')
}
