package com.example.phonebackuppro

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

private class Particle(val x: Float, val y: Float, val r: Float, val cycles: Int, val phase: Float)

/**
 * First thing the user sees: the logo draws itself (ring -> phone -> arrow), the name fades in
 * and, under it, "طراحی شده توسط Mr.PC". Total ~3.6 s (about 1 s when animations are turned off).
 */
@Composable
fun SplashScreen(animated: Boolean, onFinished: () -> Unit) {
    val ring = remember { Animatable(0f) }
    val phone = remember { Animatable(0f) }
    val arrow = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val credit = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (!animated) {
            ring.snapTo(1f); phone.snapTo(1f); arrow.snapTo(1f); name.snapTo(1f); credit.snapTo(1f)
            delay(900)
            onFinished()
            return@LaunchedEffect
        }
        coroutineScope {
            launch { ring.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }
            launch { delay(380); phone.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 170f)) }
            launch { delay(900); arrow.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 190f)) }
            launch { delay(1350); name.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
            launch { delay(1900); credit.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        }
        delay(1000)
        onFinished()
    }

    val infinite = rememberInfiniteTransition(label = "splash")
    val t by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "t")
    val glow by infinite.animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow"
    )
    val particles = remember {
        val rnd = Random(7)
        List(30) { Particle(rnd.nextFloat(), rnd.nextFloat(), 1.5f + rnd.nextFloat() * 3.5f, 1 + rnd.nextInt(2), rnd.nextFloat()) }
    }
    val accent = Accents[0]

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF080818), Color(0xFF1A0F4D), Color(0xFF080818)))),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            particles.forEach { p ->
                val y = (((p.y - t * p.cycles) % 1f) + 1f) % 1f
                val tw = 0.5f + 0.5f * kotlin.math.sin((t * 6.2831855f * p.cycles) + p.phase * 6.28f)
                drawCircle(
                    Color.White.copy(alpha = 0.10f + 0.30f * tw),
                    radius = p.r.dp.toPx(),
                    center = Offset(p.x * size.width, y * size.height)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            LogoCanvas(
                Modifier.size(230.dp),
                ring = ring.value, phone = phone.value, arrow = arrow.value,
                glow = glow * (0.3f + 0.7f * ring.value)
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "Phone Backup",
                style = Ty.title.copy(fontSize = 26.sp),
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    alpha = name.value
                    translationY = (1f - name.value) * 28.dp.toPx()
                }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White.copy(alpha = 0.72f))) { append("طراحی شده توسط ") }
                    withStyle(
                        SpanStyle(
                            brush = Brush.linearGradient(listOf(accent.c2, Color(0xFF38E8FF))),
                            fontWeight = FontWeight.Bold
                        )
                    ) { append("Mr.PC") }
                },
                style = Ty.bodyM.copy(fontSize = 15.sp),
                modifier = Modifier.graphicsLayer {
                    alpha = credit.value
                    translationY = (1f - credit.value) * 22.dp.toPx()
                }
            )
        }
    }
}
