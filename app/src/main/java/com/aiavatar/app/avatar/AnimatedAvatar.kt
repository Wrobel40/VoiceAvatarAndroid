package com.aiavatar.app.avatar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.aiavatar.app.audio.RecordingState
import kotlin.math.*

@Composable
fun AnimatedAvatar(
    recordingState: RecordingState,
    amplitude: Float,
    isSpeaking: Boolean,
    style: AvatarStyle = AvatarStyle.DARK_KNIGHT,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "av")

    val floatY by inf.animateFloat(0f, -10f,
        infiniteRepeatable(tween(2200, easing = EaseInOutSine), RepeatMode.Reverse), "fy")
    val glowAlpha by inf.animateFloat(0.3f, 1f,
        infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse), "ga")
    val shimmer by inf.animateFloat(0f, 360f,
        infiniteRepeatable(tween(6000, easing = LinearEasing)), "sh")
    val blinkP by inf.animateFloat(0f, 1f,
        infiniteRepeatable(keyframes {
            durationMillis = 4000
            0f at 0; 0f at 3600; 1f at 3700; 0f at 3800
        }), "bl")
    val swordGlow by inf.animateFloat(0.5f, 1f,
        infiniteRepeatable(tween(800, easing = EaseInOutSine), RepeatMode.Reverse), "sg")
    val listenPulse by inf.animateFloat(1f, 1.12f,
        infiniteRepeatable(tween(500, easing = EaseInOutSine), RepeatMode.Reverse), "lp")

    val isListening = recordingState == RecordingState.LISTENING
    val isProcessing = recordingState == RecordingState.PROCESSING

    // Style colors
    val body1    = Color(style.bodyColor1)
    val body2    = Color(style.bodyColor2)
    val armor1   = Color(style.armorColor1)
    val armor2   = Color(style.armorColor2)
    val reactor  = Color(style.reactorColor)
    val eyeCol   = Color(style.eyeColor)
    val particle = Color(style.particleColor)
    val glow     = Color(style.glowColor)

    val isDark = style == AvatarStyle.DARK_KNIGHT

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().offset(y = androidx.compose.ui.unit.Dp(floatY))) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val sc = minOf(size.width, size.height) / 420f

            drawAura(cx, cy, sc, glow, glowAlpha, isListening, listenPulse, amplitude)
            drawBody(cx, cy, sc, body1, body2, armor1, armor2, reactor, shimmer, glowAlpha)
            drawHead(cx, cy, sc, body1, body2, eyeCol, reactor, blinkP, amplitude, isSpeaking, isListening, isDark)
            if (isDark) drawSword(cx, cy, sc, reactor, swordGlow)
            if (isDark) drawNeonLines(cx, cy, sc, reactor, glowAlpha)
            drawParticles(cx, cy, sc, shimmer, amplitude, particle)
            if (isProcessing) drawProcessingRing(cx, cy, sc, reactor, shimmer)
        }
    }
}

private fun DrawScope.drawAura(
    cx: Float, cy: Float, sc: Float,
    glow: Color, glowAlpha: Float,
    isListening: Boolean, pulse: Float, amplitude: Float
) {
    val r = size.minDimension * 0.44f + amplitude * 25f * sc
    val s = if (isListening) pulse else 1f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.Transparent, glow.copy(glowAlpha * 0.25f), Color.Transparent),
            Offset(cx, cy), r * 1.4f * s
        ),
        radius = r * 1.4f * s, center = Offset(cx, cy)
    )
    if (isListening || amplitude > 0.1f) {
        repeat(3) { i ->
            drawCircle(
                color = glow.copy(alpha = (0.4f - i * 0.12f) * pulse),
                radius = r * (0.85f + i * 0.12f) * s,
                center = Offset(cx, cy),
                style = Stroke(width = (2f - i * 0.5f) * sc)
            )
        }
    }
}

private fun DrawScope.drawBody(
    cx: Float, cy: Float, sc: Float,
    body1: Color, body2: Color, armor1: Color, armor2: Color,
    reactor: Color, shimmer: Float, glowAlpha: Float
) {
    val bw = size.width * 0.30f
    val bh = size.height * 0.34f
    val bt = cy + size.height * 0.05f

    // Torso
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(body1, body2), startY = bt, endY = bt + bh),
        topLeft = Offset(cx - bw / 2, bt), size = Size(bw, bh),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f * sc)
    )
    // Chest plate
    val aw = bw * 0.72f; val ah = bh * 0.44f
    drawRoundRect(
        brush = Brush.linearGradient(listOf(armor1, body2, armor2),
            Offset(cx - aw / 2, bt + 8f * sc), Offset(cx + aw / 2, bt + ah)),
        topLeft = Offset(cx - aw / 2, bt + 8f * sc), size = Size(aw, ah),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f * sc)
    )
    // Reactor
    val shimRad = Math.toRadians(shimmer.toDouble())
    val rg = (sin(shimRad * 3) * 0.3f + 0.7f).toFloat()
    val rc = Offset(cx, bt + ah * 0.5f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(reactor.copy(rg), reactor.copy(rg * 0.3f), Color.Transparent),
            rc, 22f * sc
        ), radius = 22f * sc, center = rc
    )
    drawCircle(color = reactor.copy(rg * 0.9f), radius = 8f * sc, center = rc)
    // Shoulders
    listOf(-1f, 1f).forEach { side ->
        val sx = cx + side * (bw / 2 + 3f * sc)
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(armor1, body2), bt, bt + 34f * sc),
            topLeft = Offset(sx - 15f * sc, bt), size = Size(30f * sc, 34f * sc),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(9f * sc)
        )
        // Neon shoulder accent
        drawLine(reactor.copy(0.8f),
            Offset(sx - 7f * sc, bt + 5f * sc), Offset(sx + 7f * sc, bt + 5f * sc),
            2f * sc)
    }
    // Neck
    drawRect(color = body1, topLeft = Offset(cx - 13f * sc, bt - 20f * sc), size = Size(26f * sc, 22f * sc))
    // Belt
    drawRoundRect(
        color = armor1,
        topLeft = Offset(cx - bw / 2, bt + bh - 20f * sc), size = Size(bw, 20f * sc),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(0f, 18f * sc)
    )
    // Gold knee accents (matching the reference image)
    listOf(-1f, 1f).forEach { side ->
        val kx = cx + side * bw * 0.28f
        val ky = bt + bh + 20f * sc
        drawRoundRect(
            color = Color(0xFFCC8833),
            topLeft = Offset(kx - 10f * sc, ky), size = Size(20f * sc, 12f * sc),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * sc)
        )
        drawRoundRect(
            color = Color(0xFFCC8833),
            topLeft = Offset(kx - 10f * sc, ky + 14f * sc), size = Size(20f * sc, 8f * sc),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * sc)
        )
    }
}

private fun DrawScope.drawHead(
    cx: Float, cy: Float, sc: Float,
    body1: Color, body2: Color, eyeCol: Color, reactor: Color,
    blinkP: Float, amplitude: Float, isSpeaking: Boolean, isListening: Boolean,
    isDark: Boolean
) {
    val hr = size.width * 0.17f
    val hcy = cy - size.height * 0.19f

    // Head glow
    drawCircle(
        brush = Brush.radialGradient(
            listOf(eyeCol.copy(0.15f), Color.Transparent), Offset(cx, hcy), hr * 1.6f
        ), radius = hr * 1.6f, center = Offset(cx, hcy)
    )
    // Head
    drawCircle(
        brush = Brush.verticalGradient(listOf(body1, body2), hcy - hr, hcy + hr),
        radius = hr, center = Offset(cx, hcy)
    )

    if (isDark) {
        // Cracked neon X pattern on helmet (like reference image)
        val lw = 2.5f * sc
        val c = reactor.copy(0.9f)
        // Top-left to center
        drawLine(c, Offset(cx - hr * 0.5f, hcy - hr * 0.6f), Offset(cx, hcy - hr * 0.1f), lw)
        // Top-right to center
        drawLine(c, Offset(cx + hr * 0.5f, hcy - hr * 0.6f), Offset(cx, hcy - hr * 0.1f), lw)
        // Center circle glow
        drawCircle(
            brush = Brush.radialGradient(listOf(reactor, reactor.copy(0f)), Offset(cx, hcy - hr * 0.1f), hr * 0.35f),
            radius = hr * 0.35f, center = Offset(cx, hcy - hr * 0.1f)
        )
        // Horizontal bar
        drawLine(c, Offset(cx - hr * 0.55f, hcy - hr * 0.1f), Offset(cx + hr * 0.55f, hcy - hr * 0.1f), lw)
    }

    // Visor
    val vw = hr * 1.3f; val vh = hr * 0.45f
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(body2.copy(0.9f), body1.copy(0.7f)),
            Offset(cx - vw / 2, hcy - vh / 2), Offset(cx + vw / 2, hcy + vh / 2)
        ),
        topLeft = Offset(cx - vw / 2, hcy - vh / 2 - 3f * sc),
        size = Size(vw, vh + 3f * sc),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(vh / 2)
    )

    // Eyes
    val eyeY = hcy - hr * 0.07f
    val eyeSp = hr * 0.44f
    val eyeW = hr * 0.28f
    val eyeH = (hr * 0.16f * (1f - blinkP)).coerceAtLeast(1f)
    listOf(-eyeSp, eyeSp).forEach { xOff ->
        drawOval(
            brush = Brush.radialGradient(
                listOf(eyeCol.copy(if (isListening) 1f else 0.7f), Color.Transparent),
                Offset(cx + xOff, eyeY), eyeW * 1.8f
            ),
            topLeft = Offset(cx + xOff - eyeW * 1.8f, eyeY - eyeW), size = Size(eyeW * 3.6f, eyeW * 2f)
        )
        drawOval(color = eyeCol, topLeft = Offset(cx + xOff - eyeW / 2, eyeY - eyeH / 2), size = Size(eyeW, eyeH))
    }

    // Mouth
    val mouthY = hcy + hr * 0.38f
    val mouthW = hr * 0.5f
    val mOpen = if (isSpeaking || isListening) (amplitude * hr * 0.38f).coerceAtLeast(3f * sc) else 3f * sc
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(body2, body1), mouthY, mouthY + mOpen.coerceAtLeast(4f * sc)),
        topLeft = Offset(cx - mouthW / 2, mouthY),
        size = Size(mouthW, mOpen.coerceAtLeast(4f * sc)),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(mOpen / 2 + 2f * sc)
    )
    if (mOpen > 7f * sc) {
        drawRect(Color.White.copy(0.7f),
            Offset(cx - mouthW * 0.3f, mouthY + sc), Size(mouthW * 0.6f, (mOpen * 0.4f).coerceAtMost(9f * sc)))
    }
}

private fun DrawScope.drawSword(cx: Float, cy: Float, sc: Float, neonColor: Color, glow: Float) {
    // Sword to the right side — like the reference image
    val sx = cx + size.width * 0.22f
    val sy = cy + size.height * 0.05f
    val len = size.height * 0.28f

    // Blade glow
    drawLine(
        brush = Brush.linearGradient(
            listOf(Color.White.copy(glow * 0.9f), neonColor.copy(glow * 0.6f), Color.Transparent),
            Offset(sx, sy - len), Offset(sx + len * 0.4f, sy + len * 0.3f)
        ),
        start = Offset(sx, sy - len), end = Offset(sx + len * 0.4f, sy + len * 0.3f),
        strokeWidth = 6f * sc, cap = StrokeCap.Round
    )
    // Blade core
    drawLine(
        color = Color.White.copy(0.95f),
        start = Offset(sx + 2f * sc, sy - len + 4f * sc),
        end = Offset(sx + len * 0.38f - 2f * sc, sy + len * 0.28f),
        strokeWidth = 2f * sc, cap = StrokeCap.Round
    )
    // Guard
    drawLine(
        color = Color(0xFFCC8833),
        start = Offset(sx - 12f * sc, sy), end = Offset(sx + 12f * sc, sy),
        strokeWidth = 5f * sc, cap = StrokeCap.Round
    )
    // Handle
    drawLine(
        color = Color(0xFF333333),
        start = Offset(sx, sy), end = Offset(sx + 14f * sc, sy + 20f * sc),
        strokeWidth = 5f * sc, cap = StrokeCap.Round
    )
}

private fun DrawScope.drawNeonLines(cx: Float, cy: Float, sc: Float, neon: Color, alpha: Float) {
    val bw = size.width * 0.30f
    val bt = cy + size.height * 0.05f
    val bh = size.height * 0.34f
    val a = (alpha * 0.7f).coerceIn(0.3f, 0.9f)
    // Body neon strips
    drawLine(neon.copy(a), Offset(cx - bw / 2 + 4f * sc, bt + bh * 0.2f), Offset(cx - bw / 2 + 4f * sc, bt + bh * 0.7f), 1.5f * sc)
    drawLine(neon.copy(a), Offset(cx + bw / 2 - 4f * sc, bt + bh * 0.2f), Offset(cx + bw / 2 - 4f * sc, bt + bh * 0.7f), 1.5f * sc)
    drawLine(neon.copy(a * 0.6f), Offset(cx - bw * 0.3f, bt + bh * 0.55f), Offset(cx + bw * 0.3f, bt + bh * 0.55f), 1f * sc)
}

private fun DrawScope.drawParticles(cx: Float, cy: Float, sc: Float, shimmer: Float, amplitude: Float, col: Color) {
    val count = 8
    repeat(count) { i ->
        val angle = (shimmer + i * (360f / count)) * PI.toFloat() / 180f
        val r = size.minDimension * (0.38f + sin(angle * 2) * 0.04f) + amplitude * 18f * sc
        val px = cx + cos(angle) * r; val py = cy + sin(angle) * r
        val a = (0.3f + sin(angle + shimmer * 0.05f).toFloat() * 0.3f).coerceIn(0f, 1f)
        drawCircle(col.copy(a), (2.5f + amplitude * 3f) * sc, Offset(px, py))
    }
}

private fun DrawScope.drawProcessingRing(cx: Float, cy: Float, sc: Float, color: Color, shimmer: Float) {
    val r = size.minDimension * 0.44f
    rotate(shimmer * 2f, Offset(cx, cy)) {
        drawArc(
            brush = Brush.sweepGradient(listOf(Color.Transparent, color, Color.Transparent), Offset(cx, cy)),
            startAngle = 0f, sweepAngle = 240f, useCenter = false,
            topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2),
            style = Stroke(3f * sc, cap = StrokeCap.Round)
        )
    }
}
