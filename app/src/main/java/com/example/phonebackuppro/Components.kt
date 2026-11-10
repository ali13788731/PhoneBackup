package com.example.phonebackuppro

import androidx.compose.ui.draw.alpha
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale as dsScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
//  Interaction helpers
// ---------------------------------------------------------------------------------------------

/** Click with a springy "press" animation and optional haptic tick. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val anim = LocalSettings.current?.animations != false
    val s by animateFloatAsState(
        if (pressed && anim) 0.95f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 520f), label = "bounce"
    )
    val haptic = LocalHapticFeedback.current
    val hapticOn = LocalSettings.current?.haptics == true
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, enabled = enabled) {
            if (hapticOn) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Soft moving highlight for placeholders while data is loading. */
fun Modifier.shimmer(): Modifier = composed {
    val t by rememberInfiniteTransition(label = "shimmer").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shimmerT"
    )
    val hi = if (pal.dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.7f)
    this.drawWithContent {
        drawContent()
        val w = size.width
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, hi, Color.Transparent),
                startX = -w + t * 2.2f * w, endX = t * 2.2f * w
            )
        )
    }
}

/** Fades + slides its content in, [index] * 70ms after it appears (staggered entrance). */
@Composable
fun Reveal(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val anim = LocalSettings.current?.animations != false
    var shown by remember { mutableStateOf(!anim) }
    LaunchedEffect(Unit) {
        if (anim) { delay(index * 70L); shown = true }
    }
    AnimatedVisibility(
        visible = shown, modifier = modifier,
        enter = fadeIn(tween(420)) + slideInVertically(tween(480, easing = FastOutSlowInEasing)) { it / 5 }
    ) { content() }
}

@Composable
fun CountText(target: Int, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier) {
    val anim = LocalSettings.current?.animations != false
    val v by animateIntAsState(target, tween(if (anim) 1000 else 0, easing = FastOutSlowInEasing), label = "count")
    Text(v.fa(), style = style, color = color, modifier = modifier)
}

// ---------------------------------------------------------------------------------------------
//  Backgrounds, cards, buttons
// ---------------------------------------------------------------------------------------------

@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = pal
    val anim = LocalSettings.current?.animations != false
    val t by rememberInfiniteTransition(label = "bg").animateFloat(
        0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "bgT"
    )
    val k = if (anim) t else 0.5f
    Box(
        modifier
            .fillMaxSize()
            .background(p.bg)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawCircle(
                    Brush.radialGradient(
                        listOf(p.g1.copy(alpha = if (p.dark) 0.30f else 0.20f), Color.Transparent),
                        center = Offset(w * (0.05f + 0.25f * k), h * 0.05f), radius = w * 0.85f
                    ),
                    radius = w * 0.85f, center = Offset(w * (0.05f + 0.25f * k), h * 0.05f)
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(p.g2.copy(alpha = if (p.dark) 0.20f else 0.14f), Color.Transparent),
                        center = Offset(w * (0.95f - 0.25f * k), h * 0.92f), radius = w * 0.9f
                    ),
                    radius = w * 0.9f, center = Offset(w * (0.95f - 0.25f * k), h * 0.92f)
                )
            },
        content = content
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    corner: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val p = pal
    val shape = RoundedCornerShape(corner)
    val base = modifier
        .clip(shape)
        .background(p.card)
        .border(1.dp, p.stroke, shape)
    Box(if (onClick != null) base.bounceClick(onClick = onClick) else base) { content() }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    locked: Boolean = false,
    compact: Boolean = false
) {
    val p = pal
    val shape = RoundedCornerShape(if (compact) 14.dp else 18.dp)
    Row(
        modifier
            .alpha(if (locked) 0.5f else 1f)
            .clip(shape)
            .background(if (enabled) p.brush else Brush.linearGradient(listOf(p.cardHigh, p.cardHigh)))
            .bounceClick(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 10.dp else 16.dp, vertical = if (compact) 10.dp else 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val c = if (enabled) Color.White else p.textDim
        if (icon != null) { Icon(icon, null, tint = c, modifier = Modifier.size(if (compact) 17.dp else 20.dp)); Spacer(Modifier.width(if (compact) 5.dp else 8.dp)) }
        Text(
            text, style = (if (compact) Ty.smallM else Ty.bodyM).copy(fontWeight = FontWeight.Bold), color = c,
            maxLines = 1, softWrap = false
        )
        if (locked) { Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.Lock, null, tint = c, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = pal.text,
    locked: Boolean = false,
    compact: Boolean = false
) {
    val p = pal
    val shape = RoundedCornerShape(if (compact) 14.dp else 18.dp)
    Row(
        modifier
            .alpha(if (locked) 0.5f else 1f)
            .clip(shape)
            .background(p.cardHigh)
            .border(1.dp, p.stroke, shape)
            .bounceClick(onClick = onClick)
            .padding(horizontal = if (compact) 8.dp else 14.dp, vertical = if (compact) 10.dp else 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) { Icon(icon, null, tint = tint, modifier = Modifier.size(if (compact) 16.dp else 19.dp)); Spacer(Modifier.width(if (compact) 4.dp else 8.dp)) }
        Text(text, style = if (compact) Ty.smallM else Ty.bodyM, color = tint, maxLines = 1, softWrap = false)
        if (locked) { Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.Lock, null, tint = p.textDim, modifier = Modifier.size(15.dp)) }
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun FancyCheck(checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, locked: Boolean = false) {
    val p = pal
    val s by animateFloatAsState(
        if (checked) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 420f), label = "check"
    )
    Box(modifier.alpha(if (locked) 0.45f else 1f).size(28.dp).bounceClick(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .border(2.dp, if (checked) Color.Transparent else p.textDim.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = s; scaleY = s; alpha = s.coerceIn(0f, 1f) }
                    .background(p.brush),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
        }
        if (locked) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(p.card)
                    .border(1.dp, p.stroke, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.Lock, null, tint = p.warn, modifier = Modifier.size(9.dp)) }
        }
    }
}

private val AvatarColors = listOf(
    Color(0xFF7C5CFF) to Color(0xFFB36BFF),
    Color(0xFF1E8CFF) to Color(0xFF22D3EE),
    Color(0xFF0FBF8F) to Color(0xFF7BE05A),
    Color(0xFFFF6B4A) to Color(0xFFFFB347),
    Color(0xFFEC4899) to Color(0xFFF59E0B),
    Color(0xFF6366F1) to Color(0xFF22D3EE)
)

@Composable
fun Avatar(title: String, size: Dp = 46.dp) {
    val (c1, c2) = AvatarColors[(title.hashCode() and 0x7fffffff) % AvatarColors.size]
    val letter = title.trim().firstOrNull { it.isLetter() }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c1, c2))),
        contentAlignment = Alignment.Center
    ) {
        if (letter != null) Text(letter.uppercase(), style = Ty.h2, color = Color.White)
        else Icon(Icons.Rounded.Person, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = pal
    val shape = RoundedCornerShape(50)
    val bg by animateFloatAsState(if (selected) 1f else 0f, tween(260), label = "chip")
    Box(
        Modifier
            .clip(shape)
            .background(p.cardHigh)
            .drawBehind { drawRect(p.g1.copy(alpha = 0.9f * bg)) }
            .border(1.dp, if (selected) Color.Transparent else p.stroke, shape)
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = Ty.smallM, color = if (selected) Color.White else p.text)
    }
}

// ---------------------------------------------------------------------------------------------
//  Animated logo
// ---------------------------------------------------------------------------------------------

/**
 * The app mark: a phone with a "save" arrow inside a backup ring.
 * Each part has its own progress so the splash screen can draw them one after another.
 */
@Composable
fun LogoCanvas(
    modifier: Modifier = Modifier,
    ring: Float = 1f,
    phone: Float = 1f,
    arrow: Float = 1f,
    glow: Float = 0.6f,
    spin: Float = 0f
) {
    val p = pal
    Canvas(modifier) {
        val s = size.minDimension
        val c = Offset(size.width / 2f, size.height / 2f)

        if (glow > 0f) {
            drawCircle(
                Brush.radialGradient(
                    listOf(p.g1.copy(alpha = 0.55f * glow), Color.Transparent), center = c, radius = s * 0.62f
                ),
                radius = s * 0.62f, center = c
            )
        }

        // ring
        val rr = s * 0.36f
        val rw = s * 0.045f
        val start = -60f + spin
        val sweep = 300f * ring
        if (ring > 0f) {
            drawArc(
                brush = Brush.sweepGradient(listOf(p.g2, p.g1, p.g2), center = c),
                startAngle = start, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(c.x - rr, c.y - rr), size = Size(rr * 2, rr * 2),
                style = Stroke(rw, cap = StrokeCap.Round)
            )
            val a = Math.toRadians((start + sweep).toDouble())
            drawCircle(Color.White, rw * 0.95f, Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat()))
        }

        // phone
        if (phone > 0f) {
            dsScale(phone, c) {
                val pw = s * 0.27f
                val ph = s * 0.46f
                val tl = Offset(c.x - pw / 2, c.y - ph / 2)
                drawRoundRect(Color.White.copy(alpha = 0.12f), tl, Size(pw, ph), CornerRadius(s * 0.06f))
                drawRoundRect(Color.White, tl, Size(pw, ph), CornerRadius(s * 0.06f), style = Stroke(s * 0.028f))
                drawLine(
                    Color.White.copy(alpha = 0.7f),
                    Offset(c.x - pw * 0.18f, tl.y + ph * 0.08f), Offset(c.x + pw * 0.18f, tl.y + ph * 0.08f),
                    strokeWidth = s * 0.016f, cap = StrokeCap.Round
                )
                // tray
                drawLine(
                    Color.White, Offset(c.x - s * 0.085f, c.y + s * 0.085f), Offset(c.x + s * 0.085f, c.y + s * 0.085f),
                    strokeWidth = s * 0.028f, cap = StrokeCap.Round
                )
            }
        }

        // arrow dropping into the tray
        if (arrow > 0f) {
            val dy = (1f - arrow) * -s * 0.07f
            val w = s * 0.03f
            val col = Color.White.copy(alpha = arrow.coerceIn(0f, 1f))
            drawLine(col, Offset(c.x, c.y - s * 0.13f + dy), Offset(c.x, c.y + s * 0.035f + dy), w, StrokeCap.Round)
            val head = Path().apply {
                moveTo(c.x - s * 0.055f, c.y - s * 0.02f + dy)
                lineTo(c.x, c.y + s * 0.037f + dy)
                lineTo(c.x + s * 0.055f, c.y - s * 0.02f + dy)
            }
            drawPath(head, col, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** The logo on a rounded gradient tile (used in headers). */
@Composable
fun LogoTile(size: Dp = 46.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(pal.brush),
        contentAlignment = Alignment.Center
    ) { LogoCanvas(Modifier.size(size), glow = 0f) }
}

// ---------------------------------------------------------------------------------------------
//  Progress UI
// ---------------------------------------------------------------------------------------------

@Composable
fun RingProgress(progress: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val p = pal
    val indeterminate = progress < 0f
    val shown by animateFloatAsState(progress.coerceAtLeast(0f), tween(520, easing = FastOutSlowInEasing), label = "ring")
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spinA"
    )
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.85f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseA"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.minDimension * 0.085f
            val d = size.minDimension - w
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawCircle(
                Brush.radialGradient(
                    listOf(p.g1.copy(alpha = 0.28f * pulse), Color.Transparent),
                    center = center, radius = size.minDimension * 0.62f
                ),
                radius = size.minDimension * 0.62f
            )
            drawArc(p.cardHigh, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
            val brush = Brush.sweepGradient(listOf(p.g2, p.g1, p.g2), center = center)
            if (indeterminate) {
                drawArc(brush, spin, 110f, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
            } else {
                val sweep = 360f * shown
                drawArc(brush, -90f, sweep, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
                val a = Math.toRadians((-90f + sweep).toDouble())
                val r = d / 2
                if (shown > 0.01f) drawCircle(
                    Color.White, w * 0.32f,
                    Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
                )
            }
        }
        content()
    }
}

@Composable
fun ShimmerBar(progress: Float, modifier: Modifier = Modifier) {
    val p = pal
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(520, easing = FastOutSlowInEasing), label = "bar")
    val t by rememberInfiniteTransition(label = "barShimmer").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "barT"
    )
    Canvas(modifier.height(10.dp).fillMaxWidth()) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(p.cardHigh, cornerRadius = r)
        val fw = size.width * (if (progress < 0f) 0.35f else shown)
        val fx = if (progress < 0f) (size.width - fw) * t else 0f
        if (fw > 0f) {
            val fill = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(fx, 0f, fx + fw, size.height, r))
            }
            drawPath(fill, p.brush)
            clipRect(fx, 0f, fx + fw, size.height) {
                val x = fx + fw * (t * 2f - 0.5f)
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                        startX = x - 40f, endX = x + 40f
                    )
                )
            }
        }
    }
}

/** A coloured disc that pops in, then the tick (or cross) is drawn stroke by stroke. */
@Composable
fun ResultMark(ok: Boolean, modifier: Modifier = Modifier) {
    val p = pal
    val pop = remember { Animatable(0f) }
    val draw = remember { Animatable(0f) }
    LaunchedEffect(ok) {
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f))
        draw.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
    }
    val c1 = if (ok) p.success else p.danger
    val c2 = if (ok) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sc = pop.value
        dsScale(sc, center) {
            drawCircle(
                Brush.radialGradient(listOf(c1.copy(alpha = 0.35f), Color.Transparent), center, w * 0.62f),
                radius = w * 0.62f
            )
            drawCircle(Brush.linearGradient(listOf(c1, c2)), radius = w * 0.40f)
        }
        val path = Path()
        if (ok) {
            path.moveTo(w * 0.34f, h * 0.52f); path.lineTo(w * 0.46f, h * 0.63f); path.lineTo(w * 0.68f, h * 0.39f)
        } else {
            path.moveTo(w * 0.38f, h * 0.38f); path.lineTo(w * 0.62f, h * 0.62f)
            path.moveTo(w * 0.62f, h * 0.38f); path.lineTo(w * 0.38f, h * 0.62f)
        }
        val seg = Path()
        val pm = PathMeasure()
        pm.setPath(path, false)
        // PathMeasure walks only the first contour, so for the cross draw both strokes by hand
        if (ok) {
            pm.getSegment(0f, pm.length * draw.value, seg, true)
            drawPath(seg, Color.White, style = Stroke(w * 0.065f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            val k = draw.value
            drawLine(
                Color.White, Offset(w * 0.38f, h * 0.38f),
                Offset(w * (0.38f + 0.24f * k), h * (0.38f + 0.24f * k)), w * 0.065f, StrokeCap.Round
            )
            drawLine(
                Color.White, Offset(w * 0.62f, h * 0.38f),
                Offset(w * (0.62f - 0.24f * k), h * (0.38f + 0.24f * k)), w * 0.065f, StrokeCap.Round
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
//  Dialogs
// ---------------------------------------------------------------------------------------------

@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable () -> Unit
) {
    val p = pal
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    if (dismissible) onDismiss()
                }
                .padding(22.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.88f, animationSpec = spring(0.62f, 380f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(p.card)
                        .border(1.dp, p.stroke, RoundedCornerShape(28.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                ) { content() }
            }
        }
    }
}

@Composable
fun MessageDialog(text: String, onDismiss: () -> Unit) {
    AppDialog(onDismiss) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, style = Ty.body, color = pal.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            GradientButton("باشه", onDismiss, Modifier.fillMaxWidth())
        }
    }
}

/** What a running / finished background job looks like to the UI. */
sealed interface JobState {
    /** [progress] < 0 means "indeterminate". */
    data class Running(val title: String, val stage: String, val progress: Float) : JobState
    data class Done(
        val title: String,
        val ok: Boolean,
        val lines: List<String>,
        val shareUri: Uri? = null,
        val switchSmsBack: Boolean = false,
        /** Neat per-section / per-person report of what an export or import did. */
        val report: ResultReport? = null
    ) : JobState
}

/** Full-screen progress for export / import: animated ring, shimmering bar, then a result card. */
@Composable
fun JobOverlay(
    job: JobState?,
    canShare: Boolean,
    onDismiss: () -> Unit,
    onShare: (Uri) -> Unit,
    onLockedShare: () -> Unit,
    onSmsSettings: () -> Unit
) {
    if (job == null) return
    val p = pal
    AppDialog(onDismiss = onDismiss, dismissible = job is JobState.Done) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (job) {
                is JobState.Running -> {
                    Text(job.title, style = Ty.h2, color = p.text)
                    Spacer(Modifier.height(20.dp))
                    RingProgress(job.progress, Modifier.size(150.dp)) {
                        if (job.progress >= 0f) {
                            val shown by animateFloatAsState(job.progress, tween(520), label = "pct")
                            Text(
                                "٪" + (shown * 100).toInt().coerceIn(0, 100).toString().toFaDigits(),
                                style = Ty.big, color = p.text
                            )
                        } else {
                            LogoCanvas(Modifier.size(70.dp), glow = 0f)
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    ShimmerBar(job.progress)
                    Spacer(Modifier.height(14.dp))
                    Text(job.stage, style = Ty.small, color = p.textDim, textAlign = TextAlign.Center)
                }

                is JobState.Done -> {
                    val hasReport = job.report != null
                    ResultMark(job.ok, Modifier.size(if (hasReport) 84.dp else 120.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(job.title, style = Ty.h2, color = p.text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    if (job.report != null) {
                        val maxH = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
                        ReportBody(job.report, Modifier.fillMaxWidth().heightIn(max = maxH))
                    }
                    job.lines.forEach {
                        Text(it, style = Ty.body, color = p.textDim, textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(20.dp))
                    if (job.switchSmsBack) {
                        SoftButton(
                            "بازگرداندن برنامه‌ی پیامک قبلی", onSmsSettings,
                            Modifier.fillMaxWidth(), tint = p.warn
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    GradientButton("باشه", onDismiss, Modifier.fillMaxWidth())
                    if (job.shareUri != null) {
                        Spacer(Modifier.height(10.dp))
                        SoftButton(
                            "اشتراک‌گذاری فایل",
                            { if (canShare) onShare(job.shareUri) else onLockedShare() },
                            Modifier.fillMaxWidth(), icon = Icons.Rounded.Share, locked = !canShare
                        )
                    }
                }
            }
        }
    }
}


/** Bottom system-bar height, used so the last item of a scrolling screen never sticks to the edge. */
@Composable
fun navBottom(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
