package dev.andymerskin.clipboardreplacer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UrlRewriterTest {

    @Test
    fun rewritesXToFixvxAndStripsParams() {
        assertEquals(
            "https://fixvx.com/user/status/123",
            UrlRewriter.rewriteUrl(
                "https://x.com/user/status/123?s=20&t=abc",
                XFixHost.FixVx,
            ),
        )
    }

    @Test
    fun rewritesXToFixupxAndStripsParams() {
        assertEquals(
            "https://fixupx.com/user/status/123",
            UrlRewriter.rewriteUrl(
                "https://x.com/user/status/123?s=20",
                XFixHost.FixUpX,
            ),
        )
    }

    @Test
    fun rewritesTwitterToFixvx() {
        assertEquals(
            "https://fixvx.com/user/status/123",
            UrlRewriter.rewriteUrl("https://twitter.com/user/status/123"),
        )
    }

    @Test
    fun convertsFixvxToFixupx() {
        assertEquals(
            "https://fixupx.com/user/status/123",
            UrlRewriter.rewriteUrl(
                "https://fixvx.com/user/status/123?utm=1",
                XFixHost.FixUpX,
            ),
        )
    }

    @Test
    fun leavesMatchingFixHostWithoutParamsAlone() {
        assertNull(
            UrlRewriter.rewriteUrl("https://fixvx.com/user/status/123", XFixHost.FixVx),
        )
    }

    @Test
    fun cleansYoutubeWatchSiParam() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            UrlRewriter.rewriteUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=abc123"),
        )
    }

    @Test
    fun preservesYoutubeTimestamp() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ?t=90",
            UrlRewriter.rewriteUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=90&si=xyz"),
        )
    }

    @Test
    fun preservesYoutubeStartAsT() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ?t=42",
            UrlRewriter.rewriteUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&start=42"),
        )
    }

    @Test
    fun cleansYoutuBeSiParam() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ?t=1m30s",
            UrlRewriter.rewriteUrl("https://youtu.be/dQw4w9WgXcQ?t=1m30s&si=tracking"),
        )
    }

    @Test
    fun rewritesShorts() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            UrlRewriter.rewriteUrl("https://www.youtube.com/shorts/dQw4w9WgXcQ?si=abc"),
        )
    }

    @Test
    fun detectKindsFindsXAndYoutube() {
        val kinds = UrlRewriter.detectKinds(
            "https://x.com/a/status/1 and https://youtu.be/dQw4w9WgXcQ?si=z",
        )
        assertTrue(LinkKind.X in kinds)
        assertTrue(LinkKind.YOUTUBE in kinds)
    }

    @Test
    fun detectKindsFindsCustomXHost() {
        val kinds = UrlRewriter.detectKinds(
            "https://cunnyx.com/user/status/123",
            extraXHosts = listOf("cunnyx.com"),
        )
        assertTrue(LinkKind.X in kinds)
    }

    @Test
    fun rewritesCustomXHostToFixvx() {
        assertEquals(
            "https://fixvx.com/user/status/123",
            UrlRewriter.rewriteUrl(
                "https://cunnyx.com/user/status/123",
                XFixHost.FixVx,
                extraXHosts = listOf("cunnyx.com"),
            ),
        )
    }

    @Test
    fun switchesBetweenFixHosts() {
        assertEquals(
            "https://fixupx.com/user/status/123",
            UrlRewriter.rewriteTextXOnly(
                "https://fixvx.com/user/status/123",
                XFixHost.FixUpX,
            ),
        )
        assertEquals(
            "https://cunnyx.com/user/status/123",
            UrlRewriter.rewriteTextXOnly(
                "https://fixupx.com/user/status/123",
                "cunnyx.com",
            ),
        )
        assertEquals(
            "https://fixvx.com/user/status/123",
            UrlRewriter.rewriteTextXOnly(
                "https://cunnyx.com/user/status/123",
                XFixHost.FixVx,
            ),
        )
    }

    @Test
    fun detectKindsFindsStatusPathOnUnknownHost() {
        val kinds = UrlRewriter.detectKinds("https://random-fix.example/user/status/99")
        assertTrue(LinkKind.X in kinds)
    }

    @Test
    fun detectKindsIgnoresNonTweetStatusPaths() {
        val kinds = UrlRewriter.detectKinds("https://example.com/api/v1/status/healthy")
        assertTrue(LinkKind.X !in kinds)
    }

    @Test
    fun rewriteTextXOnlyLeavesYoutube() {
        val input = "https://x.com/a/status/1?s=20 and https://youtu.be/dQw4w9WgXcQ?si=z"
        val expected = "https://fixvx.com/a/status/1 and https://youtu.be/dQw4w9WgXcQ?si=z"
        assertEquals(expected, UrlRewriter.rewriteTextXOnly(input, XFixHost.FixVx))
    }

    @Test
    fun rewritesXToCustomHost() {
        assertEquals(
            "https://cunnyx.com/user/status/123",
            UrlRewriter.rewriteUrl(
                "https://x.com/user/status/123?s=20",
                "cunnyx.com",
            ),
        )
    }

    @Test
    fun acceptsValidCustomHosts() {
        assertTrue(UrlRewriter.isValidCustomXHost("cunnyx.com"))
        assertTrue(UrlRewriter.isValidCustomXHost("fixvx.com"))
        assertTrue(UrlRewriter.isValidCustomXHost("  FixUpX.com  "))
        assertEquals("fixupx.com", UrlRewriter.normalizeCustomXHost("  FixUpX.com  "))
    }

    @Test
    fun rejectsInvalidCustomHosts() {
        assertNull(UrlRewriter.normalizeCustomXHost(""))
        assertNull(UrlRewriter.normalizeCustomXHost("notadomain"))
        assertNull(UrlRewriter.normalizeCustomXHost("https://fixvx.com"))
        assertNull(UrlRewriter.normalizeCustomXHost("fixvx.com/path"))
        assertNull(UrlRewriter.normalizeCustomXHost("fixvx.com:443"))
        assertNull(UrlRewriter.normalizeCustomXHost("fix vx.com"))
    }

    @Test
    fun rewriteTextYoutubeOnlyLeavesX() {
        val input = "https://x.com/a/status/1?s=20 and https://youtu.be/dQw4w9WgXcQ?si=z"
        val expected = "https://x.com/a/status/1?s=20 and https://youtu.be/dQw4w9WgXcQ"
        assertEquals(expected, UrlRewriter.rewriteTextYoutubeOnly(input))
    }

    @Test
    fun ignoresUnrelatedUrls() {
        assertNull(UrlRewriter.rewriteUrl("https://example.com/path"))
    }

    @Test
    fun rewritesYoutubeEmbed() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            UrlRewriter.rewriteUrl("https://www.youtube.com/embed/dQw4w9WgXcQ?si=abc"),
        )
    }

    @Test
    fun rewritesYoutubeLive() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            UrlRewriter.rewriteUrl("https://www.youtube.com/live/dQw4w9WgXcQ?si=abc"),
        )
    }

    @Test
    fun rewritesMobileTwitterHost() {
        assertEquals(
            "https://fixvx.com/user/status/123",
            UrlRewriter.rewriteUrl("https://mobile.twitter.com/user/status/123?s=20"),
        )
    }

    @Test
    fun rewritesMusicYoutubeHost() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            UrlRewriter.rewriteUrl(
                "https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=abc",
            ),
        )
    }

    @Test
    fun rewritesMobileYoutubeHost() {
        assertEquals(
            "https://youtu.be/dQw4w9WgXcQ",
            UrlRewriter.rewriteUrl(
                "https://m.youtube.com/watch?v=dQw4w9WgXcQ&si=abc",
            ),
        )
    }

    @Test
    fun trimsTrailingPunctuationFromMatchedUrls() {
        assertEquals(
            "Check https://fixvx.com/user/status/123",
            UrlRewriter.rewriteText("Check https://x.com/user/status/123?s=20."),
        )
        assertEquals(
            "See https://youtu.be/dQw4w9WgXcQ",
            UrlRewriter.rewriteText(
                "See https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=abc,",
            ),
        )
    }

    @Test
    fun ignoresInvalidYoutubeVideoId() {
        assertNull(
            UrlRewriter.rewriteUrl("https://www.youtube.com/watch?v=tooshort"),
        )
    }

    @Test
    fun blankInputReturnsEarly() {
        assertEquals("", UrlRewriter.rewriteText(""))
        assertEquals("   ", UrlRewriter.rewriteText("   "))
        assertEquals("", UrlRewriter.rewriteTextXOnly("", XFixHost.FixVx))
        assertEquals("", UrlRewriter.rewriteTextYoutubeOnly(""))
        assertTrue(UrlRewriter.detectKinds("").isEmpty())
        assertTrue(UrlRewriter.detectKinds("   ").isEmpty())
    }
}
