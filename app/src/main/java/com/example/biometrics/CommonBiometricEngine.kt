package com.example.biometrics

import android.content.Context
import kotlin.math.sqrt

object CommonBiometricEngine {
    
    // Highly precise scale-invariant threshold for offline facial authentication matches
    // Adjusted to 0.83f which is the production standard for MobileFaceNet 128D L2-normalized embeddings, hardened from 0.80f.
    const val MATCHING_CONFIDENCE_THRESHOLD = 0.83f

    // Serializes a float array to string for local persistence (e.g. SQLite database)
    fun embeddingToString(embedding: FloatArray): String {
        return embedding.joinToString(",") { it.toString() }
    }

    // Deserializes a string to float array
    fun stringToEmbedding(embeddingStr: String): FloatArray? {
        if (embeddingStr.isBlank()) return null
        return try {
            embeddingStr.split(",").map { it.toFloat() }.toFloatArray()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts MobileFaceNet 128D facial feature embedding.
     */
    fun extractGeometricEmbedding(face: PlatformFace, context: Any? = null): FloatArray? {
        val leftEye = face.landmarks[PlatformLandmark.LEFT_EYE] ?: return null
        val rightEye = face.landmarks[PlatformLandmark.RIGHT_EYE] ?: return null
        val noseBase = face.landmarks[PlatformLandmark.NOSE_BASE] ?: return null
        
        val androidCtx = context as? Context
        return MobileFaceNetExtractor.extractEmbedding(face, androidCtx)
    }

    /**
     * Measures the cosine similarity between two geometric embeddings.
     * Cosine similarity is the preferred metric as it is independent of magnitude and relies
     * purely on direction in geometric space.
     */
    fun computeCosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.size != v2.size) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        return (dotProduct / (sqrt(normA.toDouble()) * sqrt(normB.toDouble()))).toFloat()
    }

    // Calculates distance between normalized coordinates
    private fun distance(p1: PlatformPoint, p2: PlatformPoint): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    /**
     * Active Liveness Challenge Evaluator Node.
     * Runs 100% platform-independently to verify face conditions against the active prompt.
     * @return Pair of (isChallengeCompleted, progressScalar (0.0 to 1.0))
     */
    fun verifyChallenge(
        face: PlatformFace,
        challenge: LivenessChallenge,
        history: LivenessStateTrackerState
    ): Pair<Boolean, Float> {
        return when (challenge) {
            LivenessChallenge.BLINK -> {
                val leftProb = face.leftEyeOpenProbability ?: 1.0f
                val rightProb = face.rightEyeOpenProbability ?: 1.0f
                
                // Eyeblink tracking state machine: closed state (<0.23) and then re-opened (>0.65)
                if (leftProb < 0.23f && rightProb < 0.23f) {
                    history.blinkStateClosedDetected = true
                }
                
                val success = history.blinkStateClosedDetected && (leftProb > 0.65f && rightProb > 0.65f)
                val progress = if (history.blinkStateClosedDetected) {
                    0.5f + (leftProb * 0.5f)
                } else {
                    (1.0f - (leftProb + rightProb) / 2f).coerceIn(0f, 0.5f)
                }
                
                Pair(success, progress)
            }
            LivenessChallenge.SMILE -> {
                val smileProb = face.smilingProbability ?: 0.0f
                val success = smileProb > 0.72f
                Pair(success, smileProb.coerceIn(0f, 1f))
            }
            LivenessChallenge.TURN_LEFT -> {
                // Yaw: Positive when looking left (depending on camera mirorring adjustments)
                val eulerY = face.headEulerAngleY
                val success = eulerY > 16f
                val progress = (eulerY / 16f).coerceIn(0f, 1f)
                Pair(success, progress)
            }
            LivenessChallenge.TURN_RIGHT -> {
                // Yaw: Negative when looking right
                val eulerY = face.headEulerAngleY
                val success = eulerY < -16f
                val progress = (-eulerY / 16f).coerceIn(0f, 1f)
                Pair(success, progress)
            }
        }
    }
}
