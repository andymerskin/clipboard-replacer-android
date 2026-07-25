package dev.andymerskin.clipboardreplacer

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipboardMonitorServiceTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        AppPrefs.get(context).edit { clear() }
    }

    @Test
    fun stopActionClearsMonitoringPrefAndStopsService() {
        AppPrefs.get(context).edit { putBoolean(AppPrefs.KEY_MONITORING, true) }

        val service = Robolectric.buildService(ClipboardMonitorService::class.java)
            .create()
            .get()

        val result = service.onStartCommand(
            Intent(context, ClipboardMonitorService::class.java)
                .setAction(ClipboardMonitorService.ACTION_STOP),
            0,
            1,
        )

        assertFalse(AppPrefs.get(context).getBoolean(AppPrefs.KEY_MONITORING, true))
        assertEquals(android.app.Service.START_NOT_STICKY, result)
    }
}
