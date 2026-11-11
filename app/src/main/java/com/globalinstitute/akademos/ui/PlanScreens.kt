package com.globalinstitute.akademos.ui

import android.app.TimePickerDialog
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.globalinstitute.akademos.data.DayClass
import com.globalinstitute.akademos.data.PlanMode
import com.globalinstitute.akademos.data.Plans
import com.globalinstitute.akademos.data.Repo
import com.globalinstitute.akademos.data.Roles
import com.globalinstitute.akademos.data.School
import com.globalinstitute.akademos.data.Session
import com.globalinstitute.akademos.data.friendly
import com.globalinstitute.akademos.reminders.ReminderScheduler
import com.globalinstitute.akademos.ui.components.AkCard
import com.globalinstitute.akademos.ui.components.Chip
import com.globalinstitute.akademos.ui.components.ErrorBlock
import com.globalinstitute.akademos.ui.components.Eyebrow
import com.globalinstitute.akademos.ui.components.Gap
import com.globalinstitute.akademos.ui.components.LoadingBlock
import com.globalinstitute.akademos.ui.components.Pill
import com.globalinstitute.akademos.ui.components.PrimaryButton
import com.globalinstitute.akademos.ui.components.ScreenHeader
import com.globalinstitute.akademos.ui.components.SecondaryButton
import com.globalinstitute.akademos.ui.components.SectionTitle
import com.globalinstitute.akademos.ui.components.prettyTime
import com.globalinstitute.akademos.ui.components.rememberLoad
import com.globalinstitute.akademos.ui.theme.LocalStatus
import com.globalinstitute.akademos.ui.theme.parseHex
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private fun sessionLine(sc: School, se: Session) = "${sc.batch(se.batchId)?.name ?: "Class"}  ·  ${prettyTime(se.startTime)} – ${prettyTime(se.endTime)}"

/** A class name with its time underneath, so nothing wraps awkwardly on small phones. */
@Composable
private fun SessionLines(sc: School, se: Session, strong: Boolean = true) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(sc.batch(se.batchId)?.name ?: "Class", style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
        Text("${prettyTime(se.startTime)} – ${prettyTime(se.endTime)}", style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The batches this person may plan a class for: their own (the administrator can pick any). */
private fun plannable(sc: School, meId: String, admin: Boolean) =
    sc.batches.filter { admin || it.teacherId == meId }

// ===================================================================== Today: "are you teaching today?"
/**
 * Shown on Today for anyone who teaches. Before they answer it asks the question with one-tap
 * answers; afterwards it shrinks to a one-line summary with a "Change" button.
 */
@Composable
fun CheckInCard(sc: School, plans: Plans, onChanged: () -> Unit, modifier: Modifier = Modifier) {
    val s = LocalSession.current
    val ctx = LocalContext.current
    val st = LocalStatus.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val d = today.toString()
    val plan = plans.plan(s.me.id, d)
    val usual = plans.usual(sc, s.me.id, today)
    val mine = plans.forTeacher(sc, s.me.id, today)
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // The administrator only gets asked if they teach a batch themselves.
    if (s.me.isAdmin && usual.isEmpty() && plan == null && plannable(sc, s.me.id, false).isEmpty()) return

    fun answer(mode: String) {
        busy = mode; error = null
        scope.launch {
            runCatching { Repo.savePlan(d, mode, emptyList()); ReminderScheduler.refresh(ctx, s.me, sc) }
                .onSuccess { onChanged() }.onFailure { error = it.friendly() }
            busy = null
        }
    }

    AkCard(modifier.animateContentSize()) {
        if (plan == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.HelpOutline, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Are you taking classes today?", style = MaterialTheme.typography.titleLarge)
            }
            Gap(6.dp)
            if (usual.isEmpty()) {
                Text("You have no usual classes on ${today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}. Choose a class and time if you're teaching today.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Your usual classes:", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                usual.forEach { se -> SessionLines(sc, se) }
            }
            Gap(14.dp)
            if (usual.isNotEmpty()) {
                PrimaryButton("Yes, as usual", { answer(PlanMode.USUAL) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.CheckCircle, loading = busy == PlanMode.USUAL)
                Gap(8.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(if (usual.isEmpty()) "Choose" else "Change", { s.nav.push(Screen.PlanDay(d)) }, Modifier.weight(1f))
                SecondaryButton("No class", { answer(PlanMode.OFF) }, Modifier.weight(1f))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Your day")
                    Text(when {
                        plan.mode == PlanMode.OFF || mine.isEmpty() -> "No class today"
                        plan.mode == PlanMode.CUSTOM -> "${mine.size} ${if (mine.size == 1) "class" else "classes"}, changed for today"
                        else -> "${mine.size} ${if (mine.size == 1) "class" else "classes"}, as usual"
                    }, style = MaterialTheme.typography.titleLarge)
                }
                TextButton({ s.nav.push(Screen.PlanDay(d)) }) { Text("Change", style = MaterialTheme.typography.titleMedium) }
            }
            val nextUp = mine.firstOrNull { LocalTime.parse(it.startTime).isAfter(LocalTime.now()) }
            if (plan.mode != PlanMode.OFF && nextUp != null) {
                Text("We'll remind you at ${prettyTime(nextUp.startTime)}.", style = MaterialTheme.typography.bodyLarge, color = st.present)
            }
        }
        error?.let { Gap(6.dp); Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/** Administrator's view on Today: who has answered the check-in, and with what. */
@Composable
fun TeachersTodayCard(sc: School, plans: Plans, modifier: Modifier = Modifier) {
    val st = LocalStatus.current
    val today = LocalDate.now()
    val teachers = sc.staff.filter { p -> p.role == Roles.TEACHER && p.active }
    if (teachers.isEmpty()) return
    val now = LocalTime.now()
    AkCard(modifier) {
        Eyebrow("Teachers today")
        Gap(4.dp)
        teachers.forEach { t ->
            val plan = plans.plan(t.id, today.toString())
            val list = plans.forTeacher(sc, t.id, today)
            val (label, color) = when {
                plan == null && list.isEmpty() -> "No usual class" to MaterialTheme.colorScheme.outline
                plan == null -> (if (now.isBefore(ReminderScheduler.CHECKIN_TIME)) "Asked at 3 PM" else "Hasn't replied") to st.late
                plan.mode == PlanMode.OFF || list.isEmpty() -> "No class" to MaterialTheme.colorScheme.outline
                plan.mode == PlanMode.CUSTOM -> "Changed" to MaterialTheme.colorScheme.primary
                else -> "As usual" to st.present
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(t.fullName, style = MaterialTheme.typography.titleMedium)
                    list.forEach { se -> SessionLines(sc, se, strong = false) }
                }
                Pill(label, color)
            }
        }
    }
}

// ===================================================================== plan one day
private data class Draft(val batchId: String?, val start: LocalTime, val end: LocalTime)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanDayScreen(date: String) {
    val s = LocalSession.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val day = LocalDate.parse(date)
    val load = rememberLoad(date) { (s.school ?: Repo.school()) to Repo.plans(date, date) }
    val drafts = remember { mutableStateListOf<Draft>() }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val data = load.data
    LaunchedEffect(data) {
        if (data != null && !ready) {
            data.second.forTeacher(data.first, s.me.id, day).forEach {
                drafts += Draft(it.batchId, LocalTime.parse(it.startTime), LocalTime.parse(it.endTime))
            }
            ready = true
        }
    }

    fun pickTime(initial: LocalTime, done: (LocalTime) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> done(LocalTime.of(h, m)) }, initial.hour, initial.minute, false).show()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (day == LocalDate.now()) "Today's classes" else day.format(DateTimeFormatter.ofPattern("EEEE")),
            day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), onBack = { s.nav.pop() })
        when {
            data == null && load.error != null -> Box(Modifier.padding(20.dp)) { ErrorBlock(load.error, load.reload) }
            data == null || !ready -> LoadingBlock()
            else -> {
                val (sc, plans) = data
                val usual = plans.usual(sc, s.me.id, day)
                val choices = plannable(sc, s.me.id, s.me.isAdmin)
                val matchesUsual = drafts.size == usual.size && drafts.sortedBy { it.start }.zip(usual).all { (a, b) ->
                    a.batchId == b.batchId && a.start == LocalTime.parse(b.startTime) && a.end == LocalTime.parse(b.endTime)
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                    if (usual.isNotEmpty()) item {
                        AkCard(color = MaterialTheme.colorScheme.surfaceVariant) {
                            Eyebrow("Usual for this day")
                            usual.forEach { SessionLines(sc, it, strong = false) }
                            if (!matchesUsual) {
                                Gap(10.dp)
                                SecondaryButton("Use the usual classes", {
                                    drafts.clear()
                                    usual.forEach { drafts += Draft(it.batchId, LocalTime.parse(it.startTime), LocalTime.parse(it.endTime)) }
                                }, icon = Icons.Rounded.Replay)
                            }
                        }
                    }
                    item { SectionTitle(if (drafts.isEmpty()) "No classes" else "Classes on this day") }
                    if (choices.isEmpty()) item {
                        Text("You haven't been given any batches yet. Ask the administrator to set you as the teacher of a batch.",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    drafts.forEachIndexed { i, dr ->
                        item(key = "d$i") {
                            // A plain surface: AkCard measures its height intrinsically, which squashes wrapped chips.
                            Surface(Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column(Modifier.padding(14.dp).animateContentSize()) {
                                Text("Class ${i + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Gap(6.dp)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    choices.forEach { b ->
                                        Chip(dr.batchId == b.id, b.name, { drafts[i] = dr.copy(batchId = b.id) }, parseHex(sc.subject(b.subjectId)?.color))
                                    }
                                }
                                Gap(10.dp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SecondaryButton("From ${prettyTime(dr.start.toString())}", { pickTime(dr.start) { t ->
                                        val len = java.time.Duration.between(drafts[i].start, drafts[i].end)
                                        drafts[i] = drafts[i].copy(start = t, end = if (drafts[i].end.isAfter(t)) drafts[i].end else t.plus(len))
                                    } })
                                    SecondaryButton("To ${prettyTime(dr.end.toString())}", { pickTime(dr.end) { drafts[i] = drafts[i].copy(end = it) } })
                                    Spacer(Modifier.weight(1f))
                                    IconButton({ drafts.removeAt(i) }) { Icon(Icons.Rounded.Delete, "Remove this class") }
                                }
                            }
                            }
                        }
                    }
                    if (choices.isNotEmpty()) item {
                        Gap(6.dp)
                        SecondaryButton("Add a class", {
                            val last = drafts.lastOrNull()
                            val start = last?.end ?: LocalTime.of(16, 0)
                            drafts += Draft(if (choices.size == 1) choices[0].id else null, start, start.plusHours(1))
                        }, icon = Icons.Rounded.Add)
                    }
                }
                Surface(shadowElevation = 12.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge); Gap(6.dp) }
                        PrimaryButton(when {
                            drafts.isEmpty() -> "Save: no class"
                            matchesUsual -> "Save: as usual"
                            else -> "Save ${drafts.size} ${if (drafts.size == 1) "class" else "classes"}"
                        }, modifier = Modifier.fillMaxWidth(), loading = busy, icon = Icons.Rounded.Check, onClick = {
                            val missing = drafts.indexOfFirst { it.batchId == null }
                            val bad = drafts.indexOfFirst { !it.end.isAfter(it.start) }
                            error = when {
                                missing >= 0 -> "Choose which class for number ${missing + 1}."
                                bad >= 0 -> "Class ${bad + 1} must end after it starts."
                                else -> null
                            }
                            if (error != null) return@PrimaryButton
                            busy = true
                            scope.launch {
                                runCatching {
                                    val mode = when { drafts.isEmpty() -> PlanMode.OFF; matchesUsual -> PlanMode.USUAL; else -> PlanMode.CUSTOM }
                                    Repo.savePlan(date, mode, drafts.map { DayClass(null, s.me.id, date, it.batchId!!, "${it.start}:00", "${it.end}:00") })
                                    ReminderScheduler.refresh(ctx, s.me, sc)
                                    ctx.getSystemService(android.app.NotificationManager::class.java).cancel("checkin".hashCode())
                                }.onSuccess { s.nav.pop() }.onFailure { error = it.friendly() }
                                busy = false
                            }
                        })
                    }
                }
            }
        }
    }
}
