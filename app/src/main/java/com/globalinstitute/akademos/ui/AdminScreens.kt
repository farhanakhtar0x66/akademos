package com.globalinstitute.akademos.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn

import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.globalinstitute.akademos.data.Batch
import com.globalinstitute.akademos.data.Repo
import com.globalinstitute.akademos.data.Roles
import com.globalinstitute.akademos.data.SchoolClass
import com.globalinstitute.akademos.data.Slot
import com.globalinstitute.akademos.data.Student
import com.globalinstitute.akademos.data.Subject
import com.globalinstitute.akademos.data.friendly
import com.globalinstitute.akademos.ui.components.AkCard
import com.globalinstitute.akademos.ui.components.BigField
import com.globalinstitute.akademos.ui.components.Chip
import com.globalinstitute.akademos.ui.components.EmptyBlock
import com.globalinstitute.akademos.ui.components.ErrorBlock
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
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.TextStyle
import java.util.Locale

// ===================================================================== hub
private data class AdminTile(val title: String, val body: String, val icon: ImageVector, val screen: Screen)

@Composable
fun AdminHomeScreen() {
    val s = LocalSession.current
    val tiles = listOf(
        AdminTile("Fees", "Who has paid, who hasn't", Icons.Rounded.CurrencyRupee, Screen.Fees),
        AdminTile("Teacher pay", "Classes taken, pay due", Icons.Rounded.Payments, Screen.TeacherPay),
        *(if (com.globalinstitute.akademos.data.Features.SALARIES) arrayOf(
            AdminTile("Salaries", "Pay teachers and staff", Icons.Rounded.Payments, Screen.Salaries),
            AdminTile("Earnings", "Fees minus salaries", Icons.Rounded.Savings, Screen.Earnings)) else emptyArray()),
        AdminTile("Access keys", "Invite teachers and families", Icons.Rounded.Key, Screen.Keys),
        AdminTile("Timetable", "Batches and class times", Icons.Rounded.EventNote, Screen.Batches),
        AdminTile("Students", "Add or edit students", Icons.Rounded.Groups, Screen.StudentsAdmin),
        AdminTile("Classes", "Class 8, Class 9…", Icons.Rounded.School, Screen.Classes),
        AdminTile("Subjects", "English, Maths…", Icons.Rounded.MenuBook, Screen.Subjects),
        AdminTile("People", "Block or remove access", Icons.Rounded.SupervisorAccount, Screen.People),
        AdminTile("Activity log", "Every change, and who", Icons.Rounded.History, Screen.Audit),
    )
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Admin", "Only you can see this section")
        LazyVerticalGrid(GridCells.Adaptive(170.dp), contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = LocalBarInset.current + 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(tiles) { t ->
                // Icon and padding are fixed; the text part grows with the phone's / app's text size.
                val tileHeight = with(androidx.compose.ui.platform.LocalDensity.current) { 104.dp + 74.sp.toDp() }
                // Every tile is the same height, so the grid reads as a grid.
                AkCard(Modifier.height(tileHeight), onClick = { s.nav.push(t.screen) }) {
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(t.icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(t.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, minLines = 2)
                }
            }
        }
    }
}

// ===================================================================== classes
@Composable
fun ClassesScreen() {
    val s = LocalSession.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<SchoolClass?>(null) }
    val list = s.school?.classes
    SimpleListScreen("Classes", onAdd = { editing = SchoolClass(name = "", sortOrder = list?.size ?: 0) }) {
        when {
            list == null -> item { LoadingBlock() }
            list.isEmpty() -> item { EmptyBlock(Icons.Rounded.School, "No classes", "Add classes like “Class 9” or “Class 10”.") }
            else -> items(list, key = { it.id!! }) { c ->
                AkCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), onClick = { editing = c }) {
                    Text(c.name, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
    editing?.let { c ->
        NameDialog(if (c.id == null) "New class" else "Edit class", c.name, "Class name",
            onDelete = c.id?.let { id -> { scope.launch { runCatching { Repo.deleteClass(id) }; s.reloadSchool(); editing = null } } },
            onDismiss = { editing = null }) { name ->
            Repo.saveClass(c.copy(name = name)); s.reloadSchool(); editing = null
        }
    }
}

// ===================================================================== subjects
private val palette = listOf("#5B3E8C", "#1F7A8C", "#C77700", "#2E8B57", "#B83280", "#3F51B5", "#C2410C", "#4A5568", "#0E7490", "#7C3AED")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubjectsScreen() {
    val s = LocalSession.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Subject?>(null) }
    val list = s.school?.subjects
    SimpleListScreen("Subjects", onAdd = { editing = Subject(name = "", color = palette[(list?.size ?: 0) % palette.size]) }) {
        when {
            list == null -> item { LoadingBlock() }
            list.isEmpty() -> item { EmptyBlock(Icons.Rounded.MenuBook, "No subjects", "Add English, Business, Science and more.") }
            else -> items(list, key = { it.id!! }) { c ->
                AkCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), onClick = { editing = c }, accent = parseHex(c.color)) {
                    Text(c.name, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
    editing?.let { subj ->
        var color by remember(subj) { mutableStateOf(subj.color) }
        NameDialog(if (subj.id == null) "New subject" else "Edit subject", subj.name, "Subject name",
            extra = {
                Gap()
                Text("Colour", style = MaterialTheme.typography.titleSmall)
                Gap(6.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.forEach { hex ->
                        Box(Modifier.size(40.dp).clip(CircleShape).background(parseHex(hex)).clickable { color = hex }
                            .border(3.dp, if (color == hex) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center) {
                            if (color == hex) Icon(Icons.Rounded.Check, null, tint = Color.White)
                        }
                    }
                }
            },
            onDelete = subj.id?.let { id -> { scope.launch { runCatching { Repo.deleteSubject(id) }; s.reloadSchool(); editing = null } } },
            onDismiss = { editing = null }) { name ->
            Repo.saveSubject(subj.copy(name = name, color = color)); s.reloadSchool(); editing = null
        }
    }
}

// ===================================================================== students
@Composable
fun StudentsAdminScreen() {
    val s = LocalSession.current
    val load = rememberLoad(Unit) { Repo.students(includeInactive = true) }
    var q by remember { mutableStateOf("") }
    LaunchedEffect(s.nav.stack.size) { if (s.nav.top == Screen.StudentsAdmin) load.reload() }
    SimpleListScreen("Students", onAdd = { s.nav.push(Screen.EditStudent(null)) }, addLabel = "Add student") {
        item { BigField(q, { q = it }, "Search", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), leading = Icons.Rounded.Search) }
        val list = load.data
        when {
            list == null && load.error != null -> item { Box(Modifier.padding(20.dp)) { ErrorBlock(load.error, load.reload) } }
            list == null -> item { LoadingBlock() }
            list.isEmpty() -> item { EmptyBlock(Icons.Rounded.Groups, "No students", "Tap “Add student” to begin.") }
            else -> items(list.filter { q.isBlank() || it.fullName.contains(q, true) || (it.rollNo ?: "").contains(q, true) }, key = { it.id!! }) { k ->
                StudentRow(k, trailing = {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(s.school?.klass(k.classId)?.name ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!k.active) Pill("Inactive", MaterialTheme.colorScheme.outline)
                    }
                }) { s.nav.push(Screen.EditStudent(k)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditStudentScreen(student: Student?) {
    val s = LocalSession.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(student?.fullName ?: "") }
    var roll by remember { mutableStateOf(student?.rollNo ?: "") }
    var phone by remember { mutableStateOf(student?.guardianPhone ?: "") }
    var classId by remember { mutableStateOf(student?.classId) }
    var active by remember { mutableStateOf(student?.active ?: true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && student != null) com.globalinstitute.akademos.ui.components.AkDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("Remove ${student.fullName} for good?") },
        text = { Text("Their attendance, marks, answer sheets and fee records will be deleted. This cannot be undone.\n\nIf they have only left, turn off “Currently studying here” instead and their records are kept.") },
        confirmButton = {
            TextButton({
                scope.launch {
                    runCatching { Repo.deleteStudent(student.id!!) }.onSuccess { confirmDelete = false; s.nav.pop() }
                        .onFailure { error = it.friendly(); confirmDelete = false }
                }
            }) { Text("Delete for good", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Keep") } })
    FormScreen(if (student == null) "New student" else "Edit student", busy, error, saveEnabled = name.trim().length >= 2,
        onDelete = student?.let { { confirmDelete = true } }, onSave = {
        busy = true; error = null
        scope.launch {
            runCatching {
                Repo.saveStudent(Student(student?.id, name.trim(), roll.trim().ifBlank { null }, classId, phone.trim().ifBlank { null }, active))
            }.onSuccess { s.nav.pop() }.onFailure { error = it.friendly() }
            busy = false
        }
    }) {
        BigField(name, { name = it }, "Full name", keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        Gap()
        BigField(roll, { roll = it.take(20) }, "Roll number (optional)")
        Gap()
        BigField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(20) }, "Parent's phone (optional)",
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Phone))
        SectionTitle("Class")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.school?.classes?.forEach { c -> Chip(classId == c.id, c.name, { classId = c.id }) }
        }
        if (student != null) {
            Gap(20.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Currently studying here", style = MaterialTheme.typography.titleMedium)
                    Text("Turn off when a student leaves. Their records are kept.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(active, { active = it })
            }
        }
    }
}

// ===================================================================== batches & timetable
@Composable
fun BatchesScreen() {
    val s = LocalSession.current
    val sc = s.school
    SimpleListScreen("Batches & timetable", onAdd = { s.nav.push(Screen.EditBatch(null)) }, addLabel = "New batch") {
        item {
            Text("A batch is a group of students who study one subject together, with its own weekly timings.",
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            sc == null -> item { LoadingBlock() }
            sc.batches.isEmpty() -> item { EmptyBlock(Icons.Rounded.EventNote, "No batches", "Create one, e.g. “Class 10 · English”.") }
            else -> items(sc.batches, key = { it.id!! }) { b ->
                val subj = sc.subject(b.subjectId)
                val slots = sc.slots.filter { it.batchId == b.id }.sortedWith(compareBy({ it.weekday }, { it.startTime }))
                AkCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), onClick = { s.nav.push(Screen.EditBatch(b.id)) }, accent = parseHex(subj?.color)) {
                    Text(b.name, style = MaterialTheme.typography.titleLarge)
                    Text(listOfNotNull(sc.klass(b.classId)?.name, subj?.name, sc.person(b.teacherId)?.fullName).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (slots.isNotEmpty()) {
                        Gap(6.dp)
                        slots.forEach { sl ->
                            Text("${dayName(sl.weekday)}  ${prettyTime(sl.startTime)} – ${prettyTime(sl.endTime)}", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

private fun dayName(d: Int) = DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, Locale.getDefault())

private data class SlotDraft(val weekday: Int, val start: LocalTime, val end: LocalTime)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditBatchScreen(batchId: String?) {
    val s = LocalSession.current
    val sc = s.school
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = sc?.batch(batchId)
    val students = rememberLoad(Unit) { Repo.students() }
    val members = rememberLoad(batchId) { batchId?.let { Repo.batchStudentIds(it) } ?: emptyList() }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var classId by remember { mutableStateOf(existing?.classId) }
    var subjectId by remember { mutableStateOf(existing?.subjectId) }
    var teacherId by remember { mutableStateOf(existing?.teacherId) }
    val selected = remember { mutableStateListOf<String>() }
    var membersReady by remember { mutableStateOf(batchId == null) }
    val slots = remember {
        mutableStateListOf<SlotDraft>().apply {
            sc?.slots?.filter { it.batchId == batchId }?.forEach { add(SlotDraft(it.weekday, LocalTime.parse(it.startTime), LocalTime.parse(it.endTime))) }
        }
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(members.data) { members.data?.let { if (!membersReady) { selected.clear(); selected.addAll(it); membersReady = true } } }
    // Suggest a name from class + subject
    LaunchedEffect(classId, subjectId) {
        if (existing == null) {
            val auto = listOfNotNull(sc?.klass(classId)?.name, sc?.subject(subjectId)?.name).joinToString(" · ")
            if (auto.isNotEmpty()) name = auto
        }
    }

    fun pickTime(initial: LocalTime, done: (LocalTime) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> done(LocalTime.of(h, m)) }, initial.hour, initial.minute, false).show()
    }

    FormScreen(if (existing == null) "New batch" else "Edit batch", busy, error, saveEnabled = name.isNotBlank() && membersReady,
        onDelete = existing?.let { { confirmDelete = true } },
        onSave = {
            val bad = slots.firstOrNull { !it.end.isAfter(it.start) }
            if (bad != null) { error = "A class must end after it starts (${dayName(bad.weekday)})."; return@FormScreen }
            busy = true; error = null
            scope.launch {
                runCatching {
                    Repo.saveBatch(Batch(existing?.id, name.trim(), classId, subjectId, teacherId), selected.toList(),
                        slots.map { Slot(batchId = "", weekday = it.weekday, startTime = "${it.start}:00", endTime = "${it.end}:00") })
                }.onSuccess { s.reloadSchool(); s.nav.pop() }.onFailure { error = it.friendly() }
                busy = false
            }
        }) {
        SectionTitle("Class")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sc?.classes?.forEach { c -> Chip(classId == c.id, c.name, { classId = c.id }) }
        }
        SectionTitle("Subject")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sc?.subjects?.forEach { c -> Chip(subjectId == c.id, c.name, { subjectId = c.id }, parseHex(c.color)) }
        }
        Gap(16.dp)
        BigField(name, { name = it }, "Batch name")
        SectionTitle("Teacher")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(teacherId == null, "Anyone", { teacherId = null })
            sc?.staff?.forEach { p -> Chip(teacherId == p.id, p.fullName, { teacherId = p.id }) }
        }

        SectionTitle("Weekly timings")
        slots.forEachIndexed { i, sl ->
            AkCard(Modifier.padding(vertical = 5.dp).animateContentSize(), contentPadding = PaddingValues(14.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { d -> Chip(sl.weekday == d, dayName(d), { slots[i] = sl.copy(weekday = d) }) }
                }
                Gap(10.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("From ${prettyTime(sl.start.toString())}", { pickTime(sl.start) { slots[i] = slots[i].copy(start = it) } })
                    SecondaryButton("To ${prettyTime(sl.end.toString())}", { pickTime(sl.end) { slots[i] = slots[i].copy(end = it) } })
                    Spacer(Modifier.weight(1f))
                    IconButton({ slots.removeAt(i) }) { Icon(Icons.Rounded.Delete, "Remove timing") }
                }
            }
        }
        SecondaryButton("Add a timing", {
            val last = slots.lastOrNull()
            slots.add(last?.copy(weekday = (last.weekday % 7) + 1) ?: SlotDraft(1, LocalTime.of(16, 0), LocalTime.of(17, 0)))
        }, icon = Icons.Rounded.Add)

        SectionTitle("Students (${selected.size})")
        val all = students.data
        if (all == null) LoadingBlock() else {
            if (classId != null) {
                SecondaryButton("Add everyone from ${sc?.klass(classId)?.name}", {
                    all.filter { it.classId == classId }.forEach { if (it.id !in selected) selected.add(it.id!!) }
                }, icon = Icons.Rounded.Groups)
                Gap(8.dp)
            }
            val sorted = all.sortedWith(compareBy({ it.classId != classId }, { it.fullName }))
            sorted.forEach { k ->
                val on = k.id in selected
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { if (on) selected.remove(k.id) else selected.add(k.id!!) }
                    .padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(on, { if (on) selected.remove(k.id) else selected.add(k.id!!) })
                    Text(k.fullName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(sc?.klass(k.classId)?.name ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (confirmDelete && existing != null) {
        com.globalinstitute.akademos.ui.components.AkDialog(onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this batch?") },
            text = { Text("“${existing.name}” and all its attendance and test records will be removed for good. This cannot be undone.") },
            confirmButton = {
                TextButton({
                    scope.launch {
                        runCatching { Repo.deleteBatch(existing.id!!) }.onSuccess { s.reloadSchool(); s.nav.pop() }.onFailure { error = it.friendly() }
                        confirmDelete = false
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Keep") } })
    }
}

// ===================================================================== access keys
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeysScreen() {
    val s = LocalSession.current
    val st = LocalStatus.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val invites = rememberLoad(Unit) { Repo.invites() }
    val students = rememberLoad(Unit) { Repo.students() }
    var role by remember { mutableStateOf(Roles.PARENT) }
    var label by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf(48) }
    var q by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<String>() }
    val batchPick = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var newKey by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { ScreenHeader("Access keys", "Each key works once, for one person", onBack = { s.nav.pop() }) }
        item {
            AkCard(Modifier.padding(horizontal = 20.dp)) {
                Text("Make a new key", style = MaterialTheme.typography.titleLarge)
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(role == Roles.PARENT, "Student / parent", { role = Roles.PARENT })
                    Chip(role == Roles.TEACHER, "Teacher", { role = Roles.TEACHER })
                }
                Gap(12.dp)
                BigField(label, { label = it.take(80) }, if (role == Roles.PARENT) "Note (e.g. Riya, or Riya's mother)" else "Teacher's name")
                Gap(10.dp)
                Text("Key stops working after", style = MaterialTheme.typography.titleSmall)
                Gap(6.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(hours == 24, "1 day", { hours = 24 }); Chip(hours == 48, "2 days", { hours = 48 }); Chip(hours == 168, "7 days", { hours = 168 })
                }
                AnimatedVisibility(role == Roles.PARENT) {
                    Column {
                        Gap(14.dp)
                        Text("Which child? (${chosen.size} chosen)", style = MaterialTheme.typography.titleSmall)
                        Gap(6.dp)
                        BigField(q, { q = it }, "Search student", leading = Icons.Rounded.Search)
                        val list = students.data.orEmpty().filter { q.isBlank() || it.fullName.contains(q, true) }.take(if (q.isBlank()) 8 else 30)
                        list.forEach { k ->
                            val on = k.id in chosen
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { if (on) chosen.remove(k.id) else chosen.add(k.id!!) },
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(on, { if (on) chosen.remove(k.id) else chosen.add(k.id!!) })
                                Text(k.fullName, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Text(s.school?.klass(k.classId)?.name ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                AnimatedVisibility(role == Roles.TEACHER) {
                    Column {
                        Gap(14.dp)
                        Text("Which batches will they teach?", style = MaterialTheme.typography.titleSmall)
                        Text("They become the teacher of these when they use the key. You can change it later in Batches.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Gap(6.dp)
                        val bs = s.school?.batches.orEmpty().sortedBy { it.teacherId != null }
                        bs.forEach { b ->
                            val on = b.id in batchPick
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { if (on) batchPick.remove(b.id) else batchPick.add(b.id!!) },
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(on, { if (on) batchPick.remove(b.id) else batchPick.add(b.id!!) })
                                Text(b.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Text(s.school?.person(b.teacherId)?.fullName ?: "No teacher", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                error?.let { Gap(); Text(it, color = MaterialTheme.colorScheme.error) }
                Gap(16.dp)
                PrimaryButton("Create key", modifier = Modifier.fillMaxWidth(), loading = busy, icon = Icons.Rounded.Key,
                    enabled = label.isNotBlank() && (role == Roles.TEACHER || chosen.isNotEmpty()), onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching { Repo.createInvite(role, label.trim(), if (role == Roles.PARENT) chosen.toList() else emptyList(), hours,
                            if (role == Roles.TEACHER) batchPick.toList() else emptyList()) }
                            .onSuccess { newKey = it; label = ""; chosen.clear(); invites.reload() }
                            .onFailure { error = it.friendly() }
                        busy = false
                    }
                })
            }
        }
        item { SectionTitle("Recent keys", Modifier.padding(horizontal = 20.dp)) }
        val list = invites.data
        when {
            list == null && invites.error != null -> item { Box(Modifier.padding(20.dp)) { ErrorBlock(invites.error, invites.reload) } }
            list == null -> item { LoadingBlock() }
            list.isEmpty() -> item { Text("No keys made yet.", Modifier.padding(horizontal = 20.dp)) }
            else -> items(list, key = { it.id }) { inv ->
                val expired = runCatching { OffsetDateTime.parse(inv.expiresAt).isBefore(OffsetDateTime.now()) }.getOrDefault(false)
                AkCard(Modifier.padding(horizontal = 20.dp, vertical = 5.dp), contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(inv.label ?: "Key", style = MaterialTheme.typography.titleMedium)
                            Text(inv.role.replaceFirstChar { it.uppercase() } + "  ·  made " + prettyDate(inv.createdAt),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when {
                            inv.usedAt != null -> Pill("Used", st.present)
                            expired -> Pill("Expired", MaterialTheme.colorScheme.outline)
                            else -> TextButton({ scope.launch { runCatching { Repo.revokeInvite(inv.id) }; invites.reload() } }) {
                                Text("Cancel key", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    newKey?.let { key ->
        com.globalinstitute.akademos.ui.components.AkDialog(onDismissRequest = {},
            title = { Text("Key ready", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    Text("Give this key only to the right person. It is shown just once.", style = MaterialTheme.typography.bodyLarge)
                    Gap(16.dp)
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(key, Modifier.fillMaxWidth().padding(18.dp), textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Gap(12.dp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Copy", {
                            ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Access key", key))
                        }, icon = Icons.Rounded.ContentCopy)
                        SecondaryButton("Share", {
                            val text = "Your Akademos (Global Institute) access key: $key\n\nInstall the app, create an account, then enter this key. It works once."
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share key"))
                        }, icon = Icons.Rounded.Share)
                    }
                }
            },
            confirmButton = { TextButton({ newKey = null }) { Text("Done", style = MaterialTheme.typography.titleMedium) } })
    }
}

// ===================================================================== people
@Composable
fun PeopleScreen() {
    val s = LocalSession.current
    val scope = rememberCoroutineScope()
    val load = rememberLoad(Unit) { Repo.people() }
    var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<com.globalinstitute.akademos.data.Profile?>(null) }
    removing?.let { p ->
        com.globalinstitute.akademos.ui.components.AkDialog(onDismissRequest = { removing = null },
            title = { Text("Remove ${p.fullName}?") },
            text = { Text("They lose access straight away and would need a new access key to come back." +
                if (p.role == Roles.TEACHER) " Their batches stay, with no teacher, until you choose a new one." else "") },
            confirmButton = {
                TextButton({
                    scope.launch {
                        runCatching { Repo.removeUser(p.id) }.onFailure { error = it.friendly() }
                        removing = null; load.reload(); s.reloadSchool()
                    }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ removing = null }) { Text("Keep") } })
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { ScreenHeader("People", "Switch someone off to block them at once, or remove them", onBack = { s.nav.pop() }) }
        error?.let { item { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) } }
        val list = load.data
        when {
            list == null && load.error != null -> item { Box(Modifier.padding(20.dp)) { ErrorBlock(load.error, load.reload) } }
            list == null -> item { LoadingBlock() }
            else -> items(list, key = { it.id }) { p ->
                AkCard(Modifier.padding(horizontal = 20.dp, vertical = 5.dp), contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.globalinstitute.akademos.ui.components.Avatar(p.fullName, 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.fullName, style = MaterialTheme.typography.titleMedium)
                            val roleName = when (p.role) { Roles.ADMIN -> "Administrator"; Roles.TEACHER -> "Teacher"; else -> "Student / parent" }
                            Text(roleName + if (!p.active) "  ·  blocked" else "",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (p.role != Roles.PARENT) s.school?.batches?.filter { it.teacherId == p.id }?.takeIf { it.isNotEmpty() }?.let { bs ->
                                Text("Teaches " + bs.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (p.id != s.me.id) {
                            Switch(p.active, { on ->
                                scope.launch { runCatching { Repo.setActive(p.id, on) }.onFailure { error = it.friendly() }; load.reload() }
                            })
                            IconButton({ removing = p }) { Icon(Icons.Rounded.Delete, "Remove ${p.fullName}", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}

// ===================================================================== audit
@Composable
fun AuditScreen() {
    val s = LocalSession.current
    val load = rememberLoad(Unit) { Repo.auditLog() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { ScreenHeader("Activity log", "Last 100 changes", onBack = { s.nav.pop() }) }
        val list = load.data
        when {
            list == null && load.error != null -> item { Box(Modifier.padding(20.dp)) { ErrorBlock(load.error, load.reload) } }
            list == null -> item { LoadingBlock() }
            else -> items(list, key = { it.id }) { e ->
                val what = when (e.tableName) {
                    "attendance" -> "attendance"; "marks" -> "marks"; "assessments" -> "a test"; "students" -> "a student"
                    "profiles" -> "an account"; "parent_students" -> "a parent link"; else -> e.tableName
                }
                val verb = when (e.action) { "INSERT" -> "added"; "UPDATE" -> "changed"; else -> "removed" }
                val at = runCatching { OffsetDateTime.parse(e.at).atZoneSameInstant(java.time.ZoneId.systemDefault()) }.getOrNull()
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("${e.actorName ?: "System"} $verb $what", style = MaterialTheme.typography.bodyLarge)
                        at?.let { Text(it.format(java.time.format.DateTimeFormatter.ofPattern("d MMM, h:mm a")), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

// ===================================================================== shared building blocks
@Composable
private fun SimpleListScreen(title: String, onAdd: () -> Unit, addLabel: String = "Add", content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val s = LocalSession.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack = { s.nav.pop() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp), content = content)
        Box(Modifier.navigationBarsPadding().padding(16.dp)) {
            PrimaryButton(addLabel, onAdd, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        }
    }
}

@Composable
private fun FormScreen(
    title: String, busy: Boolean, error: String?, saveEnabled: Boolean, onSave: () -> Unit,
    onDelete: (() -> Unit)? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val s = LocalSession.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, onBack = { s.nav.pop() }, actions = {
            if (onDelete != null) IconButton(onDelete, Modifier.size(52.dp)) { Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            item { Column(content = content) }
        }
        Surface(shadowElevation = 12.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge); Gap(6.dp) }
                PrimaryButton("Save", onSave, Modifier.fillMaxWidth(), loading = busy, enabled = saveEnabled, icon = Icons.Rounded.Check)
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String, initial: String, label: String, onDismiss: () -> Unit,
    extra: @Composable () -> Unit = {}, onDelete: (() -> Unit)? = null, onSave: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    com.globalinstitute.akademos.ui.components.AkDialog(onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                BigField(name, { name = it.take(40) }, label, keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                extra()
                error?.let { Gap(); Text(it, color = MaterialTheme.colorScheme.error) }
                if (onDelete != null) {
                    Gap(16.dp)
                    // Destructive, so it sits apart from Save and is red all the way through (icon and word).
                    androidx.compose.material3.OutlinedButton(onDelete, Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        shape = com.globalinstitute.akademos.ui.components.ButtonMetrics.CompactShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                        Text("Delete", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton("Save", loading = busy, enabled = name.isNotBlank(), onClick = {
                busy = true
                scope.launch { runCatching { onSave(name.trim()) }.onFailure { error = it.friendly() }; busy = false }
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Suppress("unused") private val keepIcons = listOf(Icons.Rounded.Category, Icons.Rounded.PersonAdd)
