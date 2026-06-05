package com.example.biometrics

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceBiometricAnalyzer(
    private val onFaceAnalyzed: (Face?, Int, Int, Bitmap?) -> Unit,
    private val onError: (Exception) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()

    private val detector = FaceDetection.getClient(options)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        try {
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return
            }

            val rotation = imageProxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    try {
                        // Sort by bounding box or face size, retrieve the primary center-most face
                        val primaryFace = faces?.maxByOrNull { face -> 
                            face.boundingBox.width() * face.boundingBox.height() 
                        }
                        
                        var faceCrop: Bitmap? = null
                        if (primaryFace != null) {
                            try {
                                val fullBitmap = imageProxy.toBitmap()
                                if (fullBitmap != null) {
                                    val bbox = primaryFace.boundingBox
                                    val left = bbox.left.coerceIn(0, fullBitmap.width - 1)
                                    val top = bbox.top.coerceIn(0, fullBitmap.height - 1)
                                    val right = bbox.right.coerceIn(0, fullBitmap.width)
                                    val bottom = bbox.bottom.coerceIn(0, fullBitmap.height)
                                    
                                    val cropW = (right - left).coerceAtLeast(1)
                                    val cropH = (bottom - top).coerceAtLeast(1)
                                    
                                    val cropped = Bitmap.createBitmap(fullBitmap, left, top, cropW, cropH)
                                    if (rotation != 0) {
                                        val matrix = Matrix()
                                        matrix.postRotate(rotation.toFloat())
                                        val rotated = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
                                        if (rotated != cropped) {
                                            cropped.recycle()
                                        }
                                        faceCrop = rotated
                                    } else {
                                        faceCrop = cropped
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("FaceBiometricAnalyzer", "Face crop generation failed", e)
                            }
                        }
                        
                        // Keep dimensions context relative to the frame rotation
                        val width = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
                        val height = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

                        onFaceAnalyzed(primaryFace, width, height, faceCrop)
                    } catch (t: Throwable) {
                        onError(Exception("Success processing error: ${t.message}", t))
                    }
                }
                .addOnFailureListener { exception ->
                    onError(exception)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } catch (t: Throwable) {
            try {
                imageProxy.close()
            } catch (ignored: Throwable) {}
            onError(Exception("Analyzer synchronous failure: ${t.message}", t))
        }
    }
}
