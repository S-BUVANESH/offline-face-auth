package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onInitializationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var loadingStep by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0.0f) }

    // Sequential initialization simulation
    LaunchedEffect(Unit) {
        // [1/4] Boot Local Room Database
        loadingStep = 1
        animateProgress(0.0f, 0.25f) { progress = it }
        delay(600)

        // [2/4] Parse Neural Vector Graph
        loadingStep = 2
        animateProgress(0.25f, 0.55f) { progress = it }
        delay(700)

        // [3/4] Secure Cryptographic Registers Verified
        loadingStep = 3
        animateProgress(0.55f, 0.85f) { progress = it }
        delay(600)

        // [4/4] Hardware Sensors Locked & Standard Authentication Configured
        loadingStep = 4
        animateProgress(0.85f, 1.00f) { progress = it }
        delay(500)

        onInitializationComplete()
    }

    // Infinite pulsing/scanning animation for the scanner radar ring
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val radarScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarScale"
    )
    val radarAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF081423),
                        Color(0xFF0D1F38),
                        Color(0xFF122742)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Background tech scan visual circles
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0x0500F0FF),
                radius = size.minDimension * 0.4f,
                center = center
            )
            drawCircle(
                color = Color(0x0200F0FF),
                radius = size.minDimension * 0.6f,
                center = center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            // High-fidelity glowing HUD scanner ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                // Outer pulsing scan ring
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 2.dp,
                            color = Color(0xFF00F0FF).copy(alpha = radarAlpha),
                            shape = CircleShape
                        )
                )

                // Inner static tracking circle
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color(0x1000F0FF))
                        .border(1.dp, Color(0xFF00F0FF).copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CenterFocusStrong,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = Color(0xFF00F0FF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Subtitle brand & Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "DATALAKE ",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "3.0",
                    color = Color(0xFF00F0FF),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "SECURE BIOMETRIC SECURE ENGINE",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Progress bar and loading diagnostics status
            Column(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF00F0FF),
                    trackColor = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive system step statements
                AnimatedContent(
                    targetState = loadingStep,
                    transitionSpec = {
                        (slideInVertically { height -> height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut())
                            .using(SizeTransform(clip = false))
                    },
                    label = "DiagnosticSteps"
                ) { step ->
                    val stepText = when (step) {
                        1 -> "BOOTING LOCAL ROOM INSTANCE... OK"
                        2 -> "PARSING ON-DEVICE NEURAL MAPS (18.4 MB)... OK"
                        3 -> "INTEGRITY OF SECURE CHANNELS COMPLETED... OK"
                        4 -> "HARDWARE ADVANCED CAMERA MATRICES DETECTED... OK"
                        else -> "PRE-WARMING ENCRYPTED SECURE DOMAIN..."
                    }
                    val icon = when (step) {
                        3 -> Icons.Outlined.Shield
                        4 -> Icons.Outlined.CenterFocusStrong
                        else -> Icons.Outlined.Fingerprint
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color(0xFF00F0FF).copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stepText,
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

private suspend fun animateProgress(from: Float, to: Float, onUpdate: (Float) -> Unit) {
    val duration = 400
    val steps = 20
    val delayMs = (duration / steps).toLong()
    for (i in 1..steps) {
        val current = from + (to - from) * (i.toFloat() / steps)
        onUpdate(current)
        delay(delayMs)
    }
}
