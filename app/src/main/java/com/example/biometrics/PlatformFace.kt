package com.example.biometrics

/**
 * PlatformPoint represents a 2D coordinate on a normalized facial canvas.
 * This class replaces Android's graphics.PointF to achieve 100% multiplatform compatibility.
 */
data class PlatformPoint(val x: Float, val y: Float)

/**
 * Standardized facial landmark points used across different platforms.
 * Corresponds to Google MLKit Landmarks on Android and Apple Vision Landmarks on iOS.
 */
enum class PlatformLandmark {
    LEFT_EYE,
    RIGHT_EYE,
    NOSE_BASE,
    MOUTH_LEFT,
    MOUTH_RIGHT,
    MOUTH_BOTTOM,
    LEFT_CHEEK,
    RIGHT_CHEEK
}

/**
 * PlatformFace is a completely platform-agnostic representation of a detected human face.
 * Holds mathematical vectors, liveness characteristics (probabilities), and rotation angles.
 * Suitable for instant integration with Kotlin Multiplatform (KMP) on Android and iOS.
 */
data class PlatformFace(
    val landmarks: Map<PlatformLandmark, PlatformPoint>,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val smilingProbability: Float?,
    val headEulerAngleX: Float, // Pitch: Looking up/down
    val headEulerAngleY: Float, // Yaw: Looking left/right
    val headEulerAngleZ: Float, // Roll: Tilting head left/right
    val faceCrop: android.graphics.Bitmap? = null
)
