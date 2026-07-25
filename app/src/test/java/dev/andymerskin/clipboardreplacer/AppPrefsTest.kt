package dev.andymerskin.clipboardreplacer

import android.content.Context
import androidx.core.content.edit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppPrefsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        AppPrefs.get(context).edit { clear() }
    }

    @Test
    fun preferredXHostDefaultsToFixVx() {
        val prefs = AppPrefs.get(context)
        assertEquals(XFixHost.FixVx.host, AppPrefs.preferredXHost(prefs))
    }

    @Test
    fun preferredXHostUsesLastRewriteHost() {
        val prefs = AppPrefs.get(context)
        prefs.edit { putString(AppPrefs.KEY_LAST_X_REWRITE_HOST, XFixHost.FixUpX.host) }
        assertEquals(XFixHost.FixUpX.host, AppPrefs.preferredXHost(prefs))
    }

    @Test
    fun extraXHostsEmptyWhenPrefsEmpty() {
        val prefs = AppPrefs.get(context)
        assertTrue(AppPrefs.extraXHosts(prefs).isEmpty())
    }

    @Test
    fun extraXHostsMergesCustomAndLastHost() {
        val prefs = AppPrefs.get(context)
        prefs.edit {
            putString(AppPrefs.KEY_CUSTOM_X_DOMAIN, "cunnyx.com")
            putString(AppPrefs.KEY_LAST_X_REWRITE_HOST, XFixHost.FixUpX.host)
        }
        assertEquals(
            setOf("cunnyx.com", XFixHost.FixUpX.host),
            AppPrefs.extraXHosts(prefs),
        )
    }

    @Test
    fun extraXHostsIgnoresInvalidCustomDomain() {
        val prefs = AppPrefs.get(context)
        prefs.edit {
            putString(AppPrefs.KEY_CUSTOM_X_DOMAIN, "not a domain")
            putString(AppPrefs.KEY_LAST_X_REWRITE_HOST, XFixHost.FixVx.host)
        }
        assertEquals(setOf(XFixHost.FixVx.host), AppPrefs.extraXHosts(prefs))
    }
}
