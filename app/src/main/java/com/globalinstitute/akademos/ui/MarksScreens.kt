package com.globalinstitute.akademos.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Grading
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.globalinstitute.akademos.data.Assessment
import com.globalinstitute.akademos.data.Mark
import com.globalinstitute.akademos.data.Repo
import com.globalinstitute.akademos.data.Student
import com.globalinstitute.akademos.data.friendly
import com.globalinstitute.akademos.ui.components.AkCard
import com.globalinstitute.akademos.ui.components.Avatar
import com.globalinstitute.akademos.ui.components.BigField
import com.globalinstitute.akademos.ui.components.EmptyBlock
import com.globalinstitute.akademos.ui.components.ErrorBlock
import com.globalinstitute.akademos.ui.components.Gap
import com.globalinstitute.akademos.ui.components.LoadingBlock
import com.globalinstitute.akademos.ui.components.Pill
import com.globalinstitute.akademos.ui.components.PrimaryButton
import com.globalinstitute.akademos.ui.components.ScreenHeader
import com.globalinstitute.akademos.ui.components.SecondaryButton
import com.globalinstitute.akademos.ui.components.rememberLoad
import com.globalinstitute.akademos.ui.theme.LocalStatus
import com.globalinstitute.akademos.ui.theme.parseHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ===================================================================== batches list
@Composable
fun MarksHomeScreen() {
    val s = LocalSession.current
    val sc = s.school
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item { ScreenHeader("Marks", "Choose a batch to add a test or enter marks") }
        when {
            sc == null -> item { LoadingBlock() }
            sc.batches.isEmpty() -> item { EmptyBlock(Icons.Rounded.Grading, "No batches yet", "The administrator needs to create batches first.") }
            else -> items(sc.batches, key = { it.id!! }) { b ->
                val subj = sc.subject(b.subjectId)
                AkCard(Modifier.padding(horizontal = 20.dp, vertical = 7.dp).animateItem(), onClick = { s.nav.push(Screen.Tests(b.id!!)) },
                    accent = parseHex(subj?.color)) {
                    Text(b.name, style = MaterialTheme.typography.headlineSmall)
                    Text(listOfNotNull(sc.klass(b.classId)?.name, subj?.name).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ===================================================================== tests of one batch
@Composable
fun TestsScreen(batchId: String) {
    val s = LocalSession.current
    val scope = rememberCoroutineScope()
    val tests = rememberLoad(batchId) { Repo.assessments(batchId) }
    var creating by remember { mutableStateOf(false) }
    val batch = s.school?.batch(batchId)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Tests", batch?.name, onBack = { s.nav.pop() })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            val list = tests.data
            when {
                list == null && tests.error != null -> item { Box(Modifier.padding(20.dp)) { ErrorBlock(tests.error, tests.reload) } }
                list == null -> item { LoadingBlock() }
                list.isEmpty() -> item { EmptyBlock(Icons.Rounded.Quiz, "No tests yet", "Tap “New test” to add one and enter marks.") }
                else -> items(list, key = { it.id!! }) { t ->
                    AkCard(Modifier.padding(horizontal = 20.dp, vertical = 7.dp).animateItem(), onClick = { s.nav.push(Screen.MarksEntry(batchId, t)) }) {
                        Text(t.title, style = MaterialTheme.typography.headlineSmall)
                        Text("Out of ${fmt(t.maxMarks)}  ·  ${t.heldOn?.let { prettyDate(it) } ?: ""}",
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Box(Modifier.navigationBarsPadding().padding(16.dp)) {
            PrimaryButton("New test", { creating = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add)
        }
    }

    if (creating) {
        var title by remember { mutableStateOf("") }
        var max by remember { mutableStateOf("100") }
        var err by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New test", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    BigField(title, { title = it }, "Test name (e.g. Unit Test 1)")
                    Gap()
                    BigField(max, { max = it.filter { c -> c.isDigit() || c == '.' } }, "Maximum marks", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    err?.let { Gap(); Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                PrimaryButton("Create", loading = busy, enabled = title.isNotBlank() && (max.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { Repo.createAssessment(Assessment(batchId = batchId, title = title.trim(), maxMarks = max.toDouble(), heldOn = LocalDate.now().toString())) }
                            .onSuccess { creating = false; s.nav.push(Screen.MarksEntry(batchId, it)); tests.reload() }
                            .onFailure { err = it.friendly() }
                        busy = false
                    }
                })
            },
            dismissButton = { TextButton({ creating = false }) { Text("Cancel") } },
        )
    }
}

// ===================================================================== marks entry
private class MarksData(val students: List<Student>, val marks: List<Mark>)

@Composable
fun MarksEntryScreen(batchId: String, test: Assessment) {
    val s = LocalSession.current
    val st = LocalStatus.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { s.activity.setSecure(true); onDispose { s.activity.setSecure(false) } }

    val load = rememberLoad(test.id) {
        val ids = Repo.batchStudentIds(batchId).toSet()
        MarksData(Repo.students().filter { it.id in ids }, Repo.marks(test.id!!))
    }
    val scores = remember(test.id) { mutableStateMapOf<String, String>() }
    val remarks = remember(test.id) { mutableStateMapOf<String, String>() }
    val sheets = remember(test.id) { mutableStateMapOf<String, String>() }
    val uploading = remember(test.id) { mutableStateMapOf<String, Boolean>() }
    var initialised by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var savedOk by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pickFor by remember { mutableStateOf<String?>(null) }
    var viewPath by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var cameraFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(load.data) {
        val d = load.data ?: return@LaunchedEffect
        if (!initialised) {
            d.marks.forEach { m ->
                m.score?.let { scores[m.studentId] = fmt(it) }
                m.remarks?.let { remarks[m.studentId] = it }
                m.answerSheetPath?.let { sheets[m.studentId] = it }
            }
            initialised = true
        }
    }

    fun upload(studentId: String, uri: Uri) {
        uploading[studentId] = true; error = null
        scope.launch {
            runCatching {
                val (bytes, mime) = withContext(Dispatchers.IO) { prepareUpload(ctx, uri) }
                Repo.uploadAnswerSheet(studentId, test.id!!, bytes, mime)
            }.onSuccess { sheets[studentId] = it }.onFailure { error = it.friendly() }
            uploading[studentId] = false
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val sid = pickFor; pickFor = null
        if (uri != null && sid != null) upload(sid, uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val sid = cameraFor; val u = cameraUri; cameraFor = null
        if (ok && sid != null && u != null) upload(sid, u)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(test.title, "Out of ${fmt(test.maxMarks)}  ·  ${s.school?.batch(batchId)?.name ?: ""}", onBack = { s.nav.pop() })
        val d = load.data
        when {
            d == null && load.error != null -> Box(Modifier.padding(20.dp)) { ErrorBlock(load.error, load.reload) }
            d == null -> LoadingBlock()
            d.students.isEmpty() -> EmptyBlock(Icons.Rounded.Grading, "No students", "This batch has no students yet.")
            else -> {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(d.students, key = { it.id!! }) { stu ->
                        val id = stu.id!!
                        val value = scores[id].orEmpty()
                        val over = (value.toDoubleOrNull() ?: 0.0) > test.maxMarks
                        AkCard(Modifier.animateContentSize(), contentPadding = PaddingValues(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(stu.fullName, 46.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stu.fullName, style = MaterialTheme.typography.titleMedium)
                                    stu.rollNo?.let { Text("Roll $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                BigField(value, { scores[id] = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, "Marks",
                                    Modifier.width(120.dp), keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    supporting = if (over) "Too high" else "/ ${fmt(test.maxMarks)}")
                            }
                            BigField(remarks[id].orEmpty(), { remarks[id] = it.take(300) }, "Remark (optional)")
                            Gap(8.dp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                when {
                                    uploading[id] == true -> { CircularProgressIndicator(Modifier.size(28.dp)); Text("Uploading…") }
                                    sheets[id] != null -> {
                                        Pill("Sheet added", st.present, icon = Icons.Rounded.CheckCircle)
                                        TextButton({ viewPath = sheets[id] }) { Icon(Icons.Rounded.Visibility, null); Spacer(Modifier.width(4.dp)); Text("View") }
                                    }
                                    else -> Text("Answer sheet:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton({
                                    val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
                                    val f = File(dir, "shot_${System.currentTimeMillis()}.jpg")
                                    val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                                    cameraUri = u; cameraFor = id; cameraLauncher.launch(u)
                                }, Modifier.size(52.dp)) { Icon(Icons.Rounded.CameraAlt, "Take photo", Modifier.size(30.dp)) }
                                IconButton({ pickFor = id; fileLauncher.launch(arrayOf("image/*", "application/pdf")) }, Modifier.size(52.dp)) {
                                    Icon(Icons.Rounded.AttachFile, "Choose file", Modifier.size(30.dp))
                                }
                            }
                        }
                    }
                }
                Surface(shadowElevation = 12.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge); Gap(6.dp) }
                        PrimaryButton(if (savedOk) "Saved ✓" else "Save marks", modifier = Modifier.fillMaxWidth(), loading = saving,
                            color = if (savedOk) st.present else MaterialTheme.colorScheme.primary, icon = Icons.Rounded.CheckCircle, onClick = {
                            val bad = d.students.firstOrNull { (scores[it.id].orEmpty().toDoubleOrNull() ?: 0.0) > test.maxMarks }
                            if (bad != null) { error = "${bad.fullName}'s marks are more than ${fmt(test.maxMarks)}."; return@PrimaryButton }
                            saving = true; error = null
                            scope.launch {
                                val entries = d.students.mapNotNull { stu ->
                                    val id = stu.id!!
                                    val sc = scores[id]?.toDoubleOrNull()
                                    val rm = remarks[id].orEmpty()
                                    if (sc == null && rm.isBlank() && d.marks.none { it.studentId == id }) null else id to (sc to rm)
                                }.toMap()
                                runCatching { Repo.saveMarks(test.id!!, entries) }
                                    .onSuccess { savedOk = true; kotlinx.coroutines.delay(1500); savedOk = false }
                                    .onFailure { error = it.friendly() }
                                saving = false
                            }
                        })
                    }
                }
            }
        }
    }
    viewPath?.let { SheetViewer(it) { viewPath = null } }
}

/** Shrinks big phone photos so uploads are quick, keeps PDFs as they are. */
private fun prepareUpload(ctx: Context, uri: Uri): Pair<ByteArray, String> {
    val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
    val raw = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    if (mime == "application/pdf") {
        require(raw.size <= 10 * 1024 * 1024) { "This PDF is bigger than 10 MB." }
        return raw to mime
    }
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
    var sample = 1
    while (maxOf(opts.outWidth, opts.outHeight) / sample > 2400) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: error("This file is not a picture or PDF.")
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray() to "image/jpeg"
}

// ===================================================================== viewer (staff & parents)
@Composable
fun SheetViewer(path: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val load = rememberLoad(path) { withContext(Dispatchers.IO) { Repo.downloadAnswerSheet(path) } }
    val isPdf = path.endsWith(".pdf")
    LaunchedEffect(load.data) {
        val bytes = load.data ?: return@LaunchedEffect
        if (isPdf) {
            val dir = File(ctx.cacheDir, "sheets").apply { mkdirs() }
            val f = File(dir, "answer_sheet.pdf").apply { writeBytes(bytes) }
            val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
            onClose()
        }
    }
    if (isPdf && load.error == null) return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val bytes = load.data
            when {
                load.error != null -> Box(Modifier.padding(24.dp).statusBarsPadding()) { ErrorBlock(load.error, load.reload) }
                bytes == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
                else -> {
                    val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                    var zoom by remember { mutableFloatStateOf(1f) }
                    var pan by remember { mutableStateOf(Offset.Zero) }
                    val tState = rememberTransformableState { z, p, _ -> zoom = (zoom * z).coerceIn(1f, 6f); pan += p }
                    if (bmp != null) Image(bmp, "Answer sheet", Modifier.fillMaxSize().transformable(tState)
                        .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y })
                    else Text("Can't show this file", color = Color.White, modifier = Modifier.align(Alignment.Center))
                }
            }
            IconButton(onClose, Modifier.statusBarsPadding().padding(12.dp).size(56.dp).align(Alignment.TopEnd)
                .background(Color.White.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape)) {
                Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

fun fmt(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else "%.1f".format(d)
fun prettyDate(iso: String): String = runCatching { LocalDate.parse(iso.take(10)).format(DateTimeFormatter.ofPattern("d MMM yyyy")) }.getOrDefault(iso)

