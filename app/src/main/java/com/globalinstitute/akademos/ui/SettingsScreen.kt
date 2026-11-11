package com.globalinstitute.akademos.ui

import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.globalinstitute.akademos.data.Prefs
import com.globalinstitute.akademos.data.ThemeMode
import com.globalinstitute.akademos.reminders.ReminderScheduler
import com.globalinstitute.akademos.ui.components.AkCard
import com.globalinstitute.akademos.ui.components.Chip
import com.globalinstitute.akademos.ui.components.Gap
import com.globalinstitute.akademos.ui.components.PrimaryButton
import com.globalinstitute.akademos.ui.components.ScreenHeader
import com.globalinstitute.akademos.ui.components.SectionTitle
import com.globalinstitute.akademos.ui.theme.LocalStatus

@Composable
fun SettingsScreen() {
    val s = LocalSession.current
    val ctx = LocalContext.current
    val theme by Prefs.theme.collectAsState()
    val scale by Prefs.textScale.collectAsState()
    val lock by Prefs.appLock.collectAsState()
    var remindAll by remember { mutableStateOf(Prefs.remindAllClasses) }
    var remindOn by remember { mutableStateOf(Prefs.remindersOn) }
    var confirmOut by remember { mutableStateOf(false) }
    var moneyOn by remember { mutableStateOf(Prefs.moneyReminders) }

    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = LocalBarInset.current + 40.dp)) {
        item { Column(Modifier.padding(horizontal = 0.dp)) { ScreenHeader("Settings", "${s.me.fullName}  ·  ${when (s.me.role) { "admin" -> "Administrator"; "teacher" -> "Teacher"; else -> "Student / parent" }}", sidePadding = 0.dp) } }

        item {
            SectionTitle("Look")
            AkCard {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(theme == ThemeMode.SYSTEM, "Automatic", { Prefs.setTheme(ThemeMode.SYSTEM) })
                    Chip(theme == ThemeMode.LIGHT, "Light", { Prefs.setTheme(ThemeMode.LIGHT) })
                    Chip(theme == ThemeMode.DARK, "Dark", { Prefs.setTheme(ThemeMode.DARK) })
                }
                Gap(18.dp)
                Text("Text size", style = MaterialTheme.typography.titleMedium)
                Gap(8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(scale == 1.0f, "Normal", { Prefs.setTextScale(1.0f) })
                    Chip(scale == 1.1f, "Large", { Prefs.setTextScale(1.1f) })
                    Chip(scale == 1.25f, "Extra large", { Prefs.setTextScale(1.25f) })
                }
                Gap(10.dp)
                Text("This is how text will look.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (s.me.isStaff) {
            item {
                SectionTitle("Reminders")
                ReminderSetupCard(showWhenAllGood = true)
                AkCard {
                    ToggleRow("Class reminders", "Full-screen alert when a class starts and ends", remindOn) {
                        remindOn = it; Prefs.remindersOn = it; ReminderScheduler.rearm(ctx)
                    }
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    if (s.me.isAdmin) {
                        Gap(12.dp)
                        ToggleRow("Remind me for every class", "Off = only classes where you are the teacher", remindAll) {
                            remindAll = it; Prefs.remindAllClasses = it
                            s.school?.let { sc -> scope.launch { runCatching { ReminderScheduler.refresh(ctx, s.me, sc) } } }
                        }
                    }
                    Gap(12.dp)
                    var checkIn by remember { mutableStateOf(Prefs.checkIn) }
                    ToggleRow("3 PM class check-in", "Every day at 3 PM: are you teaching today, which class and when", checkIn) {
                        checkIn = it; Prefs.checkIn = it; ReminderScheduler.rearm(ctx)
                    }
                    if (com.globalinstitute.akademos.data.Repo.demo) {
                        Gap(12.dp)
                        com.globalinstitute.akademos.ui.components.SecondaryButton("Preview the 3 PM check-in",
                            { ReminderScheduler.sendCheckInTest(ctx) }, Modifier.fillMaxWidth())
                    }
                    Gap(16.dp)
                    var sent by remember { mutableStateOf(false) }
                    com.globalinstitute.akademos.ui.components.SecondaryButton(
                        if (sent) "Test alert coming in 8 seconds…" else "Send a test alert",
                        { ReminderScheduler.sendTest(ctx); sent = true }, Modifier.fillMaxWidth(),
                        icon = androidx.compose.material.icons.Icons.Rounded.NotificationsActive)
                    Text("Lock the phone after tapping to see how it wakes the screen.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SectionTitle("Security")
                AkCard {
                    ToggleRow("Lock with fingerprint / screen lock", "Asks for your fingerprint or phone PIN before anyone can change marks or attendance", lock) {
                        Prefs.setAppLock(it)
                    }
                    if (!s.activity.deviceLockAvailable()) {
                        Gap(10.dp)
                        Text("This phone has no screen lock set. Please add a PIN or fingerprint in the phone's Settings so students can't use your access.",
                            color = LocalStatus.current.absent, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        if (s.me.isAdmin || s.me.role == com.globalinstitute.akademos.data.Roles.PARENT) {
            item {
                val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
                SectionTitle("Fees")
                AkCard {
                    ToggleRow(
                        if (s.me.isAdmin) "Morning money reminder" else "Fee reminder",
                        if (s.me.isAdmin) "At 9 AM: fees that are overdue" + if (com.globalinstitute.akademos.data.Features.SALARIES) " and salaries due to be paid" else ""
                        else "Every morning from 3 days before it's due until it's paid, and evenings too once it's late",
                        moneyOn,
                    ) { moneyOn = it; Prefs.moneyReminders = it; ReminderScheduler.rearm(ctx) }
                    if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(ctx,
                            android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        Gap(12.dp)
                        com.globalinstitute.akademos.ui.components.SecondaryButton("Allow notifications",
                            { notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }, Modifier.fillMaxWidth())
                    }
                    if (com.globalinstitute.akademos.data.Repo.demo) {
                        Gap(12.dp)
                        com.globalinstitute.akademos.ui.components.SecondaryButton("Preview the 9 AM reminder",
                            { ReminderScheduler.sendMoneyTest(ctx) }, Modifier.fillMaxWidth())
                        if (s.me.isAdmin) {
                            Gap(8.dp)
                            com.globalinstitute.akademos.ui.components.SecondaryButton("Preview an \"I've paid\"",
                                { Prefs.seenClaims = emptySet(); ReminderScheduler.sendClaimsTest(ctx) }, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }

        item {
            Gap(24.dp)
            PrimaryButton("Sign out", { confirmOut = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Logout,
                color = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            Gap(28.dp)
            Gap(10.dp)
            Text("Akademos · Global Institute", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }

    if (confirmOut) com.globalinstitute.akademos.ui.components.AkDialog(onDismissRequest = { confirmOut = false },
        title = { Text("Sign out?") },
        text = { Text("You will need your username and password to sign in again.") },
        confirmButton = { TextButton({ confirmOut = false; s.signOut() }) { Text("Sign out") } },
        dismissButton = { TextButton({ confirmOut = false }) { Text("Cancel") } })
}

@Composable
private fun ToggleRow(title: String, body: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, onChange)
    }
}
