package com.globalinstitute.akademos

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.globalinstitute.akademos.data.Prefs
import com.globalinstitute.akademos.ui.AppRoot
import com.globalinstitute.akademos.ui.theme.AkademosTheme

/** A request coming from a reminder: "open attendance for this batch". */
data class OpenRequest(val batchId: String, val date: String, val kind: String)

object AppLock {
    val locked = mutableStateOf(true)
    var backgroundedAt = 0L
    const val GRACE_MS = 60_000L
}

class MainActivity : FragmentActivity() {
    val openRequest = mutableStateOf<OpenRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        preferHighestRefreshRate()
        readIntent(intent)
        setContent {
            val theme by Prefs.theme.collectAsState()
            val scale by Prefs.textScale.collectAsState()
            AkademosTheme(theme, scale) {
                val dark = com.globalinstitute.akademos.ui.theme.LocalIsDark.current
                androidx.compose.runtime.LaunchedEffect(dark) {
                    val t = android.graphics.Color.TRANSPARENT
                    enableEdgeToEdge(
                        if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
                        if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
                    )
                }
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(this@MainActivity)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) AppLock.backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val away = SystemClock.elapsedRealtime() - AppLock.backgroundedAt
        if (AppLock.backgroundedAt != 0L && away > AppLock.GRACE_MS) AppLock.locked.value = true
    }

    private fun readIntent(i: Intent?) {
        val b = i?.getStringExtra(EXTRA_BATCH) ?: return
        openRequest.value = OpenRequest(b, i.getStringExtra(EXTRA_DATE) ?: return, i.getStringExtra(EXTRA_KIND) ?: "start")
        i.removeExtra(EXTRA_BATCH)
    }

    /** Ask the display for its fastest mode (90/120 Hz) so animations stay buttery. */
    private fun preferHighestRefreshRate() {
        val d = if (Build.VERSION.SDK_INT >= 30) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
        d ?: return
        val cur = d.mode
        val best = d.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId }
    }

    fun deviceLockAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    fun unlock(onResult: (Boolean, String?) -> Unit) {
        if (!deviceLockAvailable()) { onResult(true, null); return }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true, null)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false, errString.toString())
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Akademos")
                .setSubtitle("Confirm it's you before changing marks or attendance")
                .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
                .build()
        )
    }

    /** Stops screenshots and screen recording on sensitive screens. */
    fun setSecure(on: Boolean) {
        if (on && !com.globalinstitute.akademos.data.Repo.demo) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    companion object {
        const val EXTRA_BATCH = "open_batch"
        const val EXTRA_DATE = "open_date"
        const val EXTRA_KIND = "open_kind"

        fun openAttendanceIntent(ctx: Context, batchId: String, date: String, kind: String) =
            Intent(ctx, MainActivity::class.java).apply {
                putExtra(EXTRA_BATCH, batchId); putExtra(EXTRA_DATE, date); putExtra(EXTRA_KIND, kind)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
    }
}
