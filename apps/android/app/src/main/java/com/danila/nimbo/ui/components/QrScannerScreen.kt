package com.danila.nimbo.ui.components

import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.camera.core.*
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onBack: () -> Unit,
    title: String? = null,
    instruction: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val nebulaColors = LocalNebulaColors.current
    val cameraErrorText = t(
        "Не удалось запустить камеру. Закройте сканер и попробуйте снова.",
        "Could not start the camera. Close the scanner and try again."
    )
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val handledResult = remember { AtomicBoolean(false) }
    val disposed = remember { AtomicBoolean(false) }
    val currentOnResult by rememberUpdatedState(onResult)
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var scannerError by remember { mutableStateOf<String?>(null) }
    val scanner = remember {
        runCatching {
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            BarcodeScanning.getClient(options)
        }.onFailure { error ->
            Log.e("QrScanner", "Could not initialize barcode scanner", error)
        }.getOrNull()
    }

    LaunchedEffect(scanner) {
        if (scanner == null) scannerError = cameraErrorText
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
            scanner?.close()
            executor.shutdownNow()
        }
    }

    // PredictiveBackHandler для Android 14+ (API 34+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(
            enabled = true
        ) { progress ->
            try {
                progress.collect()
                onBack()
            } catch (_: CancellationException) {
                // Пользователь отменил жест назад.
            }
        }
    } else {
        BackHandler(onBack = onBack)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                cameraProviderFuture.addListener({
                    if (disposed.get()) return@addListener
                    val activeScanner = scanner ?: return@addListener
                    try {
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        imageAnalysis = analysis
                        analysis.setAnalyzer(executor) { imageProxy ->
                            if (disposed.get() || handledResult.get()) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            try {
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                activeScanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        val value = barcodes.firstNotNullOfOrNull { barcode ->
                                            barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }
                                        }
                                        if (value != null && handledResult.compareAndSet(false, true)) {
                                            mainExecutor.execute {
                                                if (!disposed.get()) currentOnResult(value)
                                            }
                                        }
                                    }
                                    .addOnFailureListener { error ->
                                        Log.e("QrScanner", "Barcode analysis failed", error)
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } catch (error: Throwable) {
                                Log.e("QrScanner", "Could not process camera frame", error)
                                imageProxy.close()
                            }
                        }

                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    } catch (error: Throwable) {
                        Log.e("QrScanner", "Use case binding failed", error)
                        scannerError = cameraErrorText
                    }
                }, mainExecutor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay & Back button
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    title ?: t("Сканирование QR", "QR scan"),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                // Focus area indicator
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .border(2.dp, nebulaColors.accent, RoundedCornerShape(24.dp))
                )
                scannerError?.let { message ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Text(
                            text = message,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    instruction ?: t(
                        "Наведите камеру на QR-код подписки",
                        "Point the camera at a subscription QR code"
                    ),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
