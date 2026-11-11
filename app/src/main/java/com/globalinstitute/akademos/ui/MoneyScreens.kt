package com.globalinstitute.akademos.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.globalinstitute.akademos.data.ClaimStatus
import com.globalinstitute.akademos.data.ClassTaken
import com.globalinstitute.akademos.data.TeacherRate
import com.globalinstitute.akademos.data.FeeClaim
import com.globalinstitute.akademos.data.FeePayment
import com.globalinstitute.akademos.data.PayMethod
import com.globalinstitute.akademos.data.Payee
import com.globalinstitute.akademos.data.Repo
import com.globalinstitute.akademos.data.SalaryPayment
import com.globalinstitute.akademos.data.Student
import com.globalinstitute.akademos.data.StudentFee
import com.globalinstitute.akademos.data.friendly
import com.globalinstitute.akademos.ui.components.AkCard
import com.globalinstitute.akademos.ui.components.Avatar
import com.globalinstitute.akademos.ui.components.BigField
import com.globalinstitute.akademos.ui.components.Chip
import com.globalinstitute.akademos.ui.components.EmptyBlock
import com.globalinstitute.akademos.ui.components.ErrorBlock
import com.globalinstitute.akademos.ui.components.Eyebrow
import com.globalinstitute.akademos.ui.components.Gap
import com.globalinstitute.akademos.ui.components.LoadingBlock
import com.globalinstitute.akademos.ui.components.Pill
import com.globalinstitute.akademos.ui.components.PrimaryButton
import com.globalinstitute.akademos.ui.components.ScreenHeader
import com.globalinstitute.akademos.ui.components.SecondaryButton
import com.globalinstitute.akademos.ui.components.SectionTitle
import com.globalinstitute.akademos.ui.components.rememberLoad
import com.globalinstitute.akademos.ui.theme.LocalStatus
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// ===================================================================== helpers
private val inr: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }
fun rupees(v: Double): String = inr.format(v)

private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy")
private val shortMonth = DateTimeFormatter.ofPattern("MMM")
private val shortMonthYear = DateTimeFormatter.ofPattern("MMM yyyy")
private fun shortDay(iso: String) = runCatching { LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("d MMM")) }.getOrDefault(iso)
fun YearMonth.first(): String = atDay(1).toString()
fun monthLabel(ym: YearMonth): String = ym.format(monthFmt)

enum class DueState { PAID, PARTIAL, DUE, OVERDUE, NONE }

fun dueState(expected: Double, paid: Double, month: YearMonth, dueDay: Int): DueState {
    if (expected <= 0) return if (paid > 0) DueState.PAID else DueState.NONE
    if (paid >= expected - 0.009) return DueState.PAID
    val due = month.atDay(dueDay.coerceIn(1, month.lengthOfMonth()))
    return when {
        LocalDate.now().isAfter(due) -> DueState.OVERDUE
        paid > 0 -> DueState.PARTIAL
        else -> DueState.DUE
    }
}

private fun ordinal(d: Int) = d.toString() + when {
    d in 11..13 -> "th"; d % 10 == 1 -> "st"; d % 10 == 2 -> "nd"; d % 10 == 3 -> "rd"; else -> "th"
}

@Composable
fun DuePill(state: DueState, left: Double, month: YearMonth, dueDay: Int) {
    val st = LocalStatus.current
    when (state) {
        DueState.PAID -> Pill("Paid", st.present, onColor = st.onPresent)
        DueState.PARTIAL -> Pill("${rupees(left)} left", st.late, onColor = st.onLate)
        DueState.DUE -> Pill("Due ${month.atDay(dueDay.coerceIn(1, month.lengthOfMonth())).format(DateTimeFormatter.ofPattern("d MMM"))}",
            MaterialTheme.colorScheme.surfaceVariant, onColor = MaterialTheme.colorScheme.onSurfaceVariant)
        DueState.OVERDUE -> Pill("${rupees(left)} overdue", st.absent, onColor = st.onAbsent)
        DueState.NONE -> Pill("No fee set", MaterialTheme.colorScheme.surfaceVariant, onColor = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** ‹ November 2026 › — cannot go past the current month. */
@Composable
fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    val now = YearMonth.now()
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton({ onChange(month.minusMonths(1)) }, Modifier.size(56.dp)) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", Modifier.size(34.dp))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(monthLabel(month), style = MaterialTheme.typography.titleLarge)
            if (month == now) Eyebrow("This month")
        }
        IconButton({ if (month < now) onChange(month.plusMonths(1)) }, Modifier.size(56.dp), enabled = month < now) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", Modifier.size(34.dp))
        }
    }
}

@Composable
private fun ProgressLine(fraction: Float, color: Color) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), com.globalinstitute.akademos.ui.components.Motion.spring(1f, 0.7f), label = "p")
    Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(color))
    }
}

/** The big money figure at the top of each money screen. */
@Composable
private fun MoneyHero(eyebrow: String, amount: Double, line: String, fraction: Float?, color: Color = MaterialTheme.colorScheme.onSurface) {
    AkCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Eyebrow(eyebrow)
        Gap(4.dp)
        // The figure rolls to its new value (e.g. when the month changes) instead of jumping.
        val shown by animateFloatAsState(amount.toFloat(), com.globalinstitute.akademos.ui.components.Motion.spring(1f, 0.55f), label = "amount")
        Text(rupees(Math.round(shown).toDouble()), style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp, fontFeatureSettings = "tnum"), color = color)
        Text(line, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (fraction != null) { Gap(12.dp); ProgressLine(fraction, LocalStatus.current.present) }
    }
}

// ===================================================================== fees overview
private class FeesData(val students: List<Student>, val fees: Map<String, StudentFee>, val pays: List<FeePayment>)

@Composable
fun FeesScreen() {
    val s = LocalSession.current
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(month)
    var filter by rememberSaveable { mutableStateOf("unpaid") }
    var q by remember { mutableStateOf("") }
    val load = rememberLoad(month, s.nav.stack.size) {
        FeesData(Repo.students(), Repo.studentFees().associateBy { it.studentId }, Repo.feePayments(ym.first()).filter { it.forMonth == ym.first() })
    }
    var claimTick by remember { mutableStateOf(0) }
    val claims = rememberLoad(claimTick, s.nav.stack.size) { runCatching { Repo.pendingClaims() }.getOrDefault(emptyList()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { ScreenHeader("Fees", onBack = { s.nav.pop() }); MonthSwitcher(ym, { month = it.toString() }) }
        val d = load.data
        when {
            d == null && load.error != null -> item { Box(Modifier.padding(20.dp)) { ErrorBlock(load.error, load.reload) } }
            d == null -> item { LoadingBlock() }
            d.students.isEmpty() -> item { EmptyBlock(Icons.Rounded.CurrencyRupee, "No students yet", "Add students first, then set their monthly fee here.") }
            else -> {
                val rows = d.students.map { st ->
                    val f = d.fees[st.id]
                    val paid = d.pays.filter { it.studentId == st.id }.sumOf { it.amount }
                    val exp = f?.monthlyFee ?: 0.0
                    Triple(st, paid, dueState(exp, paid, ym, f?.dueDay ?: 10))
                }
                val expected = d.fees.values.filter { f -> d.students.any { it.id == f.studentId } }.sumOf { it.monthlyFee }
                val collected = d.pays.sumOf { it.amount }
                val unpaidCount = rows.count { it.third == DueState.OVERDUE || it.third == DueState.DUE || it.third == DueState.PARTIAL }
                val waiting = claims.data.orEmpty()
                if (waiting.isNotEmpty()) item(key = "claims") {
                    ClaimsCard(waiting, d.students.associateBy { it.id }) { claimTick++; load.reload() }
                }
                item {
                    MoneyHero("Collected for ${ym.format(shortMonth)}", collected,
                        if (expected > collected) "of ${rupees(expected)} · ${rupees(expected - collected)} still to come" else "Everyone has paid",
                        if (expected > 0) (collected / expected).toFloat() else null)
                }
                val noFee = rows.count { it.third == DueState.NONE }
                if (noFee > 0) item {
                    Text("$noFee ${if (noFee == 1) "student has" else "students have"} no monthly fee set. Tap a name to set it.",
                        Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(filter == "unpaid", "Not paid ($unpaidCount)", { filter = "unpaid" })
                        Chip(filter == "paid", "Paid", { filter = "paid" })
                        Chip(filter == "all", "Everyone", { filter = "all" })
                    }
                    if (d.students.size > 12) BigField(q, { q = it }, "Find a student", Modifier.padding(horizontal = 20.dp), leading = Icons.Rounded.Search)
                }
                val shown = rows.filter { (st, _, state) ->
                    (q.isBlank() || st.fullName.contains(q, true)) && when (filter) {
                        "unpaid" -> state == DueState.OVERDUE || state == DueState.DUE || state == DueState.PARTIAL
                        "paid" -> state == DueState.PAID
                        else -> true
                    }
                }.sortedWith(compareBy({ it.third.ordinal.let { o -> if (o == DueState.OVERDUE.ordinal) -1 else o } }, { it.first.fullName }))
                if (shown.isEmpty()) item {
                    EmptyBlock(Icons.Rounded.CurrencyRupee, if (filter == "unpaid") "All paid" else "Nobody here",
                        if (filter == "unpaid") "Everyone has paid for ${monthLabel(ym)}." else "No students match.")
                }
                items(shown, key = { it.first.id!! }) { (st, paid, state) ->
                    val f = d.fees[st.id]
                    StudentRow(st, trailing = {
                        Column(horizontalAlignment = Alignment.End) {
                            DuePill(state, (f?.monthlyFee ?: 0.0) - paid, ym, f?.dueDay ?: 10)
                            if (f != null) Text(rupees(f.monthlyFee) + " / month", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }) { s.nav.push(Screen.StudentFees(st.id!!)) }
                }
            }
        }
    }
}

// ===================================================================== one student's fees
private class StudentFeeData(val student: Student?, val fee: StudentFee?, val pays: List<FeePayment>)

@Composable
fun StudentFeesScreen(studentId: String) {
    val s = LocalSession.current
    val scope = rememberCoroutineScope()
    val load = rememberLoad(studentId) {
        StudentFeeData(Repo.students(includeInactive = true).firstOrNull { it.id == studentId },
            Repo.studentFees().firstOrNull { it.studentId == studentId }, Repo.studentFeePayments(studentId))
    }
    var feeText by remember { mutableStateOf("") }
    var dueText by remember { mutableStateOf("10") }
    var ready by remember { mutableStateOf(false) }
    var savingFee by remember { mutableStateOf(false) }
    var feeMsg by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<FeePayment?>(null) }
    val d = load.data
    LaunchedEffect(d) {
        if (d != null && !ready) { d.fee?.let { feeText = fmtMoney(it.monthlyFee); dueText = it.dueDay.toString() }; ready = true }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { ScreenHeader(d?.student?.fullName ?: "Fees", s.school?.klass(d?.student?.classId)?.name, onBack = { s.nav.pop() }) }
        when {
            d == null && load.error != null -> item { Box(Modifier.padding(20.dp)) { ErrorBlock(load.error, load.reload) } }
            d == null -> item { LoadingBlock() }
            else -> {
                val ym = YearMonth.now()
                val paidNow = d.pays.filter { it.forMonth == ym.first() }.sumOf { it.amount }
                val fee = d.fee?.monthlyFee ?: 0.0
                val state = dueState(fee, paidNow, ym, d.fee?.dueDay ?: 10)
                item {
                    AkCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Eyebrow(monthLabel(ym))
                                Text(if (fee > 0) "${rupees(paidNow)} of ${rupees(fee)}" else "No fee set",
                                    style = MaterialTheme.typography.headlineSmall)
                            }
                            DuePill(state, fee - paidNow, ym, d.fee?.dueDay ?: 10)
                        }
                        Gap(14.dp)
                        if (state == DueState.PAID) SecondaryButton("Record another payment", { recording = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Payments)
                        else PrimaryButton("Record payment", { recording = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Payments)
                    }
                }
                item {
                    AkCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        Text("Monthly fee", style = MaterialTheme.typography.titleLarge)
                        Gap(10.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BigField(feeText, { feeText = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, "Amount (₹)",
                                Modifier.weight(1.4f), keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            BigField(dueText, { dueText = it.filter { c -> c.isDigit() }.take(2) }, "Due day",
                                Modifier.weight(1f), keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        Text("The fee is due on the ${ordinal(dueText.toIntOrNull()?.coerceIn(1, 28) ?: 10)} of every month.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        feeMsg?.let { Gap(6.dp); Text(it, color = MaterialTheme.colorScheme.error) }
                        Gap(10.dp)
                        SecondaryButton("Save fee", modifier = Modifier.fillMaxWidth(), onClick = {
                            val amt = feeText.toDoubleOrNull(); val day = dueText.toIntOrNull()
                            if (amt == null || day == null || day !in 1..28) { feeMsg = "Enter an amount and a due day from 1 to 28."; return@SecondaryButton }
                            savingFee = true; feeMsg = null
                            scope.launch {
                                runCatching { Repo.saveStudentFee(StudentFee(studentId, amt, day)) }
                                    .onSuccess { load.reload() }.onFailure { feeMsg = it.friendly() }
                                savingFee = false
                            }
                        }, enabled = !savingFee)
                    }
                }
                item { SectionTitle("Payments", Modifier.padding(horizontal = 20.dp)) }
                if (d.pays.isEmpty()) item {
                    Text("No payments recorded yet.", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(d.pays, key = { it.id ?: it.hashCode().toString() }) { p ->
                    PaymentRow(YearMonth.parse(p.forMonth.take(7)), p.amount, p.paidOn, p.method, p.note) { deleting = p }
                }
            }
        }
    }

    if (recording && d != null) {
        val fee = d.fee?.monthlyFee ?: 0.0
        RecordPaymentDialog("Fee payment", d.student?.fullName ?: "",
            suggest = { m -> (fee - d.pays.filter { it.forMonth == m.first() }.sumOf { it.amount }).coerceAtLeast(0.0) },
            defaultMethod = "cash", onDismiss = { recording = false }) { m, amt, method, note ->
            Repo.addFeePayment(FeePayment(studentId = studentId, forMonth = m.first(), amount = amt, method = method, note = note))
            recording = false; load.reload()
        }
    }
    deleting?.let { p ->
        ConfirmDelete("Delete this payment?", "${rupees(p.amount)} for ${monthLabel(YearMonth.parse(p.forMonth.take(7)))} will be removed. This is recorded in the activity log.",
            onDismiss = { deleting = null }) {
            Repo.deleteFeePayment(p.id!!); deleting = null; load.reload()
        }
    }
}

@Composable
private fun PaymentRow(month: YearMonth, amount: Double, paidOn: String?, method: String, note: String?, onDelete: (() -> Unit)?) {
    AkCard(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), contentPadding = PaddingValues(start = 20.dp, top = 14.dp, bottom = 14.dp, end = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rupees(amount), style = MaterialTheme.typography.titleLarge)
                Text("For ${month.format(shortMonthYear)}  ·  ${PayMethod.label(method)}" + (paidOn?.let { "  ·  paid ${shortDay(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            if (onDelete != null) IconButton(onDelete, Modifier.size(52.dp)) {
                Icon(Icons.Rounded.Delete, "Delete payment", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ===================================================================== salaries
private class SalaryData(val payees: List<Payee>, val pays: List<SalaryPayment>)

@Composable
fun SalariesScreen() {
    val s = LocalSession.current
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(month)
    var editing by remember { mutableStateOf<Payee?>(null) }
    var paying by remember { mutableStateOf<Payee?>(null) }
    var deleting by remember { mutableStateOf<SalaryPayment?>(null) }
    val load = rememberLoad(month) { SalaryData(Repo.payees(), Repo.salaryPayments(ym.first()).filter { it.forMonth == ym.first() }) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            item { ScreenHeader("Salaries", "Only you can see this", onBack = { s.nav.pop() }); MonthSwitcher(ym, { month = it.toString() }) }
            val d = load.data
            when {
                d == null && load.error != null -> item { Box(Modifier.padding(20.dp)) { ErrorBlock(load.error, load.reload) } }
                d == null -> item { LoadingBlock() }
                d.payees.isEmpty() -> item { EmptyBlock(Icons.Rounded.Payments, "Nobody added yet", "Add each teacher or staff member with their monthly salary and pay day.") }
                else -> {
                    val active = d.payees.filter { it.active }
                    val total = active.sumOf { it.monthlySalary }
                    val paid = d.pays.sumOf { it.amount }
                    item {
                        MoneyHero("Paid for ${ym.format(shortMonth)}", paid,
                            if (total > paid) "of ${rupees(total)} · ${rupees(total - paid)} still to pay" else "All salaries paid",
                            if (total > 0) (paid / total).toFloat() else null)
                    }
                    items(d.payees.sortedBy { !it.active }, key = { it.id!! }) { p ->
                        val got = d.pays.filter { it.payeeId == p.id }
                        val sum = got.sumOf { it.amount }
                        val state = if (!p.active) DueState.NONE else dueState(p.monthlySalary, sum, ym, p.payDay)
                        AkCard(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).animateContentSize(), onClick = { paying = p }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(p.name, 48.dp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                                    Text(listOfNotNull(p.roleLabel, "${rupees(p.monthlySalary)} on the ${ordinal(p.payDay)}").joinToString("  ·  "),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton({ editing = p }, Modifier.size(48.dp)) { Icon(Icons.Rounded.Edit, "Edit ${p.name}") }
                            }
                            Gap(8.dp)
                            if (!p.active) Pill("Not working here now", MaterialTheme.colorScheme.surfaceVariant, onColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            else Row(verticalAlignment = Alignment.CenterVertically) {
                                when (state) {
                                    DueState.PAID -> Pill("Paid", LocalStatus.current.present, onColor = LocalStatus.current.onPresent)
                                    DueState.PARTIAL -> Pill("${rupees(p.monthlySalary - sum)} left", LocalStatus.current.late, onColor = LocalStatus.current.onLate)
                                    DueState.OVERDUE -> Pill("${rupees(p.monthlySalary - sum)} overdue", LocalStatus.current.absent, onColor = LocalStatus.current.onAbsent)
                                    else -> Pill("Due on the ${ordinal(p.payDay)}", MaterialTheme.colorScheme.surfaceVariant, onColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.weight(1f))
                                if (state != DueState.PAID) Text("Tap to pay", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            got.forEach { g ->
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${rupees(g.amount)}  ·  ${PayMethod.label(g.method)}" + (g.paidOn?.let { "  ·  paid ${shortDay(it)}" } ?: ""),
                                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton({ deleting = g }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.navigationBarsPadding().padding(16.dp)) {
            PrimaryButton("Add a person", { editing = Payee(name = "", monthlySalary = 0.0) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        }
    }

    editing?.let { p -> PayeeDialog(p, onDismiss = { editing = null }) { Repo.savePayee(it); editing = null; load.reload() } }
    paying?.let { p ->
        val d = load.data
        RecordPaymentDialog("Salary payment", p.name, initialMonth = ym,
            suggest = { m -> if (m == ym && d != null) (p.monthlySalary - d.pays.filter { it.payeeId == p.id }.sumOf { it.amount }).coerceAtLeast(0.0) else p.monthlySalary },
            defaultMethod = "bank", onDismiss = { paying = null }) { m, amt, method, note ->
            Repo.addSalaryPayment(SalaryPayment(payeeId = p.id!!, forMonth = m.first(), amount = amt, method = method, note = note))
            paying = null; load.reload()
        }
    }
    deleting?.let { g ->
        ConfirmDelete("Delete this salary payment?", "${rupees(g.amount)} will be removed. This is recorded in the activity log.", onDismiss = { deleting = null }) {
            Repo.deleteSalaryPayment(g.id!!); deleting = null; load.reload()
        }
    }
}

@Composable
private fun PayeeDialog(p: Payee, onDismiss: () -> Unit, onSave: suspend (Payee) -> Unit) {
    val s = LocalSession.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(p.name) }
    var role by remember { mutableStateOf(p.roleLabel ?: "") }
    var salary by remember { mutableStateOf(if (p.monthlySalary > 0) fmtMoney(p.monthlySalary) else "") }
    var day by remember { mutableStateOf(p.payDay.toString()) }
    var active by remember { mutableStateOf(p.active) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    com.globalinstitute.akademos.ui.components.AkDialog(onDismissRequest = onDismiss,
        title = { Text(if (p.id == null) "Add a person" else "Edit ${p.name}", style = MaterialTheme.typography.headlineSmall) },
        text = {
            LazyColumn {
                item {
                    if (p.id == null) {
                        val teachers = s.school?.staff.orEmpty().filter { t -> !t.isAdmin }
                        if (teachers.isNotEmpty()) {
                            Text("Pick a teacher or type a name", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Gap(6.dp)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(teachers) { t -> Chip(name == t.fullName, t.fullName, { name = t.fullName; if (role.isBlank()) role = "Teacher" }) }
                            }
                            Gap(10.dp)
                        }
                    }
                    BigField(name, { name = it.take(80) }, "Name", keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    Gap(8.dp)
                    BigField(role, { role = it.take(40) }, "Work (e.g. Maths teacher)", keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    Gap(8.dp)
                    BigField(salary, { salary = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, "Monthly salary (₹)", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Gap(8.dp)
                    BigField(day, { day = it.filter { c -> c.isDigit() }.take(2) }, "Pay day (1–28)", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                    if (p.id != null) {
                        Gap(10.dp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Still working here", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Switch(active, { active = it })
                        }
                    }
                    err?.let { Gap(); Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            PrimaryButton("Save", loading = busy, enabled = name.trim().length >= 2 && salary.toDoubleOrNull() != null, onClick = {
                val dd = day.toIntOrNull()
                if (dd == null || dd !in 1..28) { err = "Pay day must be from 1 to 28."; return@PrimaryButton }
                busy = true
                scope.launch {
                    runCatching { onSave(p.copy(name = name.trim(), roleLabel = role.trim().ifBlank { null }, monthlySalary = salary.toDouble(), payDay = dd, active = active)) }
                        .onFailure { err = it.friendly() }
                    busy = false
                }
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

// ===================================================================== shared dialogs
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordPaymentDialog(
    title: String, who: String, suggest: (YearMonth) -> Double, defaultMethod: String,
    initialMonth: YearMonth = YearMonth.now(), onDismiss: () -> Unit,
    onSave: suspend (YearMonth, Double, String, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var month by remember { mutableStateOf(initialMonth) }
    var amount by remember { mutableStateOf(suggest(initialMonth).let { if (it > 0) fmtMoney(it) else "" }) }
    var method by remember { mutableStateOf(defaultMethod) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    com.globalinstitute.akademos.ui.components.AkDialog(onDismissRequest = onDismiss,
        title = { Column { Eyebrow(title); Text(who, style = MaterialTheme.typography.headlineSmall) } },
        text = {
            LazyColumn {
                item {
                    Text("For which month?", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ month = month.minusMonths(1); amount = suggest(month).let { if (it > 0) fmtMoney(it) else amount } }) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month")
                        }
                        Text(monthLabel(month), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        IconButton({ month = month.plusMonths(1); amount = suggest(month).let { if (it > 0) fmtMoney(it) else amount } },
                            enabled = month < YearMonth.now().plusMonths(1)) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month")
                        }
                    }
                    BigField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, "Amount (₹)",
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Gap(10.dp)
                    Text("Paid by", style = MaterialTheme.typography.titleSmall)
                    Gap(6.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PayMethod.all.forEach { m -> Chip(method == m, PayMethod.label(m), { method = m }) }
                    }
                    Gap(10.dp)
                    BigField(note, { note = it.take(200) }, "Note (optional)")
                    err?.let { Gap(); Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            PrimaryButton("Record payment", loading = busy, enabled = (amount.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                busy = true
                scope.launch {
                    runCatching { onSave(month, amount.toDouble(), method, note.trim().ifBlank { null }) }.onFailure { err = it.friendly() }
                    busy = false
                }
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
private fun ConfirmDelete(title: String, body: String, onDismiss: () -> Unit, onConfirm: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var err by remember { mutableStateOf<String?>(null) }
    com.globalinstitute.akademos.ui.components.AkDialog(onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column { Text(body, style = MaterialTheme.typography.bodyLarge); err?.let { Gap(); Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton({ scope.launch { runCatching { onConfirm() }.onFailure { err = it.friendly() } } }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onDismiss) { Text("Keep") } })
}

private fun fmtMoney(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(v)

// ===================================================================== earnings
private class EarnData(val fees: List<StudentFee>, val students: List<Student>, val feePays: List<FeePayment>, val payees: List<Payee>, val salPays: List<SalaryPayment>)

@Composable
fun EarningsScreen() {
    val s = LocalSession.current
    val st = LocalStatus.current
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(month)
    val from = YearMonth.now().minusMonths(11)
    val load = rememberLoad(Unit) {
        EarnData(Repo.studentFees(), Repo.students(), Repo.feePayments(from.first()), Repo.payees(), Repo.salaryPayments(from.first()))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { ScreenHeader("Earnings", "Fees collected minus salaries paid", onBack = { s.nav.pop() }); MonthSwitcher(ym, { month = it.toString() }) }
        val d = load.data
        when {
            d == null && load.error != null -> item { Box(Modifier.padding(20.dp)) { ErrorBlock(load.error, load.reload) } }
            d == null -> item { LoadingBlock() }
            else -> {
                fun feesIn(m: YearMonth) = d.feePays.filter { it.forMonth == m.first() }.sumOf { it.amount }
                fun salIn(m: YearMonth) = d.salPays.filter { it.forMonth == m.first() }.sumOf { it.amount }
                val activeIds = d.students.mapNotNull { it.id }.toSet()
                val expFees = d.fees.filter { it.studentId in activeIds }.sumOf { it.monthlyFee }
                val expSal = d.payees.filter { it.active }.sumOf { it.monthlySalary }
                val fees = feesIn(ym); val sal = salIn(ym); val net = fees - sal
                item {
                    // The one big number: what the institute kept this month.
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primary) {
                        Column(Modifier.padding(22.dp)) {
                            Eyebrow("Kept in ${ym.format(monthFmt)}", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                            Gap(6.dp)
                            Text((if (net < 0) "−" else "") + rupees(kotlin.math.abs(net)), style = MaterialTheme.typography.displayLarge,
                                color = if (net < 0) Color(0xFFFFB4A6) else MaterialTheme.colorScheme.onPrimary)
                            Gap(4.dp)
                            Text(if (net < 0) "Salaries paid were more than fees collected." else "after paying salaries",
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                        }
                    }
                }
                item {
                    AkCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        MoneyLine("Fees collected", fees, st.present, if (ym == YearMonth.now() && expFees > fees) "${rupees(expFees - fees)} not paid yet" else null)
                        Gap(14.dp)
                        MoneyLine("Salaries paid", sal, MaterialTheme.colorScheme.primary, if (ym == YearMonth.now() && expSal > sal) "${rupees(expSal - sal)} still to pay" else null)
                        if (ym == YearMonth.now()) {
                            Gap(14.dp)
                            Text("If every fee and salary is paid: ${rupees(expFees - expSal)}", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { SectionTitle("Last 6 months", Modifier.padding(horizontal = 20.dp)) }
                val months = (0L..5L).map { YearMonth.now().minusMonths(it) }
                val maxV = months.maxOf { maxOf(feesIn(it), salIn(it)) }.coerceAtLeast(1.0)
                items(months, key = { it.toString() }) { m ->
                    val f = feesIn(m); val sl = salIn(m); val n = f - sl
                    AkCard(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), onClick = { month = m.toString() },
                        color = if (m == ym) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.format(monthFmt), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text((if (n < 0) "−" else "") + rupees(kotlin.math.abs(n)), style = MaterialTheme.typography.titleLarge,
                                color = if (n < 0) st.absent else MaterialTheme.colorScheme.onSurface)
                        }
                        Gap(8.dp)
                        BarRow("Fees", f, (f / maxV).toFloat(), st.present)
                        Gap(4.dp)
                        BarRow("Salaries", sl, (sl / maxV).toFloat(), MaterialTheme.colorScheme.primary)
                    }
                }
                item {
                    Text("Each payment counts in the month it is for, not the day it was paid.",
                        Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MoneyLine(label: String, amount: Double, color: Color, sub: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            sub?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(rupees(amount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BarRow(label: String, amount: Double, fraction: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(76.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f)) { ProgressLine(fraction, color) }
        Text(rupees(amount), Modifier.width(92.dp), style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

// ===================================================================== parent: fee card
@Composable
fun ParentFeeCard(studentId: String, name: String) {
    val s = LocalSession.current
    val st = LocalStatus.current
    var tick by remember { mutableStateOf(0) }
    val load = rememberLoad(studentId, tick) {
        Triple(runCatching { Repo.studentFees().firstOrNull { it.studentId == studentId } }.getOrNull(),
            runCatching { Repo.studentFeePayments(studentId) }.getOrDefault(emptyList()),
            runCatching { Repo.feeClaims(studentId) }.getOrDefault(emptyList()))
    }
    var asking by remember { mutableStateOf(false) }
    val d = load.data ?: return
    val fee = d.first ?: return
    if (fee.monthlyFee <= 0) return
    val ym = YearMonth.now()
    val paid = d.second.filter { it.forMonth == ym.first() }.sumOf { it.amount }
    val left = fee.monthlyFee - paid
    val state = dueState(fee.monthlyFee, paid, ym, fee.dueDay)
    val due = ym.atDay(fee.dueDay.coerceIn(1, ym.lengthOfMonth())).format(DateTimeFormatter.ofPattern("d MMMM"))
    val claims = d.third.filter { it.forMonth == ym.first() }
    val waiting = claims.firstOrNull { it.status == ClaimStatus.PENDING }
    val refused = claims.firstOrNull()?.takeIf { it.status == ClaimStatus.REJECTED && left > 0.009 }
    // "Yes, I've paid" on the reminder brings us here.
    LaunchedEffect(s.nav.claimFor) {
        if (s.nav.claimFor == studentId) { s.nav.claimFor = null; if (waiting == null && left > 0.009) asking = true }
    }

    AkCard(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Fee for ${monthLabel(ym)}")
                Text(rupees(fee.monthlyFee), style = MaterialTheme.typography.headlineSmall)
            }
            if (waiting != null) Pill("Checking", st.late) else DuePill(state, left, ym, fee.dueDay)
        }
        Gap(6.dp)
        Text(when {
            waiting != null -> "You said you paid ${rupees(waiting.amount)} by ${PayMethod.label(waiting.method)}. The office will confirm it soon."
            refused != null -> "The office hasn't received your payment of ${rupees(refused.amount)} yet. Please check with them. ${rupees(left)} is still due."
            state == DueState.PAID -> "Paid. Thank you!"
            state == DueState.PARTIAL -> "${rupees(paid)} paid. Please pay ${rupees(left)} more by $due."
            state == DueState.OVERDUE -> "Please pay ${rupees(left)}. It was due on $due."
            else -> "Please pay by $due."
        }, style = MaterialTheme.typography.bodyLarge,
            color = if (waiting == null && (state == DueState.OVERDUE || refused != null)) st.absent else MaterialTheme.colorScheme.onSurfaceVariant)
        if (waiting == null && left > 0.009) {
            Gap(12.dp)
            PrimaryButton("I've paid", { asking = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.CheckCircle)
        }
        val history = d.second.take(4)
        if (history.isNotEmpty()) {
            Gap(12.dp)
            Text("Recent payments", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            history.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(monthLabel(YearMonth.parse(p.forMonth.take(7))), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(rupees(p.amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (asking) ClaimDialog(name, left, ym, { asking = false }) { amount, method, note ->
        Repo.addFeeClaim(FeeClaim(null, studentId, ym.first(), amount, method, note))
        asking = false; tick++
    }
}

/** "How much did you pay, and how?" Sent to the office, who confirm it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClaimDialog(name: String, left: Double, ym: YearMonth, onDismiss: () -> Unit, onSend: suspend (Double, String, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf(fmtMoney(left)) }
    var method by remember { mutableStateOf("upi") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    com.globalinstitute.akademos.ui.components.AkDialog(onDismissRequest = onDismiss,
        title = { Column { Eyebrow("${name.substringBefore(' ')}'s fee · ${monthLabel(ym)}"); Text("Tell the office you've paid", style = MaterialTheme.typography.headlineSmall) } },
        text = {
            LazyColumn {
                item {
                    BigField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(9) }, "How much did you pay? (₹)",
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Gap(10.dp)
                    Text("How did you pay?", style = MaterialTheme.typography.titleSmall)
                    Gap(6.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PayMethod.all.forEach { m -> Chip(method == m, PayMethod.label(m), { method = m }) }
                    }
                    Gap(10.dp)
                    BigField(note, { note = it.take(200) }, "Anything to add? (optional)")
                    Gap(6.dp)
                    Text("It shows as paid once the office confirms it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    err?.let { Gap(); Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            PrimaryButton("Send", loading = busy, enabled = (amount.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                busy = true
                scope.launch {
                    runCatching { onSend(amount.toDouble(), method, note.trim().ifBlank { null }) }.onFailure { err = it.friendly() }
                    busy = false
                }
            })
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

/** Administrator: people who say they've paid, waiting for a yes or no. */
@Composable
private fun ClaimsCard(claims: List<FeeClaim>, students: Map<String?, Student>, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val st = LocalStatus.current
    var busy by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    AkCard(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.surface) {
        Eyebrow("Say they've paid · please check", color = MaterialTheme.colorScheme.onSurface)
        claims.forEach { c ->
            val who = students[c.studentId]?.fullName ?: "A student"
            Gap(10.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(who, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(who, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    val cm = YearMonth.parse(c.forMonth.take(7))
                    Text("${rupees(c.amount)} by ${PayMethod.label(c.method)}" + (if (cm != YearMonth.now()) " · for ${monthLabel(cm)}" else "") +
                        (c.createdAt?.let { " · told you ${shortDay(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    c.note?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)) }
                }
            }
            Gap(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Received", modifier = Modifier.weight(1f), loading = busy == c.id + "y", color = st.present, contentColor = st.onPresent, onClick = {
                    busy = c.id + "y"; err = null
                    scope.launch { runCatching { Repo.confirmClaim(c.id!!) }.onSuccess { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm); onDone() }.onFailure { err = it.friendly() }; busy = null }
                })
                SecondaryButton("Not received", {
                    busy = c.id + "n"; err = null
                    scope.launch { runCatching { Repo.rejectClaim(c.id!!) }.onSuccess { onDone() }.onFailure { err = it.friendly() }; busy = null }
                }, Modifier.weight(1f), enabled = busy == null)
            }
        }
        err?.let { Gap(6.dp); Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

// ===================================================================== teachers' classes and pay
private class PayData(val taken: List<ClassTaken>, val rates: Map<String, Double>)

/** How many classes each teacher actually took this month (attendance saved), and what that comes to. */
@Composable
fun TeacherPayScreen() {
    val s = LocalSession.current
    val sc = s.school
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(month)
    var tick by remember { mutableStateOf(0) }
    val load = rememberLoad(month, tick) {
        PayData(Repo.classesTaken(ym.first(), ym.atEndOfMonth().toString()),
            runCatching { Repo.teacherRates() }.getOrDefault(emptyList()).associate { it.teacherId to it.perClass })
    }
    var editing by remember { mutableStateOf<com.globalinstitute.akademos.data.Profile?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item { ScreenHeader("Teachers' classes", "Counted when attendance is saved", onBack = { s.nav.pop() }); MonthSwitcher(ym, { month = it.toString() }) }
        val d = load.data
        when {
            d == null && load.error != null -> item { Box(Modifier.padding(20.dp)) { ErrorBlock(load.error, load.reload) } }
            d == null || sc == null -> item { LoadingBlock() }
            else -> {
                val byTeacher = d.taken.groupBy { it.teacherId }
                val people = sc.staff.filter { p -> p.active && (p.role == com.globalinstitute.akademos.data.Roles.TEACHER || byTeacher.containsKey(p.id)) }
                val total = people.sumOf { (byTeacher[it.id]?.size ?: 0) * (d.rates[it.id] ?: 0.0) }
                item {
                    MoneyHero("To pay for ${ym.format(shortMonth)}", total,
                        "${d.taken.size} ${if (d.taken.size == 1) "class" else "classes"} taken in all", null)
                }
                if (people.isEmpty()) item { EmptyBlock(Icons.Rounded.Payments, "No teachers yet", "Teachers appear here once they've joined with their key.") }
                items(people, key = { it.id }) { p ->
                    val mine = byTeacher[p.id].orEmpty()
                    val rate = d.rates[p.id]
                    AkCard(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), onClick = { editing = p }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(p.fullName, 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.fullName, style = MaterialTheme.typography.titleMedium)
                                Text("${mine.size} ${if (mine.size == 1) "class" else "classes"}" + (rate?.let { " × ${rupees(it)}" } ?: ""),
                                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (rate != null) Text(rupees(mine.size * rate), style = MaterialTheme.typography.titleLarge)
                            else Pill("Set rate", MaterialTheme.colorScheme.primary, onColor = MaterialTheme.colorScheme.onPrimary)
                        }
                        mine.groupBy { it.batchId }.forEach { (bid, list) ->
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text(sc.batch(bid)?.name ?: "Class", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${list.size}", style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                            }
                        }
                    }
                }
                val unassigned = byTeacher[null].orEmpty()
                if (unassigned.isNotEmpty()) item {
                    Text("${unassigned.size} classes were taken in batches with no teacher set. Set a teacher in Admin → Batches & timetable.",
                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    editing?.let { p ->
        val scope = rememberCoroutineScope()
        var amount by remember(p.id) { mutableStateOf(load.data?.rates?.get(p.id)?.let { fmtMoney(it) } ?: "") }
        var err by remember(p.id) { mutableStateOf<String?>(null) }
        com.globalinstitute.akademos.ui.components.AkDialog(onDismissRequest = { editing = null },
            title = { Column { Eyebrow("Pay per class"); Text(p.fullName, style = MaterialTheme.typography.headlineSmall) } },
            text = {
                Column {
                    BigField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(8) }, "₹ for each class",
                        keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    err?.let { Gap(); Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                PrimaryButton("Save", enabled = amount.toDoubleOrNull() != null, onClick = {
                    scope.launch {
                        runCatching { Repo.saveRate(TeacherRate(p.id, amount.toDouble())) }
                            .onSuccess { editing = null; tick++ }.onFailure { err = it.friendly() }
                    }
                })
            },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } })
    }
}
