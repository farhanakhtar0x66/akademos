package com.globalinstitute.akademos.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.globalinstitute.akademos.data.Status
import com.globalinstitute.akademos.data.friendly
import com.globalinstitute.akademos.ui.theme.LocalStatus
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

// ---------------------------------------------------------------- motion
val Bouncy = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
val Smooth = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

/**
 * The Akademos mark: a tick that rises into a raised hand — "Present!" — on jamun.
 * Same drawing as the launcher icon.
 */
@Composable
fun BrandMark(size: Dp = 88.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawRoundRect(com.globalinstitute.akademos.ui.theme.Ak.Jamun, cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.3f))
        // same geometry as res/drawable/ic_launcher_fg.xml, cropped to the icon's visible 72-unit circle
        val u = w / 72f
        fun p(v: Float) = (v - 18f) * u
        val tick = Path().apply { moveTo(p(37f), p(57f)); lineTo(p(49f), p(69f)); lineTo(p(70f), p(44f)) }
        drawPath(tick, com.globalinstitute.akademos.ui.theme.Ak.Marigold, style = Stroke(width = 9.5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(com.globalinstitute.akademos.ui.theme.Ak.Marigold, radius = 5.6f * u, center = Offset(p(76f), p(33.5f)))
    }
}

/** Small uppercase label above a title: a date, a section, a status. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier, style = MaterialTheme.typography.labelSmall, color = color)
}

// ---------------------------------------------------------------- structure
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    sidePadding: Dp = 20.dp,
    eyebrow: String? = null,
) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = sidePadding).padding(top = 8.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(52.dp).padding(end = 4.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.size(30.dp))
                }
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                if (eyebrow != null) { Eyebrow(eyebrow); Spacer(Modifier.height(4.dp)) }
                Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            actions()
        }
    }
}

@Composable
fun AkCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, Bouncy, label = "card")
    Surface(
        modifier = modifier.fillMaxWidth().scale(scale)
            .then(if (onClick != null) Modifier.clip(MaterialTheme.shapes.large).clickable(src, null, onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.large,
        color = color,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            if (accent != null) Box(Modifier.padding(start = 14.dp, top = 18.dp, bottom = 18.dp).width(5.dp).fillMaxHeight().clip(CircleShape).background(accent))
            Column(Modifier.weight(1f).padding(contentPadding), content = content)
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val haptic = LocalHapticFeedback.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, Bouncy, label = "btn")
    Button(
        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        modifier = modifier.heightIn(min = 60.dp).scale(scale),
        enabled = enabled && !loading,
        interactionSource = src,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), color = contentColor, strokeWidth = 3.dp)
        else {
            if (icon != null) { Icon(icon, null, Modifier.size(26.dp)); Spacer(Modifier.width(10.dp)) }
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    FilledTonalButton(
        onClick = onClick, modifier = modifier.heightIn(min = 56.dp), enabled = enabled,
        shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BigField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    password: Boolean = false,
    keyboard: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    supporting: String? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = leading?.let { { Icon(it, null) } },
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboard,
        shape = RoundedCornerShape(18.dp),
        supportingText = supporting?.let { { Text(it) } },
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, onColor: Color = Color.White, icon: ImageVector? = null) {
    Row(
        modifier.clip(CircleShape).background(color).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = onColor); Spacer(Modifier.width(4.dp)) }
        Text(text, color = onColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}



private val avatarColors = listOf(
    Color(0xFF5B3E8C), Color(0xFF1F7A8C), Color(0xFFC77700), Color(0xFF2E8B57),
    Color(0xFFB83280), Color(0xFF3F51B5), Color(0xFFC2410C), Color(0xFF4A5568),
)

fun colorFor(key: String): Color = avatarColors[abs(key.hashCode()) % avatarColors.size]

fun initials(name: String) = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    .let { p -> (p.firstOrNull()?.take(1) ?: "") + (if (p.size > 1) p.last().take(1) else "") }.uppercase()

@Composable
fun Avatar(name: String, size: Dp = 52.dp, color: Color = colorFor(name)) {
    Box(Modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(initials(name), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- states
@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(44.dp), strokeWidth = 4.dp)
    }
}

@Composable
fun ErrorBlock(error: Throwable, onRetry: () -> Unit) {
    AkCard(color = MaterialTheme.colorScheme.errorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudOff, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(error.friendly(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
        }
        Spacer(Modifier.height(12.dp))
        SecondaryButton("Try again", onRetry)
    }
}

@Composable
fun EmptyBlock(icon: ImageVector, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(20.dp)); action() }
    }
}

/** Load something once (and again on [reload]); shows nothing fancy, just the three states. */
class Load<T>(val data: T?, val error: Throwable?, val loading: Boolean, val reload: () -> Unit)

@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend () -> T): Load<T> {
    var tick by remember { mutableIntStateOf(0) }
    var data by remember(*keys) { mutableStateOf<T?>(null) }
    var error by remember(*keys) { mutableStateOf<Throwable?>(null) }
    var loading by remember(*keys) { mutableStateOf(true) }
    LaunchedEffect(*keys, tick) {
        loading = true; error = null
        runCatching { block() }.onSuccess { data = it }.onFailure { if (it !is kotlinx.coroutines.CancellationException) error = it }
        loading = false
    }
    return Load(data, error, loading) { tick++ }
}

// ---------------------------------------------------------------- attendance tile
@Composable
fun StudentTile(
    name: String,
    detail: String?,
    status: String?,          // null = not marked yet
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leftMode: Boolean = false, // departure screen: status "left"/null
) {
    val s = LocalStatus.current
    val haptic = LocalHapticFeedback.current
    val target = when (status) {
        Status.PRESENT, "left" -> s.presentSoft
        Status.ABSENT -> s.absentSoft
        Status.LATE -> s.lateSoft
        else -> MaterialTheme.colorScheme.surface
    }
    val edge = when (status) {
        Status.PRESENT, "left" -> s.present
        Status.ABSENT -> s.absent
        Status.LATE -> s.late
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val bg by animateColorAsState(target, androidx.compose.animation.core.tween(220), label = "bg")
    val border by animateColorAsState(edge, androidx.compose.animation.core.tween(220), label = "edge")
    var bump by remember { mutableIntStateOf(0) }
    val pop by animateFloatAsState(if (bump % 2 == 1) 1.04f else 1f, Bouncy, label = "pop", finishedListener = { if (bump % 2 == 1) bump++ })
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.95f else 1f, Bouncy, label = "press")

    Box(
        modifier
            .scale(pop * press)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(22.dp))
            .clickable(src, null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                bump = if (bump % 2 == 0) bump + 1 else bump + 2
                onClick()
            }
            .padding(14.dp)
            .heightIn(min = 60.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val label = when (status) {
                    Status.PRESENT -> "Present"; Status.ABSENT -> "Absent"; Status.LATE -> "Late"
                    "left" -> "Left"; else -> if (leftMode) "Still here" else "Tap to mark"
                }
                Text(
                    listOfNotNull(detail, label).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status == null) MaterialTheme.colorScheme.onSurfaceVariant else border,
                    fontWeight = if (status == null) FontWeight.Normal else FontWeight.SemiBold,
                )
            }
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(status != null, enter = scaleIn(Bouncy) + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(border), contentAlignment = Alignment.Center) {
                        Icon(
                            when (status) { Status.ABSENT -> Icons.Rounded.Close; Status.LATE -> Icons.Rounded.Schedule; else -> Icons.Rounded.Check },
                            null, Modifier.size(26.dp), tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- helpers
private val clock = DateTimeFormatter.ofPattern("h:mm a")
fun prettyTime(t: String): String = runCatching { LocalTime.parse(t).format(clock) }.getOrDefault(t)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = 20.dp, bottom = 10.dp))
}

@Composable
fun Chip(selected: Boolean, text: String, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.primary) {
    val bg by animateColorAsState(if (selected) color else MaterialTheme.colorScheme.surface, label = "chip")
    val onSel = if (color.luminance() > 0.45f) Color(0xFF0B1B33) else Color.White
    val fg by animateColorAsState(if (selected) onSel else MaterialTheme.colorScheme.onSurface, label = "chipfg")
    Box(
        Modifier.clip(CircleShape).background(bg)
            .border(1.5.dp, if (selected) color else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
    ) { Text(text, color = fg, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))
