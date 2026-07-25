package dev.munchkin.clipboardreplacer

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dev.munchkin.clipboardreplacer.ui.theme.ClipboardReplacerTheme

class MainActivity : ComponentActivity() {
    private val prefs by lazy {
        AppPrefs.get(this)
    }

    private var monitoring by mutableStateOf(false)
    private var linkKinds by mutableStateOf<Set<LinkKind>>(emptySet())
    private var customXDomain by mutableStateOf("")
    private var lastXRewriteHost by mutableStateOf<String?>(null)

    /** Set only when opened via the ongoing (shade) monitoring notification. */
    private var openedFromOngoingNotification = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enableMonitoring()
        } else {
            monitoring = false
            prefs.edit { putBoolean(AppPrefs.KEY_MONITORING, false) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openedFromOngoingNotification = isFromOngoingNotification(intent)
        monitoring = prefs.getBoolean(AppPrefs.KEY_MONITORING, false)
        customXDomain = prefs.getString(AppPrefs.KEY_CUSTOM_X_DOMAIN, "").orEmpty()
        lastXRewriteHost = prefs.getString(AppPrefs.KEY_LAST_X_REWRITE_HOST, null)

        setContent {
            ClipboardReplacerTheme {
                MainScreen(
                    monitoring = monitoring,
                    linkKinds = linkKinds,
                    customXDomain = customXDomain,
                    onCustomXDomainChange = ::updateCustomXDomain,
                    onToggleMonitoring = { enabled ->
                        if (enabled) {
                            requestMonitorPermissionThenEnable()
                        } else {
                            disableMonitoring()
                        }
                    },
                    onCleanYoutube = {
                        fixClipboardNow(youtubeOnly = true)
                        refreshClipboardState()
                    },
                    onUseFixVx = {
                        fixClipboardNow(xHost = XFixHost.FixVx.host, xOnly = true)
                        refreshClipboardState()
                    },
                    onUseFixUpX = {
                        fixClipboardNow(xHost = XFixHost.FixUpX.host, xOnly = true)
                        refreshClipboardState()
                    },
                    onUseCustomX = { host ->
                        fixClipboardNow(xHost = host, xOnly = true)
                        refreshClipboardState()
                    },
                )
            }
        }

        if (monitoring) {
            requestMonitorPermissionThenEnable()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openedFromOngoingNotification = isFromOngoingNotification(intent)
    }

    override fun onStart() {
        super.onStart()
        // Sync if monitoring was stopped from the notification action.
        monitoring = prefs.getBoolean(AppPrefs.KEY_MONITORING, false)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            refreshClipboardState()
        }
    }

    private fun isFromOngoingNotification(intent: Intent?): Boolean =
        intent?.getBooleanExtra(
            ClipboardMonitorService.EXTRA_FROM_ONGOING_NOTIFICATION,
            false,
        ) == true

    private fun extraXHosts(): Set<String> = AppPrefs.extraXHosts(prefs)

    private fun updateCustomXDomain(value: String) {
        customXDomain = value
        prefs.edit { putString(AppPrefs.KEY_CUSTOM_X_DOMAIN, value) }
        refreshClipboardState()
    }

    private fun rememberXRewriteHost(host: String) {
        lastXRewriteHost = host
        prefs.edit { putString(AppPrefs.KEY_LAST_X_REWRITE_HOST, host) }
    }

    private fun refreshClipboardState() {
        val text = ClipboardHelper.readText(this).orEmpty()
        linkKinds = UrlRewriter.detectKinds(text, extraXHosts())
    }

    private fun requestMonitorPermissionThenEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        enableMonitoring()
    }

    private fun enableMonitoring() {
        ClipboardMonitorService.start(this)
        monitoring = true
        prefs.edit { putBoolean(AppPrefs.KEY_MONITORING, true) }
    }

    private fun disableMonitoring() {
        ClipboardMonitorService.stop(this)
        monitoring = false
        prefs.edit { putBoolean(AppPrefs.KEY_MONITORING, false) }
    }

    private fun fixClipboardNow(
        xHost: String = XFixHost.FixVx.host,
        youtubeOnly: Boolean = false,
        xOnly: Boolean = false,
    ) {
        val original = ClipboardHelper.readText(this)
        if (original.isNullOrBlank()) {
            showToast(R.string.toast_clipboard_empty)
            return
        }

        val extraHosts = extraXHosts()
        if (xOnly) {
            if (LinkKind.X !in UrlRewriter.detectKinds(original, extraHosts)) {
                showToast(R.string.toast_nothing_to_fix)
                return
            }

            val rewritten = UrlRewriter.rewriteTextXOnly(original, xHost, extraHosts)
            if (rewritten == original) {
                showToast(R.string.toast_nothing_to_fix)
                return
            }

            rememberXRewriteHost(xHost)
            ClipboardHelper.writeText(this, rewritten)
            onFixedUrlCopied()
            showToast(R.string.toast_fixed)
            return
        }

        val rewritten = when {
            youtubeOnly -> UrlRewriter.rewriteTextYoutubeOnly(original)
            else -> UrlRewriter.rewriteText(original, xHost, extraHosts)
        }

        if (rewritten == original) {
            showToast(R.string.toast_nothing_to_fix)
            return
        }

        ClipboardHelper.writeText(this, rewritten)
        onFixedUrlCopied()
        showToast(R.string.toast_fixed)
    }

    /**
     * After a successful fix from an ongoing-notification open, send this task behind
     * so the user lands back in the app they came from.
     */
    private fun onFixedUrlCopied() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(ClipboardMonitorService.PROMPT_NOTIFICATION_ID)

        if (!openedFromOngoingNotification) return
        openedFromOngoingNotification = false
        moveTaskToBack(true)
    }

    private fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    monitoring: Boolean,
    linkKinds: Set<LinkKind>,
    customXDomain: String,
    onCustomXDomainChange: (String) -> Unit,
    onToggleMonitoring: (Boolean) -> Unit,
    onCleanYoutube: () -> Unit,
    onUseFixVx: () -> Unit,
    onUseFixUpX: () -> Unit,
    onUseCustomX: (String) -> Unit,
) {
    val customXHost = UrlRewriter.normalizeCustomXHost(customXDomain)
    val customXValid = customXHost != null
    val customXShowError = customXDomain.isNotBlank() && !customXValid

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.monitor_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.monitor_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = monitoring,
                    onCheckedChange = onToggleMonitoring,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            Text(
                text = stringResource(R.string.copy_section_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            val hasX = LinkKind.X in linkKinds
            val hasYoutube = LinkKind.YOUTUBE in linkKinds
            val hasLinks = hasX || hasYoutube

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!hasLinks) {
                        Text(
                            text = stringResource(R.string.copy_section_waiting),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.copy_section_hint_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (hasX) {
                        DetectionHint(
                            iconRes = R.drawable.ic_x,
                            circleColor = Color.Black,
                            text = stringResource(R.string.copy_section_hint_x),
                        )
                    }
                    if (hasYoutube) {
                        DetectionHint(
                            iconRes = R.drawable.ic_youtube,
                            circleColor = YoutubeBrandRed,
                            text = stringResource(R.string.copy_section_hint_youtube),
                        )
                    }

                    if (hasYoutube) {
                        Button(
                            onClick = onCleanYoutube,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_clean_youtube))
                        }
                        YoutubeCleanupBullets()
                    }

                    if (hasX) {
                        Button(
                            onClick = onUseFixVx,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_use_fixvx))
                        }
                        Button(
                            onClick = onUseFixUpX,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_use_fixupx))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = stringResource(R.string.custom_x_domain_label),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        OutlinedTextField(
                            value = customXDomain,
                            onValueChange = onCustomXDomainChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = customXShowError,
                            placeholder = {
                                Text(stringResource(R.string.custom_x_domain_placeholder))
                            },
                            supportingText = if (customXShowError) {
                                {
                                    Text(stringResource(R.string.custom_x_domain_invalid))
                                }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done,
                            ),
                        )
                        Button(
                            onClick = {
                                customXHost?.let(onUseCustomX)
                            },
                            enabled = customXValid,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_use_custom_x))
                        }
                    }
                }
            }
        }
    }
}

private val YoutubeBrandRed = Color(0xFFFF0000)

@Composable
private fun DetectionHint(
    iconRes: Int,
    circleColor: Color,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(DetectionIconCircleSize)
                .background(circleColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(DetectionIconGlyphSize),
                tint = Color.White,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val DetectionIconCircleSize = 28.dp
private val DetectionIconGlyphSize = 14.dp

@Composable
private fun YoutubeCleanupBullets() {
    val bulletStyle = MaterialTheme.typography.bodyMedium
    val bulletColor = MaterialTheme.colorScheme.onSurfaceVariant
    val siLabel = stringResource(R.string.youtube_bullet_si)
    val siCode = stringResource(R.string.youtube_bullet_si_code)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = buildAnnotatedString {
                append("• $siLabel (")
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(siCode)
                }
                append(")")
            },
            style = bulletStyle,
            color = bulletColor,
        )
        Text(
            text = "• ${stringResource(R.string.youtube_bullet_timestamp)}",
            style = bulletStyle,
            color = bulletColor,
        )
        Text(
            text = buildAnnotatedString {
                append("• ${stringResource(R.string.youtube_bullet_short_domain)} ")
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                    append(stringResource(R.string.youtube_bullet_short_domain_code))
                }
                append(" ${stringResource(R.string.youtube_bullet_short_domain_suffix)}")
            },
            style = bulletStyle,
            color = bulletColor,
        )
    }
}
