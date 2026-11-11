package com.globalinstitute.akademos.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object Roles {
    const val ADMIN = "admin"
    const val TEACHER = "teacher"
    const val PARENT = "parent"
}

object Status {
    const val PRESENT = "present"
    const val ABSENT = "absent"
    const val LATE = "late"
}

@Serializable
data class Profile(
    val id: String,
    @SerialName("full_name") val fullName: String,
    val role: String,
    val active: Boolean = true,
) {
    val isStaff get() = role == Roles.ADMIN || role == Roles.TEACHER
    val isAdmin get() = role == Roles.ADMIN
}

@Serializable
data class SchoolClass(
    val id: String? = null,
    val name: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class Subject(
    val id: String? = null,
    val name: String,
    val color: String = "#5B3E8C",
)

@Serializable
data class Student(
    val id: String? = null,
    @SerialName("full_name") val fullName: String,
    @SerialName("roll_no") val rollNo: String? = null,
    @SerialName("class_id") val classId: String? = null,
    @SerialName("guardian_phone") val guardianPhone: String? = null,
    val active: Boolean = true,
)

@Serializable
data class Batch(
    val id: String? = null,
    val name: String,
    @SerialName("class_id") val classId: String? = null,
    @SerialName("subject_id") val subjectId: String? = null,
    @SerialName("teacher_id") val teacherId: String? = null,
)

@Serializable
data class BatchStudent(
    @SerialName("batch_id") val batchId: String,
    @SerialName("student_id") val studentId: String,
)

@Serializable
data class Slot(
    val id: String? = null,
    @SerialName("batch_id") val batchId: String,
    val weekday: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
)

@Serializable
data class Attendance(
    val id: String? = null,
    @SerialName("batch_id") val batchId: String,
    @SerialName("student_id") val studentId: String,
    val date: String,
    val status: String,
    @SerialName("arrived_at") val arrivedAt: String? = null,
    @SerialName("left_at") val leftAt: String? = null,
)

@Serializable
data class Assessment(
    val id: String? = null,
    @SerialName("batch_id") val batchId: String,
    val title: String,
    @SerialName("max_marks") val maxMarks: Double,
    @SerialName("held_on") val heldOn: String? = null,
)

@Serializable
data class Mark(
    val id: String? = null,
    @SerialName("assessment_id") val assessmentId: String,
    @SerialName("student_id") val studentId: String,
    val score: Double? = null,
    val remarks: String? = null,
    @SerialName("answer_sheet_path") val answerSheetPath: String? = null,
)

@Serializable
data class MarkWithTest(
    val id: String? = null,
    @SerialName("assessment_id") val assessmentId: String,
    @SerialName("student_id") val studentId: String,
    val score: Double? = null,
    val remarks: String? = null,
    @SerialName("answer_sheet_path") val answerSheetPath: String? = null,
    val assessments: Assessment? = null,
)

@Serializable
data class Invite(
    val id: String,
    val role: String,
    val label: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("used_at") val usedAt: String? = null,
)

@Serializable
data class AuditEntry(
    val id: Long,
    val at: String,
    @SerialName("actor_name") val actorName: String? = null,
    @SerialName("table_name") val tableName: String,
    val action: String,
)

@Serializable
data class RedeemResult(val ok: Boolean, val role: String? = null, val error: String? = null)

/** Everything the staff screens need about the timetable, loaded in one go. */
data class School(
    val classes: List<SchoolClass>,
    val subjects: List<Subject>,
    val batches: List<Batch>,
    val slots: List<Slot>,
    val staff: List<Profile>,
) {
    fun subject(id: String?) = subjects.firstOrNull { it.id == id }
    fun klass(id: String?) = classes.firstOrNull { it.id == id }
    fun batch(id: String?) = batches.firstOrNull { it.id == id }
    fun person(id: String?) = staff.firstOrNull { it.id == id }
}

// ------------------------------------------------------------------ money
object PayMethod {
    val all = listOf("cash", "upi", "bank", "cheque", "other")
    fun label(m: String) = when (m) { "upi" -> "UPI"; "bank" -> "Bank"; "cheque" -> "Cheque"; "cash" -> "Cash"; else -> "Other" }
}

@Serializable
data class StudentFee(
    @SerialName("student_id") val studentId: String,
    @SerialName("monthly_fee") val monthlyFee: Double,
    @SerialName("due_day") val dueDay: Int = 10,
)

@Serializable
data class FeePayment(
    val id: String? = null,
    @SerialName("student_id") val studentId: String,
    @SerialName("for_month") val forMonth: String,   // yyyy-MM-01
    val amount: Double,
    @SerialName("paid_on") val paidOn: String? = null,
    val method: String = "cash",
    val note: String? = null,
)

@Serializable
data class Payee(
    val id: String? = null,
    val name: String,
    @SerialName("role_label") val roleLabel: String? = null,
    @SerialName("monthly_salary") val monthlySalary: Double,
    @SerialName("pay_day") val payDay: Int = 1,
    val active: Boolean = true,
)

@Serializable
data class SalaryPayment(
    val id: String? = null,
    @SerialName("payee_id") val payeeId: String,
    @SerialName("for_month") val forMonth: String,
    val amount: Double,
    @SerialName("paid_on") val paidOn: String? = null,
    val method: String = "bank",
    val note: String? = null,
)

// ------------------------------------------------------------------ daily check-in
object PlanMode {
    const val USUAL = "usual"
    const val CUSTOM = "custom"
    const val OFF = "off"
}

@Serializable
data class DayPlan(
    @SerialName("teacher_id") val teacherId: String,
    val date: String,
    val mode: String,
)

@Serializable
data class DayClass(
    val id: String? = null,
    @SerialName("teacher_id") val teacherId: String,
    val date: String,
    @SerialName("batch_id") val batchId: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
)

/** One class actually happening on a date: the usual timing, or what the teacher set for that day. */
data class Session(
    val key: String,
    val batchId: String,
    val startTime: String,
    val endTime: String,
    val teacherId: String?,
    val changed: Boolean,
)

class Plans(val plans: List<DayPlan>, val classes: List<DayClass>) {
    fun plan(teacherId: String?, date: String) = plans.firstOrNull { it.teacherId == teacherId && it.date == date }

    /** The usual weekly timings this teacher has on that weekday. */
    fun usual(school: School, teacherId: String?, date: java.time.LocalDate): List<Session> =
        school.slots.filter { sl -> sl.weekday == date.dayOfWeek.value && school.batch(sl.batchId)?.teacherId == teacherId }
            .map { Session("slot:${it.id}:$date", it.batchId, it.startTime, it.endTime, teacherId, false) }
            .sortedBy { it.startTime }

    /** Everything happening on [date], with each teacher's check-in applied. */
    fun sessions(school: School, date: java.time.LocalDate): List<Session> {
        val d = date.toString()
        val teachers = school.batches.map { it.teacherId }.distinct() + classes.filter { it.date == d }.map { it.teacherId }
        return teachers.distinct().flatMap { t -> forTeacher(school, t, date) }.sortedBy { it.startTime }
    }

    fun forTeacher(school: School, teacherId: String?, date: java.time.LocalDate): List<Session> {
        val d = date.toString()
        return when (plan(teacherId, d)?.mode) {
            PlanMode.OFF -> emptyList()
            PlanMode.CUSTOM -> classes.filter { it.teacherId == teacherId && it.date == d }
                .map { Session("day:${it.id ?: (it.batchId + it.startTime)}", it.batchId, it.startTime, it.endTime, teacherId, true) }
                .sortedBy { it.startTime }
            else -> usual(school, teacherId, date)
        }
    }

    companion object { val EMPTY = Plans(emptyList(), emptyList()) }
}

// ------------------------------------------------------------------ "I've paid"
object ClaimStatus {
    const val PENDING = "pending"
    const val CONFIRMED = "confirmed"
    const val REJECTED = "rejected"
}

@Serializable
data class FeeClaim(
    val id: String? = null,
    @SerialName("student_id") val studentId: String,
    @SerialName("for_month") val forMonth: String,
    val amount: Double,
    val method: String = "upi",
    val note: String? = null,
    val status: String = ClaimStatus.PENDING,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null,
)

// ------------------------------------------------------------------ teachers' classes and pay
/** Salaries, payees and the earnings page are switched off for now (switch back on later). */
object Features { const val SALARIES = false }

@Serializable
data class ClassTaken(
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("batch_id") val batchId: String,
    val date: String,
    val present: Int = 0,
    val total: Int = 0,
)

@Serializable
data class TeacherRate(
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("per_class") val perClass: Double,
)
