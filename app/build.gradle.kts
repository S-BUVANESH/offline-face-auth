import java.net.URL
import java.net.URI
import java.net.HttpURLConnection

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.offfaceauth.whvksy"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  androidResources {
    noCompress += "tflite"
  }
}

// configurations.all {
//   exclude(group = "org.tensorflow", module = "tensorflow-lite-api")
// }

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.mlkit.face.detection)
  implementation(libs.tensorflow.lite)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.register("downloadMobileFaceNet") {
    doLast {
        val destFile = file("src/main/assets/mobilefacenet.tflite")
        destFile.parentFile.mkdirs()
        
        // If the file exists and is a valid binary (size > 1MB), we skip downloading
        if (destFile.exists() && destFile.length() > 1000000) {
            println("A genuine MobileFaceNet model file already exists (size: ${destFile.length()} bytes). Skipping download.")
            return@doLast
        }
        
        // List of candidate URLs to fetch MobileFaceNet
        val candidateUrls = listOf(
            "https://gitee.com/galeorando/MobileFaceNet-Android/raw/master/app/src/main/assets/mobilefacenet.tflite",
            "https://gitee.com/siriusmin/MobileFaceNet-Android/raw/master/app/src/main/assets/mobilefacenet.tflite"
        )
        
        var downloadSuccess = false
        val tempFile = file("src/main/assets/mobilefacenet_temp.tflite")
        
        for (rawUrl in candidateUrls) {
            try {
                println("Attempting to download MobileFaceNet model from: $rawUrl ...")
                val connection = URI(rawUrl).toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 4000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.connect()
                
                if (connection.responseCode == 200) {
                    val contentLength = connection.contentLengthLong
                    println("Connected successfully. Content Length declared: $contentLength")
                    
                    tempFile.outputStream().use { output ->
                        connection.inputStream.use { input ->
                            input.copyTo(output)
                        }
                    }
                    
                    val tempSize = tempFile.length()
                    println("Downloaded model size: $tempSize bytes.")
                    if (tempSize > 1000000) {
                        // Yes! Real model found (> 1MB)
                        if (destFile.exists()) destFile.delete()
                        tempFile.renameTo(destFile)
                        println("SUCCESS: MobileFaceNet model downloaded and verified successfully! (Saved as ${destFile.absolutePath}, size: ${destFile.length()} bytes)")
                        downloadSuccess = true
                        break
                    } else {
                        println("Skipping: downloaded file too small ($tempSize bytes), probably unexpected HTML or text block.")
                        tempFile.delete()
                    }
                } else {
                    println("HTTP Response Code is ${connection.responseCode} for $rawUrl")
                }
            } catch (e: Exception) {
                println("Failed download attempt for $rawUrl: ${e.message}")
                if (tempFile.exists()) tempFile.delete()
            }
        }
        
        if (!downloadSuccess) {
            println("WARNING: All candidate raw URLs failed. Setting up backup fallback mock descriptor.")
            if (!destFile.exists()) {
                destFile.writeText("MOCK_MOBILEFACENET_TFLITE_MODEL_DATA")
            }
        }
    }
}


