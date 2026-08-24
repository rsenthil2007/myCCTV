package com.opencode.multilensipcam

import android.Manifest
import android.content.BroadcastReceiver
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.opencode.multilensipcam.databinding.ActivityMainBinding
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraRepository: CameraLensRepository
    private lateinit var cameraScanCache: CameraScanCache
    private lateinit var cameraScanStateFactory: CameraScanStateFactory
    private lateinit var cameraScanRunner: CameraScanRunner
    private lateinit var cameraStreamValidator: CameraStreamValidator
    private lateinit var cameraScanCoordinator: CameraScanCoordinator
    private lateinit var streamer: CameraStreamer
    private lateinit var audioStreamer: AudioStreamer
    private lateinit var server: MjpegHttpServer
    private lateinit var rtspServer: RtspServer
    private lateinit var streamingSession: StreamingSessionController
    private lateinit var webControlCoordinator: WebControlCoordinator
    private var blackoutOverlay: View? = null

    // Current camera/control selections mirrored by native UI and WebUI.
    private var publicCameraOptions: List<CameraLensOption> = emptyList()
    private var options: List<CameraLensOption> = emptyList()
    private var availableFocusModes: List<FocusMode> = listOf(FocusMode.AUTO)
    private var resolutionEntries: List<ResolutionEntry> = emptyList()
    private var fpsEntries: List<FpsEntry> = emptyList()
    private var currentCapabilities: CameraCapabilities? = null
    private var selectedOption: CameraLensOption? = null
    private var selectedOutputSize: Size? = null
    private var selectedTargetFps: Int? = null
    private var selectedStreamQuality: Int = StreamControlOptions.defaultQuality
    private var selectedZoomRatio: Float? = null
    private var selectedFocusMode: FocusMode = FocusMode.AUTO
    private var selectedFocusDistance: Float? = 0f
    private var selectedExposureCompensation: Int = 0
    private var selectedTorchEnabled = false
    @Volatile
    private var selectedVideoOverlayEnabled = false
    @Volatile
    private var selectedVideoOverlaySize = 25
    private var selectedMjpegPipeline = MjpegPipeline.YUV_JPEG
    private var useFullMjpegSize = true
    private var previewAdjustment = PreviewAdjustment.DEFAULT
    private var isManualResolutionMode = false
    private var isUnlimitedFpsSelected = false
    private var isPanelOpen = true
    private var isChineseUi = true
    private var activeSkin: UiSkin = UiSkin.GREEN
    private var hasLoadedCameraOptions = false
    private var statusMode: StatusMode = StatusMode.IDLE
    private var suppressUiCallbacks = false
    private var lastStatusMessage = "Idle"
    @Volatile
    private var audioEnabled = false
    @Volatile
    private var lastAudioStatus = "Audio disabled"
    @Volatile
    private var rtspAutoEnabledAudio = false
    @Volatile
    private var batteryPercent: Int? = null
    @Volatile
    private var batteryCharging = false
    private var isNativeCameraDebugExpanded = false
    private var isAppPreviewTuningExpanded = false
    private val port = 41737
    private val rtspPort = 8554
    private var previousScreenBrightness: Float? = null
    private val h264IdleHandler = Handler(Looper.getMainLooper())
    private val h264IdleStopRunnable = Runnable { stopH264IfStillIdle() }
    private val mjpegDemandSyncHandler = Handler(Looper.getMainLooper())
    private val mjpegDemandSyncRunnable = Runnable {
        if (::streamer.isInitialized) {
            streamer.onMjpegDemandStateChanged()
        }
    }
    private val cameraDeviceProfile = CameraDeviceProfileLibrary.profileForCurrentDevice()
    private val cameraScanInProgress: Boolean
        get() = ::cameraScanCoordinator.isInitialized && cameraScanCoordinator.inProgress
    private val isStreaming: Boolean
        get() = ::streamingSession.isInitialized && streamingSession.isStreaming
    private val h264EnabledForSession: Boolean
        get() = ::streamingSession.isInitialized && streamingSession.h264EnabledForSession
    private val audioRetryHandler = Handler(Looper.getMainLooper())
    private val audioStartRetryRunnable = Runnable { retryAudioStreamingAfterStop() }
    private var audioStartRetryAttempts = 0
    private val batteryStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateBatteryStatus(intent)
        }
    }
    @Volatile
    private var lastKnownDisplayRotation: Int = Surface.ROTATION_0
    private val displayRotationListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            refreshLandscapeStreamingOrientation(force = false)
        }
    }

    // Activity lifecycle and startup wiring.
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadCameraOptions()
            requestAudioPermissionIfNeeded()
        } else {
            updateStatus("Camera permission denied")
        }
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            lastAudioStatus = "Audio permission granted"
            if (audioEnabled && isStreaming) {
                startAudioStreaming()
            } else {
                updateStatus("Audio permission granted")
                updateParameterSummary()
            }
        } else {
            lastAudioStatus = "Microphone permission denied"
            updateStatus("Microphone permission denied")
            updateParameterSummary()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        lastKnownDisplayRotation = currentDisplayRotation()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding.previewView.setAspectRatio(9, 16)
        binding.previewView.configurePreview(1920, 1080, 270)
        applyPreviewAdjustment()

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraRepository = CameraLensRepository(cameraManager)
        cameraScanCache = CameraScanCache(this)
        cameraScanStateFactory = CameraScanStateFactory(
            repository = cameraRepository,
            scanCache = cameraScanCache,
            deviceProfile = cameraDeviceProfile,
            packageName = packageName
        )
        cameraStreamValidator = CameraStreamValidator(cameraManager, cameraRepository)
        cameraScanRunner = CameraScanRunner(
            cameraManager = cameraManager,
            repository = cameraRepository,
            scanCache = cameraScanCache,
            deviceProfile = cameraDeviceProfile,
            streamValidator = cameraStreamValidator,
            stateFactory = cameraScanStateFactory,
            packageName = packageName
        )
        cameraScanCoordinator = CameraScanCoordinator(
            scanCache = cameraScanCache,
            stateFactory = cameraScanStateFactory,
            scanRunner = cameraScanRunner,
            optionsProvider = { options },
            isChineseUi = { isChineseUi },
            isStreaming = { isStreaming },
            selectedControlKey = { selectedOption?.let(cameraRepository::controlKeyFor) },
            refreshCameraOptionsPreservingSelection = ::refreshCameraOptionsPreservingSelection,
            refreshControls = ::refreshControls,
            stopStreaming = ::stopStreaming,
            runBlockingOnUiThread = { action ->
                UiThreadActions.runBlocking(activity = this) {
                    action()
                }
            }
        )
        server = MjpegHttpServer(
            port = port,
            snapshotCacheRoot = cacheDir,
            stateProvider = ::buildWebControlState,
            controlHandler = ::applyWebControls,
            h264RequestHandler = ::handleH264StreamRequest,
            audioRequestHandler = ::handleAudioStreamRequest,
            streamDebugProvider = { if (::streamer.isInitialized) streamer.debugState() else null },
            cameraDebugProvider = { cameraRepository.buildDebugState(packageName) },
            cameraScanStateProvider = ::buildCameraScanState,
            cameraScanHandler = ::runCameraScan,
            cameraScanCacheDeleteHandler = ::deleteCameraScanCache,
            cameraOpenProbeHandler = CameraOpenProbe(cameraManager)::run,
            mjpegClientCountChanged = ::onMjpegClientCountChanged,
            h264ClientCountChanged = ::onH264ClientCountChanged
        )
        rtspServer = RtspServer(
            port = rtspPort,
            streamRequestHandler = ::handleRtspStreamRequest,
            audioAvailableProvider = ::isRtspAudioAvailable,
            clientCountChanged = ::onH264ClientCountChanged
        )
        streamer = CameraStreamer(
            cameraManager = cameraManager,
            onFrame = { server.updateFrame(it) },
            onH264AccessUnit = { accessUnit ->
                server.updateH264(accessUnit)
                rtspServer.updateH264(accessUnit)
            },
            onStatus = ::updateStatus,
            videoOverlayStatusProvider = ::currentVideoOverlayStatus,
            mjpegOutputRotationProvider = ::currentMjpegOutputRotationDegrees,
            mjpegConsumerActive = { server.hasRecentMjpegClients(MJPEG_IDLE_GRACE_MS) }
        )
        audioStreamer = AudioStreamer(
            onAccessUnit = { accessUnit ->
                server.updateAudio(accessUnit)
                rtspServer.updateAudio(accessUnit)
            },
            onStatus = { status ->
                lastAudioStatus = status
                updateStatus(status)
            }
        )
        streamingSession = StreamingSessionController(
            repository = cameraRepository,
            server = server,
            streamer = streamer,
            startRequestProvider = ::streamingStartRequest,
            setPanelOpen = { isPanelOpen = it },
            setRequestedOrientation = { requestedOrientation = it },
            setKeepScreenOn = ::updateKeepScreenOn,
            updatePreviewPresentation = ::updatePreviewPresentation,
            updatePreviewFillMode = ::updatePreviewFillMode,
            updateStreamingUiState = ::updateStreamingUiState,
            updateUrl = ::updateUrl,
            updateParameterSummary = ::updateParameterSummary,
            updateStatus = ::updateStatus,
            audioEnabledProvider = { audioEnabled },
            audioPermissionGranted = ::hasAudioPermission,
            startAudio = ::startAudioStreaming,
            stopAudio = ::stopAudioStreaming,
            runOnUiThread = { action -> runOnUiThread(action) }
        )
        webControlCoordinator = WebControlCoordinator(
            isStreaming = { isStreaming },
            stopStreaming = ::stopStreaming,
            startStreaming = ::startStreaming,
            restartStreaming = ::restartStreaming,
            applyStreamPreset = ::applyStreamPreset,
            getPreviewAdjustment = { previewAdjustment },
            setPreviewAdjustment = { previewAdjustment = it },
            applyPreviewAdjustment = ::applyPreviewAdjustment,
            getMjpegPipeline = { selectedMjpegPipeline },
            setMjpegPipeline = { selectedMjpegPipeline = it },
            getMjpegFullSize = { useFullMjpegSize },
            setMjpegFullSize = { useFullMjpegSize = it },
            getVideoOverlayEnabled = { selectedVideoOverlayEnabled },
            setVideoOverlayEnabled = { selectedVideoOverlayEnabled = it },
            setVideoOverlaySize = { selectedVideoOverlaySize = it },
            applyAudioControl = ::applyAudioControl,
            applyWebCameraSelection = ::applyWebCameraSelection,
            selectedOption = { selectedOption },
            resolveCapabilities = { option -> currentCapabilities ?: cameraRepository.getCapabilities(option) },
            setCurrentCapabilities = { currentCapabilities = it },
            applyRecommendedSettings = ::applyRecommendedSettings,
            applyWebResolutionSelection = ::applyWebResolutionSelection,
            applyWebFpsSelection = ::applyWebFpsSelection,
            applyWebRuntimeControls = ::applyWebRuntimeControls,
            refreshControls = ::refreshControls,
            pushRuntimeControlsIfStreaming = ::pushRuntimeControlsIfStreaming,
            updateParameterSummary = ::updateParameterSummary
        )

        bindUi()
        binding.versionText.text = appVersionLabel()
        applyLanguageText()
        applySkinState()
        updateStreamingUiState()
        updateUrl()
        updateBatteryStatus(registerReceiver(batteryStatusReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
        checkPermissionAndLoad()
    }

    override fun onStart() {
        super.onStart()
        (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.registerDisplayListener(
            displayRotationListener,
            Handler(Looper.getMainLooper())
        )
        refreshLandscapeStreamingOrientation(force = true)
    }

    override fun onStop() {
        runCatching {
            (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                ?.unregisterDisplayListener(displayRotationListener)
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::streamingSession.isInitialized) {
            streamingSession.shutdown()
            if (::rtspServer.isInitialized) rtspServer.stop()
        } else {
            streamer.stop()
            server.stop()
            if (::rtspServer.isInitialized) rtspServer.stop()
            updateKeepScreenOn(false)
        }
        h264IdleHandler.removeCallbacks(h264IdleStopRunnable)
        mjpegDemandSyncHandler.removeCallbacks(mjpegDemandSyncRunnable)
        runCatching { unregisterReceiver(batteryStatusReceiver) }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshLandscapeStreamingOrientation(force = true)
        if (!isLandscapeStreaming()) {
            refreshPreviewPresentationForSelection()
        }
        updateControlSurfaceMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            refreshLandscapeStreamingOrientation(force = true)
        }
    }

    // Native control binding and compact chip/collapsible renderers.
    private fun bindUi() {
        binding.settingsButton.setOnClickListener { toggleFloatingPanel() }
        binding.previewStage.setOnClickListener {
            if (isLandscapeStreaming() && isPanelOpen) {
                isPanelOpen = false
                updateControlSurfaceMode()
            }
        }
        binding.languageToggleButton.setOnClickListener { toggleLanguage() }
        binding.skinToggleButton.setOnClickListener { toggleSkin() }
        binding.preset1080p30Button.setOnClickListener { applyStreamPreset(StreamControlOptions.preset1080p30) }
        binding.preset4k30Button.setOnClickListener { applyStreamPreset(StreamControlOptions.preset4k30) }
        binding.preset720p60Button.setOnClickListener { applyStreamPreset(StreamControlOptions.preset720p30) }
        binding.blackoutButton.setOnClickListener { enterBlackoutMode() }
        binding.applyManualResolutionButton.setOnClickListener { applyManualResolution() }
        binding.nativeCameraScanButton.setOnClickListener { runNativeCameraScanAsync() }
        binding.nativeCameraCacheRefreshButton.setOnClickListener { refreshNativeVerifiedCameraList() }
        binding.nativeCameraReportButton.setOnClickListener { openCameraReport() }
        binding.previewRotationMinusButton.setOnClickListener { adjustPreviewRotation(-90) }
        binding.previewRotationResetButton.setOnClickListener { resetPreviewTuning() }
        binding.previewRotationPlusButton.setOnClickListener { adjustPreviewRotation(90) }
        binding.previewScaleDownButton.setOnClickListener { adjustPreviewScale(-0.25f) }
        binding.previewScaleResetButton.setOnClickListener { setPreviewFitMode() }
        binding.previewScaleUpButton.setOnClickListener { adjustPreviewScale(0.25f) }
        bindCollapsibleBlock(binding.nativeCameraDebugBlock) {
            isNativeCameraDebugExpanded = !isNativeCameraDebugExpanded
            updateCollapsibleBlocks()
        }
        bindCollapsibleBlock(binding.appPreviewTuningBlock) {
            isAppPreviewTuningExpanded = !isAppPreviewTuningExpanded
            updateCollapsibleBlocks()
        }
        updateCollapsibleBlocks()

        binding.cameraSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressUiCallbacks) return
                val option = options.getOrNull(position) ?: return
                val currentKey = selectedOption?.let(cameraRepository::controlKeyFor)
                if (cameraRepository.controlKeyFor(option) == currentKey) return
                selectedOption = option
                refreshControls(resetCameraSpecificValues = true)
                updateParameterSummary()
                if (isStreaming) restartStreaming("Camera changed")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.resolutionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressUiCallbacks) return
                val entry = resolutionEntries.getOrNull(position) ?: return
                if (entry.isManual) {
                    isManualResolutionMode = true
                    updateManualResolutionUi()
                    selectedOutputSize?.let(::populateManualResolutionInputs)
                    updateParameterSummary()
                    return
                }

                val size = entry.size ?: return
                isManualResolutionMode = false
                updateManualResolutionUi()
                if (size == selectedOutputSize) {
                    updateParameterSummary()
                    return
                }
                selectedOutputSize = size
                currentCapabilities?.let { updatePreviewPresentation(size, it.sensorOrientation) }
                updateParameterSummary()
                if (isStreaming) restartStreaming("Resolution changed")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.fpsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressUiCallbacks) return
                val entry = fpsEntries.getOrNull(position) ?: return
                val capabilities = currentCapabilities ?: return
                val resolvedValue = entry.value ?: cameraRepository.chooseHighestFps(capabilities)
                val changed = isUnlimitedFpsSelected != entry.isUnlimited || selectedTargetFps != resolvedValue
                if (!changed) return
                isUnlimitedFpsSelected = entry.isUnlimited
                selectedTargetFps = resolvedValue
                updateParameterSummary()
                if (isStreaming) restartStreaming("Frame rate changed")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.focusModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressUiCallbacks) return
                val mode = availableFocusModes.getOrNull(position) ?: FocusMode.AUTO
                if (mode == selectedFocusMode) return
                selectedFocusMode = mode
                refreshControls(resetCameraSpecificValues = false)
                updateParameterSummary()
                pushRuntimeControlsIfStreaming()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        binding.qualitySeekBar.max = StreamControlOptions.qualityOptions.lastIndex
        binding.qualitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (suppressUiCallbacks) return
                selectedStreamQuality = StreamControlOptions.qualityOptions.getOrElse(progress) { StreamControlOptions.defaultQuality }
                binding.qualityValueText.text = NativeStatusSummaries.formatQuality(selectedStreamQuality)
                updateParameterSummary()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                pushRuntimeControlsIfStreaming()
            }
        })

        binding.zoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (suppressUiCallbacks) return
                val range = effectiveZoomRange(selectedOption, currentCapabilities) ?: return
                selectedZoomRatio = NativeControlFormatters.progressToZoom(range, progress)
                binding.zoomValueText.text = NativeControlFormatters.formatZoom(selectedZoomRatio, isChineseUi)
                updateParameterSummary()
                pushRuntimeControlsIfStreaming()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.focusDistanceSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (suppressUiCallbacks) return
                val maxDistance = currentCapabilities?.minimumFocusDistance ?: return
                selectedFocusDistance = NativeControlFormatters.progressToFocusDistance(maxDistance, progress)
                binding.focusDistanceValueText.text = NativeControlFormatters.formatFocusDistance(selectedFocusDistance, isChineseUi)
                updateParameterSummary()
                pushRuntimeControlsIfStreaming()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.exposureSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (suppressUiCallbacks) return
                val range = currentCapabilities?.exposureCompensationRange ?: return
                selectedExposureCompensation = range.lower + progress
                binding.exposureValueText.text = NativeControlFormatters.formatExposure(selectedExposureCompensation)
                updateParameterSummary()
                pushRuntimeControlsIfStreaming()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        binding.torchSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressUiCallbacks) return@setOnCheckedChangeListener
            if (selectedTorchEnabled == isChecked) return@setOnCheckedChangeListener
            selectedTorchEnabled = isChecked
            updateParameterSummary()
            pushRuntimeControlsIfStreaming()
        }
        binding.videoOverlaySizeSeekBar.max =
            VIDEO_OVERLAY_SIZE_MAX_PERCENT - VIDEO_OVERLAY_SIZE_MIN_PERCENT
        binding.videoOverlaySizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (suppressUiCallbacks) return
                val sizeValue = VIDEO_OVERLAY_SIZE_MIN_PERCENT + progress
                if (selectedVideoOverlaySize == sizeValue) return
                selectedVideoOverlaySize = sizeValue
                binding.videoOverlaySizeValueText.text = sizeValue.toString()
                updateParameterSummary()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        binding.videoOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressUiCallbacks) return@setOnCheckedChangeListener
            if (selectedVideoOverlayEnabled == isChecked) return@setOnCheckedChangeListener
            selectedVideoOverlayEnabled = isChecked
            syncVideoOverlayControls()
            updateParameterSummary()
        }
        syncVideoOverlayControls()

        binding.startButton.setOnClickListener { ensurePermissionAndStart() }
        binding.stopButton.setOnClickListener { stopStreaming("Stopped") }
    }

    private fun runNativeCameraScanAsync() {
        updateNativeCameraScanInProgress(true)
        Thread {
            val response = runCatching { runCameraScan() }.getOrElse { throwable ->
                CameraScanResponse(
                    ok = false,
                    state = buildCameraScanState(),
                    results = emptyList(),
                    error = "${throwable.javaClass.simpleName}: ${throwable.message?.take(120)}"
                )
            }
            runOnUiThread {
                updateNativeCameraScanInProgress(false)
                refreshNativeVerifiedCameraList()
                updateStatus(response.error ?: if (response.ok) "Camera scan complete." else "Camera scan failed.")
            }
        }.start()
    }

    private fun openCameraReport() {
        val ip = AppDisplayInfo.localIpv4Address() ?: return
        val uri = Uri.parse("http://$ip:$port/api/debug/camera-report")
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onFailure {
            updateStatus("Camera report: $uri")
        }
    }

    private fun adjustPreviewRotation(delta: Int) {
        previewAdjustment = previewAdjustment.rotateBy(delta)
        applyPreviewAdjustment()
    }

    private fun adjustPreviewScale(delta: Float) {
        previewAdjustment = previewAdjustment.scaleBy(delta)
        applyPreviewAdjustment()
    }

    private fun resetPreviewTuning() {
        previewAdjustment = PreviewAdjustment.DEFAULT
        applyPreviewAdjustment()
    }

    private fun setPreviewFitMode() {
        previewAdjustment = previewAdjustment.fit()
        applyPreviewAdjustment()
    }

    private fun renderCameraChipGroup() {
        val container = binding.cameraChipGroup
        container.removeAllViews()
        val palette = currentPalette()
        options.forEachIndexed { index, option ->
            val selected = option == selectedOption
            container.addView(
                buildSelectorChip(
                    text = option.label,
                    selected = selected,
                    palette = palette
                ) {
                    if (selectedOption == option) return@buildSelectorChip
                    binding.cameraSpinner.setSelection(index, false)
                    selectedOption = option
                    refreshControls(resetCameraSpecificValues = true)
                    updateParameterSummary()
                    if (isStreaming) restartStreaming("Camera changed")
                }
            )
        }
    }

    private fun renderFocusModeChipGroup() {
        val container = binding.focusModeChipGroup
        container.removeAllViews()
        val palette = currentPalette()
        availableFocusModes.forEach { mode ->
            val selected = selectedFocusMode == mode
            container.addView(
                buildSelectorChip(
                    text = localizedFocusModeLabel(mode),
                    selected = selected,
                    palette = palette
                ) {
                    if (selectedFocusMode == mode) return@buildSelectorChip
                    selectedFocusMode = mode
                    refreshControls(resetCameraSpecificValues = false)
                    updateParameterSummary()
                    pushRuntimeControlsIfStreaming()
                }
            )
        }
    }

    private fun buildSelectorChip(
        text: String,
        selected: Boolean,
        palette: UiPalette,
        onClick: () -> Unit
    ): TextView {
        return NativeSelectorChips.build(
            context = this,
            text = text,
            selected = selected,
            palette = palette,
            heightPx = dpToPx(34),
            minWidthPx = dpToPx(72),
            horizontalPaddingPx = dpToPx(12),
            marginEndPx = dpToPx(8),
            backgroundRes = skinDrawable(R.drawable.bg_preset_chip, R.drawable.bg_preset_chip_warm, R.drawable.bg_preset_chip_mineral),
            onClick = onClick
        )
    }

    private fun bindCollapsibleBlock(block: ViewGroup, onToggle: () -> Unit) {
        NativeCollapsibleBlocks.bind(block, onToggle)
    }

    private fun updateCollapsibleBlocks() {
        updateCollapsibleBlock(
            block = binding.nativeCameraDebugBlock,
            expanded = isNativeCameraDebugExpanded,
            title = localizedText("Camera debug", "\u6444\u50cf\u5934\u8c03\u8bd5")
        )
        updateCollapsibleBlock(
            block = binding.appPreviewTuningBlock,
            expanded = isAppPreviewTuningExpanded,
            title = localizedText("App preview tuning", "App \u9884\u89c8\u8c03\u6821")
        )
    }

    private fun updateCollapsibleBlock(block: ViewGroup, expanded: Boolean, title: String) {
        NativeCollapsibleBlocks.update(
            block = block,
            expanded = expanded,
            title = title,
            expandText = localizedText("Expand", "\u5c55\u5f00"),
            collapseText = localizedText("Collapse", "\u6536\u8d77")
        )
    }

    private fun refreshNativeVerifiedCameraList() {
        val entries = localizedVerifiedEntries(buildCameraScanState().activeEntries)
        val container = binding.nativeVerifiedCameraList
        val emptyText = binding.nativeVerifiedCameraEmptyText
        while (container.childCount > 1) {
            container.removeViewAt(1)
        }
        emptyText.text = if (cameraScanInProgress) {
            localizedText("Scanning cameras...", "\u6b63\u5728\u626b\u63cf\u6444\u50cf\u5934...")
        } else {
            localizedText("Verified cameras will appear here", "\u5df2\u9a8c\u8bc1\u6444\u50cf\u5934\u4f1a\u663e\u793a\u5728\u8fd9\u91cc")
        }
        emptyText.visibility = if (cameraScanInProgress || entries.isEmpty()) View.VISIBLE else View.GONE
        entries.forEach { entry ->
            container.addView(buildNativeVerifiedCameraRow(entry))
        }
    }

    private fun updateNativeCameraScanInProgress(inProgress: Boolean) {
        binding.nativeCameraScanButton.isEnabled = !inProgress
        binding.nativeCameraScanButton.alpha = if (inProgress) 0.65f else 1f
        binding.nativeCameraScanButton.text = if (inProgress) {
            localizedText("Scanning...", "\u626b\u63cf\u4e2d...")
        } else {
            localizedString(R.string.ui_scan_cameras_en, R.string.ui_scan_cameras_zh)
        }
        if (!isNativeCameraDebugExpanded) {
            isNativeCameraDebugExpanded = true
            updateCollapsibleBlocks()
        }
        if (inProgress) {
            updateStatus("Camera scan in progress")
        }
        refreshNativeVerifiedCameraList()
    }

    private fun buildNativeVerifiedCameraRow(entry: VerifiedCameraEntry): View {
        return NativeVerifiedCameraRows.build(
            context = this,
            entry = entry,
            labels = NativeVerifiedCameraRows.Labels(
                builtInVerified = localizedString(R.string.ui_builtin_profile_verified_en, R.string.ui_builtin_profile_verified_zh),
                localScanVerified = localizedString(R.string.ui_local_scan_verified_en, R.string.ui_local_scan_verified_zh),
                builtInTag = localizedString(R.string.ui_built_in_tag_en, R.string.ui_built_in_tag_zh),
                localTag = localizedString(R.string.ui_local_tag_en, R.string.ui_local_tag_zh),
                deleteCamera = localizedString(R.string.ui_delete_camera_en, R.string.ui_delete_camera_zh)
            ),
            onDeleteClick = ::confirmDeleteCameraEntry
        )
    }

    private fun confirmDeleteCameraEntry(cameraId: String) {
        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_MultiLensIpCam_DeleteConfirmDialog)
            .setTitle(localizedString(
                R.string.ui_delete_camera_confirm_title_en,
                R.string.ui_delete_camera_confirm_title_zh
            ))
            .setMessage(localizedString(
                R.string.ui_delete_camera_confirm_message_en,
                R.string.ui_delete_camera_confirm_message_zh
            ))
            .setPositiveButton(localizedString(
                R.string.ui_delete_camera_confirm_action_en,
                R.string.ui_delete_camera_confirm_action_zh
            )) { _, _ ->
                deleteCameraScanCache(cameraId)
                refreshNativeVerifiedCameraList()
            }
            .setNegativeButton(localizedString(R.string.ui_cancel_en, R.string.ui_cancel_zh), null)
            .show()
        styleDeleteCameraDialog(dialog)
    }

    private fun styleDeleteCameraDialog(dialog: androidx.appcompat.app.AlertDialog) {
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog_surface)
        dialog.setOnShowListener {
            styleDialogActionButton(
                button = dialog.getButton(DialogInterface.BUTTON_POSITIVE),
                backgroundRes = R.drawable.bg_dialog_button_danger,
                textColor = color(R.color.ui_text_primary)
            )
            styleDialogActionButton(
                button = dialog.getButton(DialogInterface.BUTTON_NEGATIVE),
                backgroundRes = R.drawable.bg_dialog_button_secondary,
                textColor = color(R.color.ui_text_secondary)
            )
        }
    }

    private fun styleDialogActionButton(button: TextView?, backgroundRes: Int, textColor: Int) {
        button ?: return
        val horizontalPadding = dpToPx(16)
        val verticalPadding = dpToPx(10)
        button.setBackgroundResource(backgroundRes)
        button.backgroundTintList = null
        button.setTextColor(textColor)
        button.isAllCaps = false
        button.minimumHeight = dpToPx(40)
        button.minHeight = dpToPx(40)
        button.minimumWidth = dpToPx(88)
        button.minWidth = dpToPx(88)
        button.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
    }

    private fun localizedFocusModeLabel(mode: FocusMode): String {
        return when (mode) {
            FocusMode.AUTO -> localizedString(R.string.ui_focus_auto_en, R.string.ui_focus_auto_zh)
            FocusMode.MANUAL -> localizedString(R.string.ui_focus_manual_en, R.string.ui_focus_manual_zh)
        }
    }

    // Permission, stream session, and camera option loading.
    private fun checkPermissionAndLoad() {
        if (hasCameraPermission()) {
            loadCameraOptions()
            requestAudioPermissionIfNeeded()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun requestAudioPermissionIfNeeded() {
        if (isAudioSupported() && !hasAudioPermission()) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun ensurePermissionAndStart() {
        if (!hasCameraPermission()) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        startStreaming()
    }

    private fun startStreaming() {
        if (::rtspServer.isInitialized) {
            rtspServer.clearCachedVideoConfig()
        }
        streamingSession.start()
    }

    private fun restartStreaming(reason: String) {
        if (::rtspServer.isInitialized) {
            rtspServer.clearCachedVideoConfig()
        }
        streamingSession.restart(reason)
    }

    private fun stopStreaming(status: String, keepServerAlive: Boolean = false) {
        if (::rtspServer.isInitialized) {
            rtspServer.clearCachedVideoConfig()
        }
        streamingSession.stop(status, keepServerAlive)
    }

    private fun handleH264StreamRequest(): Boolean {
        val wasH264Enabled = h264EnabledForSession
        val handled = streamingSession.handleH264StreamRequest()
        if (handled && !wasH264Enabled && ::rtspServer.isInitialized) {
            rtspServer.clearCachedVideoConfig()
        }
        return handled
    }

    private fun handleRtspStreamRequest(): Boolean {
        if (!isStreaming) return false
        val videoReady = handleH264StreamRequest()
        if (videoReady && isRtspAudioAvailable()) {
            if (!audioEnabled) {
                rtspAutoEnabledAudio = true
                audioEnabled = true
            }
            startAudioStreaming()
        }
        return videoReady
    }

    private fun isRtspAudioAvailable(): Boolean {
        return isAudioSupported() && hasAudioPermission()
    }

    private fun onMjpegClientCountChanged() {
        if (::streamer.isInitialized) {
            streamer.onMjpegDemandStateChanged()
        }
        mjpegDemandSyncHandler.removeCallbacks(mjpegDemandSyncRunnable)
        mjpegDemandSyncHandler.postDelayed(
            mjpegDemandSyncRunnable,
            MJPEG_IDLE_GRACE_RESYNC_DELAY_MS
        )
    }

    private fun onH264ClientCountChanged() {
        h264IdleHandler.removeCallbacks(h264IdleStopRunnable)
        if (h264EnabledForSession && activeH264ClientCount() == 0) {
            h264IdleHandler.postDelayed(h264IdleStopRunnable, H264_IDLE_STOP_DELAY_MS)
        }
    }

    private fun activeH264ClientCount(): Int {
        val httpClients = if (::server.isInitialized) server.h264ClientCount() else 0
        val rtspClients = if (::rtspServer.isInitialized) rtspServer.clientCount else 0
        return httpClients + rtspClients
    }

    private fun stopH264IfStillIdle() {
        if (activeH264ClientCount() == 0) {
            streamingSession.stopH264AfterIdle("H.264 idle")
            stopRtspAutoAudioIfIdle()
        }
    }

    private fun stopRtspAutoAudioIfIdle() {
        if (!rtspAutoEnabledAudio) return
        val audioClients = if (::server.isInitialized) server.audioClientCount() else 0
        if (activeH264ClientCount() != 0 || audioClients != 0) return
        rtspAutoEnabledAudio = false
        audioEnabled = false
        stopAudioStreaming()
        updateParameterSummary()
    }

    private fun handleAudioStreamRequest(waitForReady: Boolean): Boolean {
        if (!audioEnabled) {
            lastAudioStatus = "Audio disabled"
            return false
        }
        if (!hasAudioPermission()) {
            lastAudioStatus = "Microphone permission required"
            return false
        }
        if (!isStreaming) {
            lastAudioStatus = "Audio enabled; start streaming first"
            return false
        }
        if (audioStreamer.isReady) {
            return true
        }
        if (waitForReady && audioStreamer.isRunning) {
            return waitForAudioReady()
        }
        lastAudioStatus = if (audioStreamer.isRunning) {
            "Audio starting; retry shortly"
        } else {
            lastAudioStatus.takeIf { it.isNotBlank() } ?: "Audio not ready"
        }
        return false
    }

    private fun waitForAudioReady(): Boolean {
        val deadlineMs = System.currentTimeMillis() + AUDIO_READY_WAIT_TIMEOUT_MS
        while (
            System.currentTimeMillis() < deadlineMs &&
            audioEnabled &&
            hasAudioPermission() &&
            isStreaming &&
            audioStreamer.isRunning
        ) {
            if (audioStreamer.isReady) return true
            try {
                Thread.sleep(AUDIO_READY_WAIT_INTERVAL_MS)
            } catch (_: InterruptedException) {
                break
            }
        }
        if (audioStreamer.isReady) return true
        lastAudioStatus = if (audioStreamer.isRunning) {
            "Audio starting; retry shortly"
        } else {
            lastAudioStatus.takeIf { it.isNotBlank() } ?: "Audio not ready"
        }
        return false
    }

    private fun applyAudioControl(enabled: Boolean) {
        rtspAutoEnabledAudio = false
        audioEnabled = enabled
        if (!enabled) {
            lastAudioStatus = "Audio disabled"
            stopAudioStreaming()
            updateStatus("Audio disabled")
            updateParameterSummary()
            return
        }

        if (!isAudioSupported()) {
            audioEnabled = false
            lastAudioStatus = "Microphone unavailable"
            updateStatus("Microphone unavailable")
            updateParameterSummary()
            return
        }

        if (!hasAudioPermission()) {
            audioEnabled = false
            lastAudioStatus = "Microphone permission required"
            updateStatus("Microphone permission required")
            updateParameterSummary()
            return
        }

        if (isStreaming) {
            startAudioStreaming()
        } else {
            lastAudioStatus = "Audio enabled; start streaming to capture microphone"
            updateStatus("Audio enabled")
        }
        updateParameterSummary()
    }

    private fun startAudioStreaming() {
        if (!audioEnabled || !hasAudioPermission()) {
            cancelAudioStartRetry()
            return
        }
        if (audioStreamer.isRunning) {
            cancelAudioStartRetry()
            return
        }
        lastAudioStatus = "Audio starting"
        when (audioStreamer.start(AudioStreamConfig())) {
            AudioStartResult.STARTED,
            AudioStartResult.ALREADY_RUNNING -> cancelAudioStartRetry()
            AudioStartResult.RETRY_AFTER_PREVIOUS_STOP -> scheduleAudioStartRetry()
        }
        updateParameterSummary()
    }

    private fun stopAudioStreaming() {
        cancelAudioStartRetry()
        if (::server.isInitialized) {
            server.closeAudioStreams()
        }
        if (::audioStreamer.isInitialized) {
            audioStreamer.stop()
        }
        if (!audioEnabled) {
            lastAudioStatus = "Audio disabled"
        } else if (!isStreaming) {
            lastAudioStatus = "Audio enabled; stream is stopped"
        }
    }

    private fun scheduleAudioStartRetry() {
        if (!audioEnabled || !hasAudioPermission()) return
        if (audioStartRetryAttempts >= MAX_AUDIO_START_RETRY_ATTEMPTS) {
            lastAudioStatus = "Audio stopped; retry limit reached"
            updateParameterSummary()
            return
        }
        audioStartRetryAttempts += 1
        audioRetryHandler.removeCallbacks(audioStartRetryRunnable)
        audioRetryHandler.postDelayed(audioStartRetryRunnable, AUDIO_START_RETRY_DELAY_MS)
    }

    private fun retryAudioStreamingAfterStop() {
        if (!audioEnabled || !hasAudioPermission() || !isStreaming) {
            cancelAudioStartRetry()
            return
        }
        if (audioStreamer.isRunning) {
            cancelAudioStartRetry()
            return
        }
        startAudioStreaming()
    }

    private fun cancelAudioStartRetry() {
        audioStartRetryAttempts = 0
        audioRetryHandler.removeCallbacks(audioStartRetryRunnable)
    }

    private fun streamingStartRequest(): StreamingStartRequest {
        val option = selectedOption
        val capabilities = currentCapabilities ?: option?.let(cameraRepository::getCapabilities)
        return StreamingStartRequest(
            cameraScanInProgress = cameraScanInProgress,
            option = option,
            previewTexture = binding.previewView.surfaceTexture,
            capabilities = capabilities,
            selectedOutputSize = selectedOutputSize,
            useFullMjpegSize = useFullMjpegSize,
            streamQuality = selectedStreamQuality,
            selectedTargetFps = selectedTargetFps,
            unlimitedFpsSelected = isUnlimitedFpsSelected,
            runtimeControls = if (option != null && capabilities != null) resolvedRuntimeControls(option, capabilities) else null,
            focusMode = selectedFocusMode,
            mjpegPipeline = selectedMjpegPipeline
        )
    }

    private fun loadCameraOptions() {
        publicCameraOptions = cameraRepository.loadOptions()
        options = buildSelectableCameraOptions()
        suppressUiCallbacks = true
        try {
            binding.cameraSpinner.adapter = themedSpinnerAdapter(options)
            selectedOption = options.firstOrNull()
            binding.cameraSpinner.setSelection(0, false)
        } finally {
            suppressUiCallbacks = false
        }
        hasLoadedCameraOptions = options.isNotEmpty()
        statusMode = if (hasLoadedCameraOptions) StatusMode.READY else StatusMode.IDLE
        refreshControls(resetCameraSpecificValues = true)
        renderCameraChipGroup()
        refreshNativeVerifiedCameraList()
        if (hasLoadedCameraOptions) {
            server.start()
            val rtspStarted = rtspServer.start()
            updateUrl()
            updateStatus(if (rtspStarted) "Ready on port $port" else "Ready on port $port; RTSP port $rtspPort unavailable")
        } else {
            updateStatus("Loaded ${options.size} camera entries")
        }
    }

    private fun buildSelectableCameraOptions(): List<CameraLensOption> {
        return CameraOptionPresentation.buildSelectableOptions(
            repository = cameraRepository,
            publicOptions = publicCameraOptions,
            activeEntries = buildCameraScanState().activeEntries,
            isChineseUi = isChineseUi
        )
    }

    private fun localizedCameraOption(option: CameraLensOption): CameraLensOption {
        return CameraOptionPresentation.localizeOption(
            repository = cameraRepository,
            option = option,
            verifiedEntries = localizedVerifiedEntries(buildCameraScanState().activeEntries),
            isChineseUi = isChineseUi
        )
    }

    private fun refreshCameraOptionsPreservingSelection() {
        if (publicCameraOptions.isEmpty()) return
        val selectedKey = selectedOption?.let(cameraRepository::controlKeyFor)
        val updatedOptions = buildSelectableCameraOptions()
        val unchanged = updatedOptions.map(cameraRepository::controlKeyFor) == options.map(cameraRepository::controlKeyFor) &&
            updatedOptions.map { it.label } == options.map { it.label }
        if (unchanged) return

        options = updatedOptions
        selectedOption = selectedKey
            ?.let { key -> options.firstOrNull { cameraRepository.controlKeyFor(it) == key } }
            ?: options.firstOrNull()
        suppressUiCallbacks = true
        try {
            binding.cameraSpinner.adapter = themedSpinnerAdapter(options)
            val index = selectedOption?.let { option ->
                options.indexOfFirst { cameraRepository.controlKeyFor(it) == cameraRepository.controlKeyFor(option) }
            } ?: 0
            binding.cameraSpinner.setSelection(index.coerceAtLeast(0), false)
        } finally {
            suppressUiCallbacks = false
        }
        renderCameraChipGroup()
    }

    private fun refreshControls(resetCameraSpecificValues: Boolean) {
        val option = selectedOption ?: return
        val capabilities = cameraRepository.getCapabilities(option)
        suppressUiCallbacks = true
        val result = try {
            NativeControlsCoordinator.refresh(
                binding = binding,
                repository = cameraRepository,
                request = NativeControlsCoordinator.Request(
                    option = option,
                    capabilities = capabilities,
                    allOptions = options,
                    selectedOutputSize = selectedOutputSize,
                    selectedTargetFps = selectedTargetFps,
                    selectedStreamQuality = selectedStreamQuality,
                    selectedFocusMode = selectedFocusMode,
                    selectedZoomRatio = selectedZoomRatio,
                    selectedFocusDistance = selectedFocusDistance,
                    selectedExposureCompensation = selectedExposureCompensation,
                    selectedTorchEnabled = selectedTorchEnabled,
                    isUnlimitedFpsSelected = isUnlimitedFpsSelected,
                    isManualResolutionMode = isManualResolutionMode,
                    resetCameraSpecificValues = resetCameraSpecificValues,
                    isChineseUi = isChineseUi,
                    manualResolutionLabel = manualResolutionLabel(),
                    unlimitedFpsLabel = localizedText("Unlimited", "\u4e0d\u9650")
                ),
                stringAdapterFactory = { items -> themedSpinnerAdapter(items) },
                focusModeLabel = ::localizedFocusModeLabel
            )
        } finally {
            suppressUiCallbacks = false
        }
        val snapshot = result.snapshot

        currentCapabilities = snapshot.capabilities
        resolutionEntries = snapshot.resolutionEntries
        fpsEntries = snapshot.fpsEntries
        availableFocusModes = snapshot.availableFocusModes
        selectedOutputSize = snapshot.selectedOutputSize
        selectedTargetFps = snapshot.selectedTargetFps
        selectedFocusMode = snapshot.selectedFocusMode
        selectedZoomRatio = result.selectedZoomRatio
        selectedFocusDistance = result.selectedFocusDistance
        selectedExposureCompensation = result.selectedExposureCompensation
        selectedTorchEnabled = result.selectedTorchEnabled
        syncVideoOverlayControls()

        updateCapabilityText(option, capabilities)
        updatePreviewPresentation(snapshot.selectedOutputSize, capabilities.sensorOrientation)
        updatePresetButtonStates(capabilities)
        renderCameraChipGroup()
        renderFocusModeChipGroup()
        updateSessionInfo()
    }

    private fun applyRecommendedSettings() {
        applyStreamPreset(StreamControlOptions.preset1080p30)
    }

    private fun applyStreamPreset(preset: StreamPreset) {
        val option = selectedOption ?: return
        val capabilities = currentCapabilities ?: cameraRepository.getCapabilities(option)

        if (!StreamControlOptions.isPresetSupported(cameraRepository, capabilities, preset)) {
            updateStatus("${preset.label} requires ${preset.fps} fps support")
            return
        }

        val selection = StreamControlOptions.resolvePresetSelection(cameraRepository, capabilities, preset)
        selectedOutputSize = selection.outputSize
        isManualResolutionMode = selection.manualResolutionMode
        selectedTargetFps = selection.targetFps
        isUnlimitedFpsSelected = false
        selectedStreamQuality = selection.streamQuality
        selectedFocusMode = FocusMode.AUTO
        selectedFocusDistance = 0f
        selectedExposureCompensation = 0
        selectedTorchEnabled = false
        selectedZoomRatio = defaultZoomRatio(option, capabilities)
        refreshControls(resetCameraSpecificValues = false)
        if (isStreaming) {
            restartStreaming("Applied ${preset.label} preset")
        } else {
            updateStatus("Applied ${preset.label} preset")
        }
    }

    private fun applyManualResolution() {
        val option = selectedOption ?: return
        val capabilities = currentCapabilities ?: cameraRepository.getCapabilities(option)
        val width = binding.manualWidthInput.text?.toString()?.trim()?.toIntOrNull()
        val height = binding.manualHeightInput.text?.toString()?.trim()?.toIntOrNull()
        if (width == null || height == null || width <= 0 || height <= 0) {
            binding.manualResolutionHintText.text = defaultManualResolutionHint()
            updateStatus("Enter a valid manual resolution")
            return
        }

        val resolved = cameraRepository.findSupportedOrNearestSize(capabilities, width, height)
        if (resolved == null) {
            updateStatus("No supported sizes available for this camera")
            return
        }

        selectedOutputSize = resolved
        isManualResolutionMode = true
        populateManualResolutionInputs(resolved)
        binding.manualResolutionHintText.text = if (resolved.width == width && resolved.height == height) {
            defaultManualResolutionHint()
        } else {
            if (isChineseUi) {
                "闂佽娴烽幊鎾诲箟闄囬妵鎰板礃椤忓啰鍓ㄥ銈嗘磵閸嬫捇鏌＄仦鏂ゅ伐妞ゆ挸銈稿畷鍗炍熼崷顓炵闂傚倷娴囬～澶嬬娴犲纾块悗闈涙啞瀹曟煡骞栧ǎ顒€濡介柛搴＄Ч閺屾盯寮撮妸銉ヮ潽闂佸搫妫欑划鎾诲蓟閻斿吋鈷愰柟閭﹀櫘濡差噣姊洪崫鍕仼闁稿﹤顭烽崺鈧? ${cameraRepository.sizeLabel(resolved)}"
            } else {
                "Nearest supported size matched: ${cameraRepository.sizeLabel(resolved)}"
            }
        }
        if (resolved.width != width || resolved.height != height) {
            binding.manualResolutionHintText.text = localizedText(
                "Nearest supported size matched: ${cameraRepository.sizeLabel(resolved)}",
                "\u5df2\u5339\u914d\u6700\u63a5\u8fd1\u7684\u652f\u6301\u5c3a\u5bf8\uff1a${cameraRepository.sizeLabel(resolved)}"
            )
        }
        refreshControls(resetCameraSpecificValues = false)
        if (!isStreaming && (resolved.width != width || resolved.height != height)) {
            updateStatus(
                localizedText(
                    "Using nearest supported size ${cameraRepository.sizeLabel(resolved)}",
                    "\u5df2\u4f7f\u7528\u6700\u63a5\u8fd1\u7684\u652f\u6301\u5c3a\u5bf8 ${cameraRepository.sizeLabel(resolved)}"
                )
            )
            return
        }
        if (isStreaming) {
            restartStreaming("Manual resolution applied")
        } else {
            updateStatus(
                if (resolved.width == width && resolved.height == height) {
                    "Manual resolution applied"
                } else {
                    if (isChineseUi) {
                        "婵犵數鍋犻幓顏嗙礊閳ь剚绻涙径瀣鐎殿噮鍋婃俊鑸靛緞婵犲倻褰夐梻渚€鈧偛鑻晶瀛橆殽閻愭潙娴┑锛勫厴婵℃瓕顦存い鏂挎嚇濮婂搫效閸パ冾瀳闁诲孩鍑归崣鍐嚕椤愶富鏁嬮柍褜鍓熼悰顕€骞囬鐔奉€撻梺鍛婄☉閿曘儵锝為崨瀛樼厽?${cameraRepository.sizeLabel(resolved)}"
                    } else {
                        "Using nearest supported size ${cameraRepository.sizeLabel(resolved)}"
                    }
                }
            )
        }
    }

    private fun updatePresetButtonStates(capabilities: CameraCapabilities) {
        val palette = currentPalette()
        val selection = NativePresetChips.Selection(
            outputSize = selectedOutputSize,
            targetFps = selectedTargetFps,
            streamQuality = selectedStreamQuality,
            unlimitedFpsSelected = isUnlimitedFpsSelected
        )
        updatePresetChipState(binding.preset1080p30Button, StreamControlOptions.preset1080p30, enabled = true, palette = palette, selection = selection)
        updatePresetChipState(binding.preset4k30Button, StreamControlOptions.preset4k30, enabled = true, palette = palette, selection = selection)
        val preset720p30Supported = StreamControlOptions.isPresetSupported(
            cameraRepository,
            capabilities,
            StreamControlOptions.preset720p30
        )
        updatePresetChipState(
            binding.preset720p60Button,
            StreamControlOptions.preset720p30,
            enabled = preset720p30Supported,
            palette = palette,
            selection = selection
        )
    }

    private fun updatePresetChipState(
        button: TextView,
        preset: StreamPreset,
        enabled: Boolean,
        palette: UiPalette,
        selection: NativePresetChips.Selection
    ) {
        NativePresetChips.update(
            button = button,
            preset = preset,
            enabled = enabled,
            palette = palette,
            selection = selection
        )
    }

    private fun updateCapabilityText(option: CameraLensOption, capabilities: CameraCapabilities) {
        updateParameterSummary()
    }

    private fun updatePreviewPresentation(size: Size, sensorOrientation: Int) {
        val rotation = PreviewAdjustment.normalizeSensorOrientation(sensorOrientation)
        if (isLandscapeStreaming()) {
            binding.previewView.setAspectRatio(size.width, size.height)
        } else if (rotation == 90 || rotation == 270) {
            binding.previewView.setAspectRatio(size.height, size.width)
        } else {
            binding.previewView.setAspectRatio(size.width, size.height)
        }
        updatePreviewFillMode()
        val previewRotation = if (isLandscapeStreaming()) currentMjpegOutputRotationDegrees() else rotation
        binding.previewView.configurePreview(size.width, size.height, previewRotation)
        applyPreviewAdjustment()
    }

    private fun currentMjpegOutputRotationDegrees(): Int {
        if (!isLandscapeStreaming()) return 0
        return when (lastKnownDisplayRotation) {
            Surface.ROTATION_270 -> 180
            else -> 0
        }
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            windowManager.defaultDisplay.rotation
        }
    }

    private fun refreshLandscapeStreamingOrientation(force: Boolean) {
        val rotation = currentDisplayRotation()
        val changed = rotation != lastKnownDisplayRotation
        if (!changed && !force) return
        lastKnownDisplayRotation = rotation
        if (!isLandscapeStreaming()) return
        refreshPreviewPresentationForSelection()
    }

    private fun refreshPreviewPresentationForSelection() {
        val size = selectedOutputSize ?: return
        val capabilities = currentCapabilities ?: return
        updatePreviewPresentation(size, capabilities.sensorOrientation)
    }

    private fun applyPreviewAdjustment() {
        binding.previewView.setPreviewAdjustment(
            rotationDegrees = previewAdjustment.rotation,
            scale = previewAdjustment.scale,
            stretchX = previewAdjustment.stretchX,
            stretchY = previewAdjustment.stretchY
        )
    }

    private fun pushRuntimeControlsIfStreaming() {
        if (!isStreaming) return
        val option = selectedOption ?: return
        val capabilities = currentCapabilities ?: return
        val runtimeControls = resolvedRuntimeControls(option, capabilities)
        streamer.updateRuntimeControls(
            streamQuality = selectedStreamQuality,
            zoomRatio = runtimeControls.zoomRatio,
            focusMode = selectedFocusMode,
            focusDistance = runtimeControls.focusDistance,
            exposureCompensation = runtimeControls.exposureCompensation,
            torchEnabled = runtimeControls.torchEnabled
        )
        updateSessionInfo()
    }

    private fun effectiveZoomRange(
        option: CameraLensOption?,
        capabilities: CameraCapabilities?
    ): Range<Float>? {
        return NativeZoomControls.effectiveRange(cameraRepository, option, capabilities, options)
    }

    private fun defaultZoomRatio(option: CameraLensOption, capabilities: CameraCapabilities): Float? {
        return NativeZoomControls.defaultRatio(cameraRepository, option, capabilities, options)
    }

    private fun resolvedRuntimeControls(
        option: CameraLensOption,
        capabilities: CameraCapabilities
    ): NativeRuntimeControlValues {
        return NativeRuntimeControls.resolve(
            repository = cameraRepository,
            option = option,
            capabilities = capabilities,
            allOptions = options,
            selectedZoomRatio = selectedZoomRatio,
            selectedFocusMode = selectedFocusMode,
            selectedFocusDistance = selectedFocusDistance,
            selectedExposureCompensation = selectedExposureCompensation,
            selectedTorchEnabled = selectedTorchEnabled
        )
    }

    // Web dashboard bridge.
    private fun buildWebControlState(): WebControlState {
        val versionInfo = currentVersionInfo()
        val option = selectedOption
        val capabilities = currentCapabilities ?: option?.let(cameraRepository::getCapabilities)
        val zoomRange = effectiveZoomRange(option, capabilities)
        val fpsOptions = capabilities?.let(cameraRepository::listTargetFpsOptions).orEmpty().filter { it < 60 }
        val focusDistanceSupported = selectedFocusMode == FocusMode.MANUAL && capabilities?.let(cameraRepository::hasManualFocus) == true
        val runtimeControls = if (option != null && capabilities != null) {
            resolvedRuntimeControls(option, capabilities)
        } else {
            null
        }
        val localizedVerifiedEntries = localizedVerifiedEntries(buildCameraScanState().activeEntries)
        val verifiedCameraOptions = localizedVerifiedEntries.map {
            WebOptionItem(value = it.cameraId, label = it.label)
        }
        val selectableOptions = buildSelectableCameraOptions().ifEmpty { options }

        return WebControlStates.buildFromSelections(
            versionName = versionInfo.name,
            versionCode = versionInfo.code,
            versionLabel = versionInfo.label,
            repository = cameraRepository,
            streaming = isStreaming,
            cameraOptions = selectableOptions,
            selectedCameraOption = option,
            capabilities = capabilities,
            selectedOutputSize = selectedOutputSize,
            manualResolutionEnabled = isManualResolutionMode,
            fpsOptions = fpsOptions,
            selectedFps = selectedTargetFps,
            unlimitedFpsSelected = isUnlimitedFpsSelected,
            qualityValue = selectedStreamQuality,
            zoomRange = zoomRange,
            runtimeControls = runtimeControls,
            selectedFocusMode = selectedFocusMode,
            focusDistanceSupported = focusDistanceSupported,
            previewAdjustment = previewAdjustment,
            selectedJpegPipeline = selectedMjpegPipeline,
            mjpegFullSize = useFullMjpegSize,
            videoOverlayEnabled = selectedVideoOverlayEnabled,
            videoOverlaySize = selectedVideoOverlaySize,
            rtspUrl = if (::rtspServer.isInitialized && rtspServer.isRunning) rtspUrl() else "",
            rtspAvailable = ::rtspServer.isInitialized && rtspServer.isRunning,
            verifiedCameraOptions = verifiedCameraOptions,
            fallbackSelectedCameraLabel = option?.let(::localizedCameraOption)?.label,
            audioSupported = isAudioSupported(),
            audioPermissionGranted = hasAudioPermission(),
            audioEnabled = audioEnabled,
            audioRunning = ::audioStreamer.isInitialized && audioStreamer.isReady,
            audioClients = if (::server.isInitialized) server.audioClientCount() else 0,
            audioUrl = audioUrl(),
            audioStatus = currentAudioStatus()
        )
    }

    private fun applyWebControls(command: WebControlCommand) {
        if (handleFastWebAudioEnableFailure(command)) return
        UiThreadActions.runBlocking(
            activity = this,
            timeoutMessage = "Timed out waiting for web camera controls to apply."
        ) {
            applyWebControlsOnUiThread(command)
        }
    }

    private fun applyWebControlsOnUiThread(command: WebControlCommand) {
        webControlCoordinator.apply(command)
        syncVideoOverlayControls()
    }

    private fun handleFastWebAudioEnableFailure(command: WebControlCommand): Boolean {
        if (command != WebControlCommand(audioEnabled = true)) return false
        val status = when {
            !isAudioSupported() -> "Microphone unavailable"
            !hasAudioPermission() -> "Microphone permission required"
            else -> return false
        }
        audioEnabled = false
        lastAudioStatus = status
        if (::server.isInitialized) {
            server.closeAudioStreams()
        }
        runOnUiThread {
            updateStatus(status)
            updateParameterSummary()
        }
        return true
    }

    private fun applyWebCameraSelection(command: WebControlCommand): Boolean {
        refreshCameraOptionsPreservingSelection()
        val allowedDebugCameraIds = buildCameraScanState().activeEntries.map { it.cameraId }.toSet()
        val selection = WebCameraSelections.resolve(
            repository = cameraRepository,
            options = options,
            command = command,
            currentOption = selectedOption,
            allowedDebugCameraIds = allowedDebugCameraIds
        ) ?: return false
        selectedOption = selection.option
        suppressUiCallbacks = true
        try {
            if (selection.index >= 0) binding.cameraSpinner.setSelection(selection.index, false)
        } finally {
            suppressUiCallbacks = false
        }
        refreshControls(resetCameraSpecificValues = true)
        return true
    }

    private fun applyWebResolutionSelection(
        command: WebControlCommand,
        capabilities: CameraCapabilities
    ): Boolean {
        val selection = WebResolutionSelections.resolve(
            repository = cameraRepository,
            capabilities = capabilities,
            command = command,
            selectedOutputSize = selectedOutputSize
        ) ?: return false
        selectedOutputSize = selection.outputSize
        isManualResolutionMode = selection.manualResolutionMode
        return true
    }

    private fun applyWebFpsSelection(
        command: WebControlCommand,
        capabilities: CameraCapabilities
    ): Boolean {
        val selection = StreamControlOptions.resolveFpsCommand(
            repository = cameraRepository,
            capabilities = capabilities,
            selectedTargetFps = selectedTargetFps,
            unlimitedFpsSelected = isUnlimitedFpsSelected,
            requestedFps = command.fps,
            requestedUnlimitedFps = command.unlimitedFps
        ) ?: return false
        selectedTargetFps = selection.targetFps
        isUnlimitedFpsSelected = selection.unlimitedFpsSelected
        return true
    }

    private fun applyWebRuntimeControls(
        command: WebControlCommand,
        option: CameraLensOption,
        capabilities: CameraCapabilities,
        cameraChanged: Boolean
    ): Boolean {
        val selection = WebRuntimeControlSelections.resolve(
            repository = cameraRepository,
            option = option,
            capabilities = capabilities,
            allOptions = options,
            command = command,
            availableFocusModes = availableFocusModes,
            selectedStreamQuality = selectedStreamQuality,
            selectedFocusMode = selectedFocusMode,
            selectedZoomRatio = selectedZoomRatio,
            selectedFocusDistance = selectedFocusDistance,
            selectedExposureCompensation = selectedExposureCompensation,
            selectedTorchEnabled = selectedTorchEnabled,
            cameraChanged = cameraChanged
        ) ?: return false
        selectedStreamQuality = selection.streamQuality
        selectedFocusMode = selection.focusMode
        selectedZoomRatio = selection.zoomRatio
        selectedFocusDistance = selection.focusDistance
        selectedExposureCompensation = selection.exposureCompensation
        selectedTorchEnabled = selection.torchEnabled
        return true
    }

    // Verified direct-camera scan/cache coordination.
    private fun buildCameraScanState(): CameraScanState {
        return cameraScanCoordinator.buildState()
    }

    private fun runCameraScan(): CameraScanResponse {
        return cameraScanCoordinator.runScan()
    }

    private fun deleteCameraScanCache(cameraId: String?): CameraScanCacheDeleteResponse {
        return cameraScanCoordinator.deleteCache(cameraId)
    }

    private fun localizedVerifiedEntries(entries: List<VerifiedCameraEntry>): List<VerifiedCameraEntry> {
        return cameraScanCoordinator.localizedEntries(entries)
    }

    private fun currentDeviceKey(): String {
        return cameraScanCoordinator.currentDeviceKey()
    }

    // Status text, localization, skinning, and layout modes.
    private fun updateUrl() {
        val rtspLine = if (::rtspServer.isInitialized && rtspServer.isRunning) {
            "RTSP  ${rtspUrl()}"
        } else {
            "RTSP  unavailable on port $rtspPort"
        }
        binding.urlText.text = "${AppDisplayInfo.dashboardSummary(port)}\n$rtspLine"
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            lastStatusMessage = message
            binding.statusText.text = NativeStatusLocalizer.localize(message, isChineseUi)
            statusMode = NativeStatusBadges.statusMode(message, isStreaming, hasLoadedCameraOptions)
            updateBadgeState()
        }
    }

    private fun updateStreamingUiState() {
        if (!isLandscapeStreaming() && blackoutOverlay != null) {
            exitBlackoutMode()
        }
        binding.startButton.visibility = if (isStreaming) View.GONE else View.VISIBLE
        binding.stopButton.visibility = if (isStreaming) View.VISIBLE else View.GONE
        binding.startButton.isEnabled = !isStreaming
        binding.stopButton.isEnabled = isStreaming
        updateControlSurfaceMode()
        updateBadgeState()
        updateSessionInfo()
        updateLiveOverlay()
        updateSystemBarsForMode()
    }

    private fun toggleLanguage() {
        isChineseUi = !isChineseUi
        cameraScanCoordinator.invalidateState()
        applyLanguageText()
        updateStatus(if (isChineseUi) "Switched to Chinese UI" else "Switched to English UI")
    }

    private fun applyLanguageText() {
        NativeLanguageTexts.apply(
            binding = binding,
            labels = NativeLanguageTexts.Labels(
                languageToggle = getString(
                    if (isChineseUi) R.string.ui_language_to_english else R.string.ui_language_to_chinese
                ),
                scanCameras = localizedString(R.string.ui_scan_cameras_en, R.string.ui_scan_cameras_zh),
                refreshList = localizedString(R.string.ui_refresh_list_en, R.string.ui_refresh_list_zh),
                exportReport = localizedString(R.string.ui_export_report_en, R.string.ui_export_report_zh),
                rotation = localizedString(R.string.ui_rotation_en, R.string.ui_rotation_zh),
                fit = localizedString(R.string.ui_fit_en, R.string.ui_fit_zh),
                scale = localizedString(R.string.ui_scale_en, R.string.ui_scale_zh),
                startStreaming = localizedString(R.string.ui_start_streaming_en, R.string.ui_start_streaming_zh),
                stopStreaming = localizedString(R.string.ui_stop_streaming_en, R.string.ui_stop_streaming_zh),
                applyManualResolution = localizedString(
                    R.string.ui_apply_custom_resolution_en,
                    R.string.ui_apply_custom_resolution_zh
                ),
                preset1080p30 = getString(R.string.ui_preset_1080p_30),
                preset4k30 = getString(R.string.ui_preset_4k_30),
                preset720p60 = getString(R.string.ui_preset_720p_60),
                exposure = localizedText("Exposure", "\u66dd\u5149"),
                manualResolutionHint = defaultManualResolutionHint()
            )
        )
        applyStaticLabelText()
        refreshCameraOptionsPreservingSelection()
        updateCollapsibleBlocks()
        currentCapabilities?.let { capabilities ->
            selectedOption?.let { option -> updateCapabilityText(option, capabilities) }
        }
        renderCameraChipGroup()
        renderFocusModeChipGroup()
        refreshNativeVerifiedCameraList()
        updateUrl()
        binding.statusText.text = NativeStatusLocalizer.localize(lastStatusMessage, isChineseUi)
        updateSessionInfo()
    }

    private fun toggleSkin() {
        activeSkin = activeSkin.next()
        applySkinState()
        updateStatus(activeSkin.statusMessage)
    }

    private fun applySkinState() {
        val palette = currentPalette()
        NativeSkinSurfaces.apply(
            binding = binding,
            resolveDrawable = ::skinDrawable,
            cardSurfaceViews = cardSurfaceViews(),
            presetGroupView = presetGroupView()
        )
        applyTextPalette(binding.root, palette)
        applySeekBarPalette(palette)
        refreshSpinnerAdapters()
        renderCameraChipGroup()
        renderFocusModeChipGroup()
        refreshNativeVerifiedCameraList()
        currentCapabilities?.let(::updatePresetButtonStates)
        updateControlSurfaceMode()
    }

    private fun presetGroupView(): View? {
        val presetRow = binding.preset1080p30Button.parent as? View
        val presetColumn = presetRow?.parent as? View
        return presetColumn?.parent as? View
    }

    private fun cardSurfaceViews(): List<View> {
        val cameraCard = binding.cameraSpinner.parent as? View
        val videoCard = ((binding.resolutionSpinner.parent as? View)?.parent as? View)?.parent as? View
        val focusCard = binding.focusModeSpinner.parent as? View
        val zoomCard = binding.zoomContainer
        val torchCard = binding.torchContainer
        val videoOverlayCard = binding.videoOverlayContainer
        val exposureCard = binding.exposureContainer
        return listOfNotNull(cameraCard, videoCard, focusCard, zoomCard, torchCard, videoOverlayCard, exposureCard)
    }

    private fun refreshSpinnerAdapters() {
        binding.cameraSpinner.adapter = themedSpinnerAdapter(options)
        binding.cameraSpinner.setSelection(options.indexOf(selectedOption).coerceAtLeast(0), false)
        binding.resolutionSpinner.adapter = themedSpinnerAdapter(resolutionEntries.map { it.label })
        binding.resolutionSpinner.setSelection(resolveResolutionSelectionIndex(), false)
        binding.fpsSpinner.adapter = themedSpinnerAdapter(fpsEntries.map { it.label })
        binding.fpsSpinner.setSelection(resolveFpsSelectionIndex(), false)
        binding.focusModeSpinner.adapter = themedSpinnerAdapter(availableFocusModes.map { it.label })
        binding.focusModeSpinner.setSelection(availableFocusModes.indexOf(selectedFocusMode).coerceAtLeast(0), false)
    }

    private fun updateBadgeState() {
        binding.modeBadgeText.text = NativeStatusBadges.modeBadgeText(statusMode, isChineseUi)
        binding.panelStateChip.text = NativeStatusBadges.panelStateText(
            isStreaming = isStreaming,
            hasLoadedCameraOptions = hasLoadedCameraOptions,
            isChineseUi = isChineseUi
        )
    }

    private fun toggleFloatingPanel() {
        if (!isLandscapeStreaming()) return
        isPanelOpen = !isPanelOpen
        updateControlSurfaceMode()
    }

    private fun updateControlSurfaceMode() {
        updatePreviewFillMode()
        updateSystemBarsForMode()
        binding.blackoutButton.visibility = if (isLandscapeStreaming() && blackoutOverlay == null) View.VISIBLE else View.GONE
        NativeControlSurfaceLayout.apply(
            binding = binding,
            isLandscapeStreaming = isLandscapeStreaming(),
            isPanelOpen = isPanelOpen,
            isStreaming = isStreaming,
            drawables = NativeControlSurfaceLayout.Drawables(
                floatingPanel = skinDrawable(R.drawable.bg_panel_floating, R.drawable.bg_panel_floating_warm, R.drawable.bg_panel_floating_mineral),
                portraitPanel = skinDrawable(R.drawable.bg_panel_surface, R.drawable.bg_panel_surface_warm, R.drawable.bg_panel_surface_mineral)
            ),
            dpToPx = ::dpToPx
        )
        updateBadgeState()
        updateLiveOverlay()
    }

    private fun enterBlackoutMode() {
        if (!isLandscapeStreaming() || blackoutOverlay != null) return
        val decor = window.decorView as? ViewGroup ?: return
        previousScreenBrightness = window.attributes.screenBrightness
        window.attributes = window.attributes.apply {
            screenBrightness = 0.01f
        }
        binding.blackoutButton.visibility = View.GONE
        blackoutOverlay = View(this).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
            setOnClickListener { exitBlackoutMode() }
        }.also { overlay ->
            decor.addView(
                overlay,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            overlay.bringToFront()
        }
        updateSystemBarsForMode()
    }

    private fun exitBlackoutMode() {
        val overlay = blackoutOverlay ?: return
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        blackoutOverlay = null
        val restoredBrightness = previousScreenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = window.attributes.apply {
            screenBrightness = restoredBrightness
        }
        previousScreenBrightness = null
        updateControlSurfaceMode()
    }

    private fun isLandscapeStreaming(): Boolean {
        return isStreaming && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun updateLiveOverlay() {
        NativeLiveOverlay.apply(
            binding = binding,
            showLiveOverlay = isLandscapeStreaming(),
            liveStatusLine1Text = liveStatusLine1Text(),
            liveStatusLine2Text = liveStatusLine2Text(),
            liveStatusLine1Color = currentPalette().primaryText,
            liveStatusLine2Color = currentPalette().secondaryText,
            dpToPx = ::dpToPx
        )
    }

    private fun updatePreviewFillMode() {
        NativePreviewFillMode.apply(
            previewView = binding.previewView,
            liveLandscape = isLandscapeStreaming()
        )
    }

    private fun updateParameterSummary() {
        binding.capabilityText.text = currentParameterSummary()
        updateSessionInfo()
        currentCapabilities?.let(::updatePresetButtonStates)
    }

    private fun updateSessionInfo() {
        binding.sessionInfoText.text = currentParameterSummary()
        binding.versionText.text = appVersionLabel()
        binding.liveStatusLine1Text.text = liveStatusLine1Text()
        binding.liveStatusLine2Text.text = liveStatusLine2Text()
    }

    private fun updateKeepScreenOn(keepAwake: Boolean) {
        if (keepAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        binding.previewStage.keepScreenOn = keepAwake
    }

    @Suppress("DEPRECATION")
    private fun updateSystemBarsForMode() {
        window.decorView.systemUiVisibility = if (isLandscapeStreaming()) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        supportActionBar?.let { actionBar ->
            if (isLandscapeStreaming()) actionBar.hide() else actionBar.show()
        }
    }


    // Summaries, adapters, formatting, and small utility helpers.
    private fun currentParameterSummary(): String {
        val localizedCameraLabel = selectedOption?.let(::localizedCameraOption)?.label
            ?: localizedText("Back camera", "\u540e\u6444")
        val localizedResolutionLabel = selectedOutputSize?.let(cameraRepository::sizeLabel) ?: "-"
        val localizedFpsLabel = NativeStatusSummaries.fpsLabel(selectedTargetFps, isUnlimitedFpsSelected, isChineseUi)
        return NativeStatusSummaries.parameterSummary(
            cameraLabel = localizedCameraLabel,
            resolutionLabel = localizedResolutionLabel,
            fpsLabel = localizedFpsLabel,
            streamQuality = selectedStreamQuality
        )
    }

    private fun liveStatusLine1Text(): String {
        return localizedText("Live  ${appVersionLabel()}", "\u6b63\u5728\u76f4\u64ad  ${appVersionLabel()}")
    }

    private fun liveStatusLine2Text(): String {
        return currentParameterSummary()
    }

    private fun updateManualResolutionUi() {
        binding.manualResolutionContainer.visibility = if (isManualResolutionMode) View.VISIBLE else View.GONE
        if (isManualResolutionMode) {
            selectedOutputSize?.let(::populateManualResolutionInputs)
        }
    }

    private fun populateManualResolutionInputs(size: Size) {
        binding.manualWidthInput.setText(size.width.toString())
        binding.manualHeightInput.setText(size.height.toString())
    }

    private fun resolveResolutionSelectionIndex(): Int {
        if (isManualResolutionMode) {
            return resolutionEntries.indexOfFirst { it.isManual }.coerceAtLeast(0)
        }
        val selectedLabel = selectedOutputSize?.let(cameraRepository::sizeLabel)
        return resolutionEntries.indexOfFirst { it.label == selectedLabel }.coerceAtLeast(0)
    }

    private fun resolveFpsSelectionIndex(): Int {
        if (isUnlimitedFpsSelected) {
            return fpsEntries.indexOfFirst { it.isUnlimited }.coerceAtLeast(0)
        }
        return fpsEntries.indexOfFirst { it.value == selectedTargetFps && !it.isUnlimited }.coerceAtLeast(0)
    }

    private fun <T> themedSpinnerAdapter(items: List<T>): ArrayAdapter<T> {
        return NativeSpinnerAdapters.create(
            context = this,
            items = items,
            palette = currentPalette(),
            selectedBackgroundRes = skinDrawable(
                    R.drawable.bg_spinner_selected_item,
                    R.drawable.bg_spinner_selected_item_warm,
                    R.drawable.bg_spinner_selected_item_mineral
            ),
            dropdownBackgroundRes = skinDrawable(
                    R.drawable.bg_spinner_dropdown_item,
                    R.drawable.bg_spinner_dropdown_item_warm,
                    R.drawable.bg_spinner_dropdown_item_mineral
            )
        )
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAudioSupported(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
    }

    private fun audioUrl(): String {
        return AppDisplayInfo.dashboardUrl(port).removeSuffix("/") + "/audio.aac"
    }

    private fun rtspUrl(): String {
        return AppDisplayInfo.rtspUrl(rtspPort)
    }

    private fun currentAudioStatus(): String {
        return when {
            !isAudioSupported() -> "Microphone unavailable"
            !hasAudioPermission() && lastAudioStatus == "Microphone permission required" -> lastAudioStatus
            !audioEnabled -> "Audio disabled"
            !hasAudioPermission() -> "Microphone permission required"
            ::audioStreamer.isInitialized && audioStreamer.isReady -> {
                val clients = if (::server.isInitialized) server.audioClientCount() else 0
                "Audio running ($clients clients)"
            }
            isStreaming -> lastAudioStatus.takeIf { it.isNotBlank() } ?: "Audio enabled; starting"
            else -> "Audio enabled; stream is stopped"
        }
    }

    private fun syncVideoOverlayControls() {
        val clampedSize = selectedVideoOverlaySize.coerceIn(
            VIDEO_OVERLAY_SIZE_MIN_PERCENT,
            VIDEO_OVERLAY_SIZE_MAX_PERCENT
        )
        if (selectedVideoOverlaySize != clampedSize) {
            selectedVideoOverlaySize = clampedSize
        }
        suppressUiCallbacks = true
        try {
            binding.videoOverlaySwitch.isChecked = selectedVideoOverlayEnabled
            binding.videoOverlaySizeSeekBar.progress = clampedSize - VIDEO_OVERLAY_SIZE_MIN_PERCENT
            binding.videoOverlaySizeValueText.text = clampedSize.toString()
        } finally {
            suppressUiCallbacks = false
        }
        binding.videoOverlaySizeContainer.visibility = if (selectedVideoOverlayEnabled) View.VISIBLE else View.GONE
        binding.videoOverlaySizeSeekBar.isEnabled = selectedVideoOverlayEnabled
    }

    private fun currentVideoOverlayStatus(): VideoOverlayStatus {
        return VideoOverlayStatus(
            enabled = selectedVideoOverlayEnabled,
            sizePercent = selectedVideoOverlaySize,
            batteryPercent = batteryPercent,
            charging = batteryCharging
        )
    }

    private fun updateBatteryStatus(intent: Intent?) {
        if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        batteryPercent = if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        } else {
            null
        }
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        batteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun localizedString(englishRes: Int, chineseRes: Int): String {
        return getString(if (isChineseUi) chineseRes else englishRes)
    }

    private fun localizedText(english: String, chinese: String): String {
        return if (isChineseUi) chinese else english
    }

    private fun applyStaticLabelText() {
        NativeStaticLabels.apply(
            root = binding.root,
            bindings = NativeStaticTextBindings.all,
            isChinese = isChineseUi,
            stringProvider = ::getString
        )
    }

    private fun applyTextPalette(root: View, palette: UiPalette) {
        NativePaletteApplier.applyTextPalette(
            root = root,
            palette = palette,
            onAccentTextViews = setOf(binding.startButton)
        )
    }

    private fun applySeekBarPalette(palette: UiPalette) {
        NativePaletteApplier.applySeekBarPalette(
            seekBars = listOf(
                binding.qualitySeekBar,
                binding.focusDistanceSeekBar,
                binding.videoOverlaySizeSeekBar,
                binding.zoomSeekBar,
                binding.exposureSeekBar
            ),
            palette = palette
        )
    }

    private fun currentPalette(): UiPalette {
        return NativeSkinResources.palette(activeSkin, ::color)
    }

    private fun skinDrawable(greenRes: Int, sunriseRes: Int, mineralRes: Int): Int {
        return NativeSkinResources.drawable(activeSkin, greenRes, sunriseRes, mineralRes)
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)

    private fun defaultManualResolutionHint(): String {
        return localizedString(R.string.ui_custom_resolution_hint_en, R.string.ui_custom_resolution_hint_zh)
    }

    private fun manualResolutionLabel(): String {
        return localizedText("Manual input", "\u624b\u52a8\u8f93\u5165")
    }

    private fun appVersionLabel(): String {
        return currentVersionInfo().label
    }

    private fun currentVersionInfo(): VersionInfo {
        val fallbackName = BuildConfig.VERSION_NAME
        val fallbackCode = BuildConfig.VERSION_CODE.toLong()
        return runCatching {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName?.takeIf { it.isNotBlank() } ?: fallbackName
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            VersionInfo(name = versionName, code = versionCode)
        }.getOrElse { VersionInfo(name = fallbackName, code = fallbackCode) }
    }

    private companion object {
        data class VersionInfo(
            val name: String,
            val code: Long
        ) {
            val label: String
                get() = "v$name ($code)"
        }

        const val AUDIO_START_RETRY_DELAY_MS = 250L
        const val MAX_AUDIO_START_RETRY_ATTEMPTS = 20
        const val AUDIO_READY_WAIT_TIMEOUT_MS = 3_000L
        const val AUDIO_READY_WAIT_INTERVAL_MS = 100L
        const val MJPEG_IDLE_GRACE_MS = 5_000L
        const val MJPEG_IDLE_GRACE_RESYNC_DELAY_MS = MJPEG_IDLE_GRACE_MS + 300L
        const val H264_IDLE_STOP_DELAY_MS = 5_000L
    }

}
