package com.globalinstitute.akademos.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Sample data so the app can be explored before the server is set up.
 * Only reachable in debug builds that have no Supabase keys (see Repo.demo).
 * Everything lives in memory and disappears when the app closes.
 */
object Demo {
    var role: String = Roles.ADMIN
    private const val ADMIN_ID = "demo-admin"
    private const val TEACHER_ID = "demo-teacher"
    private const val PARENT_ID = "demo-parent"

    val profile get() = when (role) {
        Roles.TEACHER -> Profile(TEACHER_ID, "Meera Rao", Roles.TEACHER)
        Roles.PARENT -> Profile(PARENT_ID, "Sunita Sharma", Roles.PARENT)
        else -> Profile(ADMIN_ID, "Rajesh Akhtar", Roles.ADMIN)
    }

    private fun id() = UUID.randomUUID().toString()
    private suspend fun net() = delay(250)

    private val classes = mutableListOf(SchoolClass("c9", "Class 9", 0), SchoolClass("c10", "Class 10", 1), SchoolClass("c12", "Class 12", 2))
    private val subjects = mutableListOf(
        Subject("s-en", "English", "#1F7A8C"), Subject("s-sc", "Science", "#2E8B57"),
        Subject("s-bs", "Business", "#C77700"), Subject("s-ma", "Maths", "#B83280"),
    )
    private val staff = mutableListOf(
        Profile(ADMIN_ID, "Rajesh Akhtar", Roles.ADMIN), Profile(TEACHER_ID, "Meera Rao", Roles.TEACHER),
        Profile("demo-t2", "Arjun Mehta", Roles.TEACHER),
    )
    private val parents = mutableListOf(Profile(PARENT_ID, "Sunita Sharma", Roles.PARENT), Profile("demo-p2", "Vikram Verma", Roles.PARENT))

    private val names10 = listOf("Riya Sharma", "Aman Verma", "Zoya Khan", "Kabir Singh", "Ananya Iyer", "Rohan Das", "Ishita Gupta", "Farhan Ali", "Neha Patel", "Arnav Joshi")
    private val names12 = listOf("Sana Qureshi", "Dev Malhotra", "Priya Nair", "Yash Kapoor", "Tanvi Reddy", "Omar Siddiqui")
    private val names9 = listOf("Aarav Mishra", "Diya Bose", "Vihaan Chopra", "Myra Sethi")
    private val students = mutableListOf<Student>().apply {
        names10.forEachIndexed { i, n -> add(Student("st10-$i", n, "10" + "%02d".format(i + 1), "c10", "98${(10000000 + i * 7919)}")) }
        names12.forEachIndexed { i, n -> add(Student("st12-$i", n, "12" + "%02d".format(i + 1), "c12")) }
        names9.forEachIndexed { i, n -> add(Student("st9-$i", n, "9" + "%02d".format(i + 1), "c9")) }
    }
    private const val CHILD = "st10-0"

    private val batches = mutableListOf(
        Batch("b-10en", "Class 10 · English", "c10", "s-en", TEACHER_ID),
        Batch("b-10sc", "Class 10 · Science", "c10", "s-sc", "demo-t2"),
        Batch("b-12bs", "Class 12 · Business", "c12", "s-bs", TEACHER_ID),
        Batch("b-9ma", "Class 9 · Maths", "c9", "s-ma", "demo-t2"),
    )
    private val members = mutableListOf<BatchStudent>().apply {
        students.forEach { s ->
            when (s.classId) {
                "c10" -> { add(BatchStudent("b-10en", s.id!!)); if (s.id != "st10-9") add(BatchStudent("b-10sc", s.id)) }
                "c12" -> add(BatchStudent("b-12bs", s.id!!))
                "c9" -> add(BatchStudent("b-9ma", s.id!!))
            }
        }
    }

    // Timetable built around "now" so the Today screen has a live class to show.
    private val slots = mutableListOf<Slot>().apply {
        val now = LocalTime.now().withSecond(0).withNano(0)
        val today = LocalDate.now().dayOfWeek.value
        fun t(x: LocalTime) = "${x}:00"
        val liveStart = now.minusMinutes(5)
        add(Slot("sl1", "b-10en", today, t(liveStart), t(liveStart.plusMinutes(60))))
        add(Slot("sl2", "b-12bs", today, t(liveStart.plusMinutes(70)), t(liveStart.plusMinutes(130))))
        add(Slot("sl3", "b-10sc", today, t(liveStart.minusMinutes(90)), t(liveStart.minusMinutes(30))))
        listOf(1, 3, 5).filter { it != today }.forEach { d -> add(Slot(id(), "b-10en", d, "16:00:00", "17:00:00")) }
        listOf(2, 4).filter { it != today }.forEach { d -> add(Slot(id(), "b-10sc", d, "17:00:00", "18:00:00")); add(Slot(id(), "b-9ma", d, "15:00:00", "16:00:00")) }
        add(Slot(id(), "b-12bs", if (today == 6) 7 else 6, "10:00:00", "12:00:00"))
    }

    private val attendance = mutableListOf<Attendance>().apply {
        // history for the last few weeks
        val start = LocalDate.now().minusDays(1)
        for (back in 0 until 24) {
            val d = start.minusDays(back.toLong())
            if (d.dayOfWeek.value == 7) continue
            members.filter { it.batchId == "b-10en" }.forEach { m ->
                val r = (m.studentId.hashCode() + back * 31) % 11
                val st = when { r == 0 -> Status.ABSENT; r == 3 -> Status.LATE; else -> Status.PRESENT }
                add(Attendance(id(), "b-10en", m.studentId, d.toString(), st,
                    if (st == Status.ABSENT) null else d.atTime(16, if (st == Status.LATE) 18 else 2).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString(),
                    if (st == Status.ABSENT) null else d.atTime(17, 1).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString()))
            }
        }
        // the class earlier today was taken
        members.filter { it.batchId == "b-10sc" }.forEachIndexed { i, m ->
            add(Attendance(id(), "b-10sc", m.studentId, LocalDate.now().toString(), if (i == 2) Status.ABSENT else Status.PRESENT, Instant.now().toString()))
        }
    }

    private val tests = mutableListOf(
        Assessment("a1", "b-10en", "Unit Test 1", 50.0, LocalDate.now().minusDays(20).toString()),
        Assessment("a2", "b-10sc", "Chapter 3 Quiz", 25.0, LocalDate.now().minusDays(9).toString()),
        Assessment("a3", "b-10en", "Grammar Test", 30.0, LocalDate.now().minusDays(3).toString()),
    )
    private val marks = mutableListOf<Mark>().apply {
        members.filter { it.batchId == "b-10en" }.forEachIndexed { i, m ->
            add(Mark(id(), "a1", m.studentId, (32 + (i * 7) % 18).toDouble(), if (i == 0) "Very neat handwriting. Keep reading daily." else null,
                if (i == 0) "${m.studentId}/a1/sheet.jpg" else null))
            add(Mark(id(), "a3", m.studentId, (18 + (i * 5) % 12).toDouble()))
        }
        members.filter { it.batchId == "b-10sc" }.forEachIndexed { i, m -> add(Mark(id(), "a2", m.studentId, (14 + (i * 3) % 11).toDouble())) }
    }
    private val invites = mutableListOf(
        Invite("i1", Roles.TEACHER, "Meera Rao", Instant.now().minusSeconds(86400 * 30).toString(), Instant.now().minusSeconds(86400 * 28).toString(), Instant.now().minusSeconds(86400 * 29).toString()),
        Invite("i2", Roles.PARENT, "Riya's mother", Instant.now().minusSeconds(86400 * 10).toString(), Instant.now().minusSeconds(86400 * 8).toString(), Instant.now().minusSeconds(86400 * 9).toString()),
    )

    private fun myBatchIds(): Set<String> = when (role) {
        Roles.TEACHER -> batches.filter { it.teacherId == TEACHER_ID }.mapNotNull { it.id }.toSet()
        Roles.PARENT -> members.filter { it.studentId == CHILD }.map { it.batchId }.toSet()
        else -> batches.mapNotNull { it.id }.toSet()
    }
    private fun visibleStudents() = when (role) {
        Roles.PARENT -> students.filter { it.id == CHILD }
        Roles.TEACHER -> myBatchIds().let { mine -> students.filter { st -> members.any { it.studentId == st.id && it.batchId in mine } } }
        else -> students
    }

    // ------------------------------------------------------------------ API mirror of Repo
    suspend fun myProfile(): Profile? { net(); return profile }
    suspend fun school(): School { net(); val mine = myBatchIds(); return School(classes.toList(), subjects.toList(), batches.filter { it.id in mine }, slots.filter { it.batchId in mine }, if (role == Roles.PARENT) emptyList() else staff.toList()) }
    suspend fun saveClass(c: SchoolClass) { net(); if (c.id == null) classes += c.copy(id = id()) else classes.replaceAll { if (it.id == c.id) c else it } }
    suspend fun deleteClass(id: String) { classes.removeAll { it.id == id } }
    suspend fun saveSubject(s: Subject) { net(); if (s.id == null) subjects += s.copy(id = id()) else subjects.replaceAll { if (it.id == s.id) s else it } }
    suspend fun deleteSubject(id: String) { subjects.removeAll { it.id == id } }
    suspend fun students(includeInactive: Boolean): List<Student> { net(); return visibleStudents().filter { includeInactive || it.active }.sortedBy { it.fullName } }
    suspend fun saveStudent(s: Student) { net(); if (s.id == null) students += s.copy(id = id()) else students.replaceAll { if (it.id == s.id) s else it } }
    suspend fun batchStudentIds(batchId: String) = members.filter { it.batchId == batchId }.map { it.studentId }.also { net() }
    suspend fun studentBatchIds(studentId: String) = members.filter { it.studentId == studentId }.map { it.batchId }
    suspend fun saveBatch(b: Batch, studentIds: List<String>, newSlots: List<Slot>): String {
        net()
        val bid = b.id ?: id()
        if (b.id == null) batches += b.copy(id = bid) else batches.replaceAll { if (it.id == bid) b else it }
        members.removeAll { it.batchId == bid }; studentIds.distinct().forEach { members += BatchStudent(bid, it) }
        slots.removeAll { it.batchId == bid }; newSlots.forEach { slots += it.copy(id = id(), batchId = bid) }
        return bid
    }
    suspend fun deleteBatch(id: String) { batches.removeAll { it.id == id }; slots.removeAll { it.batchId == id } }

    suspend fun attendance(batchId: String, date: String) = attendance.filter { it.batchId == batchId && it.date == date && canSee(it.studentId) && it.batchId in myBatchIds() }.also { net() }
    suspend fun attendanceOn(date: String) = attendance.filter { it.date == date && canSee(it.studentId) && it.batchId in myBatchIds() }
    suspend fun saveAttendance(batchId: String, date: String, statuses: Map<String, String>, existing: List<Attendance>) {
        net()
        statuses.forEach { (sid, st) ->
            val prev = attendance.firstOrNull { it.batchId == batchId && it.studentId == sid && it.date == date }
            val arrived = if (st == Status.ABSENT) null else prev?.arrivedAt ?: Instant.now().toString()
            if (prev == null) attendance += Attendance(id(), batchId, sid, date, st, arrived)
            else attendance.replaceAll { if (it === prev) it.copy(status = st, arrivedAt = arrived) else it }
        }
    }
    suspend fun markLeft(batchId: String, date: String, ids: List<String>, left: Boolean) {
        attendance.replaceAll { if (it.batchId == batchId && it.date == date && it.studentId in ids) it.copy(leftAt = if (left) Instant.now().toString() else null) else it }
    }
    suspend fun studentAttendance(studentId: String, limit: Long) =
        attendance.filter { it.studentId == studentId && canSee(studentId) && it.batchId in myBatchIds() }.sortedByDescending { it.date }.take(limit.toInt()).also { net() }

    suspend fun assessments(batchId: String) = tests.filter { it.batchId == batchId }.sortedByDescending { it.heldOn }.also { net() }
    suspend fun createAssessment(a: Assessment): Assessment { net(); return a.copy(id = id()).also { tests += it } }
    suspend fun marks(assessmentId: String) = marks.filter { it.assessmentId == assessmentId }.also { net() }
    suspend fun saveMarks(assessmentId: String, entries: Map<String, Pair<Double?, String>>) {
        net()
        entries.forEach { (sid, v) ->
            val prev = marks.firstOrNull { it.assessmentId == assessmentId && it.studentId == sid }
            val rm = v.second.trim().ifBlank { null }
            if (prev == null) marks += Mark(id(), assessmentId, sid, v.first, rm)
            else marks.replaceAll { if (it === prev) it.copy(score = v.first, remarks = rm) else it }
        }
    }
    suspend fun uploadAnswerSheet(studentId: String, assessmentId: String, bytes: ByteArray, mime: String): String {
        delay(800)
        val path = "$studentId/$assessmentId/${id()}.jpg"
        uploaded[path] = bytes
        val prev = marks.firstOrNull { it.assessmentId == assessmentId && it.studentId == studentId }
        if (prev == null) marks += Mark(id(), assessmentId, studentId, answerSheetPath = path)
        else marks.replaceAll { if (it === prev) it.copy(answerSheetPath = path) else it }
        return path
    }
    private val uploaded = mutableMapOf<String, ByteArray>()
    suspend fun downloadAnswerSheet(path: String): ByteArray { delay(400); return uploaded[path] ?: sampleSheet() }
    suspend fun studentMarks(studentId: String): List<MarkWithTest> {
        net()
        val mine = myBatchIds()
        return marks.filter { m -> m.studentId == studentId && canSee(studentId) && tests.any { it.id == m.assessmentId && it.batchId in mine } }.map { m ->
            MarkWithTest(m.id, m.assessmentId, m.studentId, m.score, m.remarks, m.answerSheetPath, tests.firstOrNull { it.id == m.assessmentId })
        }.sortedByDescending { it.assessments?.heldOn ?: "" }
    }

    suspend fun createInvite(r: String, label: String, studentIds: List<String>, hours: Int, batchIds: List<String> = emptyList()): String {
        net()
        val a = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = java.security.SecureRandom()
        val code = (1..12).map { a[rnd.nextInt(32)] }.joinToString("")
        invites.add(0, Invite(id(), r, label, Instant.now().toString(), Instant.now().plusSeconds(hours * 3600L).toString()))
        return code.chunked(4).joinToString("-")
    }
    suspend fun invites() = invites.toList().also { net() }
    suspend fun revokeInvite(id: String) { invites.removeAll { it.id == id } }
    suspend fun people() = (staff + parents).also { net() }
    suspend fun setActive(userId: String, active: Boolean) {
        staff.replaceAll { if (it.id == userId) it.copy(active = active) else it }
        parents.replaceAll { if (it.id == userId) it.copy(active = active) else it }
    }
    suspend fun auditLog(): List<AuditEntry> {
        net()
        val now = Instant.now()
        return listOf(
            AuditEntry(5, now.minusSeconds(600).toString(), "Meera Rao", "attendance", "INSERT"),
            AuditEntry(4, now.minusSeconds(3600).toString(), "Meera Rao", "marks", "UPDATE"),
            AuditEntry(3, now.minusSeconds(86400).toString(), "Rajesh Akhtar", "students", "INSERT"),
            AuditEntry(2, now.minusSeconds(86400 * 2).toString(), "Arjun Mehta", "assessments", "INSERT"),
            AuditEntry(1, now.minusSeconds(86400 * 9).toString(), "Rajesh Akhtar", "profiles", "INSERT"),
        )
    }

    // ------------------------------------------------------------------ money
    private fun monthStart(back: Long) = LocalDate.now().withDayOfMonth(1).minusMonths(back).toString()
    private val fees = mutableListOf<StudentFee>().apply {
        students.forEach { st ->
            when (st.classId) {
                "c10" -> add(StudentFee(st.id!!, 3500.0, 10))
                "c12" -> add(StudentFee(st.id!!, 4500.0, 5))
                "c9" -> add(StudentFee(st.id!!, 3000.0, 10))
            }
        }
    }
    private val feePays = mutableListOf<FeePayment>().apply {
        for (back in 5L downTo 0L) {
            val m = monthStart(back)
            fees.forEachIndexed { i, f ->
                val skip = (i * 7 + back.toInt() * 3) % 11 == 0
                if (back == 0L) {
                    if (f.studentId == CHILD) return@forEachIndexed
                    when (i % 5) {
                        0, 1, 2 -> add(FeePayment(id(), f.studentId, m, f.monthlyFee, LocalDate.now().withDayOfMonth(minOf(3 + i % 5, LocalDate.now().dayOfMonth)).toString(), if (i % 2 == 0) "upi" else "cash"))
                        3 -> add(FeePayment(id(), f.studentId, m, f.monthlyFee / 2, LocalDate.now().withDayOfMonth(1).toString(), "cash", "Rest next week"))
                    }
                } else if (!skip) {
                    add(FeePayment(id(), f.studentId, m, f.monthlyFee, LocalDate.parse(m).plusDays((i % 9).toLong()).toString(), if (i % 3 == 0) "cash" else "upi"))
                }
            }
        }
    }
    private val payees = mutableListOf(
        Payee("p1", "Meera Rao", "English & Business", 18000.0, 1),
        Payee("p2", "Arjun Mehta", "Science & Maths", 16000.0, 1),
        Payee("p3", "Sunil Kumar", "Caretaker", 7000.0, 5),
    )
    private val salaryPays = mutableListOf<SalaryPayment>().apply {
        for (back in 5L downTo 1L) payees.forEach { p ->
            add(SalaryPayment(id(), p.id!!, monthStart(back), p.monthlySalary, LocalDate.parse(monthStart(back)).plusDays(p.payDay.toLong()).toString(), "bank"))
        }
        add(SalaryPayment(id(), "p1", monthStart(0), 18000.0, LocalDate.now().withDayOfMonth(1).toString(), "bank"))
    }

    suspend fun studentFees(): List<StudentFee> { net(); return fees.filter { canSee(it.studentId) } }
    suspend fun saveStudentFee(f: StudentFee) { net(); fees.removeAll { it.studentId == f.studentId }; fees += f }
    suspend fun feePayments(fromMonth: String) = feePays.filter { it.forMonth >= fromMonth && canSee(it.studentId) }.sortedByDescending { it.paidOn }.also { net() }
    suspend fun studentFeePayments(studentId: String) =
        feePays.filter { it.studentId == studentId && canSee(studentId) }.sortedWith(compareByDescending<FeePayment> { it.forMonth }.thenByDescending { it.paidOn }).also { net() }
    suspend fun addFeePayment(p: FeePayment) { net(); feePays += p.copy(id = id(), paidOn = p.paidOn ?: LocalDate.now().toString()) }
    suspend fun deleteFeePayment(id: String) { feePays.removeAll { it.id == id } }
    suspend fun payees() = payees.sortedBy { it.name }.also { net() }
    suspend fun savePayee(p: Payee) { net(); if (p.id == null) payees += p.copy(id = id()) else payees.replaceAll { if (it.id == p.id) p else it } }
    suspend fun salaryPayments(fromMonth: String) = salaryPays.filter { it.forMonth >= fromMonth }.sortedByDescending { it.paidOn }.also { net() }
    suspend fun addSalaryPayment(p: SalaryPayment) { net(); salaryPays += p.copy(id = id(), paidOn = p.paidOn ?: LocalDate.now().toString()) }
    suspend fun deleteSalaryPayment(id: String) { salaryPays.removeAll { it.id == id } }

    private fun canSee(studentId: String) = when (role) {
        Roles.PARENT -> studentId == CHILD
        Roles.TEACHER -> visibleStudents().any { it.id == studentId }
        else -> true
    }

    // ------------------------------------------------------------------ daily check-in
    private val dayPlans = mutableListOf(DayPlan("demo-t2", LocalDate.now().toString(), PlanMode.USUAL))
    private val dayClasses = mutableListOf<DayClass>()
    suspend fun plans(from: String, to: String): Plans {
        net()
        val mine: (String) -> Boolean = { t -> role == Roles.ADMIN || t == profile.id }
        return Plans(dayPlans.filter { it.date in from..to && mine(it.teacherId) }, dayClasses.filter { it.date in from..to && mine(it.teacherId) })
    }
    suspend fun savePlan(date: String, mode: String, list: List<DayClass>) {
        net()
        val me = profile.id
        dayPlans.removeAll { it.teacherId == me && it.date == date }; dayPlans += DayPlan(me, date, mode)
        dayClasses.removeAll { it.teacherId == me && it.date == date }
        if (mode == PlanMode.CUSTOM) list.forEach { dayClasses += it.copy(id = id(), teacherId = me, date = date) }
    }

    // ------------------------------------------------------------------ "I've paid"
    private val claims = mutableListOf(
        FeeClaim("cl1", "st10-4", monthStart(0), 3500.0, "upi", "Paid on GPay this morning", ClaimStatus.PENDING, Instant.now().minusSeconds(5400).toString()),
    )
    suspend fun feeClaims(studentId: String?) = claims.filter { (studentId == null || it.studentId == studentId) && canSee(it.studentId) && role != Roles.TEACHER }
        .sortedByDescending { it.createdAt }.also { net() }
    suspend fun addFeeClaim(c: FeeClaim) {
        net()
        if (claims.any { it.studentId == c.studentId && it.forMonth == c.forMonth && it.status == ClaimStatus.PENDING })
            error("duplicate key value violates unique constraint \"fee_claims_one_open\"")
        claims += c.copy(id = id(), status = ClaimStatus.PENDING, createdAt = Instant.now().toString())
    }
    suspend fun confirmClaim(cid: String, amount: Double?) {
        net()
        val c = claims.firstOrNull { it.id == cid && it.status == ClaimStatus.PENDING } ?: error("This was already handled")
        feePays += FeePayment(id(), c.studentId, c.forMonth, amount ?: c.amount, c.createdAt?.take(10), c.method, c.note ?: "Reported paid in the app")
        claims.replaceAll { if (it.id == cid) it.copy(status = ClaimStatus.CONFIRMED, resolvedAt = Instant.now().toString()) else it }
    }
    suspend fun rejectClaim(cid: String) {
        net()
        claims.replaceAll { if (it.id == cid) it.copy(status = ClaimStatus.REJECTED, resolvedAt = Instant.now().toString()) else it }
    }

    private val rates = mutableListOf(TeacherRate(TEACHER_ID, 500.0))
    suspend fun teacherRates() = rates.toList().also { net() }
    suspend fun saveRate(r: TeacherRate) { net(); rates.removeAll { it.teacherId == r.teacherId }; rates += r }
    suspend fun classesTaken(from: String, to: String): List<ClassTaken> {
        net()
        return attendance.filter { it.date in from..to }.groupBy { it.batchId to it.date }.map { (k, rows) ->
            ClassTaken(batches.firstOrNull { it.id == k.first }?.teacherId, k.first, k.second, rows.count { it.status != Status.ABSENT }, rows.size)
        }
    }

    suspend fun removeUser(userId: String) { net(); staff.removeAll { it.id == userId }; parents.removeAll { it.id == userId } }
    suspend fun deleteStudent(sid: String) {
        net(); students.removeAll { it.id == sid }; members.removeAll { it.studentId == sid }
        attendance.removeAll { it.studentId == sid }; marks.removeAll { it.studentId == sid }; fees.removeAll { it.studentId == sid }
    }

    /** A drawn "answer sheet" so the viewer has something to show. */
    private fun sampleSheet(): ByteArray {
        val bmp = Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(android.graphics.Color.rgb(252, 250, 244))
        val line = Paint().apply { color = android.graphics.Color.rgb(190, 205, 230); strokeWidth = 3f }
        for (y in 220 until 1600 step 70) c.drawLine(60f, y.toFloat(), 1140f, y.toFloat(), line)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(30, 50, 110); textSize = 46f }
        val red = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(200, 40, 40); textSize = 64f; isFakeBoldText = true }
        c.drawText("Unit Test 1 — English", 80f, 140f, ink.apply { textSize = 60f })
        ink.textSize = 46f
        listOf("Q1. The poem describes the river as a", "     patient teacher who shapes the stones.", "Q2. Simile: \"as brave as a lion\"",
            "Q3. The author's tone is hopeful because", "     the last line speaks of a new dawn.", "Q4. (a) were  (b) has gone  (c) will").forEachIndexed { i, s ->
            c.drawText(s, 80f, 270f + i * 140f, ink)
        }
        c.drawText("32 / 50", 860f, 140f, red)
        val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return out.toByteArray()
    }
}
