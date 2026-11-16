package com.unofficial.kodakremote

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.HapticFeedbackConstants
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val Bg = Color(0xFF0B0C0E)
val Panel = Color(0xFF16181C)
val KeyColor = Color(0xFF23262C)
val KeyPressed = Color(0xFF3A3E47)
val Accent = Color(0xFF4F9DFF)
val Danger = Color(0xFFE53935)
val Good = Color(0xFF43A047)
val Warn = Color(0xFFFFB300)
val Muted = Color(0xFF9AA0A6)

val LocalHaptics = compositionLocalOf { true }

@Composable
fun KodakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Bg,
            surface = Panel,
            onBackground = Color(0xFFECEFF4),
            onSurface = Color(0xFFECEFF4),
            error = Danger,
        ),
        content = content
    )
}

@Composable
fun KeyButton(
    label: String,
    description: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = KeyColor,
    contentColor: Color = Color.White,
    shape: Shape = RoundedCornerShape(18.dp),
    repeat: Boolean = false,
    fontSize: TextUnit = 15.sp,
    icon: (@Composable () -> Unit)? = null,
) {
    val view = LocalView.current
    val haptics = LocalHaptics.current
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    val fire: () -> Unit = {
        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        onPress()
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
            .clip(shape)
            .background(if (pressed) KeyPressed else container)
            .pointerInput(repeat) {
                detectTapGestures(onPress = {
                    pressed = true
                    fire()
                    val job = if (repeat) scope.launch {
                        delay(450)
                        while (true) { fire(); delay(130) }
                    } else null
                    tryAwaitRelease()
                    job?.cancel()
                    pressed = false
                })
            }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
                onClick(label = description) { fire(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) icon() else Text(
            label, color = contentColor, fontSize = fontSize,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun RK(
    key: RemoteKey,
    onKey: (RemoteKey) -> Unit,
    modifier: Modifier = Modifier,
    container: Color = KeyColor,
    contentColor: Color = Color.White,
    shape: Shape = RoundedCornerShape(18.dp),
    repeat: Boolean = false,
    fontSize: TextUnit = 15.sp,
) {
    KeyButton(
        label = key.label,
        description = stringResource(key.desc),
        onPress = { onKey(key) },
        modifier = modifier,
        container = container,
        contentColor = contentColor,
        shape = shape,
        repeat = repeat,
        fontSize = fontSize,
    )
}

@Composable
fun PowerGlyph(color: Color, modifier: Modifier = Modifier.size(28.dp)) {
    Canvas(modifier) {
        val w = size.minDimension
        val sw = w * 0.11f
        drawArc(
            color = color, startAngle = -60f, sweepAngle = 300f, useCenter = false,
            topLeft = Offset(w * 0.12f, w * 0.18f), size = Size(w * 0.76f, w * 0.76f),
            style = Stroke(width = sw, cap = StrokeCap.Round)
        )
        drawLine(color, Offset(w / 2, w * 0.06f), Offset(w / 2, w * 0.5f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}
