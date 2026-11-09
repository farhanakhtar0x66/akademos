package com.globalinstitute.akademos.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import com.globalinstitute.akademos.data.Prefs
import com.globalinstitute.akademos.data.Profile
import com.globalinstitute.akademos.data.School
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Serializable
data class ReminderSlot(
    val slotId: String,
    val batchId: String,
    val title: String,      // e.g. "Class 10 · English"
    val color: String,
    val weekday: Int,       // 1 = Monday
    val start: String,      // HH:mm:ss
    val end: String,
)

object ReminderKind {
    const val START = "start"
    const val END = "end"
    const val FOLLOW_UP = "followup"
}

object ReminderScheduler {
    const val CHANNEL_ALERTS = "class_alerts"
    private val json = Json { ignoreUnknownKeys = true }

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL_ALERTS, "Class attendance alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Full-screen alerts when it is time to take attendance or when a class ends"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
    }

    /** Save the timetable this person should be reminded about, then (re)arm the alarms. */
    fun sync(ctx: Context, school: School, me: Profile) {
        val mine = school.slots.filter { slot ->
            val b = school.batch(slot.batchId) ?: return@filter false
            me.isStaff && (Prefs.remindAllClasses || b.teacherId == null || b.teacherId == me.id)
        }.mapNotNull { slot ->
            val b = school.batch(slot.batchId) ?: return@mapNotNull null
            val subj = school.subject(b.subjectId)
            ReminderSlot(slot.id ?: return@mapNotNull null, b.id!!, b.name, subj?.color ?: "#5B3E8C",
                slot.weekday, slot.startTime, slot.endTime)
        }
        Prefs.scheduleCache = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ReminderSlot.serializer()), mine)
        rearm(ctx)
    }

    fun cached(): List<ReminderSlot> = runCatching {
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ReminderSlot.serializer()), Prefs.scheduleCache ?: "[]")
    }.getOrDefault(emptyList())

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    /** Arms the next start and end alarm for every slot. Safe to call any time. */
    fun rearm(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val old = Prefs.scheduledCodes
        val now = LocalDateTime.now()
        val newCodes = mutableSetOf<String>()
        if (Prefs.remindersOn && Prefs.lastUserId != null && Prefs.lastRole != "parent") {
            for (s in cached()) {
                for (kind in listOf(ReminderKind.START, ReminderKind.END)) {
                    val t = LocalTime.parse(if (kind == ReminderKind.START) s.start else s.end)
                    val at = nextOccurrence(now, s.weekday, t)
                    val key = "${s.slotId}:$kind"
                    newCodes += key
                    set(ctx, am, key.hashCode(), at, intent(ctx, kind, s, at.toLocalDate()))
                }
            }
        }
        (old - newCodes).forEach { cancel(ctx, am, it.hashCode()) }
        Prefs.scheduledCodes = newCodes
    }

    /** Fires a sample "time to take attendance" alert after a few seconds, to check the phone allows it. */
    fun sendTest(ctx: Context, seconds: Long = 8) {
        val s = cached().firstOrNull() ?: ReminderSlot("test", "test", "Test class", "#5B3E8C", 1, "16:00:00", "17:00:00")
        val am = ctx.getSystemService(AlarmManager::class.java)
        val i = intent(ctx, ReminderKind.START, s, LocalDate.now()).putExtra("test", true)
        set(ctx, am, "test-reminder".hashCode(), LocalDateTime.now().plusSeconds(seconds), i)
    }

    fun scheduleFollowUp(ctx: Context, batchId: String, title: String, date: String, minutes: Long = 15) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val at = LocalDateTime.now().plusMinutes(minutes)
        val i = Intent(ctx, ReminderReceiver::class.java).apply {
            action = "akademos.REMINDER"
            putExtra("kind", ReminderKind.FOLLOW_UP); putExtra("batchId", batchId)
            putExtra("title", title); putExtra("date", date)
        }
        set(ctx, am, "fu:$batchId:$date".hashCode(), at, i)
    }

    fun snooze(ctx: Context, extras: Intent, minutes: Long = 5) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val i = Intent(ctx, ReminderReceiver::class.java).apply {
            action = "akademos.REMINDER"; putExtras(extras); putExtra("snoozed", true)
        }
        set(ctx, am, ("snooze:" + extras.getStringExtra("batchId") + extras.getStringExtra("kind")).hashCode(),
            LocalDateTime.now().plusMinutes(minutes), i)
    }

    fun cancelAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        Prefs.scheduledCodes.forEach { cancel(ctx, am, it.hashCode()) }
        Prefs.scheduledCodes = emptySet()
    }

    private fun intent(ctx: Context, kind: String, s: ReminderSlot, date: LocalDate) =
        Intent(ctx, ReminderReceiver::class.java).apply {
            action = "akademos.REMINDER"
            putExtra("kind", kind); putExtra("batchId", s.batchId); putExtra("title", s.title)
            putExtra("start", s.start.take(5)); putExtra("end", s.end.take(5)); putExtra("date", date.toString())
            putExtra("color", s.color)
        }

    private fun set(ctx: Context, am: AlarmManager, code: Int, at: LocalDateTime, i: Intent) {
        val pi = PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canExact(ctx)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, millis, 60_000, pi)
        }
    }

    private fun cancel(ctx: Context, am: AlarmManager, code: Int) {
        val pi = PendingIntent.getBroadcast(ctx, code, Intent(ctx, ReminderReceiver::class.java).setAction("akademos.REMINDER"),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        pi?.let { am.cancel(it); it.cancel() }
    }

    fun nextOccurrence(now: LocalDateTime, weekday: Int, time: LocalTime): LocalDateTime {
        var d = now.toLocalDate()
        repeat(8) {
            if (d.dayOfWeek.value == weekday) {
                val cand = d.atTime(time)
                if (cand.isAfter(now.plusSeconds(5))) return cand
            }
            d = d.plusDays(1)
        }
        return now.toLocalDate().plusDays(7).atTime(time)
    }
}
