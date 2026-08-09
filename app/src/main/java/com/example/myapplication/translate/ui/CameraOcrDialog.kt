package com.example.myapplication.translate.ui

import android.graphics.RectF
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.Rect
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

/** Width over height of the on-screen preview: a 16:9 frame stood on end. */
private const val FRAME_ASPECT = 9f / 16f

/** A wide, shallow band across the middle — the shape a line of text on a sign tends to occupy. */
private val DEFAULT_CROP = Rect(left = 0.08f, top = 0.36f, right = 0.92f, bottom = 0.64f)

private fun Rect.toRectF() = RectF(left, top, right, bottom)

/**
 * Full-screen camera that reads the text inside a selection box.
 *
 * Nothing is captured until Start is tapped. Firing on stability alone, as this first did, took the
 * frame roughly a second after the camera opened — before the user had any chance to place the box
 * over the text they actually wanted.
 *
 * Start arms the capture rather than taking it outright: [SceneStabilityDetector] then waits for the
 * scene to settle, which absorbs the wobble of the tap itself and is why the detector exists. The
 * app still reads one frame rather than every frame, because a vision encode plus a translation
 * takes seconds — it just no longer guesses at which moment the user meant.
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
    onCapture: (jpeg: ByteArray, rotationDegrees: Int, crop: RectF) -> Unit,
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

    /** Set by Start. Read on the analyzer thread, hence the atomic rather than the UI flag. */
    val armedFlag = remember { AtomicBoolean(false) }

    // Held as state rather than a delegate so the capture path can read the current value at
    // the moment it fires, rather than whatever it was when the analyzer was set up.
    val cropState = remember { mutableStateOf(DEFAULT_CROP) }

    var reading by remember { mutableStateOf(SceneStabilityDetector.Reading.MOVING) }
    var armed by remember { mutableStateOf(false) }
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

        // Preview and capture are pinned to the same aspect ratio, and the preview is laid out at
        // exactly that ratio below, so what is on screen is the whole captured frame and nothing
        // else. That is the entire basis for the crop rectangle's normalised coordinates meaning
        // the same thing in both — left to their own devices CameraX will happily give the preview
        // 16:9 and the still 4:3, and the box would then select the wrong part of the image.
        val ratio = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()

        val preview = Preview.Builder().setResolutionSelector(ratio).build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val capture = ImageCapture.Builder()
            .setResolutionSelector(ratio)
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

                    if (armedFlag.get() &&
                        result == SceneStabilityDetector.Reading.STABLE &&
                        captureStarted.compareAndSet(false, true)
                    ) {
                        // Hopped to the main thread first so the crop rectangle is read from
                        // the same thread that edits it.
                        mainExecutor.execute {
                            capturing = true
                            takeStill(
                                capture = capture,
                                callbackExecutor = mainExecutor,
                                crop = cropState.value.toRectF(),
                                onCapture = onCapture,
                                onFailed = { failed = true },
                            )
                        }
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
            // The activity is locked to portrait, so a 16:9 frame is always 9:16 on screen.
            // Sizing the preview to exactly that leaves the overlay's coordinates equal to the
            // image's, with no letterbox arithmetic in between.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .aspectRatio(FRAME_ASPECT)
            ) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                CropOverlay(
                    crop = cropState.value,
                    onCropChange = { cropState.value = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }

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
                            // Once armed the reading no longer matters to the user: either way the
                            // thing to do is keep the phone still until it fires.
                            armed -> R.string.camera_hint_hold
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

            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
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

                Button(
                    onClick = {
                        armed = true
                        armedFlag.set(true)
                        // Restarts the settle grace, so the shake from this very tap is not what
                        // the capture is taken through.
                        detector.reset()
                    },
                    enabled = !armed && !failed,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        disabledContainerColor = Color.White.copy(alpha = 0.3f),
                        disabledContentColor = Color.Black.copy(alpha = 0.5f),
                    ),
                ) {
                    Text(stringResource(R.string.action_start))
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
    crop: RectF,
    onCapture: (ByteArray, Int, RectF) -> Unit,
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
                Log.i(TAG, "Captured ${image.width}x${image.height}, rotation $rotation, crop $crop")
                onCapture(bytes, rotation, crop)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Capture failed", exception)
                onFailed()
            }
        },
    )
}
