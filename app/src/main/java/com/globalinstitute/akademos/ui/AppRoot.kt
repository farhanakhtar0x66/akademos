package com.globalinstitute.akademos.ui

import com.globalinstitute.akademos.ui.components.rememberReduceMotion

import com.globalinstitute.akademos.ui.components.LocalReduceMotion

import com.globalinstitute.akademos.ui.components.Motion

import androidx.compose.animation.ExitTransition

import androidx.compose.animation.EnterTransition

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.layout.onSizeChanged

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Grading
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.globalinstitute.akademos.AppLock
import com.globalinstitute.akademos.MainActivity
import com.globalinstitute.akademos.data.Assessment
import com.globalinstitute.akademos.data.Prefs
import com.globalinstitute.akademos.data.Profile
import com.globalinstitute.akademos.data.Repo
import com.globalinstitute.akademos.data.Roles
import com.globalinstitute.akademos.data.School
import com.globalinstitute.akademos.data.Student
import com.globalinstitute.akademos.data.friendly
import com.globalinstitute.akademos.reminders.ReminderScheduler
import com.globalinstitute.akademos.ui.components.AkCard
import com.globalinstitute.akademos.ui.components.BigField
import com.globalinstitute.akademos.ui.components.Centered
import com.globalinstitute.akademos.ui.components.EmptyBlock
import com.globalinstitute.akademos.ui.components.ErrorBlock
import com.globalinstitute.akademos.ui.components.Gap
import com.globalinstitute.akademos.ui.components.LoadingBlock
import com.globalinstitute.akademos.ui.components.PrimaryButton
import com.globalinstitute.akademos.ui.components.rememberLoad
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

// ===================================================================== navigation
enum class AttMode { ARRIVAL, DEPARTURE, LATE }

sealed interface Screen {
    data object Home : Screen
    data class Attendance(val batchId: String, val date: String, val mode: AttMode) : Screen
    data class Tests(val batchId: String) : Screen
    data class MarksEntry(val batchId: String, val test: Assessment) : Screen
    data class StudentDetail(val studentId: String) : Screen
    data object Classes : Screen
    data object Subjects : Screen
    data object StudentsAdmin : Screen
    data class EditStudent(val student: Student?) : Screen
    data object Batches : Screen
    data class EditBatch(val batchId: String?) : Screen
    data object Keys : Screen
    data object People : Screen
    data object Audit : Screen
    data object Fees : Screen
    data class StudentFees(val studentId: String) : Screen
    data object Salaries : Screen
    data object Earnings : Screen
    data class PlanDay(val date: String) : Screen
    data object TeacherPay : Screen
}

class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var forward by mutableStateOf(true)
    /** The bottom tab that was open, so Back returns to it. */
    var tab by mutableStateOf<String?>(null)
    /** A fee reminder's "Yes, I've paid" was tapped for this student. */
    var claimFor by mutableStateOf<String?>(null)
    val top get() = stack.last()
    /** Set after a back swipe finishes: the swipe already moved the page, so skip the pop animation. */
    var instant by mutableStateOf(false)
    fun push(s: Screen) { forward = true; stack.add(s) }
    fun pop() { if (stack.size > 1) { forward = false; stack.removeAt(stack.lastIndex) } }
    fun home() { forward = false; while (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

class SessionCtx(
    val me: Profile,
    val activity: MainActivity,
    val nav: Nav,
    val school: School?,
    val reloadSchool: () -> Unit,
    val signOut: () -> Unit,
)

val LocalSession = compositionLocalOf<SessionCtx> { error("no session") }

/**
 * Pages enter and leave along the same path: a new page slides in from the right over the old
 * one (which drifts left and dims), and Back sends it out to the right again. Springs, so a
 * transition can be interrupted at any point. With "Remove animations" on, a plain cross-fade.
 */
private fun pageTransition(fwd: Boolean, depth: Int, reduce: Boolean): ContentTransform {
    if (reduce) return (fadeIn(Motion.PageFade) togetherWith fadeOut(Motion.PageFade)).apply { targetContentZIndex = depth.toFloat() }
    val t = if (fwd)
        slideInHorizontally(Motion.Page) { it } togetherWith
            (slideOutHorizontally(Motion.Page) { -it * 3 / 10 } + fadeOut(Motion.PageFade, targetAlpha = 0.5f))
    else
        (slideInHorizontally(Motion.Page) { -it * 3 / 10 } + fadeIn(Motion.PageFade, initialAlpha = 0.5f)) togetherWith
            slideOutHorizontally(Motion.Page) { it }
    // The deeper page is always drawn on top, whichever way we're going.
    return t.apply { targetContentZIndex = depth.toFloat() }
}

/** Tabs are siblings, not a journey: a quick cross-fade with the faintest lift, no sideways slide. */
private fun tabTransition(): ContentTransform =
    (fadeIn(Motion.spring(1f, 0.28f)) + scaleIn(Motion.spring(1f, 0.32f), initialScale = 0.985f)) togetherWith fadeOut(Motion.spring(1f, 0.18f))

/** Height of the floating tab bar, so the tab screens can scroll their last item clear of it. */
val LocalBarInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

// ===================================================================== root
@Composable
fun AppRoot(activity: MainActivity) {
    if (Repo.demo) { DemoRoot(activity); return }
    if (!Repo.isConfigured) { NotConfigured(); return }
    val status by Repo.sessionStatus.collectAsState()
    val stage = when (status) {
        is SessionStatus.Initializing -> 0
        is SessionStatus.Authenticated -> 2
        else -> 1
    }
    AnimatedContent(stage, transitionSpec = { fadeIn(Motion.spring(1f, 0.45f)) togetherWith fadeOut(Motion.spring(1f, 0.25f)) }, label = "auth") { s ->
        when (s) {
            0 -> Splash()
            1 -> AuthScreen()
            else -> SignedIn(activity)
        }
    }
}

@Composable
private fun SignedIn(activity: MainActivity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val uid = Repo.uid()
    val profile = rememberLoad(uid) { Repo.myProfile() }
    val signOut: () -> Unit = {
        scope.launch {
            ReminderScheduler.cancelAll(ctx)
            ctx.getSystemService(android.app.NotificationManager::class.java).cancelAll()
            Prefs.clearUserData()
            AppLock.locked.value = true
            Repo.signOut()
        }
    }
    val p = profile.data
    when {
        profile.loading && p == null -> Splash()
        profile.error != null && p == null -> Column(Modifier.statusBarsPadding().padding(20.dp)) {
            ErrorBlock(profile.error, profile.reload)
            TextButton(onClick = signOut) { Text("Sign out") }
        }
        p == null -> RedeemScreen(onDone = profile.reload, onSignOut = signOut)
        !p.active -> Centered {
            EmptyBlock(Icons.Rounded.Block, "Access paused", "The institute has paused this account. Please contact the office.") {
                TextButton(onClick = signOut) { Text("Sign out") }
            }
        }
        else -> {
            LaunchedEffect(p.id) { Prefs.lastUserId = p.id; Prefs.lastRole = p.role }
            val lockOn by Prefs.appLock.collectAsState()
            val locked by AppLock.locked
            if (p.isStaff && lockOn && locked) LockScreen(activity, p)
            else {
                if (!p.isStaff) LaunchedEffect(Unit) { AppLock.locked.value = false }
                MainShell(p, activity, signOut)
            }
        }
    }
}

@Composable
private fun MainShell(me: Profile, activity: MainActivity, signOut: () -> Unit) {
    val ctx = LocalContext.current
    val nav = remember { Nav() }
    val school = rememberLoad(me.id) { Repo.school() }
    LaunchedEffect(me.id) { Prefs.lastUserId = me.id; Prefs.lastRole = me.role; ReminderScheduler.rearm(ctx) }
    LaunchedEffect(school.data) {
        school.data?.let { if (me.isStaff) runCatching { ReminderScheduler.refresh(ctx, me, it) } }
    }
    // A reminder asked us to open a particular class
    val req by activity.openRequest
    LaunchedEffect(req) {
        val r = req ?: return@LaunchedEffect
        if (r.batchId == "money") {
            nav.home()
            if (me.isAdmin) when (r.kind) { "fees", "claims" -> nav.push(Screen.Fees); "salary" -> if (com.globalinstitute.akademos.data.Features.SALARIES) nav.push(Screen.Salaries) }
            if (r.kind.startsWith("claim:")) { nav.tab = "CHILD"; nav.claimFor = r.kind.removePrefix("claim:") }
        } else if (r.batchId == "plan") {
            if (me.isStaff) { nav.home(); nav.push(Screen.PlanDay(r.date)) }
        } else if (me.isStaff) {
            nav.home()
            nav.push(Screen.Attendance(r.batchId, r.date, when (r.kind) { "end" -> AttMode.DEPARTURE; "followup" -> AttMode.LATE; else -> AttMode.ARRIVAL }))
        }
        activity.openRequest.value = null
    }
    // Back is a gesture you can hold: the page follows your thumb, the previous page waits
    // underneath, and you can change your mind half way. Let go past the point and it finishes.
    val back = remember { androidx.compose.animation.core.Animatable(0f) }
    var fromLeft by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    androidx.activity.compose.PredictiveBackHandler(enabled = nav.stack.size > 1) { events ->
        try {
            events.collect { e -> fromLeft = e.swipeEdge != androidx.activity.BackEventCompat.EDGE_RIGHT; back.snapTo(e.progress) }
            back.animateTo(1f, Motion.spring(1f, 0.22f))
            nav.instant = true
            nav.pop()
            back.snapTo(0f)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            scope.launch { back.animateTo(0f, Motion.Release) }
            throw e
        }
    }
    LaunchedEffect(nav.stack.size) { nav.instant = false }
    val reduce = rememberReduceMotion()

    val session = SessionCtx(me, activity, nav, school.data, school.reload, signOut)
    androidx.compose.runtime.CompositionLocalProvider(LocalSession provides session, LocalReduceMotion provides reduce) {
      androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val dir = if (fromLeft) 1f else -1f
        // Read the progress only inside draw-phase lambdas, so a swipe doesn't recompose the screens every frame.
        val swiping by remember { androidx.compose.runtime.derivedStateOf { back.value > 0f } }
        if (swiping && nav.stack.size > 1) {
            // The page you're going back to, drifting into place and brightening as you pull.
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = -dir * w * 0.3f * (1f - back.value) }) {
                ScreenHost(nav.stack[nav.stack.size - 2], me, school)
            }
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.22f * (1f - back.value) }.background(androidx.compose.ui.graphics.Color.Black))
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            val p = back.value
            translationX = dir * w * p
            if (p > 0f) {
                shadowElevation = 28.dp.toPx() * (1f - p)
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp * p.coerceAtMost(0.4f) / 0.4f)
                clip = true
            }
        }) {
        AnimatedContent(nav.top, transitionSpec = {
            if (nav.instant) (EnterTransition.None togetherWith ExitTransition.None).apply { targetContentZIndex = nav.stack.size.toFloat() }
            else pageTransition(nav.forward, nav.stack.size, reduce)
        }, label = "nav") { screen -> ScreenHost(screen, me, school) }
        }
      }
    }
}

@Composable
private fun ScreenHost(screen: Screen, me: Profile, school: com.globalinstitute.akademos.ui.components.Load<School>) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
                when (screen) {
                    Screen.Home -> if (me.isStaff) StaffHome(school) else ParentHome()
                    is Screen.Attendance -> AttendanceScreen(screen)
                    is Screen.Tests -> TestsScreen(screen.batchId)
                    is Screen.MarksEntry -> MarksEntryScreen(screen.batchId, screen.test)
                    is Screen.StudentDetail -> StudentDetailScreen(screen.studentId)
                    Screen.Classes -> ClassesScreen()
                    Screen.Subjects -> SubjectsScreen()
                    Screen.StudentsAdmin -> StudentsAdminScreen()
                    is Screen.EditStudent -> EditStudentScreen(screen.student)
                    Screen.Batches -> BatchesScreen()
                    is Screen.EditBatch -> EditBatchScreen(screen.batchId)
                    Screen.Keys -> KeysScreen()
                    Screen.People -> PeopleScreen()
                    Screen.Audit -> AuditScreen()
                    Screen.Fees -> FeesScreen()
                    is Screen.StudentFees -> StudentFeesScreen(screen.studentId)
                    Screen.Salaries -> SalariesScreen()
                    Screen.Earnings -> EarningsScreen()
                    is Screen.PlanDay -> PlanDayScreen(screen.date)
                    Screen.TeacherPay -> TeacherPayScreen()
                }
            }
}

private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    TODAY("Today", Icons.Rounded.Today), MARKS("Marks", Icons.Rounded.Grading),
    STUDENTS("Students", Icons.Rounded.Groups), ADMIN("Admin", Icons.Rounded.AdminPanelSettings),
    CHILD("Progress", Icons.Rounded.Face), SETTINGS("Settings", Icons.Rounded.Settings),
}

@Composable
private fun TabbedHome(tabs: List<Tab>, content: @Composable (Tab) -> Unit) {
    val nav = LocalSession.current.nav
    val current = tabs.firstOrNull { it.name == nav.tab } ?: tabs.first()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var barHeight by remember { mutableStateOf(0) }
    // Content runs the full height and scrolls under a translucent tab bar.
    Box(Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(LocalBarInset provides with(density) { barHeight.toDp() }) {
            AnimatedContent(current, transitionSpec = { tabTransition() }, label = "tabs") { t -> content(t) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { barHeight = it.height }) {
            // A soft edge where content meets the bar, instead of a hard line.
            Box(Modifier.fillMaxWidth().height(14.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.5f)))))
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            tabs.forEach { t ->
                NavigationBarItem(
                    selected = t == current, onClick = {
                        if (t != current) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentTick)
                        nav.tab = t.name
                    },
                    icon = { Icon(t.icon, null, Modifier.size(30.dp)) },
                    label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
        }
    }
}

@Composable
private fun StaffHome(school: com.globalinstitute.akademos.ui.components.Load<School>) {
    val me = LocalSession.current.me
    val tabs = buildList { add(Tab.TODAY); add(Tab.MARKS); add(Tab.STUDENTS); if (me.isAdmin) add(Tab.ADMIN); add(Tab.SETTINGS) }
    TabbedHome(tabs) { t ->
        when (t) {
            Tab.TODAY -> TodayScreen(school)
            Tab.MARKS -> MarksHomeScreen()
            Tab.STUDENTS -> StudentsTab()
            Tab.ADMIN -> AdminHomeScreen()
            else -> SettingsScreen()
        }
    }
}

@Composable
private fun ParentHome() {
    TabbedHome(listOf(Tab.CHILD, Tab.SETTINGS)) { t ->
        if (t == Tab.CHILD) ParentChildScreen() else SettingsScreen()
    }
}

// ===================================================================== simple screens
@Composable
fun Splash() {
    Centered {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            com.globalinstitute.akademos.ui.components.BrandMark(96.dp)
            Gap(18.dp)
            Text("Akademos", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

/** Debug-only: explore the app with sample data before the server exists. */
@Composable
private fun DemoRoot(activity: MainActivity) {
    var role by rememberSaveable { mutableStateOf<String?>(null) }
    AnimatedContent(role, transitionSpec = { fadeIn(Motion.spring(1f, 0.45f)) togetherWith fadeOut(Motion.spring(1f, 0.25f)) }, label = "demo") { r ->
        if (r == null) {
            Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
                Brand()
                AkCard {
                    Text("Preview mode", style = MaterialTheme.typography.headlineSmall)
                    Text("Sample students and classes, nothing is saved. Connect the server to use real data.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Gap(16.dp)
                    listOf(Roles.ADMIN to "Administrator", Roles.TEACHER to "Teacher", Roles.PARENT to "Student / parent")
                        .forEach { (k, label) ->
                            PrimaryButton(label, { com.globalinstitute.akademos.data.Demo.role = k; Prefs.lastUserId = "demo"; Prefs.lastRole = k; role = k },
                                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                icon = when (k) { Roles.ADMIN -> Icons.Rounded.AdminPanelSettings; Roles.TEACHER -> Icons.Rounded.Badge; else -> Icons.Rounded.Face })
                        }
                }
            }
        } else {
            LaunchedEffect(Unit) { AppLock.locked.value = false }
            MainShell(com.globalinstitute.akademos.data.Demo.profile, activity) {
                activity.getSystemService(android.app.NotificationManager::class.java).cancelAll(); role = null
            }
        }
    }
}

@Composable
private fun NotConfigured() {
    Centered {
        EmptyBlock(Icons.Rounded.Settings, "Almost ready",
            "This copy of the app has not been connected to the institute's server yet. Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties and rebuild.")
    }
}

@Composable
private fun Brand() {
    Column(Modifier.fillMaxWidth()) {
        Gap(40.dp)
        com.globalinstitute.akademos.ui.components.BrandMark(64.dp)
        Gap(22.dp)
        com.globalinstitute.akademos.ui.components.Eyebrow("Global Institute")
        Gap(2.dp)
        Text("Akademos", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
        Gap(28.dp)
    }
}

@Composable
private fun AuthScreen() {
    val scope = rememberCoroutineScope()
    var newAccount by rememberSaveable { mutableStateOf(false) }
    var login by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Brand()
        AkCard {
            Text(if (newAccount) "Create your account" else "Welcome back", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (newAccount) "You will need the access key given to you by the institute in the next step."
                else "Sign in with the username and password you created.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(16.dp)
            BigField(login, { login = it.trim() }, "Username", leading = Icons.Rounded.Person,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.None))
            Gap()
            BigField(password, { password = it }, "Password", leading = Icons.Rounded.Lock, password = true,
                keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (newAccount) ImeAction.Next else ImeAction.Done))
            if (newAccount) {
                Gap()
                BigField(password2, { password2 = it }, "Type password again", leading = Icons.Rounded.Lock, password = true,
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    supporting = "At least 8 characters. Use letters and numbers.")
            }
            if (error != null) { Gap(); Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
            Gap(20.dp)
            PrimaryButton(
                if (newAccount) "Create account" else "Sign in",
                modifier = Modifier.fillMaxWidth(), loading = busy,
                enabled = login.length >= 3 && password.isNotEmpty(),
                onClick = {
                    error = null
                    if (newAccount) {
                        if (password.length < 8 || !password.any { it.isDigit() } || !password.any { it.isLetter() }) {
                            error = "Password needs at least 8 characters with letters and numbers."; return@PrimaryButton
                        }
                        if (password != password2) { error = "The two passwords don't match."; return@PrimaryButton }
                    }
                    busy = true
                    scope.launch {
                        runCatching { if (newAccount) Repo.signUp(login, password) else Repo.signIn(login, password) }
                            .onFailure { error = it.friendly() }
                        busy = false
                    }
                },
            )
        }
        Gap(8.dp)
        TextButton(onClick = { newAccount = !newAccount; error = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (newAccount) "I already have an account" else "New here? Create an account", style = MaterialTheme.typography.titleMedium)
        }
        Gap(24.dp)
    }
}

@Composable
private fun RedeemScreen(onDone: () -> Unit, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Brand()
        AkCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Key, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.size(12.dp))
                Text("Enter your access key", style = MaterialTheme.typography.headlineSmall)
            }
            Gap(6.dp)
            Text(
                "The institute gives every teacher and parent their own one-time key. It decides what you can see and do.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(16.dp)
            BigField(name, { name = it }, "Your full name", leading = Icons.Rounded.Badge,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
            Gap()
            BigField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' }.take(14) }, "Access key",
                leading = Icons.Rounded.Key, supporting = "12 letters and numbers, like ABCD-EFGH-JKLM",
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done))
            if (error != null) { Gap(); Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge) }
            Gap(20.dp)
            PrimaryButton("Continue", modifier = Modifier.fillMaxWidth(), loading = busy,
                enabled = code.count { it.isLetterOrDigit() } == 12 && name.trim().length >= 2, onClick = {
                busy = true; error = null
                scope.launch {
                    runCatching { Repo.redeem(code, name) }
                        .onSuccess { if (it.ok) onDone() else error = it.error }
                        .onFailure { error = it.friendly() }
                    busy = false
                }
            })
        }
        TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
            Text("Use a different account", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun LockScreen(activity: MainActivity, me: Profile) {
    var msg by remember { mutableStateOf<String?>(null) }
    val doUnlock = { activity.unlock { ok, err -> if (ok) AppLock.locked.value = false else msg = err } }
    LaunchedEffect(Unit) { doUnlock() }
    Centered {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.Fingerprint, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
            Gap(16.dp)
            Text("Hello, ${me.fullName.substringBefore(' ')}", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                if (me.role == Roles.ADMIN) "Administrator access is locked" else "Teacher access is locked",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (msg != null) { Gap(); Text(msg!!, color = MaterialTheme.colorScheme.error) }
            Gap(28.dp)
            PrimaryButton("Unlock", onClick = doUnlock, icon = Icons.Rounded.Fingerprint)
        }
    }
}
