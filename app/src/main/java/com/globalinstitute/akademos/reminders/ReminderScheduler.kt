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
import com.globalinstitute.akademos.data.Plans
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
    val date: String? = null, // the exact day (from the check-in); null = every week
)

object ReminderKind {
    const val START = "start"
    const val END = "end"
    const val FOLLOW_UP = "followup"
    const val MONEY = "money"
    const val CHECKIN = "checkin"
    const val PLAN = "plan"         // a button on the check-in notification
    const val CLAIMS = "claims"     // administrator: someone says they paid
    const val DISMISS = "dismiss"
}

object ReminderScheduler {
    const val CHANNEL_ALERTS = "class_alerts"
    const val CHANNEL_MONEY = "money_reminders"
    const val CHANNEL_CHECKIN = "daily_checkin"
    private val MONEY_TIME: LocalTime = LocalTime.of(9, 0)
    private val MONEY_EVENING: LocalTime = LocalTime.of(18, 0)
    val CHECKIN_TIME: LocalTime = LocalTime.of(15, 0)
    private val CHECKIN_NUDGE: LocalTime = LocalTime.of(15, 45)
    private const val DAYS_AHEAD = 7L
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
        nm.createNotificationChannel(NotificationChannel(CHANNEL_MONEY, "Fee and salary reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A morning reminder about fees that are due and salaries to pay"
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_CHECKIN, "Daily class check-in", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "At 3 PM: are you taking classes today, and when?"
            enableVibration(true)
        })
    }

    /**
     * Save the classes this person should be reminded about for the next week, with every
     * teacher's daily check-in applied, then (re)arm the alarms.
     */
    fun sync(ctx: Context, school: School, me: Profile, plans: Plans) {
        if (!me.isStaff) return
        Prefs.hasOwnClasses = school.batches.any { it.teacherId == me.id } || plans.classes.any { it.teacherId == me.id }
        val today = LocalDate.now()
        val mine = (0..DAYS_AHEAD).flatMap { off ->
            val day = today.plusDays(off)
            plans.sessions(school, day).filter { se ->
                se.teacherId == me.id || se.teacherId == null || (me.isAdmin && Prefs.remindAllClasses)
            }.mapNotNull { se ->
                val b = school.batch(se.batchId) ?: return@mapNotNull null
                ReminderSlot("${se.key}:$day", b.id!!, b.name, school.subject(b.subjectId)?.color ?: "#5B3E8C",
                    day.dayOfWeek.value, se.startTime, se.endTime, day.toString())
            }
        }
        Prefs.scheduleCache = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ReminderSlot.serializer()), mine)
        rearm(ctx)
    }

    /** Load everyone's check-ins for the coming week and re-arm this person's reminders. */
    suspend fun refresh(ctx: Context, me: Profile, school: School) {
        if (!me.isStaff) return
        val today = LocalDate.now()
        sync(ctx, school, me, com.globalinstitute.akademos.data.Repo.plans(today.toString(), today.plusDays(DAYS_AHEAD).toString()))
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
        val role = Prefs.lastRole
        val signedIn = Prefs.lastUserId != null
        val staff = role == "admin" || role == "teacher"
        if (Prefs.remindersOn && signedIn && staff) {
            for (s in cached()) {
                for (kind in listOf(ReminderKind.START, ReminderKind.END)) {
                    val t = LocalTime.parse(if (kind == ReminderKind.START) s.start else s.end)
                    val at = if (s.date != null) LocalDate.parse(s.date).atTime(t) else nextOccurrence(now, s.weekday, t)
                    if (!at.isAfter(now.plusSeconds(5))) continue
                    val key = "${s.slotId}:$kind"
                    newCodes += key
                    set(ctx, am, key.hashCode(), at, intent(ctx, kind, s, at.toLocalDate()))
                }
            }
        }
        // 3 PM: "Are you taking classes today?" (and a nudge at 3:45 if there's no answer yet)
        if (Prefs.checkIn && signedIn && (role == "teacher" || (role == "admin" && Prefs.hasOwnClasses))) {
            for ((i, t) in listOf(CHECKIN_TIME, CHECKIN_NUDGE).withIndex()) {
                val at = next(now, t)
                val key = "checkin:$i"
                newCodes += key
                set(ctx, am, key.hashCode(), at, simple(ctx, ReminderKind.CHECKIN, at.toLocalDate()).putExtra("nudge", i == 1))
            }
        }
        if (Prefs.moneyReminders && signedIn && (role == "admin" || role == "parent")) {
            val at = next(now, MONEY_TIME)
            newCodes += "money:daily"
            set(ctx, am, "money:daily".hashCode(), at, moneyIntent(ctx, at.toLocalDate()))
            if (role == "parent") {
                // Overdue fees get a second reminder in the evening.
                val eve = next(now, MONEY_EVENING)
                newCodes += "money:evening"
                set(ctx, am, "money:evening".hashCode(), eve, moneyIntent(ctx, eve.toLocalDate()).putExtra("evening", true))
            }
        }
        // Administrator: look for new "I've paid" messages every 30 minutes, 8 AM to 9 PM.
        if (signedIn && role == "admin") {
            val at = nextClaimCheck(now)
            newCodes += "claims:poll"
            set(ctx, am, "claims:poll".hashCode(), at, simple(ctx, ReminderKind.CLAIMS, at.toLocalDate()), exact = false)
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

    private fun next(now: LocalDateTime, t: LocalTime): LocalDateTime =
        now.toLocalDate().atTime(t).let { if (it.isAfter(now.plusSeconds(5))) it else it.plusDays(1) }

    private fun nextClaimCheck(now: LocalDateTime): LocalDateTime {
        val cand = now.plusMinutes(30)
        return when {
            cand.toLocalTime().isBefore(LocalTime.of(8, 0)) -> cand.toLocalDate().atTime(8, 0)
            cand.toLocalTime().isAfter(LocalTime.of(21, 0)) -> cand.toLocalDate().plusDays(1).atTime(8, 0)
            else -> cand
        }
    }

    fun simple(ctx: Context, kind: String, date: LocalDate) = Intent(ctx, ReminderReceiver::class.java).apply {
        action = "akademos.REMINDER"
        putExtra("kind", kind); putExtra("batchId", kind); putExtra("date", date.toString())
    }

    /** Debug preview only: show the 3 PM check-in in a few seconds. */
    fun sendCheckInTest(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        set(ctx, am, "checkin:test".hashCode(), LocalDateTime.now().plusSeconds(5),
            simple(ctx, ReminderKind.CHECKIN, LocalDate.now()).putExtra("snoozed", true).putExtra("test", true))
    }

    /** Debug preview only: run the "someone says they paid" check in a few seconds. */
    fun sendClaimsTest(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        set(ctx, am, "claims:test".hashCode(), LocalDateTime.now().plusSeconds(5),
            simple(ctx, ReminderKind.CLAIMS, LocalDate.now()).putExtra("snoozed", true))
    }

    private fun moneyIntent(ctx: Context, date: LocalDate) = Intent(ctx, ReminderReceiver::class.java).apply {
        action = "akademos.REMINDER"
        putExtra("kind", ReminderKind.MONEY); putExtra("batchId", "money"); putExtra("date", date.toString())
    }

    /** Debug preview only: run the morning fee/salary check in a few seconds. */
    fun sendMoneyTest(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        set(ctx, am, "money:test".hashCode(), LocalDateTime.now().plusSeconds(5), moneyIntent(ctx, LocalDate.now()).putExtra("snoozed", true))
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

    private fun set(ctx: Context, am: AlarmManager, code: Int, at: LocalDateTime, i: Intent, exact: Boolean = true) {
        val pi = PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (!exact) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else if (canExact(ctx)) {
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
