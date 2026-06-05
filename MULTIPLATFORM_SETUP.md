# Kotlin Multiplatform (KMP) Setup Guide for Android & iOS

This project is engineered with a **Platform-Independent Clean Architecture** specifically tailored to allow instant compilation and execution on **both Android and iOS** using **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**.

By decoupling our Core Biometric Math, local database caching, State Machinery, and ViewModels from hard platform targets, we have achieved maximum code sharing.

---

## 🏗️ Technical Architecture Matrix

| Component | Portability Status | Android Implementation | iOS Native Implementation |
| :--- | :--- | :--- | :--- |
| **Common UI Views** | 🚀 **100% Shared** | Jetpack Compose (M3) | Compose Multiplatform (UIKit Canvas) |
| **State Flows / ViewModels**| 🚀 **100% Shared** | Native ViewModel (lifecycle) | Compose-Multiplatform ViewModel / SKIE |
| **Local Cache Storage** | 🚀 **100% Shared** | Room DB, SQLite Driver | Room DB, Native Apple CFBundle SQLite |
| **Geometric Facial Analysis**| 🚀 **100% Shared** | Pure Kotlin Vector Similarity | Pure Kotlin Vector Similarity |
| **Anti-Spoofing Challenge** | 🚀 **100% Shared** | Pure Kotlin Challenge Machine | Pure Kotlin Challenge Machine |
| **Camera Feed Buffer** | 🔌 **Platform-Bound** | Jetpack CameraX API | iOS AVFoundation / AVCaptureSession |
| **Face Coordinates Extractor**| 🔌 **Platform-Bound** | Google MLKit Face Detection API| Apple Vision Framework (`VNFaceObservation`) |

---

## 🔧 File Mapping to Kotlin Multiplatform Project (KMP)

If you migrate this project to a unified KMP structure (under `:composeApp` or `:shared`), place the shared and platform classes as follows:

### 1. `commonMain` (100% Shared)
Place these files in `composeApp/src/commonMain/kotlin/com/example/`:
*   **Definitions & Models**: `com/example/biometrics/PlatformFace.kt`
*   **Validation Engines**: `com/example/biometrics/CommonBiometricEngine.kt`
*   **Repositioning Logic**: `com/example/data/repository/DatalakeAuthRepository.kt`
*   **State Machine Engines**: `com/example/ui/viewmodel/DatalakeAuthViewModel.kt`
*   **Unified Screens**: `DashboardScreen.kt`, `SplashScreen.kt`
*   **Theming**: `com/example/ui/theme/Theme.kt`

> 💡 *Note: The Room Database is natively supported inside `commonMain` starting from version `2.7.0` using KSP.*

### 2. `androidMain` (Android Targets)
Place these files in `composeApp/src/androidMain/kotlin/com/example/`:
*   `com/example/MainActivity.kt`
*   `com/example/biometrics/FaceBiometricAnalyzer.kt`
*   `com/example/biometrics/BiometricMappers.kt` (maps MLKit `Face` → `PlatformFace`)
*   `RegistrationScreen.kt` & `ScannerScreen.kt` (hosting Android CameraX Surface views)

### 3. `iosMain` (iOS Targets)
Place these files in `composeApp/src/iosMain/kotlin/com/example/`:
*   `com/example/biometrics/IosBiometricService.kt`
*   SwiftUI Main View Controller or Kotlin-to-UIKit bindings.

---

## 🍎 Mapping Face Coordinates on iOS (Swift & Kotlin)

On iOS, we use Apple's **Vision Framework** to inspect coordinate spaces. Below is the exact implementation detail for mapping local iOS face observation markers directly to our shared KMP `PlatformFace` payload.

### iOS Swift Mapping Interface (AVFoundation Delegate)
```swift
import Vision
import AVFoundation

class CameraFrameProcessor: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    
    // Process frames from iOS Camera
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        
        let request = VNDetectFaceLandmarksRequest { (req, error) in
            guard let results = req.results as? [VNFaceObservation], let face = results.first else { return }
            
            // 1. Fetch Euler angles for tilt & head position
            let yaw = face.yaw?.floatValue ?? 0.0
            let pitch = face.pitch?.floatValue ?? 0.0
            let roll = face.roll?.floatValue ?? 0.0
            
            // 2. Map coordinates of primary landmarks
            var landmarks = [String: PlatformPoint]()
            if let leftEye = face.landmarks?.leftEye {
                landmarks["leftEye"] = PlatformPoint(x: Float(leftEye.normalizedPoints[0].x), y: Float(leftEye.normalizedPoints[0].y))
            }
            if let rightEye = face.landmarks?.rightEye {
                landmarks["rightEye"] = PlatformPoint(x: Float(rightEye.normalizedPoints[0].x), y: Float(rightEye.normalizedPoints[0].y))
            }
            if let nose = face.landmarks?.nose {
                landmarks["noseBase"] = PlatformPoint(x: Float(nose.normalizedPoints[0].x), y: Float(nose.normalizedPoints[0].y))
            }
            
            // 3. Estimate smile score & eye closures using facial geometric landmarks
            let leftBlink = estimateEyeClosure(face.landmarks?.leftEye)
            let rightBlink = estimateEyeClosure(face.landmarks?.rightEye)
            let smileConfidence = estimateSmile(face.landmarks?.outerLips)
            
            // 4. Pass the mapped PlatformFace directly into the shared CommonBiometricEngine
            let platformFace = PlatformFace(
                landmarks: landmarks,
                leftEyeOpenProbability: 1.0 - leftBlink,
                rightEyeOpenProbability: 1.0 - rightBlink,
                smilingProbability: smileConfidence,
                headEulerAngleX: pitch,
                headEulerAngleY: yaw,
                headEulerAngleZ: roll
            )
            
            // Let the shared mathematical core evaluate
            let result = CommonBiometricEngine.shared.verifyChallenge(...)
        }
        
        let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, options: [:])
        try? handler.perform([request])
    }
}
```

---

## ⚡ Room Database Multiplatform Setup

To compile Room on both platforms, add the SQLite Driver dependencies to your `libs.versions.toml` and configure the database builder expect/actual declarations as shown below:

### 1. Unified Dependency Gradle Configuration (`build.gradle.kts`):
```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.sqlite.bundled) // Bundled Multiplatform SQLite Driver
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}
```

### 2. Multiplatform Database Creator Abstraction:
```kotlin
// In commonMain (expect)
expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

// In androidMain (actual)
actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val appContext = LocalContext.current.applicationContext
    val dbFile = appContext.getDatabasePath("datalake_auth.db")
    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = dbFile.absolutePath
    )
}

// In iosMain (actual)
actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = NSHomeDirectory() + "/Library/datalake_auth.db"
    return Room.databaseBuilder<AppDatabase>(
        name = dbFilePath,
        factory = { AppDatabase::class.instantiateImpl() } // Auto-generated by Room compiler
    )
}
```
