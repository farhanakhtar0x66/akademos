package com.globalinstitute.akademos.reminders

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.globalinstitute.akademos.MainActivity
import com.globalinstitute.akademos.R
import com.globalinstitute.akademos.data.ClaimStatus
import com.globalinstitute.akademos.data.PayMethod
import com.globalinstitute.akademos.data.PlanMode
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
        when (kind) {
            ReminderKind.MONEY -> { if (Prefs.moneyReminders && Prefs.lastUserId != null) money(ctx, intent.getBooleanExtra("evening", false)); return }
            ReminderKind.CHECKIN -> { if (Prefs.lastUserId != null) checkIn(ctx, intent); return }
            ReminderKind.PLAN -> { planAction(ctx, intent); return }
            ReminderKind.CLAIMS -> { if (Prefs.lastRole == "admin") claims(ctx); return }
            ReminderKind.DISMISS -> { ctx.getSystemService(NotificationManager::class.java).cancel(intent.getIntExtra("notifId", 0)); return }
        }
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

// ===================================================================== 3 PM check-in
private val CHECKIN_ID = "checkin".hashCode()

private fun broadcast(ctx: Context, code: Int, extras: Intent.() -> Unit): PendingIntent =
    PendingIntent.getBroadcast(ctx, code, Intent(ctx, ReminderReceiver::class.java).setAction("akademos.REMINDER").apply(extras),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

private fun openApp(ctx: Context, code: Int, target: String, kind: String, date: String = java.time.LocalDate.now().toString()): PendingIntent =
    PendingIntent.getActivity(ctx, code, MainActivity.openAttendanceIntent(ctx, target, date, kind),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

private fun classLine(name: String?, start: String, end: String) =
    "${name ?: "Class"} · ${com.globalinstitute.akademos.ui.components.prettyTime(start)} – ${com.globalinstitute.akademos.ui.components.prettyTime(end)}"

/** "Are you taking classes today?" with the teacher's usual classes and one-tap answers. */
private suspend fun checkIn(ctx: Context, intent: Intent) {
    val test = intent.getBooleanExtra("test", false)
    if (!Prefs.checkIn && !test) return
    if (withTimeoutOrNull(8000) { Repo.awaitSession() } != true) return
    val today = java.time.LocalDate.now()
    runCatching {
        val me = Repo.myProfile() ?: return
        if (!me.isStaff) return
        val school = Repo.school()
        val plans = Repo.plans(today.toString(), today.plusDays(7).toString())
        ReminderScheduler.sync(ctx, school, me, plans)   // a fresh copy of the week, once a day
        if (plans.plan(me.id, today.toString()) != null && !test) return   // already answered
        val usual = plans.usual(school, me.id, today)
        val nudge = intent.getBooleanExtra("nudge", false)
        val title = if (nudge) "Please confirm today's classes" else "Are you taking classes today?"
        val body = if (usual.isEmpty()) "Tap to choose which class and from when to when."
            else "Your usual classes:\n" + usual.joinToString("\n") { classLine(school.batch(it.batchId)?.name, it.startTime, it.endTime) }
        val d = today.toString()
        val b = NotificationCompat.Builder(ctx, ReminderScheduler.CHANNEL_CHECKIN)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle(title).setContentText(body.lineSequence().drop(if (usual.isEmpty()) 0 else 1).firstOrNull() ?: body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openApp(ctx, CHECKIN_ID, "plan", "plan", d))
            .setAutoCancel(true)
            .setTimeoutAfter(6 * 60 * 60 * 1000L)
        if (usual.isNotEmpty()) {
            b.addAction(0, "Yes, as usual", broadcast(ctx, CHECKIN_ID + 1) { putExtra("kind", ReminderKind.PLAN); putExtra("batchId", "plan"); putExtra("date", d); putExtra("mode", PlanMode.USUAL) })
            b.addAction(0, "Change", openApp(ctx, CHECKIN_ID + 2, "plan", "plan", d))
        } else {
            b.addAction(0, "Choose classes", openApp(ctx, CHECKIN_ID + 2, "plan", "plan", d))
        }
        b.addAction(0, "No class today", broadcast(ctx, CHECKIN_ID + 3) { putExtra("kind", ReminderKind.PLAN); putExtra("batchId", "plan"); putExtra("date", d); putExtra("mode", PlanMode.OFF) })
        ctx.getSystemService(NotificationManager::class.java).notify(CHECKIN_ID, b.build())
    }
}

/** A button on the check-in notification was pressed. */
private suspend fun planAction(ctx: Context, intent: Intent) {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val date = intent.getStringExtra("date") ?: return
    val mode = intent.getStringExtra("mode") ?: return
    val ok = withTimeoutOrNull(12_000) {
        Repo.awaitSession() && runCatching {
            Repo.savePlan(date, mode, emptyList())
            val me = Repo.myProfile() ?: return@runCatching "Saved."
            val school = Repo.school()
            val day = java.time.LocalDate.parse(date)
            val plans = Repo.plans(date, day.plusDays(7).toString())
            ReminderScheduler.sync(ctx, school, me, plans)
            val mine = plans.forTeacher(school, me.id, day)
            val nextUp = mine.firstOrNull { java.time.LocalTime.parse(it.startTime).isAfter(java.time.LocalTime.now()) }
            if (mode == PlanMode.OFF || mine.isEmpty()) "Noted: no class today."
            else "${mine.size} ${if (mine.size == 1) "class" else "classes"} today." +
                (nextUp?.let { " We'll remind you at ${com.globalinstitute.akademos.ui.components.prettyTime(it.startTime)}." } ?: "")
        }.getOrNull().also { msg -> if (msg != null) {
            nm.notify(CHECKIN_ID, NotificationCompat.Builder(ctx, ReminderScheduler.CHANNEL_CHECKIN)
                .setSmallIcon(R.drawable.ic_launcher_fg).setContentTitle("Thanks, you're all set").setContentText(msg)
                .setContentIntent(openApp(ctx, CHECKIN_ID, "plan", "plan", date))
                .setSilent(true).setAutoCancel(true).setTimeoutAfter(20 * 60 * 1000L).build())
        } } != null
    } ?: false
    if (!ok) {
        nm.notify(CHECKIN_ID, NotificationCompat.Builder(ctx, ReminderScheduler.CHANNEL_CHECKIN)
            .setSmallIcon(R.drawable.ic_launcher_fg).setContentTitle("Couldn't save your answer")
            .setContentText("No internet? Tap to open the app and try again.")
            .setContentIntent(openApp(ctx, CHECKIN_ID, "plan", "plan", date)).setAutoCancel(true).build())
    }
}

// ===================================================================== "I've paid" (administrator)
/** Tell the administrator when a student or parent says they've paid. */
private suspend fun claims(ctx: Context) {
    if (withTimeoutOrNull(8000) { Repo.awaitSession() } != true) return
    runCatching {
        val pending = Repo.pendingClaims()
        val seen = Prefs.seenClaims
        val fresh = pending.filter { it.id !in seen }
        Prefs.seenClaims = pending.mapNotNull { it.id }.toSet()
        if (fresh.isEmpty()) return
        val students = Repo.students().associateBy { it.id }
        val inr = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN")).apply { maximumFractionDigits = 0 }
        fun line(c: com.globalinstitute.akademos.data.FeeClaim) = "${students[c.studentId]?.fullName ?: "A student"}: ${inr.format(c.amount)} by ${PayMethod.label(c.method)}"
        val title = if (pending.size == 1) "${students[pending[0].studentId]?.fullName ?: "A student"} says they've paid"
            else "${pending.size} people say they've paid"
        val body = (if (pending.size == 1) "${inr.format(pending[0].amount)} by ${PayMethod.label(pending[0].method)}" + (pending[0].note?.let { " · “$it”" } ?: "")
            else pending.take(6).joinToString("\n") { line(it) }) + "\nTap to check and confirm."
        notifyMoney(ctx, "claims", title, body, "claims")
    }
}

/** Morning check: overdue fees and salaries for the administrator, upcoming fee for parents. */
private suspend fun money(ctx: Context, evening: Boolean = false) {
    if (withTimeoutOrNull(8000) { Repo.awaitSession() } != true) return
    val role = Prefs.lastRole
    val ym = java.time.YearMonth.now()
    val month = ym.atDay(1).toString()
    val today = java.time.LocalDate.now()
    val monthName = ym.format(java.time.format.DateTimeFormatter.ofPattern("MMMM"))
    val inr = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN")).apply { maximumFractionDigits = 0 }
    fun due(day: Int) = ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))
    runCatching {
        val students = Repo.students().associateBy { it.id }
        val fees = Repo.studentFees().filter { it.monthlyFee > 0 && students.containsKey(it.studentId) }
        val paid = Repo.feePayments(month).filter { it.forMonth == month }.groupBy { it.studentId }.mapValues { e -> e.value.sumOf { it.amount } }
        if (role == "admin") {
            val late = fees.filter { today.isAfter(due(it.dueDay)) && (paid[it.studentId] ?: 0.0) < it.monthlyFee - 0.009 }
            if (late.isNotEmpty()) {
                val total = late.sumOf { it.monthlyFee - (paid[it.studentId] ?: 0.0) }
                val names = late.mapNotNull { students[it.studentId]?.fullName?.substringBefore(' ') }
                notifyMoney(ctx, "fees", "Fees overdue for $monthName",
                    "${late.size} ${if (late.size == 1) "student hasn't" else "students haven't"} paid · ${inr.format(total)}\n${names.take(6).joinToString()}${if (names.size > 6) "…" else ""}",
                    "fees")
            }
            if (!com.globalinstitute.akademos.data.Features.SALARIES) return@runCatching
            val sal = Repo.salaryPayments(month).filter { it.forMonth == month }.groupBy { it.payeeId }.mapValues { e -> e.value.sumOf { it.amount } }
            val owed = Repo.payees().filter { it.active && it.monthlySalary > 0 && !today.isBefore(due(it.payDay)) && (sal[it.id] ?: 0.0) < it.monthlySalary - 0.009 }
            if (owed.isNotEmpty()) {
                notifyMoney(ctx, "salary", if (owed.size == 1) "Salary to pay: ${owed[0].name}" else "${owed.size} salaries to pay",
                    owed.joinToString("\n") { "${it.name}: ${inr.format(it.monthlySalary - (sal[it.id] ?: 0.0))}" }, "salary")
            }
        } else if (role == "parent") {
            val claims = runCatching { Repo.feeClaims() }.getOrDefault(emptyList())
            fees.forEach { f ->
                val left = f.monthlyFee - (paid[f.studentId] ?: 0.0)
                val d = due(f.dueDay)
                if (left <= 0.009 || today.isBefore(d.minusDays(3))) return@forEach
                if (evening && !today.isAfter(d)) return@forEach      // evenings are only for overdue fees
                val mine = claims.filter { it.studentId == f.studentId && it.forMonth == month }
                if (mine.any { it.status == ClaimStatus.PENDING }) return@forEach   // waiting for the office to confirm
                val name = students[f.studentId]?.fullName?.substringBefore(' ') ?: "Your child"
                val when_ = d.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM"))
                val body = when {
                    mine.firstOrNull()?.status == ClaimStatus.REJECTED ->
                        "The office hasn't received your payment yet. ${inr.format(left)} is still due. Have you paid?"
                    today.isAfter(d) -> "${inr.format(left)} is overdue. It was due on $when_. Have you paid?"
                    else -> "${inr.format(left)} is due on $when_. Have you paid?"
                }
                notifyFee(ctx, f.studentId, "$name's fee for $monthName", body)
            }
        }
    }
}

/** The daily fee reminder for a student, with "Yes, I've paid" and "Not yet". */
private fun notifyFee(ctx: Context, studentId: String, title: String, body: String) {
    val id = "fee:$studentId".hashCode()
    val n = NotificationCompat.Builder(ctx, ReminderScheduler.CHANNEL_MONEY)
        .setSmallIcon(R.drawable.ic_launcher_fg)
        .setContentTitle(title).setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(openApp(ctx, id, "money", "home"))
        .addAction(0, "Yes, I've paid", openApp(ctx, id + 1, "money", "claim:$studentId"))
        .addAction(0, "Not yet", broadcast(ctx, id + 2) { putExtra("kind", ReminderKind.DISMISS); putExtra("batchId", "x"); putExtra("date", "x"); putExtra("notifId", id) })
        .setAutoCancel(true)
        .build()
    ctx.getSystemService(NotificationManager::class.java).notify(id, n)
}

private fun notifyMoney(ctx: Context, tag: String, title: String, body: String, open: String) {
    val pi = PendingIntent.getActivity(ctx, tag.hashCode(), MainActivity.openAttendanceIntent(ctx, "money", java.time.LocalDate.now().toString(), open),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val n = NotificationCompat.Builder(ctx, ReminderScheduler.CHANNEL_MONEY)
        .setSmallIcon(R.drawable.ic_launcher_fg)
        .setContentTitle(title).setContentText(body.lineSequence().first())
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setContentIntent(pi).setAutoCancel(true)
        .build()
    ctx.getSystemService(NotificationManager::class.java).notify(tag.hashCode(), n)
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.rearm(context.applicationContext)
    }
}
