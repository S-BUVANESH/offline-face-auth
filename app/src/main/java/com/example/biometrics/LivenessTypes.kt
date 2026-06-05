package com.example.biometrics

/**
 * Standard liveness verification prompt tokens.
 * Decoupled from platform logic and mapped to human-readable guidance strings.
 */
enum class LivenessChallenge(val prompt: String) {
    BLINK("Blink both eyes to verify liveness"),
    SMILE("Smile slightly to verify liveness"),
    TURN_LEFT("Turn your head slowly to the left"),
    TURN_RIGHT("Turn your head slowly to the right")
}

/**
 * Platform-independent memory buffer designed to track structural transitions in active challenges
 * (e.g. eye-blink transition tracking where eyes go from open, to closed, to open).
 */
class LivenessStateTrackerState {
    var blinkStateClosedDetected: Boolean = false

    fun reset() {
        blinkStateClosedDetected = false
    }
}
