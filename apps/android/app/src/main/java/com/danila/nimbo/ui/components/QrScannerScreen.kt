package com.danila.nimbo.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.SystemClock
import android.view.View
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
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import com.danila.nimbo.BuildConfig
import com.danila.nimbo.ui.i18n.t
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.Logger
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val QR_SCANNER_LOG_TAG = "QrScanner"

private fun logQrFailure(sessionId: String, stage: String, error: Throwable) {
    Logger.e(
        QR_SCANNER_LOG_TAG,
        "session=$sessionId stage=$stage failed type=${error::class.java.name}",
        error
    )
    val stack = error.stackTrace
        .take(10)
        .joinToString(" <- ") { frame ->
            "${frame.className.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}"
        }
    if (stack.isNotBlank()) {
        Logger.e(QR_SCANNER_LOG_TAG, "session=$sessionId stage=$stage stack=$stack")
    }
}

private fun cameraInventory(context: Context): String = runCatching {
    val manager = context.getSystemService(CameraManager::class.java)
    manager.cameraIdList.joinToString(prefix = "[", postfix = "]") { id ->
        val characteristics = manager.getCameraCharacteristics(id)
        val facing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_BACK -> "back"
            CameraCharacteristics.LENS_FACING_FRONT -> "front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
            else -> "unknown"
        }
        val level = when (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "legacy"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "limited"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "full"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "level3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "external"
            else -> "unknown"
        }
        "$id:$facing/$level"
    }
}.getOrElse { error ->
    "unavailable:${error::class.java.simpleName}:${error.message.orEmpty().take(120)}"
}

@Composable
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onBack: () -> Unit,
    title: String? = null,
    instruction: String? = null,
    diagnosticSource: String = "generic"
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val nebulaColors = LocalNebulaColors.current
    val sessionId = remember {
        java.lang.Long.toString(SystemClock.elapsedRealtime(), 36).uppercase()
    }
    val cameraErrorText = t(
        "Не удалось запустить камеру. Закройте сканер и попробуйте снова.",
        "Could not start the camera. Close the scanner and try again."
    )
    val cameraPermissionErrorText = t(
        "Нет доступа к камере. Разрешите доступ в настройках приложения.",
        "Camera access is unavailable. Allow it in the app settings."
    )
    val qrEngineErrorText = t(
        "Не удалось запустить распознавание QR-кода.",
        "Could not start QR code recognition."
    )
    val executor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val handledResult = remember { AtomicBoolean(false) }
    val disposed = remember { AtomicBoolean(false) }
    val analyzedFrames = remember { AtomicLong(0L) }
    val analysisFailureLogged = remember { AtomicBoolean(false) }
    val currentOnResult by rememberUpdatedState(onResult)
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraStateObserver by remember { mutableStateOf<Observer<CameraState>?>(null) }
    var scannerError by remember { mutableStateOf<String?>(null) }
    var failureStage by remember { mutableStateOf<String?>(null) }
    var cameraStarting by remember { mutableStateOf(true) }
    var retryKey by remember { mutableIntStateOf(0) }
    val scanner = remember {
        runCatching {
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            BarcodeScanning.getClient(options)
        }.onFailure { error ->
            logQrFailure(sessionId, "MLKIT_INIT", error)
        }.getOrNull()
    }

    DisposableEffect(lifecycleOwner, previewView, diagnosticSource, sessionId) {
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        Logger.i(
            QR_SCANNER_LOG_TAG,
            "session=$sessionId event=opened source=$diagnosticSource " +
                "app=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) " +
                "device=${Build.MANUFACTURER}/${Build.MODEL} sdk=${Build.VERSION.SDK_INT} " +
                "permission=$permissionGranted lifecycle=${lifecycleOwner.lifecycle.currentState} " +
                "featureAny=${context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)} " +
                "featureBack=${context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)} " +
                "cameras=${cameraInventory(context)}"
        )

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            Logger.d(
                QR_SCANNER_LOG_TAG,
                "session=$sessionId event=lifecycle value=$event " +
                    "state=${lifecycleOwner.lifecycle.currentState} " +
                    "previewAttached=${previewView.isAttachedToWindow}"
            )
        }
        val streamObserver = Observer<PreviewView.StreamState> { state ->
            Logger.i(
                QR_SCANNER_LOG_TAG,
                "session=$sessionId event=preview_stream state=$state " +
                    "size=${previewView.width}x${previewView.height}"
            )
        }
        var layoutLogged = false
        val layoutListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            if (!layoutLogged && view.width > 0 && view.height > 0) {
                layoutLogged = true
                Logger.d(
                    QR_SCANNER_LOG_TAG,
                    "session=$sessionId event=preview_layout size=${view.width}x${view.height} " +
                        "attached=${view.isAttachedToWindow}"
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        previewView.previewStreamState.observe(lifecycleOwner, streamObserver)
        previewView.addOnLayoutChangeListener(layoutListener)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            previewView.previewStreamState.removeObserver(streamObserver)
            previewView.removeOnLayoutChangeListener(layoutListener)
        }
    }

    LaunchedEffect(scanner, lifecycleOwner, retryKey) {
        handledResult.set(false)
        analyzedFrames.set(0L)
        analysisFailureLogged.set(false)
        scannerError = null
        failureStage = null
        cameraStarting = true
        Logger.i(
            QR_SCANNER_LOG_TAG,
            "session=$sessionId event=start attempt=${retryKey + 1} " +
                "permission=${ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)} " +
                "lifecycle=${lifecycleOwner.lifecycle.currentState} " +
                "previewAttached=${previewView.isAttachedToWindow} size=${previewView.width}x${previewView.height}"
        )

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            failureStage = "PERMISSION"
            Logger.e(QR_SCANNER_LOG_TAG, "session=$sessionId stage=PERMISSION camera permission is not granted")
            scannerError = cameraPermissionErrorText
            cameraStarting = false
            return@LaunchedEffect
        }
        val activeScanner = scanner
        if (activeScanner == null) {
            failureStage = "MLKIT_INIT"
            Logger.e(QR_SCANNER_LOG_TAG, "session=$sessionId stage=MLKIT_INIT scanner client is null")
            scannerError = qrEngineErrorText
            cameraStarting = false
            return@LaunchedEffect
        }

        // Permission callbacks and full-screen navigation can complete in the same
        // frame. Give the PreviewView/lifecycle one frame to become attachable.
        delay(180)
        Logger.d(
            QR_SCANNER_LOG_TAG,
            "session=$sessionId event=after_attach_delay lifecycle=${lifecycleOwner.lifecycle.currentState} " +
                "previewAttached=${previewView.isAttachedToWindow} size=${previewView.width}x${previewView.height}"
        )

        val providerStartedAt = SystemClock.elapsedRealtime()
        val provider = try {
            Logger.i(QR_SCANNER_LOG_TAG, "session=$sessionId stage=PROVIDER requesting CameraX provider")
            val providerFuture = ProcessCameraProvider.getInstance(context)
            suspendCancellableCoroutine<ProcessCameraProvider> { continuation ->
                providerFuture.addListener({
                    runCatching { providerFuture.get() }
                        .onSuccess { value ->
                            if (continuation.isActive) continuation.resume(value)
                        }
                        .onFailure { error ->
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                }, mainExecutor)
            }
        } catch (error: Throwable) {
            failureStage = "PROVIDER"
            logQrFailure(sessionId, "PROVIDER", error)
            scannerError = cameraErrorText
            cameraStarting = false
            return@LaunchedEffect
        }

        cameraProvider = provider
        Logger.i(
            QR_SCANNER_LOG_TAG,
            "session=$sessionId stage=PROVIDER readyInMs=${SystemClock.elapsedRealtime() - providerStartedAt} " +
                "availableCameraInfos=${provider.availableCameraInfos.size}"
        )
        var lastBindingError: Throwable? = null
        for (attempt in 0..1) {
            try {
                Logger.i(
                    QR_SCANNER_LOG_TAG,
                    "session=$sessionId stage=BIND attempt=${attempt + 1} " +
                        "lifecycle=${lifecycleOwner.lifecycle.currentState} " +
                        "previewAttached=${previewView.isAttachedToWindow}"
                )
                imageAnalysis?.clearAnalyzer()
                cameraStateObserver?.let { observer ->
                    boundCamera?.cameraInfo?.cameraState?.removeObserver(observer)
                }
                cameraStateObserver = null
                boundCamera = null
                provider.unbindAll()

                val hasBackCamera = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                val hasFrontCamera = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                Logger.i(
                    QR_SCANNER_LOG_TAG,
                    "session=$sessionId stage=SELECTOR hasBack=$hasBackCamera hasFront=$hasFrontCamera"
                )
                val selector = when {
                    hasBackCamera -> CameraSelector.DEFAULT_BACK_CAMERA
                    hasFrontCamera -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> error("No usable camera was reported by CameraX")
                }
                val selectedLens = if (hasBackCamera) "back" else "front"
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                imageAnalysis = analysis
                analysis.setAnalyzer(executor) { imageProxy ->
                    val frameNumber = analyzedFrames.incrementAndGet()
                    if (frameNumber == 1L) {
                        Logger.i(
                            QR_SCANNER_LOG_TAG,
                            "session=$sessionId event=first_frame " +
                                "size=${imageProxy.width}x${imageProxy.height} " +
                                "rotation=${imageProxy.imageInfo.rotationDegrees} format=${imageProxy.format}"
                        )
                    }
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
                            mediaImage!!,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        activeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val value = barcodes.firstNotNullOfOrNull { barcode ->
                                    barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }
                                }
                                if (value != null && handledResult.compareAndSet(false, true)) {
                                    Logger.i(
                                        QR_SCANNER_LOG_TAG,
                                        "session=$sessionId event=qr_detected " +
                                            "barcodes=${barcodes.size} valueLength=${value.length}"
                                    )
                                    mainExecutor.execute {
                                        if (!disposed.get()) currentOnResult(value)
                                    }
                                }
                            }
                            .addOnFailureListener { error ->
                                if (analysisFailureLogged.compareAndSet(false, true)) {
                                    logQrFailure(sessionId, "MLKIT_ANALYSIS", error)
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } catch (error: Throwable) {
                        if (analysisFailureLogged.compareAndSet(false, true)) {
                            logQrFailure(sessionId, "FRAME_CONVERSION", error)
                        }
                        imageProxy.close()
                    }
                }

                val camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                val stateObserver = Observer<CameraState> { state ->
                    val cameraError = state.error
                    Logger.i(
                        QR_SCANNER_LOG_TAG,
                        "session=$sessionId event=camera_state type=${state.type} " +
                            "errorCode=${cameraError?.code ?: "none"}"
                    )
                    cameraError?.cause?.let { cause ->
                        logQrFailure(sessionId, "CAMERA_STATE_${cameraError.code}", cause)
                    }
                }
                boundCamera = camera
                cameraStateObserver = stateObserver
                camera.cameraInfo.cameraState.observe(lifecycleOwner, stateObserver)
                Logger.i(
                    QR_SCANNER_LOG_TAG,
                    "session=$sessionId stage=BIND success lens=$selectedLens " +
                        "previewResolution=${preview.resolutionInfo?.resolution} " +
                        "analysisResolution=${analysis.resolutionInfo?.resolution}"
                )
                scannerError = null
                failureStage = null
                cameraStarting = false
                return@LaunchedEffect
            } catch (error: Throwable) {
                lastBindingError = error
                logQrFailure(sessionId, "BIND_ATTEMPT_${attempt + 1}", error)
                imageAnalysis?.clearAnalyzer()
                runCatching { provider.unbindAll() }
                    .onFailure { unbindError -> logQrFailure(sessionId, "UNBIND_AFTER_FAILURE", unbindError) }
                if (attempt == 0) delay(650)
            }
        }

        failureStage = "BIND"
        lastBindingError?.let { logQrFailure(sessionId, "BIND_FINAL", it) }
        scannerError = if (lastBindingError is SecurityException) {
            cameraPermissionErrorText
        } else {
            cameraErrorText
        }
        cameraStarting = false
    }

    DisposableEffect(Unit) {
        onDispose {
            Logger.i(
                QR_SCANNER_LOG_TAG,
                "session=$sessionId event=disposed frames=${analyzedFrames.get()} " +
                    "handled=${handledResult.get()} failureStage=${failureStage ?: "none"}"
            )
            disposed.set(true)
            imageAnalysis?.clearAnalyzer()
            cameraStateObserver?.let { observer ->
                boundCamera?.cameraInfo?.cameraState?.removeObserver(observer)
            }
            runCatching { cameraProvider?.unbindAll() }
                .onFailure { error -> logQrFailure(sessionId, "DISPOSE_UNBIND", error) }
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
            factory = { previewView },
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
                if (cameraStarting && scannerError == null) {
                    CircularProgressIndicator(
                        color = nebulaColors.accent,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(42.dp)
                    )
                }
                scannerError?.let { message ->
                    Surface(
                        color = Color.Black.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(28.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text(
                                text = message,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            failureStage?.let { stage ->
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = t(
                                        "Код диагностики: QR-$sessionId-$stage\nЗакройте сканер, откройте Настройки → Логи и отправьте отчёт.",
                                        "Diagnostic code: QR-$sessionId-$stage\nClose the scanner, open Settings → Logs and send the report."
                                    ),
                                    color = Color.White.copy(alpha = 0.68f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            TextButton(
                                onClick = {
                                    Logger.i(
                                        QR_SCANNER_LOG_TAG,
                                        "session=$sessionId event=retry_clicked from=${failureStage ?: "unknown"}"
                                    )
                                    retryKey += 1
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = nebulaColors.accent)
                            ) {
                                Text(t("Повторить", "Retry"))
                            }
                        }
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
