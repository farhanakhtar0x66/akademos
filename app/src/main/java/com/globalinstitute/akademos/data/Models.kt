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
