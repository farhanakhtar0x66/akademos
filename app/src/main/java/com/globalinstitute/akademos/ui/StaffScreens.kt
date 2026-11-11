package com.globalinstitute.akademos.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.globalinstitute.akademos.data.Attendance
import com.globalinstitute.akademos.data.Prefs
import com.globalinstitute.akademos.data.Repo
import com.globalinstitute.akademos.data.School
import com.globalinstitute.akademos.data.Slot
import com.globalinstitute.akademos.data.Status
import com.globalinstitute.akademos.data.Student
import com.globalinstitute.akademos.data.friendly
import com.globalinstitute.akademos.reminders.ReminderScheduler
import com.globalinstitute.akademos.ui.components.AkCard
import com.globalinstitute.akademos.ui.components.BigField
import com.globalinstitute.akademos.ui.components.Bouncy
import com.globalinstitute.akademos.ui.components.Chip
import com.globalinstitute.akademos.ui.components.EmptyBlock
import com.globalinstitute.akademos.ui.components.ErrorBlock
import com.globalinstitute.akademos.ui.components.Gap
import com.globalinstitute.akademos.ui.components.Load
import com.globalinstitute.akademos.ui.components.LoadingBlock
import com.globalinstitute.akademos.ui.components.Pill
import com.globalinstitute.akademos.ui.components.PrimaryButton
import com.globalinstitute.akademos.ui.components.ScreenHeader
import com.globalinstitute.akademos.ui.components.SecondaryButton
import com.globalinstitute.akademos.ui.components.SectionTitle
import com.globalinstitute.akademos.ui.components.StudentTile
import com.globalinstitute.akademos.ui.components.prettyTime
import com.globalinstitute.akademos.ui.components.rememberLoad
import com.globalinstitute.akademos.ui.theme.LocalStatus
import com.globalinstitute.akademos.ui.theme.parseHex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ===================================================================== Today
@Composable
fun TodayScreen(school: Load<School>) {
    val s = LocalSession.current
    val today = remember { LocalDate.now() }
    var day by rememberSaveable { mutableIntStateOf(today.dayOfWeek.value) }
    var tick by remember { mutableIntStateOf(0) }
    val todays = rememberLoad(tick) { Repo.attendanceOn(today.toString()) }
    val plans = rememberLoad(tick, s.nav.stack.size) {
        runCatching { Repo.plans(today.toString(), today.plusDays(6).toString()) }.getOrDefault(com.globalinstitute.akademos.data.Plans.EMPTY)
    }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalTime.now() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++; now = LocalTime.now() }

    val greeting = when (now.hour) { in 0..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }

    LazyColumn(contentPadding = PaddingValues(bottom = LocalBarInset.current + 32.dp)) {
        item {
            ScreenHeader("$greeting, ${s.me.fullName.substringBefore(' ')}",
                eyebrow = today.format(DateTimeFormatter.ofPattern("EEEE · d MMMM")))
        }
        item { ReminderSetupCard(Modifier.padding(horizontal = 20.dp)) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DayOfWeek.entries.toList()) { d ->
                    Chip(day == d.value, if (d.value == today.dayOfWeek.value) "Today" else d.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        { day = d.value })
                }
            }
        }
        val sc = school.data
        when {
            sc == null && school.error != null -> item { Box(Modifier.padding(20.dp)) { ErrorBlock(school.error, school.reload) } }
            sc == null -> item { LoadingBlock() }
            else -> {
                val isTodayView = day == today.dayOfWeek.value
                val pl = plans.data ?: com.globalinstitute.akademos.data.Plans.EMPTY
                if (isTodayView && plans.data != null) {
                    item(key = "checkin") { CheckInCard(sc, pl, { tick++ }, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                    if (s.me.isAdmin) item(key = "teachers") { TeachersTodayCard(sc, pl, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
                }
                // The chosen weekday in the coming week, with every teacher's check-in applied.
                val date = today.plusDays(((day - today.dayOfWeek.value + 7) % 7).toLong())
                val slots = pl.sessions(sc, date).sortedWith(compareBy(
                    { isTodayView && !now.isBefore(LocalTime.parse(it.endTime)) }, { it.startTime }))
                if (slots.isEmpty()) item {
                    EmptyBlock(Icons.Rounded.EventBusy, "No classes", when {
                        sc.batches.isEmpty() && s.me.isAdmin -> "Add batches and their timings in Admin → Batches & timetable."
                        sc.batches.isEmpty() -> "The administrator hasn't given you any batches yet."
                        else -> "There are no classes on this day."
                    })
                }
                val isToday = day == today.dayOfWeek.value
                items(slots, key = { it.key }) { slot ->
                    SlotCard(sc, slot, isToday, now, todays.data.orEmpty().filter { it.batchId == slot.batchId },
                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).animateItem())
                }
            }
        }
    }
}

@Composable
private fun SlotCard(sc: School, slot: com.globalinstitute.akademos.data.Session, isToday: Boolean, now: LocalTime, records: List<Attendance>, modifier: Modifier) {
    val s = LocalSession.current
    val st = LocalStatus.current
    val batch = sc.batch(slot.batchId) ?: return
    val subject = sc.subject(batch.subjectId)
    val color = parseHex(subject?.color)
    val start = LocalTime.parse(slot.startTime)
    val end = LocalTime.parse(slot.endTime)
    val live = isToday && !now.isBefore(start.minusMinutes(10)) && now.isBefore(end)
    val over = isToday && !now.isBefore(end)
    val taken = records.isNotEmpty()
    val present = records.count { it.status != Status.ABSENT }
    val date = LocalDate.now().toString()

    val subjInName = subject != null && batch.name.contains(subject.name, ignoreCase = true)
    val who = listOfNotNull(subject?.name?.takeUnless { subjInName }, sc.person(slot.teacherId ?: batch.teacherId)?.fullName,
        "changed for today".takeIf { slot.changed }).joinToString("  ·  ")
    val clock = java.time.format.DateTimeFormatter.ofPattern("h:mm")
    val ampm = java.time.format.DateTimeFormatter.ofPattern("a")

    if (live && !taken) {
        // The signature: what's happening right now, as loud as a timetable board.
        val c = com.globalinstitute.akademos.ui.theme.Ak.OnMarigold
        Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = com.globalinstitute.akademos.ui.theme.Ak.Marigold) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                    Spacer(Modifier.width(8.dp))
                    com.globalinstitute.akademos.ui.components.Eyebrow("Happening now · ends ${prettyTime(slot.endTime)}", color = c)
                }
                Gap(10.dp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(start.format(clock), style = MaterialTheme.typography.displayLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(64f, androidx.compose.ui.unit.TextUnitType.Sp)), color = c)
                    Spacer(Modifier.width(6.dp))
                    Text(start.format(ampm), Modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.titleLarge, color = c)
                }
                Text(batch.name, style = MaterialTheme.typography.headlineSmall, color = c)
                if (who.isNotEmpty()) Text(who, style = MaterialTheme.typography.bodyLarge, color = c.copy(alpha = 0.75f))
                Gap(18.dp)
                PrimaryButton("Take attendance", onClick = { s.nav.push(Screen.Attendance(batch.id!!, date, AttMode.ARRIVAL)) },
                    icon = Icons.Rounded.TaskAlt, modifier = Modifier.fillMaxWidth(),
                    color = com.globalinstitute.akademos.ui.theme.Ak.Jamun, contentColor = Color.White)
            }
        }
        return
    }

    AkCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${prettyTime(slot.startTime)} – ${prettyTime(slot.endTime)}", Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                taken -> Pill("$present present", st.present, icon = Icons.Rounded.CheckCircle, onColor = st.onPresent)
                over -> Pill("Not taken", st.absent, icon = Icons.Rounded.WarningAmber, onColor = st.onAbsent)
            }
        }
        Gap(6.dp)
        Text(batch.name, style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(who.ifEmpty { subject?.name ?: "" }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isToday) {
            Gap(14.dp)
            if (!taken) {
                SecondaryButton(if (over) "Take attendance now" else "Take attendance early", { s.nav.push(Screen.Attendance(batch.id!!, date, AttMode.ARRIVAL)) },
                    Modifier.fillMaxWidth(), icon = Icons.Rounded.TaskAlt)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Edit", { s.nav.push(Screen.Attendance(batch.id!!, date, AttMode.ARRIVAL)) }, Modifier.weight(1f))
                    SecondaryButton("Mark leaving", { s.nav.push(Screen.Attendance(batch.id!!, date, AttMode.DEPARTURE)) }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Friendly checklist so the full-screen reminders actually work on this phone. */
@Composable
fun ReminderSetupCard(modifier: Modifier = Modifier, showWhenAllGood: Boolean = false) {
    val ctx = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(showWhenAllGood) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    @Suppress("UNUSED_EXPRESSION") refresh

    val nm = ctx.getSystemService(NotificationManager::class.java)
    val notifOk = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val exactOk = ReminderScheduler.canExact(ctx)
    val fullOk = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent()
    val pm = ctx.getSystemService(android.os.PowerManager::class.java)
    val batteryOk = pm.isIgnoringBatteryOptimizations(ctx.packageName)
    val allGood = notifOk && exactOk && fullOk

    AnimatedVisibility(!allGood || showWhenAllGood, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        AkCard(modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surface) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Alarm, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(10.dp))
                Text(if (allGood) "Reminders are ready" else "Turn on class reminders", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            Gap(6.dp)
            val missing = listOf(notifOk, exactOk, fullOk, batteryOk).count { !it }
            Text(if (allGood) "The phone will wake you when it's time for attendance and when class ends."
                 else "$missing ${if (missing == 1) "setting needs" else "settings need"} your OK so the phone can wake you when it's time for attendance.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!expanded) {
                Gap(12.dp)
                PrimaryButton("Set up now", { expanded = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Alarm)
                return@AkCard
            }
            Gap(12.dp)
            SetupRow("Allow notifications", notifOk) {
                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            SetupRow("Allow alarms on time", exactOk) {
                if (Build.VERSION.SDK_INT >= 31) ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
            }
            SetupRow("Allow full-screen alerts", fullOk) {
                if (Build.VERSION.SDK_INT >= 34) ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
            }
            SetupRow("Don't let battery saver stop reminders", batteryOk) {
                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }
}

@Composable
private fun SetupRow(label: String, ok: Boolean, fix: () -> Unit) {
    val st = LocalStatus.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber, null, Modifier.size(26.dp), tint = if (ok) st.present else st.late)
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (!ok) PrimaryButton("Turn on", fix)
    }
}

// ===================================================================== Attendance
private class AttData(val students: List<Student>, val records: List<Attendance>, val title: String, val color: Color)

@Composable
fun AttendanceScreen(screen: Screen.Attendance) {
    val s = LocalSession.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val ctx = LocalContext.current
    val st = LocalStatus.current
    val scope = rememberCoroutineScope()
    val load = rememberLoad(screen) {
        val sc = s.school ?: Repo.school()
        val b = sc.batch(screen.batchId)
        val ids = Repo.batchStudentIds(screen.batchId).toSet()
        val students = Repo.students().filter { it.id in ids }
        AttData(students, Repo.attendance(screen.batchId, screen.date), b?.name ?: "Class", parseHex(sc.subject(b?.subjectId)?.color))
    }
    val marks = remember(screen) { mutableStateMapOf<String, String?>() }
    var initialised by remember(screen) { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val data = load.data
    LaunchedEffect(data) {
        if (data != null && !initialised) {
            data.records.forEach { r ->
                marks[r.studentId] = when (screen.mode) {
                    AttMode.DEPARTURE -> if (r.leftAt != null) "left" else null
                    else -> r.status
                }
            }
            initialised = true
        }
    }

    val dateLabel = LocalDate.parse(screen.date).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    val modeTitle = when (screen.mode) { AttMode.ARRIVAL -> "Attendance"; AttMode.DEPARTURE -> "Who is leaving"; AttMode.LATE -> "Late arrivals" }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(modeTitle, "${data?.title ?: ""}  ·  $dateLabel", onBack = { s.nav.pop() })
        when {
            data == null && load.error != null -> Box(Modifier.padding(20.dp)) { ErrorBlock(load.error, load.reload) }
            data == null -> LoadingBlock()
            else -> {
                val visible = when (screen.mode) {
                    AttMode.ARRIVAL -> data.students
                    AttMode.DEPARTURE -> data.students.filter { sid -> data.records.any { it.studentId == sid.id && it.status != Status.ABSENT } }
                    AttMode.LATE -> data.students.filter { sid -> data.records.any { it.studentId == sid.id && it.status == Status.ABSENT } }
                }.filter { query.isBlank() || it.fullName.contains(query, true) || (it.rollNo ?: "").contains(query, true) }

                if (visible.isEmpty() && query.isBlank()) {
                    Box(Modifier.weight(1f)) {
                        EmptyBlock(Icons.Rounded.DoneAll,
                            when (screen.mode) { AttMode.ARRIVAL -> "No students yet"; AttMode.DEPARTURE -> "Nobody marked present"; AttMode.LATE -> "Everyone is here" },
                            when (screen.mode) { AttMode.ARRIVAL -> "Ask the administrator to add students to this batch."; AttMode.DEPARTURE -> "Take attendance first."; AttMode.LATE -> "No one was absent. Well done!" })
                    }
                    return@Column
                }

                // ---- live counters
                val total = visible.size
                val pres = visible.count { marks[it.id] == Status.PRESENT || marks[it.id] == Status.LATE || marks[it.id] == "left" }
                val abs = visible.count { marks[it.id] == Status.ABSENT }
                val un = total - pres - abs
                Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Counter(if (screen.mode == AttMode.DEPARTURE) "Left" else "Here", pres, st.present, Modifier.weight(1f))
                    if (screen.mode == AttMode.ARRIVAL) Counter("Absent", abs, st.absent, Modifier.weight(1f))
                    Counter(if (screen.mode == AttMode.DEPARTURE) "Still here" else "To mark", un, MaterialTheme.colorScheme.outline, Modifier.weight(1f))
                }

                LazyVerticalGrid(
                    GridCells.Adaptive(320.dp), Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            if (data.students.size > 12) {
                                BigField(query, { query = it }, "Find a student", leading = Icons.Rounded.Search)
                                Gap(8.dp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                when (screen.mode) {
                                    AttMode.ARRIVAL -> {
                                        SecondaryButton("Everyone present", { visible.forEach { marks[it.id!!] = Status.PRESENT } }, icon = Icons.Rounded.DoneAll)
                                    }
                                    AttMode.DEPARTURE -> SecondaryButton("Everyone has left", { visible.forEach { marks[it.id!!] = "left" } }, icon = Icons.Rounded.DoneAll)
                                    AttMode.LATE -> {}
                                }
                            }
                            Gap(4.dp)
                            Text(
                                when (screen.mode) {
                                    AttMode.ARRIVAL -> "Tap a name: once = present, twice = absent, three times = late."
                                    AttMode.DEPARTURE -> "Tap each student as they leave."
                                    AttMode.LATE -> "Tap students who have now arrived."
                                },
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(visible, key = { it.id!! }) { stu ->
                        val id = stu.id!!
                        StudentTile(
                            stu.fullName, stu.rollNo?.let { "Roll $it" }, marks[id],
                            leftMode = screen.mode == AttMode.DEPARTURE,
                            onClick = {
                                marks[id] = when (screen.mode) {
                                    AttMode.ARRIVAL -> when (marks[id]) { null -> Status.PRESENT; Status.PRESENT -> Status.ABSENT; Status.ABSENT -> Status.LATE; else -> Status.PRESENT }
                                    AttMode.DEPARTURE -> if (marks[id] == "left") null else "left"
                                    AttMode.LATE -> if (marks[id] == Status.LATE) Status.ABSENT else Status.LATE
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // ---- save bar
                Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).animateContentSize()) {
                        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                        if (screen.mode == AttMode.ARRIVAL && un > 0)
                            Text("$un not marked — they will be saved as absent.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Gap(6.dp)
                        AnimatedContent(saved, transitionSpec = { (scaleIn(Bouncy) + fadeIn()) togetherWith fadeOut() }, label = "save") { done ->
                            if (done) {
                                Row(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(20.dp)).background(st.present),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(30.dp), tint = st.onPresent)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Saved", color = st.onPresent, style = MaterialTheme.typography.titleLarge)
                                }
                            } else {
                                PrimaryButton("Save", modifier = Modifier.fillMaxWidth(), loading = saving, icon = Icons.Rounded.CheckCircle, onClick = {
                                    saving = true; error = null
                                    scope.launch {
                                        runCatching {
                                            when (screen.mode) {
                                                AttMode.ARRIVAL -> {
                                                    val map = data.students.associate { it.id!! to (marks[it.id] ?: Status.ABSENT) }
                                                    Repo.saveAttendance(screen.batchId, screen.date, map, data.records)
                                                    Prefs.markDone("in", screen.batchId, screen.date)
                                                    if (data.records.isEmpty() && map.values.any { it == Status.ABSENT })
                                                        ReminderScheduler.scheduleFollowUp(ctx, screen.batchId, data.title, screen.date)
                                                }
                                                AttMode.DEPARTURE -> {
                                                    val left = visible.filter { marks[it.id] == "left" }.map { it.id!! }
                                                    val notLeft = visible.filter { marks[it.id] != "left" }.mapNotNull { v -> v.id?.takeIf { id -> data.records.any { it.studentId == id && it.leftAt != null } } }
                                                    Repo.markLeft(screen.batchId, screen.date, left, true)
                                                    Repo.markLeft(screen.batchId, screen.date, notLeft, false)
                                                    Prefs.markDone("out", screen.batchId, screen.date)
                                                }
                                                AttMode.LATE -> {
                                                    val changed = visible.filter { marks[it.id] == Status.LATE }.associate { it.id!! to Status.LATE }
                                                    Repo.saveAttendance(screen.batchId, screen.date, changed, data.records)
                                                }
                                            }
                                        }.onSuccess {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                                            saved = true; delay(900); s.nav.pop()
                                        }.onFailure { error = it.friendly() }
                                        saving = false
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, color: Color, modifier: Modifier) {
    val sc by animateFloatAsState(1f, Bouncy, label = "c")
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.14f)).padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(value, transitionSpec = {
            if (targetState > initialState) (androidx.compose.animation.slideInVertically { it } + fadeIn()) togetherWith (androidx.compose.animation.slideOutVertically { -it } + fadeOut())
            else (androidx.compose.animation.slideInVertically { -it } + fadeIn()) togetherWith (androidx.compose.animation.slideOutVertically { it } + fadeOut())
        }, label = "count") { v ->
            Text("$v", style = MaterialTheme.typography.headlineMedium, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.scale(sc))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

