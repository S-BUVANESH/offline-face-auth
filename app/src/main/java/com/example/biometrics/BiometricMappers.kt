package com.example.biometrics

import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

/**
 * Extension mapper that translates Android's native MLKit [Face] model
 * into the platform-independent [PlatformFace] structure.
 */
fun Face.toPlatformFace(faceCrop: android.graphics.Bitmap? = null): PlatformFace {
    val landmarkMap = mutableMapOf<PlatformLandmark, PlatformPoint>()
    
    val mappings = mapOf(
        FaceLandmark.LEFT_EYE to PlatformLandmark.LEFT_EYE,
        FaceLandmark.RIGHT_EYE to PlatformLandmark.RIGHT_EYE,
        FaceLandmark.NOSE_BASE to PlatformLandmark.NOSE_BASE,
        FaceLandmark.MOUTH_LEFT to PlatformLandmark.MOUTH_LEFT,
        FaceLandmark.MOUTH_RIGHT to PlatformLandmark.MOUTH_RIGHT,
        FaceLandmark.MOUTH_BOTTOM to PlatformLandmark.MOUTH_BOTTOM,
        FaceLandmark.LEFT_CHEEK to PlatformLandmark.LEFT_CHEEK,
        FaceLandmark.RIGHT_CHEEK to PlatformLandmark.RIGHT_CHEEK
    )
    
    for ((mlkitId, platformId) in mappings) {
        val position = this.getLandmark(mlkitId)?.position
        if (position != null) {
            landmarkMap[platformId] = PlatformPoint(position.x, position.y)
        }
    }
    
    return PlatformFace(
        landmarks = landmarkMap,
        leftEyeOpenProbability = this.leftEyeOpenProbability,
        rightEyeOpenProbability = this.rightEyeOpenProbability,
        smilingProbability = this.smilingProbability,
        headEulerAngleX = this.headEulerAngleX,
        headEulerAngleY = this.headEulerAngleY,
        headEulerAngleZ = this.headEulerAngleZ,
        faceCrop = faceCrop
    )
}
