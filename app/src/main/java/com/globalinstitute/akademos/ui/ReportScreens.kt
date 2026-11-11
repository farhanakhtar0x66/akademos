package com.globalinstitute.akademos.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.globalinstitute.akademos.data.Attendance
import com.globalinstitute.akademos.data.MarkWithTest
import com.globalinstitute.akademos.data.Repo
import com.globalinstitute.akademos.data.School
import com.globalinstitute.akademos.data.Status
import com.globalinstitute.akademos.data.Student
import com.globalinstitute.akademos.ui.components.AkCard
import com.globalinstitute.akademos.ui.components.Avatar
import com.globalinstitute.akademos.ui.components.BigField
import com.globalinstitute.akademos.ui.components.Chip
import com.globalinstitute.akademos.ui.components.EmptyBlock
import com.globalinstitute.akademos.ui.components.ErrorBlock
import com.globalinstitute.akademos.ui.components.Gap
import com.globalinstitute.akademos.ui.components.LoadingBlock
import com.globalinstitute.akademos.ui.components.ScreenHeader
import com.globalinstitute.akademos.ui.components.SecondaryButton
import com.globalinstitute.akademos.ui.components.SectionTitle
import com.globalinstitute.akademos.ui.components.rememberLoad
import com.globalinstitute.akademos.ui.theme.LocalStatus
import com.globalinstitute.akademos.ui.theme.parseHex
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ===================================================================== parent home
@Composable
fun ParentChildScreen() {
    val s = LocalSession.current
    val kids = rememberLoad(s.me.id) { Repo.students(includeInactive = true) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val list = kids.data.orEmpty()
    val child = list.firstOrNull { it.id == selected } ?: list.firstOrNull()

    LazyColumn(contentPadding = PaddingValues(bottom = LocalBarInset.current + 32.dp)) {
        item {
            ScreenHeader("Namaste, ${s.me.fullName.substringBefore(' ')}", "Attendance, marks and fees at Global Institute")
        }
        when {
            kids.data == null && kids.error != null -> item { Box(Modifier.padding(20.dp)) { ErrorBlock(kids.error, kids.reload) } }
            kids.data == null -> item { LoadingBlock() }
            list.isEmpty() -> item { EmptyBlock(Icons.Rounded.Groups, "No child linked yet", "Please ask the institute office to link your child to your account.") }
            else -> {
                if (list.size > 1) item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list, key = { it.id!! }) { k -> Chip(k.id == child?.id, k.fullName.substringBefore(' '), { selected = k.id }) }
                    }
                }
                child?.let { c ->
                    item(key = "fee-${c.id}") { Box(Modifier.padding(horizontal = 20.dp)) { ParentFeeCard(c.id!!, c.fullName) } }
                    item(key = "gap-${c.id}") { Gap(12.dp) }
                    report(c, s.school)
                }
            }
        }
    }
}

// ===================================================================== staff: student list & detail
@Composable
fun StudentsTab() {
    val s = LocalSession.current
    val students = rememberLoad(Unit) { Repo.students() }
    var q by remember { mutableStateOf("") }
    val sc = s.school
    LazyColumn(contentPadding = PaddingValues(bottom = LocalBarInset.current + 32.dp)) {
        item {
            ScreenHeader("Students", "Tap a student to see their attendance and marks")
            BigField(q, { q = it }, "Search by name or roll no.", Modifier.padding(horizontal = 20.dp), leading = Icons.Rounded.Search)
        }
        val all = students.data
        when {
            all == null && students.error != null -> item { Box(Modifier.padding(20.dp)) { ErrorBlock(students.error, students.reload) } }
            all == null -> item { LoadingBlock() }
            all.isEmpty() -> item { EmptyBlock(Icons.Rounded.Groups, "No students yet", "The administrator can add students in Admin → Students.") }
            else -> {
                val f = all.filter { q.isBlank() || it.fullName.contains(q, true) || (it.rollNo ?: "").contains(q, true) }
                val groups = f.groupBy { it.classId }.toList().sortedBy { (cid, _) -> sc?.classes?.indexOfFirst { it.id == cid }?.takeIf { it >= 0 } ?: 999 }
                groups.forEach { (cid, kids) ->
                    item(key = "h$cid") { SectionTitle(sc?.klass(cid)?.name ?: "No class", Modifier.padding(horizontal = 20.dp)) }
                    items(kids, key = { it.id!! }) { k -> StudentRow(k) { s.nav.push(Screen.StudentDetail(k.id!!)) } }
                }
            }
        }
    }
}

@Composable
fun StudentRow(k: Student, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    AkCard(Modifier.padding(horizontal = 20.dp, vertical = 5.dp), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(k.fullName, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(k.fullName, style = MaterialTheme.typography.titleMedium)
                k.rollNo?.let { Text("Roll $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun StudentDetailScreen(studentId: String) {
    val s = LocalSession.current
    val stu = rememberLoad(studentId) { Repo.students(includeInactive = true).firstOrNull { it.id == studentId } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            ScreenHeader(stu.data?.fullName ?: "Student", s.school?.klass(stu.data?.classId)?.name, onBack = { s.nav.pop() }, actions = {
                if (s.me.isAdmin) SecondaryButton("Fees", { s.nav.push(Screen.StudentFees(studentId)) }, icon = Icons.Rounded.CurrencyRupee)
            })
        }
        stu.data?.let { report(it, s.school) } ?: item { LoadingBlock() }
    }
}

// ===================================================================== the report itself
private fun LazyListScope.report(student: Student, school: School?) {
    item(key = "report-${student.id}") { ChildReport(student, school) }
}

private class ReportData(val att: List<Attendance>, val marks: List<MarkWithTest>)

@Composable
private fun ChildReport(student: Student, school: School?) {
    val st = LocalStatus.current
    val load = rememberLoad(student.id) { ReportData(Repo.studentAttendance(student.id!!), Repo.studentMarks(student.id)) }
    var viewPath by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    val d = load.data
    Column(Modifier.padding(horizontal = 20.dp)) {
        when {
            d == null && load.error != null -> ErrorBlock(load.error, load.reload)
            d == null -> LoadingBlock()
            else -> {
                // ---------- attendance summary
                val total = d.att.size
                val here = d.att.count { it.status != Status.ABSENT }
                val late = d.att.count { it.status == Status.LATE }
                val pct = if (total == 0) 0f else here.toFloat() / total
                AkCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ring(pct, if (pct >= 0.85f) st.present else if (pct >= 0.7f) st.late else st.absent)
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Attendance", style = MaterialTheme.typography.titleLarge)
                            Text(if (total == 0) "No classes recorded yet" else "Came to $here of $total classes",
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (late > 0) Text("Late $late ${if (late == 1) "time" else "times"}", style = MaterialTheme.typography.bodyLarge, color = st.late)
                        }
                    }
                    if (d.att.isNotEmpty()) {
                        Gap(16.dp)
                        Text("Recent classes", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Gap(8.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            d.att.take(10).reversed().forEach { a ->
                                val c = when (a.status) { Status.ABSENT -> st.absent; Status.LATE -> st.late; else -> st.present }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Box(Modifier.size(22.dp).clip(CircleShape).background(c))
                                    Text(LocalDate.parse(a.date).dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Gap(8.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Legend("Present", st.present); Legend("Late", st.late); Legend("Absent", st.absent)
                        }
                    }
                }

                // ---------- marks
                SectionTitle("Test results")
                if (d.marks.isEmpty()) EmptyBlock(Icons.Rounded.Quiz, "No results yet", "Marks appear here as soon as the teacher enters them.")
                d.marks.forEach { m ->
                    val t = m.assessments
                    val batch = school?.batch(t?.batchId)
                    val subj = school?.subject(batch?.subjectId)
                    val frac = if (t != null && m.score != null) (m.score / t.maxMarks).toFloat() else 0f
                    AkCard(Modifier.padding(vertical = 6.dp), accent = parseHex(subj?.color)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t?.title ?: "Test", style = MaterialTheme.typography.titleLarge)
                                Text(listOfNotNull(subj?.name ?: batch?.name, t?.heldOn?.let { prettyDate(it) }).joinToString("  ·  "),
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                if (m.score != null && t != null) "${fmt(m.score)}/${fmt(t.maxMarks)}" else "—",
                                style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (m.score != null) { Gap(10.dp); Bar(frac, MaterialTheme.colorScheme.primary) }
                        m.remarks?.let { Gap(8.dp); Text("“$it”", style = MaterialTheme.typography.bodyLarge) }
                        m.answerSheetPath?.let { p ->
                            Gap(10.dp)
                            SecondaryButton("View answer sheet", { viewPath = p }, icon = Icons.Rounded.Visibility)
                        }
                    }
                }
                // ---------- per-class log
                if (d.att.isNotEmpty()) {
                    SectionTitle("Attendance log")
                    AkCard(contentPadding = PaddingValues(vertical = 8.dp, horizontal = 18.dp)) {
                        d.att.take(if (showAll) 60 else 6).forEach { a ->
                            val c = when (a.status) { Status.ABSENT -> st.absent; Status.LATE -> st.late; else -> st.present }
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(c))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(LocalDate.parse(a.date).format(DateTimeFormatter.ofPattern("EEEE, d MMM")), style = MaterialTheme.typography.titleSmall)
                                    Text(school?.batch(a.batchId)?.name ?: "Class", style = MaterialTheme.typography.bodyLarge)
                                    Text(a.status.replaceFirstChar { it.uppercase() } + times(a),
                                        style = MaterialTheme.typography.bodyMedium, color = c, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        if (d.att.size > 6) TextButton({ showAll = !showAll }) {
                            Text(if (showAll) "Show less" else "Show all ${minOf(d.att.size, 60)}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

            }
        }
    }
    viewPath?.let { SheetViewer(it) { viewPath = null } }
}

private fun times(a: Attendance): String {
    val f = DateTimeFormatter.ofPattern("h:mm a")
    val z = java.time.ZoneId.systemDefault()
    val inT = a.arrivedAt?.let { runCatching { java.time.OffsetDateTime.parse(it).atZoneSameInstant(z).format(f) }.getOrNull() }
    val outT = a.leftAt?.let { runCatching { java.time.OffsetDateTime.parse(it).atZoneSameInstant(z).format(f) }.getOrNull() }
    return when {
        inT != null && outT != null -> "  ·  $inT – $outT"
        inT != null -> "  ·  came $inT"
        else -> ""
    }
}

@Composable
private fun Legend(label: String, c: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Ring(fraction: Float, color: Color) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction, com.globalinstitute.akademos.ui.components.Motion.spring(1f, 0.9f)) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 14.dp.toPx()
            val sz = Size(size.width - w, size.height - w)
            val tl = Offset(w / 2, w / 2)
            drawArc(track, -90f, 360f, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * anim.value, false, tl, sz, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text("${(anim.value * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    val f by animateFloatAsState(if (go) fraction.coerceIn(0f, 1f) else 0f, com.globalinstitute.akademos.ui.components.Motion.spring(1f, 0.8f), label = "bar")
    Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(color))
    }
}
