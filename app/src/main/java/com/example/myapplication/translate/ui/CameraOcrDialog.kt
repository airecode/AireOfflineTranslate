package com.example.myapplication.translate.ui

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.R
import com.example.myapplication.translate.SceneStabilityDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "CameraOcr"

/**
 * Full-screen camera that reads text without a shutter button.
 *
 * There is nothing to press. The preview runs, a [SceneStabilityDetector] watches for the user to
 * stop moving, and the still is taken at that moment. That is the only workable reading of "live"
 * OCR on-device: a vision encode plus a translation takes seconds, so the app cannot read every
 * frame — it has to choose one, and the moment the user stops moving is when they have aimed.
 *
 * Capture hands the raw JPEG and its rotation straight out through [onCapture] without decoding
 * anything. Normalising the frame is several megabytes of bitmap work, and the caller already has a
 * coroutine to do it in; doing it here would mean doing it on a camera callback thread.
 *
 * One shot per opening. Re-scanning on a loop would spend the battery re-reading a sign that has
 * not changed, and the result has to be read on the panels behind this screen anyway.
 */
@Composable
fun CameraOcrDialog(
    onCapture: (jpeg: ByteArray, rotationDegrees: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val detector = remember { SceneStabilityDetector() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    /**
     * Race guard, separate from [capturing] below: the detector keeps reporting STABLE on every
     * frame after the first, and those frames arrive on the analyzer thread, not the main one.
     */
    val captureStarted = remember { AtomicBoolean(false) }

    var reading by remember { mutableStateOf(SceneStabilityDetector.Reading.MOVING) }
    var capturing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    LaunchedEffect(Unit) {
        val provider = try {
            withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
        } catch (t: Throwable) {
            Log.e(TAG, "No camera provider", t)
            failed = true
            return@LaunchedEffect
        }

        val preview = Preview.Builder().build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val capture = ImageCapture.Builder()
            // The frame wanted is the one the user was looking at when they held still, so latency
            // matters more than the last of the image quality.
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val mainExecutor = ContextCompat.getMainExecutor(context)

        val analysis = ImageAnalysis.Builder()
            // Only the newest frame matters for a movement test; queueing stale ones would make the
            // detector report on a scene that has already gone.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { stage ->
                stage.setAnalyzer(analysisExecutor) { frame ->
                    val result = frame.use { proxy ->
                        proxy.image?.let(detector::offer)
                    } ?: return@setAnalyzer

                    mainExecutor.execute { reading = result }

                    if (result == SceneStabilityDetector.Reading.STABLE &&
                        captureStarted.compareAndSet(false, true)
                    ) {
                        mainExecutor.execute { capturing = true }
                        takeStill(
                            capture = capture,
                            callbackExecutor = mainExecutor,
                            onCapture = onCapture,
                            onFailed = { failed = true },
                        )
                    }
                }
            }

        try {
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
                capture,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Could not bind the camera", t)
            failed = true
            return@LaunchedEffect
        }

        // Reset last: the grace period must start when frames start, not when composition did.
        detector.reset()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Scrimmed, so the hint stays readable over a bright scene.
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(
                        when {
                            failed -> R.string.msg_camera_unavailable
                            capturing -> R.string.camera_hint_capturing
                            reading == SceneStabilityDetector.Reading.SETTLING ->
                                R.string.camera_hint_hold
                            else -> R.string.camera_hint_aim
                        }
                    ),
                    color = Color.White,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                // Capture is not instant, and by this point the user has been told to hold still —
                // they need to see that something is happening.
                if (capturing) {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.18f),
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

/**
 * Takes the still and passes the bytes on untouched.
 *
 * [onFailed] rather than a thrown exception: this runs on a camera callback, where throwing takes
 * the process down instead of showing the user anything.
 */
private fun takeStill(
    capture: ImageCapture,
    callbackExecutor: java.util.concurrent.Executor,
    onCapture: (ByteArray, Int) -> Unit,
    onFailed: () -> Unit,
) {
    capture.takePicture(
        callbackExecutor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotation = image.imageInfo.rotationDegrees
                val bytes = image.use { proxy ->
                    val buffer = proxy.planes[0].buffer
                    ByteArray(buffer.remaining()).also(buffer::get)
                }
                onCapture(bytes, rotation)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture failed", exception)
                onFailed()
            }
        },
    )
}
