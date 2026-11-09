package com.globalinstitute.akademos.reminders

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.globalinstitute.akademos.MainActivity
import com.globalinstitute.akademos.R
import com.globalinstitute.akademos.data.Prefs
import com.globalinstitute.akademos.data.Repo
import com.globalinstitute.akademos.data.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handle(app, intent)
            } finally {
                // keep next week's alarms armed
                if (intent.getStringExtra("kind") != ReminderKind.FOLLOW_UP && !intent.getBooleanExtra("snoozed", false)) {
                    ReminderScheduler.rearm(app)
                }
                pending.finish()
            }
        }
    }

    private suspend fun handle(ctx: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: return
        val batchId = intent.getStringExtra("batchId") ?: return
        val date = intent.getStringExtra("date") ?: return
        val title = intent.getStringExtra("title") ?: "Class"
        val test = intent.getBooleanExtra("test", false)
        if (!test && (Prefs.lastUserId == null || !Prefs.remindersOn)) return

        // Check online whether someone already did it (another teacher, another phone).
        val records = withTimeoutOrNull(8000) {
            if (Repo.awaitSession()) runCatching { Repo.attendance(batchId, date) }.getOrNull() else null
        }

        val heading: String
        val body: String
        var names: ArrayList<String>? = null
        when (kind) {
            ReminderKind.START -> {
                if (!test && (Prefs.isDone("in", batchId, date) || (records != null && records.isNotEmpty()))) return
                heading = "Time to take attendance"
                body = "$title\n${com.globalinstitute.akademos.ui.components.prettyTime(intent.getStringExtra("start") ?: "")} – ${com.globalinstitute.akademos.ui.components.prettyTime(intent.getStringExtra("end") ?: "")}"
            }
            ReminderKind.END -> {
                if (Prefs.isDone("out", batchId, date) || (records != null && records.any { it.leftAt != null })) return
                heading = "Class is over"
                body = "$title — mark who is leaving"
            }
            else -> {
                val absent = records?.filter { it.status == Status.ABSENT }
                if (absent != null && absent.isEmpty()) return
                if (absent != null) {
                    val students = runCatching { Repo.students() }.getOrDefault(emptyList()).associateBy { it.id }
                    names = ArrayList(absent.mapNotNull { students[it.studentId]?.fullName })
                }
                heading = "Check late arrivals"
                body = if (names.isNullOrEmpty()) "$title — some students were absent 15 minutes ago"
                else "$title — ${names.size} not here yet: ${names.take(4).joinToString()}${if (names.size > 4) "…" else ""}"
            }
        }
        post(ctx, intent, kind, batchId, date, title, heading, body, names)
    }

    private fun post(ctx: Context, src: Intent, kind: String, batchId: String, date: String, title: String,
                     heading: String, body: String, names: ArrayList<String>?) {
        val id = "$kind:$batchId:$date".hashCode()
        val full = Intent(ctx, ReminderActivity::class.java).apply {
            putExtras(src); putExtra("heading", heading); putExtra("body", body); putExtra("notifId", id)
            names?.let { putStringArrayListExtra("names", it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullPi = PendingIntent.getActivity(ctx, id, full, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = MainActivity.openAttendanceIntent(ctx, batchId, date, kind)
        val openPi = PendingIntent.getActivity(ctx, id + 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = NotificationCompat.Builder(ctx, ReminderScheduler.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(heading)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(openPi)
            .addAction(0, if (kind == ReminderKind.END) "Mark leaving" else "Take attendance", openPi)
            .setAutoCancel(true)
            .setTimeoutAfter(45 * 60 * 1000L)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.rearm(context.applicationContext)
    }
}
