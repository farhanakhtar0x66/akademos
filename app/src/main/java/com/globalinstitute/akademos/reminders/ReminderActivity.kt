package com.globalinstitute.akademos.reminders

import android.app.KeyguardManager
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.globalinstitute.akademos.MainActivity
import com.globalinstitute.akademos.data.Prefs
import com.globalinstitute.akademos.ui.components.Gap
import com.globalinstitute.akademos.ui.components.PrimaryButton
import com.globalinstitute.akademos.ui.theme.AkademosTheme
import com.globalinstitute.akademos.ui.theme.parseHex

/** The full-screen alert shown over the lock screen when a class starts / ends. */
class ReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val kind = intent.getStringExtra("kind") ?: ReminderKind.START
        val heading = intent.getStringExtra("heading") ?: "Class reminder"
        val body = intent.getStringExtra("body") ?: ""
        val names = intent.getStringArrayListExtra("names").orEmpty()
        val color = parseHex(intent.getStringExtra("color"))
        val notifId = intent.getIntExtra("notifId", 0)

        setContent {
            val theme by Prefs.theme.collectAsState()
            val scale by Prefs.textScale.collectAsState()
            AkademosTheme(theme, scale) {
                val pulse = rememberInfiniteTransition(label = "pulse")
                val ring by pulse.animateFloat(1f, 1.18f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ring")
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
                Box(
                    Modifier.fillMaxSize().background(
                        com.globalinstitute.akademos.ui.theme.Ak.Marigold
                    )
                ) {
                    Column(
                        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.size(150.dp).scale(ring).clip(CircleShape).background(com.globalinstitute.akademos.ui.theme.Ak.Jamun.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(112.dp).clip(CircleShape).background(com.globalinstitute.akademos.ui.theme.Ak.Jamun), contentAlignment = Alignment.Center) {
                                Icon(when (kind) { ReminderKind.END -> Icons.Rounded.Logout; ReminderKind.FOLLOW_UP -> Icons.Rounded.PersonSearch; else -> Icons.Rounded.Alarm },
                                    null, Modifier.size(64.dp), tint = com.globalinstitute.akademos.ui.theme.Ak.Marigold)
                            }
                        }
                        Gap(28.dp)
                        AnimatedVisibility(shown, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(heading, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, color = com.globalinstitute.akademos.ui.theme.Ak.OnMarigold)
                                Gap(10.dp)
                                Text(body, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = com.globalinstitute.akademos.ui.theme.Ak.OnMarigold)
                                if (names.isNotEmpty()) {
                                    Gap(14.dp)
                                    Text(names.joinToString("\n"), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                                        color = com.globalinstitute.akademos.ui.theme.Ak.OnMarigold.copy(alpha = 0.8f))
                                }
                                Gap(18.dp)
                            }
                        }
                        Spacer(Modifier.height(36.dp))
                        PrimaryButton(
                            when (kind) { ReminderKind.END -> "Mark who is leaving"; ReminderKind.FOLLOW_UP -> "Mark late arrivals"; else -> "Take attendance now" },
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { open(kind, notifId) },
                            color = com.globalinstitute.akademos.ui.theme.Ak.Jamun, contentColor = Color.White,
                        )
                        Gap(10.dp)
                        TextButton(onClick = {
                            ReminderScheduler.snooze(this@ReminderActivity, intent); close(notifId)
                        }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Remind me in 5 minutes", style = MaterialTheme.typography.titleMedium, color = com.globalinstitute.akademos.ui.theme.Ak.Jamun) }
                        TextButton(onClick = { close(notifId) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text("Dismiss", style = MaterialTheme.typography.titleMedium, color = com.globalinstitute.akademos.ui.theme.Ak.OnMarigold.copy(alpha = 0.8f))
                        }
                    }
                }
                }
            }
        }
    }

    private fun open(kind: String, notifId: Int) {
        val batch = intent.getStringExtra("batchId") ?: return close(notifId)
        val date = intent.getStringExtra("date") ?: return close(notifId)
        val go = { startActivity(MainActivity.openAttendanceIntent(this, batch, date, kind)); close(notifId) }
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked && Build.VERSION.SDK_INT >= 26) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { go() }
            })
        } else go()
    }

    private fun close(notifId: Int) {
        getSystemService(NotificationManager::class.java).cancel(notifId)
        finish()
    }
}
