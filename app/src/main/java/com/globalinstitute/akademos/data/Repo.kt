package com.globalinstitute.akademos.data

import android.content.Context
import com.globalinstitute.akademos.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

object Repo {
    private const val SHEETS = "answer-sheets"
    private lateinit var client: SupabaseClient
    /** Debug builds without server keys run on sample data. */
    val demo get() = BuildConfig.DEBUG && !isConfigured

    val isConfigured get() = BuildConfig.SUPABASE_URL.startsWith("https://") && !BuildConfig.SUPABASE_URL.contains("YOUR-PROJECT")

    fun init(context: Context) {
        if (::client.isInitialized) return
        val url = if (isConfigured) BuildConfig.SUPABASE_URL else "https://not-configured.supabase.co"
        val key = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "missing" }
        client = createSupabaseClient(url, key) {
            defaultSerializer = KotlinXSerializer(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
            install(Auth) {
                sessionManager = SecureSessionManager(context.applicationContext)
            }
            install(Postgrest)
            install(Storage)
        }
    }

    val sessionStatus get() = client.auth.sessionStatus

    suspend fun awaitSession(): Boolean {
        if (demo) return true
        val st = withTimeoutOrNull(8000) { client.auth.sessionStatus.first { it !is SessionStatus.Initializing } }
        return st is SessionStatus.Authenticated
    }

    fun uid(): String? = if (demo) Demo.profile.id else client.auth.currentUserOrNull()?.id

    // ------------------------------------------------------------- auth
    /** Usernames are turned into private, never-mailed addresses so older users don't need email. */
    private fun toEmail(login: String): String {
        val l = login.trim().lowercase()
        return if ("@" in l) l else "${l.replace(Regex("[^a-z0-9._-]"), "")}@akademos.example.com"
    }

    suspend fun signIn(login: String, password: String) {
        client.auth.signInWith(Email) { email = toEmail(login); this.password = password }
    }

    suspend fun signUp(login: String, password: String) {
        client.auth.signUpWith(Email) { email = toEmail(login); this.password = password }
        if (client.auth.currentUserOrNull() == null) signIn(login, password)
    }

    suspend fun signOut() = runCatching { client.auth.signOut() }

    suspend fun redeem(code: String, fullName: String): RedeemResult =
        client.postgrest.rpc("redeem_invite", buildJsonObject {
            put("p_code", code); put("p_full_name", fullName)
        }).decodeAs()

    suspend fun myProfile(): Profile? { if (demo) return Demo.myProfile();
        val id = uid() ?: return null
        return client.from("profiles").select { filter { eq("id", id) } }.decodeList<Profile>().firstOrNull()
    }

    // ------------------------------------------------------------- school setup
    suspend fun school(): School = if (demo) Demo.school() else School(
        classes = client.from("classes").select { order("sort_order", Order.ASCENDING); order("name", Order.ASCENDING) }.decodeList(),
        subjects = client.from("subjects").select { order("name", Order.ASCENDING) }.decodeList(),
        batches = client.from("batches").select { order("name", Order.ASCENDING) }.decodeList(),
        slots = client.from("schedule_slots").select { order("start_time", Order.ASCENDING) }.decodeList(),
        staff = runCatching {
            client.from("profiles").select { filter { isIn("role", listOf(Roles.ADMIN, Roles.TEACHER)) }; order("full_name", Order.ASCENDING) }
                .decodeList<Profile>()
        }.getOrDefault(emptyList()),
    )

    suspend fun saveClass(c: SchoolClass) { if (demo) return Demo.saveClass(c);
        if (c.id == null) client.from("classes").insert(c) else client.from("classes").update(c) { filter { eq("id", c.id) } }
    }
    suspend fun deleteClass(id: String) { if (demo) return Demo.deleteClass(id); client.from("classes").delete { filter { eq("id", id) } } }

    suspend fun saveSubject(s: Subject) { if (demo) return Demo.saveSubject(s);
        if (s.id == null) client.from("subjects").insert(s) else client.from("subjects").update(s) { filter { eq("id", s.id) } }
    }
    suspend fun deleteSubject(id: String) { if (demo) return Demo.deleteSubject(id); client.from("subjects").delete { filter { eq("id", id) } } }

    suspend fun students(includeInactive: Boolean = false): List<Student> = if (demo) Demo.students(includeInactive) else
        client.from("students").select {
            if (!includeInactive) filter { eq("active", true) }
            order("full_name", Order.ASCENDING)
        }.decodeList()

    suspend fun saveStudent(s: Student) { if (demo) return Demo.saveStudent(s);
        if (s.id == null) client.from("students").insert(s) else client.from("students").update(s) { filter { eq("id", s.id) } }
    }

    suspend fun batchStudentIds(batchId: String): List<String> = if (demo) Demo.batchStudentIds(batchId) else
        client.from("batch_students").select { filter { eq("batch_id", batchId) } }.decodeList<BatchStudent>().map { it.studentId }

    suspend fun studentBatchIds(studentId: String): List<String> = if (demo) Demo.studentBatchIds(studentId) else
        client.from("batch_students").select { filter { eq("student_id", studentId) } }.decodeList<BatchStudent>().map { it.batchId }

    suspend fun saveBatch(b: Batch, studentIds: List<String>, slots: List<Slot>): String { if (demo) return Demo.saveBatch(b, studentIds, slots);
        val id = if (b.id == null) {
            client.from("batches").insert(b) { select() }.decodeSingle<Batch>().id!!
        } else {
            client.from("batches").update(b) { filter { eq("id", b.id) } }; b.id
        }
        client.from("batch_students").delete { filter { eq("batch_id", id) } }
        if (studentIds.isNotEmpty()) client.from("batch_students").insert(studentIds.distinct().map { BatchStudent(id, it) })
        client.from("schedule_slots").delete { filter { eq("batch_id", id) } }
        if (slots.isNotEmpty()) client.from("schedule_slots").insert(slots.map { it.copy(id = null, batchId = id) })
        return id
    }
    suspend fun deleteBatch(id: String) { if (demo) return Demo.deleteBatch(id); client.from("batches").delete { filter { eq("id", id) } } }

    // ------------------------------------------------------------- attendance
    suspend fun attendance(batchId: String, date: String): List<Attendance> = if (demo) Demo.attendance(batchId, date) else
        client.from("attendance").select { filter { eq("batch_id", batchId); eq("date", date) } }.decodeList()

    suspend fun attendanceOn(date: String): List<Attendance> = if (demo) Demo.attendanceOn(date) else
        client.from("attendance").select { filter { eq("date", date) } }.decodeList()

    suspend fun attendanceTaken(batchId: String, date: String): Boolean = attendance(batchId, date).isNotEmpty()

    suspend fun saveAttendance(batchId: String, date: String, statuses: Map<String, String>, existing: List<Attendance>) { if (demo) return Demo.saveAttendance(batchId, date, statuses, existing);
        val now = Instant.now().toString()
        val rows = statuses.map { (sid, status) ->
            val prev = existing.firstOrNull { it.studentId == sid }
            val arrived: String? = when (status) {
                Status.ABSENT -> null
                else -> prev?.arrivedAt ?: now
            }
            buildJsonObject {
                put("batch_id", batchId); put("student_id", sid); put("date", date); put("status", status)
                put("arrived_at", arrived?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        }
        if (rows.isEmpty()) return
        client.from("attendance").upsert(JsonArray(rows)) { onConflict = "batch_id,student_id,date" }
    }

    suspend fun markLeft(batchId: String, date: String, studentIds: List<String>, left: Boolean) { if (demo) return Demo.markLeft(batchId, date, studentIds, left);
        if (studentIds.isEmpty()) return
        client.from("attendance").update(buildJsonObject {
            put("left_at", if (left) JsonPrimitive(Instant.now().toString()) else JsonNull)
        }) { filter { eq("batch_id", batchId); eq("date", date); isIn("student_id", studentIds) } }
    }

    suspend fun studentAttendance(studentId: String, limit: Long = 120): List<Attendance> = if (demo) Demo.studentAttendance(studentId, limit) else
        client.from("attendance").select {
            filter { eq("student_id", studentId) }
            order("date", Order.DESCENDING); limit(limit)
        }.decodeList()

    // ------------------------------------------------------------- tests & marks
    suspend fun assessments(batchId: String): List<Assessment> = if (demo) Demo.assessments(batchId) else
        client.from("assessments").select { filter { eq("batch_id", batchId) }; order("held_on", Order.DESCENDING) }.decodeList()

    suspend fun createAssessment(a: Assessment): Assessment = if (demo) Demo.createAssessment(a) else
        client.from("assessments").insert(a) { select() }.decodeSingle()

    suspend fun marks(assessmentId: String): List<Mark> = if (demo) Demo.marks(assessmentId) else
        client.from("marks").select { filter { eq("assessment_id", assessmentId) } }.decodeList()

    suspend fun saveMarks(assessmentId: String, entries: Map<String, Pair<Double?, String>>) { if (demo) return Demo.saveMarks(assessmentId, entries);
        val rows = entries.map { (sid, v) ->
            buildJsonObject {
                put("assessment_id", assessmentId); put("student_id", sid)
                put("score", v.first?.let { JsonPrimitive(it) } ?: JsonNull)
                put("remarks", v.second.trim().ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        }
        if (rows.isEmpty()) return
        client.from("marks").upsert(JsonArray(rows)) { onConflict = "assessment_id,student_id" }
    }

    suspend fun uploadAnswerSheet(studentId: String, assessmentId: String, bytes: ByteArray, mime: String): String { if (demo) return Demo.uploadAnswerSheet(studentId, assessmentId, bytes, mime);
        val ext = when (mime) { "application/pdf" -> "pdf"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val path = "$studentId/$assessmentId/${UUID.randomUUID()}.$ext"
        client.storage.from(SHEETS).upload(path, bytes) { upsert = false; contentType = ContentType.parse(mime) }
        client.from("marks").upsert(buildJsonObject {
            put("assessment_id", assessmentId); put("student_id", studentId); put("answer_sheet_path", path)
        }) { onConflict = "assessment_id,student_id" }
        return path
    }

    suspend fun downloadAnswerSheet(path: String): ByteArray = if (demo) Demo.downloadAnswerSheet(path) else client.storage.from(SHEETS).downloadAuthenticated(path)

    suspend fun studentMarks(studentId: String): List<MarkWithTest> = if (demo) Demo.studentMarks(studentId) else
        client.from("marks").select(Columns.raw("*, assessments(*)")) {
            filter { eq("student_id", studentId) }
        }.decodeList<MarkWithTest>().sortedByDescending { it.assessments?.heldOn ?: "" }

    // ------------------------------------------------------------- admin
    suspend fun createInvite(role: String, label: String, studentIds: List<String>, hours: Int): String = if (demo) Demo.createInvite(role, label, studentIds, hours) else
        client.postgrest.rpc("create_invite", buildJsonObject {
            put("p_role", role); put("p_label", label)
            put("p_student_ids", JsonArray(studentIds.map { JsonPrimitive(it) }))
            put("p_valid_hours", hours)
        }).decodeAs()

    suspend fun invites(): List<Invite> = if (demo) Demo.invites() else
        client.from("invites").select(Columns.list("id", "role", "label", "created_at", "expires_at", "used_at")) {
            order("created_at", Order.DESCENDING); limit(50)
        }.decodeList()

    suspend fun revokeInvite(id: String) { if (demo) return Demo.revokeInvite(id); client.postgrest.rpc("revoke_invite", buildJsonObject { put("p_id", id) }) }

    suspend fun people(): List<Profile> = if (demo) Demo.people() else
        client.from("profiles").select { order("role", Order.ASCENDING); order("full_name", Order.ASCENDING) }.decodeList()

    suspend fun setActive(userId: String, active: Boolean) { if (demo) return Demo.setActive(userId, active);
        client.postgrest.rpc("set_user_active", buildJsonObject { put("p_user", userId); put("p_active", active) })
    }

    suspend fun auditLog(): List<AuditEntry> = if (demo) Demo.auditLog() else
        client.from("audit_log").select(Columns.list("id", "at", "actor_name", "table_name", "action")) {
            order("at", Order.DESCENDING); limit(100)
        }.decodeList()

    @Suppress("unused") private fun JsonObject.debug() = toString()
}

/** Turns technical errors into short sentences an older person can understand. */
fun Throwable.friendly(): String {
    val m = (message ?: "").lowercase()
    return when {
        "invalid login" in m || "invalid_credentials" in m -> "Wrong username or password."
        "already registered" in m || "user_already_exists" in m -> "That username is taken. Try another one."
        "password" in m && ("short" in m || "least" in m || "weak" in m) -> "Password must be at least 8 characters."
        "unable to resolve" in m || "failed to connect" in m || "timeout" in m || "unknownhost" in m || "network" in m ->
            "No internet connection. Please check and try again."
        "row-level security" in m || "42501" in m || "not allowed" in m || "permission" in m ->
            "You don't have permission to do this."
        "more than the maximum" in m -> "A score is higher than the maximum marks."
        "duplicate key" in m -> "This already exists."
        else -> message?.lineSequence()?.firstOrNull()?.take(140) ?: "Something went wrong."
    }
}
