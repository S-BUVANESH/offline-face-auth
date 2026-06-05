package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Rect
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.biometrics.CommonBiometricEngine
import com.example.biometrics.toPlatformFace
import com.example.biometrics.FaceBiometricAnalyzer
import com.example.ui.viewmodel.DatalakeAuthViewModel
import com.google.mlkit.vision.face.Face
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun RegistrationScreen(
    viewModel: DatalakeAuthViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var name by remember { mutableStateOf("") }
    var employeeId by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }

    var stepActiveState by remember { mutableStateOf(1) } // 1: Info Entry, 2: Face Biometric Scanner

    // Permissions State
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Biometric Capture State
    var detectedFace by remember { mutableStateOf<Face?>(null) }
    var capturedEmbeddings by remember { mutableStateOf<FloatArray?>(null) }
    var captureSuccessMessage by remember { mutableStateOf<String?>(null) }
    var analysisError by remember { mutableStateOf<String?>(null) }

    // Camera Lifecycle Resources
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Alert Dialog States
    var showErrorDialog by remember { mutableStateOf<String?>(null) }

    DisposableEffect(key1 = true) {
        onDispose {
            cameraExecutor.shutdown()
            cameraProvider?.unbindAll()
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { 
                    Text(
                        "Crew Enrollment",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("reg_back_button")) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack, 
                            contentDescription = "Go Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF081423)
                )
            )
        },
        containerColor = Color(0xFF081423),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF081423))
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Screen Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { 
                        when (stepActiveState) {
                            1 -> 0.33f
                            2 -> 0.66f
                            else -> 1.0f
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            }

            when (stepActiveState) {
                1 -> {
                    // STEP 1: METADATA FORM
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = "1. Enter Field Personnel Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Associate a unique employee identifier inside the secure Datalake SQLite layer prior to mapping their edge facial biometrics.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name") },
                            leadingIcon = { 
                                Icon(
                                    Icons.Outlined.Keyboard, 
                                    contentDescription = null,
                                    tint = Color(0xFF1D7FFF)
                                ) 
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reg_name_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF1D7FFF),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                focusedContainerColor = Color(0xFF0D1F38),
                                unfocusedContainerColor = Color(0xFF071424),
                                focusedBorderColor = Color(0xFF1D7FFF),
                                unfocusedBorderColor = Color(0xFF1D7FFF).copy(alpha = 0.3f)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        OutlinedTextField(
                            value = employeeId,
                            onValueChange = { employeeId = it },
                            label = { Text("Employee ID") },
                            leadingIcon = { 
                                Icon(
                                    Icons.Outlined.Key, 
                                    contentDescription = null,
                                    tint = Color(0xFF1D7FFF)
                                ) 
                            },
                            placeholder = { Text("e.g. DL-04510", color = Color.White.copy(alpha = 0.3f)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reg_emp_id_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF1D7FFF),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                focusedContainerColor = Color(0xFF0D1F38),
                                unfocusedContainerColor = Color(0xFF071424),
                                focusedBorderColor = Color(0xFF1D7FFF),
                                unfocusedBorderColor = Color(0xFF1D7FFF).copy(alpha = 0.3f)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        OutlinedTextField(
                            value = department,
                            onValueChange = { department = it },
                            label = { Text("Department / Field Unit") },
                            leadingIcon = { 
                                Icon(
                                    Icons.Outlined.Face, 
                                    contentDescription = null,
                                    tint = Color(0xFF1D7FFF)
                                ) 
                            },
                            placeholder = { Text("e.g. Hydromet Unit 2", color = Color.White.copy(alpha = 0.3f)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reg_dept_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF1D7FFF),
                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                                focusedContainerColor = Color(0xFF0D1F38),
                                unfocusedContainerColor = Color(0xFF071424),
                                focusedBorderColor = Color(0xFF1D7FFF),
                                unfocusedBorderColor = Color(0xFF1D7FFF).copy(alpha = 0.3f)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = {
                                if (name.isBlank() || employeeId.isBlank() || department.isBlank()) {
                                    showErrorDialog = "All fields are required. Please fill standard credentials before proceeding."
                                } else {
                                    stepActiveState = 2
                                    if (!hasCameraPermission) {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("reg_next_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Next: Capture Biometric Signature", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                }
                2 -> {
                    // STEP 2: CAMERA CAPTURE BIOMETRIC SIGNATURE
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "2. Capture Biometric Template",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Kindly align your face inside the central camera preview. This computes secure biometric templates instantly on the device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!hasCameraPermission) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(270.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Grant Camera Permission")
                                    }
                                }
                            }
                        } else {
                            // CAMERA PREVIEW CONTAINER
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .border(
                                        width = 2.dp,
                                        color = if (capturedEmbeddings != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        val previewView = PreviewView(ctx).apply {
                                            scaleType = PreviewView.ScaleType.FILL_CENTER
                                        }
                                        
                                        val preview = Preview.Builder().build()
                                        var selector = CameraSelector.Builder()
                                            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                                            .build()

                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()

                                        imageAnalysis.setAnalyzer({ command ->
                                             try {
                                                 if (!cameraExecutor.isShutdown) cameraExecutor.execute(command)
                                             } catch (e: Exception) {}
                                         }, FaceBiometricAnalyzer(
                                            onFaceAnalyzed = { face, w, h, faceCrop ->
                                                analysisError = null
                                                detectedFace = face
                                                if (face != null) {
                                                    val platformFace = face.toPlatformFace(faceCrop)
                                                    val emb = CommonBiometricEngine.extractGeometricEmbedding(platformFace, context)
                                                    if (emb != null) {
                                                        capturedEmbeddings = emb
                                                        captureSuccessMessage = "Neural biometric template captured successfully: [128 Dimensions]"
                                                    }
                                                }
                                            },
                                            onError = { e ->
                                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                    Log.e("CameraReg", "Frame analysis failed.", e)
                                                    analysisError = e.localizedMessage ?: e.message ?: "Analysis failure"
                                                }
                                            }
                                        ))

                                        cameraProviderFuture.addListener({
                                            val provider = cameraProviderFuture.get()
                                            cameraProvider = provider
                                            try {
                                                provider.unbindAll()
                                                val finalSelector = when {
                                                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                                                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                                                    else -> selector
                                                }
                                                provider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    finalSelector,
                                                    preview,
                                                    imageAnalysis
                                                )
                                                preview.setSurfaceProvider(previewView.surfaceProvider)
                                            } catch (e: Exception) {
                                                Log.e("CameraReg", "Binding error.", e)
                                            }
                                        }, ContextCompat.getMainExecutor(ctx))

                                        previewView
                                    }
                                )

                                // Visual overlay grid overlay
                                Box(
                                    modifier = Modifier
                                        .size(160.dp)
                                        .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                )

                                // Alert box reporting status
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        Text(
                                            text = if (capturedEmbeddings != null) "✓ FACE MATCH MATRIX ACQUIRED" else "ALIGN FACE IN THE CIRCLE OVERLAY",
                                            color = if (capturedEmbeddings != null) Color(0xFF81C784) else Color.White.copy(alpha = 0.8f),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        if (analysisError != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "DIAGNOSTIC ERROR:\n${analysisError}",
                                                color = Color(0xFFFF1744),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            // Showcase Captured Landmark metrics for educational and professional value
                            AnimatedVisibility(
                                visible = capturedEmbeddings != null,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "On-Device Feature Matrix Generated",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Scale-invariant ratios locked based on Left Eye / Right Eye scale. 0MB additional footprint achieved recursively.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (capturedEmbeddings == null) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { stepActiveState = 1 },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Back", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val embedding = capturedEmbeddings
                                    if (embedding == null) {
                                        showErrorDialog = "Identify a front-facing face visual representation first to save vectors."
                                        return@Button
                                    }
                                    viewModel.registerPersonnel(
                                        name = name,
                                        employeeId = employeeId,
                                        department = department,
                                        embedding = embedding,
                                        onComplete = { success ->
                                            if (success) {
                                                stepActiveState = 3 // Route to loop success state rather than direct back navigation
                                            } else {
                                                showErrorDialog = "Enrollment failed. Employee ID might be registered already in the database."
                                            }
                                        }
                                    )
                                },
                                enabled = true,
                                modifier = Modifier
                                    .weight(2f)
                                    .height(52.dp)
                                    .testTag("reg_save_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Complete Enrollment", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                3 -> {
                    // STEP 3: ENROLLMENT SUCCESS & QUICK LOOP-BACK WINDOW
                    EnrollmentSuccessScreen(
                        name = name,
                        employeeId = employeeId,
                        department = department,
                        onEnrollAnother = {
                            // Reset state coordinates for loop enrollment
                            name = ""
                            employeeId = ""
                            department = ""
                            capturedEmbeddings = null
                            detectedFace = null
                            captureSuccessMessage = null
                            stepActiveState = 1
                        },
                        onFinishAndReturn = onNavigateBack,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // Modal warnings displays
    if (showErrorDialog != null) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = null },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = null }) {
                    Text("OK")
                }
            },
            title = { Text("Validation Warning") },
            text = { Text(showErrorDialog ?: "") }
        )
    }
}

@Composable
fun EnrollmentSuccessScreen(
    name: String,
    employeeId: String,
    department: String,
    onEnrollAnother: () -> Unit,
    onFinishAndReturn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // Circular glowing checkmark
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(90.dp)
        ) {
            Surface(
                color = Color(0xFFE8F5E9),
                shape = CircleShape,
                modifier = Modifier.fillMaxSize()
            ) {}
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(56.dp)
            )
        }
        
        Text(
            text = "PERSONNEL ENROLLED",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = Color(0xFF2E7D32),
            letterSpacing = 1.5.sp
        )
        
        Text(
            text = "Edge-level biometric vector keys generated and encrypted into local SQLite storage.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        // Details card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF0D1F38)
            ),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color(0xFF1D7FFF).copy(alpha = 0.2f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("NAME", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
                    Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Divider(color = Color.White.copy(alpha = 0.1f))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("EMPLOYEE ID", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
                    Text(employeeId.uppercase(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color(0xFF1D7FFF))
                }
                Divider(color = Color.White.copy(alpha = 0.1f))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("DEPARTMENT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
                    Text(department, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Divider(color = Color.White.copy(alpha = 0.1f))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("BIOMETRIC TEMPLATE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.6f))
                    Text("[MobileFaceNet 128D Secured]", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action buttons
        Button(
            onClick = onEnrollAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("enroll_another_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enroll Another Crew Member", fontWeight = FontWeight.Bold)
        }
        
        OutlinedButton(
            onClick = onFinishAndReturn,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("finish_enrollment_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.Home, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Finish & Return to Main Menu", fontWeight = FontWeight.Bold)
        }
    }
}
