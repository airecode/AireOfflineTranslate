package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.translate.CrashReporter
import com.example.myapplication.translate.billing.DonationBilling
import com.example.myapplication.translate.translator.EngineStatus
import com.example.myapplication.translate.ui.CameraOcrDialog
import com.example.myapplication.translate.ui.DonationDialog
import com.example.myapplication.translate.ui.LoadingDialog
import com.example.myapplication.translate.ui.ModelsDialog
import com.example.myapplication.translate.ui.Phase
import com.example.myapplication.translate.ui.RunProgressDialog
import com.example.myapplication.translate.ui.ConversationViewModel
import com.example.myapplication.translate.ui.TranslateScreen
import com.example.myapplication.translate.ui.TranslateTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Installed before anything else so a crash during engine setup is still captured.
        CrashReporter.install(this)

        // A face-to-face conversation involves long stretches with nobody touching the screen.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            TranslateTheme {
                val viewModel: ConversationViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()

                var micGranted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> micGranted = granted }

                // Photo picker: no storage permission needed, and it only ever hands back the one
                // image the user chose.
                val pickPhoto = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()
                ) { uri -> uri?.let(viewModel::onImagePicked) }

                var showCamera by remember { mutableStateOf(false) }

                // Asked for on first use, not at startup: someone who only ever speaks or picks
                // photos should never see a camera prompt.
                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        showCamera = true
                    } else {
                        viewModel.onCameraDenied()
                    }
                }

                fun scanWithCamera() {
                    val granted = ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        showCamera = true
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                if (showCamera) {
                    CameraOcrDialog(
                        onCapture = { jpeg, rotation ->
                            // Closed before the run starts, so the progress dialog is what the user
                            // sees next rather than a frozen preview.
                            showCamera = false
                            viewModel.onCameraCapture(jpeg, rotation)
                        },
                        onDismiss = { showCamera = false },
                    )
                }

                LaunchedEffect(micGranted) {
                    if (!micGranted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                // ProcessLifecycleOwner, not this activity's lifecycle: it fires only when the
                // whole app moves between foreground and background, so a screen rotation or a
                // permission dialog does not start the unload countdown.
                val processOwner = ProcessLifecycleOwner.get()
                DisposableEffect(processOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                            Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                            else -> Unit
                        }
                    }
                    processOwner.lifecycle.addObserver(observer)
                    onDispose { processOwner.lifecycle.removeObserver(observer) }
                }

                // Billing is bound to the Activity, not the ViewModel: launchBillingFlow needs an
                // Activity, and the connection should not outlive the screen that can show it.
                val billing = remember { DonationBilling(applicationContext) }
                val billingState by billing.state.collectAsStateWithLifecycle()
                var showDonation by remember { mutableStateOf(false) }

                DisposableEffect(billing) {
                    billing.start()
                    onDispose { billing.release() }
                }

                // One modal at a time. A run that triggers an auto-load is in both states at once —
                // TRANSLATING and Loading — and stacking two dialogs would show the user two
                // cancel buttons over each other. Loading wins because it is the more specific
                // thing to say, and cancelling it abandons the run as well.
                //
                // LISTENING and SPEAKING have no dialog. Recording is driven by the panel's own
                // microphone button, which becomes a stop button; speaking has the translation on
                // screen already and the user should be able to read along.
                when {
                    // Driven by engine status rather than a local flag, so this covers the
                    // auto-load paths and not just the load button.
                    state.engineStatus is EngineStatus.Loading -> LoadingDialog(
                        modelName = state.activeVariant.displayName,
                        cancelling = state.cancellingLoad,
                        onCancel = viewModel::onCancelLoad,
                    )

                    state.phase == Phase.TRANSLATING -> RunProgressDialog(
                        onCancel = viewModel::onCancelRun,
                    )
                }

                var showModels by remember { mutableStateOf(false) }
                if (showModels) {
                    ModelsDialog(
                        activeVariant = state.activeVariant,
                        states = state.modelStates,
                        onSelect = viewModel::onSelectModel,
                        onDownload = viewModel::onDownloadModel,
                        onCancelDownload = viewModel::onCancelDownload,
                        onDelete = viewModel::onDeleteModel,
                        onDismiss = { showModels = false },
                    )
                }

                if (showDonation) {
                    DonationDialog(
                        state = billingState,
                        onSelect = { tier -> billing.launch(this@MainActivity, tier) },
                        onDismiss = { showDonation = false },
                    )
                }

                TranslateScreen(
                    state = state,
                    onDonate = { showDonation = true },
                    micPermissionGranted = micGranted,
                    onMicToggled = viewModel::onMicToggled,
                    onSpeakAgain = viewModel::onSpeakAgain,
                    onCopyText = viewModel::onCopyText,
                    onSwapLanguages = viewModel::onSwapLanguages,
                    onLanguageSelected = viewModel::onLanguageSelected,
                    onSplitChanged = viewModel::onSplitChanged,
                    onToggleFarRotation = viewModel::onToggleFarRotation,
                    onRestoreLayout = viewModel::onRestoreLayout,
                    onTranslateTypedText = viewModel::onTranslateTypedText,
                    onPickPhoto = {
                        viewModel.onExternalActivityLaunched()
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    // No onExternalActivityLaunched: the camera is a dialog inside this app, not a
                    // hand-off, so the idle-unload countdown must not start.
                    onScanCamera = { scanWithCamera() },
                    onRestartSession = viewModel::onRestartSession,
                    onManageModels = { showModels = true },
                    onLoadModel = viewModel::onLoadModel,
                    onUnloadModel = viewModel::onUnloadModel,
                )
            }
        }
    }
}
