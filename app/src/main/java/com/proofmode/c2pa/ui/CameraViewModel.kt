package com.proofmode.c2pa.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.core.content.PermissionChecker
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proofmode.c2pa.c2pa_signing.C2PAManager
import com.proofmode.c2pa.c2pa_signing.IPreferencesManager
import com.proofmode.c2pa.c2pa_signing.createTempFileFromUri
import com.proofmode.c2pa.data.Media
import com.proofmode.c2pa.utils.Constants
import com.proofmode.c2pa.utils.getCurrentLocation
import com.proofmode.c2pa.utils.getMediaFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.manifest.Ingredient
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val c2paManager: C2PAManager,
    private val preferencesManager: IPreferencesManager
) : ViewModel() {

    private val outputDirectory = Constants.outputDirectory
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private var recording: Recording? = null

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState

    private val _cameraSelector = MutableStateFlow(CameraSelector.DEFAULT_BACK_CAMERA)
    val cameraSelector: StateFlow<CameraSelector> = _cameraSelector

    private var cameraProvider: ProcessCameraProvider? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var surfaceOrientedMeteringPointFactory: SurfaceOrientedMeteringPointFactory? = null

    private val cameraPreviewUseCase = Preview.Builder().build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequest.update { newSurfaceRequest }
            surfaceOrientedMeteringPointFactory = SurfaceOrientedMeteringPointFactory(
                newSurfaceRequest.resolution.width.toFloat(),
                newSurfaceRequest.resolution.height.toFloat()
            )
        }
    }
    private var camera: Camera? = null


    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val imageCaptureUseCase = ImageCapture.Builder().build()

    private var _mediaFiles: MutableStateFlow<List<Media>> = MutableStateFlow(emptyList())
    val mediaFiles: StateFlow<List<Media>> = _mediaFiles

    private var _thumbPreviewUri = MutableStateFlow<Media?>(null)
    val thumbPreviewUri: StateFlow<Media?> = _thumbPreviewUri

    init {
        loadFiles()
    }

    private fun loadFiles() {
        viewModelScope.launch {
            getMediaFlow(context, outputDirectory)
                .collect { media ->
                    _mediaFiles.value = media
                    _thumbPreviewUri.value = media.firstOrNull()
                }
        }
    }
    private val videoCaptureUseCase by lazy {
        val recorder = Recorder.Builder()
            .setExecutor(cameraExecutor)
            .build()
        VideoCapture.withOutput(recorder)
    }

    suspend fun bindToCamera(
        lifecycleOwner: LifecycleOwner,
    ) {
        this.lifecycleOwner = lifecycleOwner
        this.cameraProvider = ProcessCameraProvider.awaitInstance(context)
        rebindUseCases()
        try {
            awaitCancellation()
        } finally {
            cameraProvider?.unbindAll()
        }
    }

    private fun rebindUseCases() {
        val provider = cameraProvider ?: return
        val owner = lifecycleOwner ?: return
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                owner,
                cameraSelector.value,
                cameraPreviewUseCase,
                imageCaptureUseCase,
                videoCaptureUseCase
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to rebind camera use cases")
        }
    }

    fun flipCamera() {
        _cameraSelector.update {
            if (it == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
        }
        rebindUseCases()
    }

    fun saveLocationSharing(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setLocationSharing(enabled)
        }
    }

    fun takePhoto() {
        val imageCapture = imageCaptureUseCase
        viewModelScope.launch {
            val location = if (preferencesManager.locationSharing.first()) {
                getCurrentLocation(context)
            } else {
                null
            }

            val outputOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis())
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, outputDirectory)
                }
                ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            } else {
                File(outputDirectory).mkdirs()
                val name = generateName()
                val fileMedia = File(outputDirectory, "$name.jpg")
                ImageCapture.OutputFileOptions.Builder(fileMedia)
            }.apply {
                location?.let { setMetadata(ImageCapture.Metadata().apply { this.location = it }) }
            }.build()

            imageCapture.takePicture(
                outputOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Timber.e(exc)
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        viewModelScope.launch {
                            output.savedUri?.let {
                                c2paManager.signMediaFile(it, "image/jpeg", location = location)
                            }
                            loadFiles()
                        }
                    }
                }
            )
        }
    }

    fun captureVideo() {
        val videoCapture = this.videoCaptureUseCase

        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            _recordingState.update { RecordingState.Idle }
            return
        }

        viewModelScope.launch {
            val location = if (preferencesManager.locationSharing.first()) {
                getCurrentLocation(context)
            } else {
                null
            }

            val name = generateName()
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, outputDirectory)
                }
            }

            val mediaStoreOutputOptions = MediaStoreOutputOptions
                .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setLocation(location)
                .setContentValues(contentValues)
                .build()

            recording = videoCapture.output
                .prepareRecording(context, mediaStoreOutputOptions)
                .apply {
                    if (PermissionChecker.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED) {
                        withAudioEnabled()
                    }
                }
                .asPersistentRecording() // persist recording
                .start(cameraExecutor) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> _recordingState.update { RecordingState.Recording }
                        is VideoRecordEvent.Pause -> _recordingState.update { RecordingState.Paused }
                        is VideoRecordEvent.Resume -> _recordingState.update { RecordingState.Recording }
                        is VideoRecordEvent.Finalize -> {
                            if (!recordEvent.hasError()) {
                                _recordingState.update { RecordingState.Finalized(recordEvent.outputResults.outputUri) }
                                viewModelScope.launch {
                                    c2paManager.signMediaFile(recordEvent.outputResults.outputUri, "video/mp4", location = location)
                                    loadFiles()
                                }
                            } else {
                                recording?.close()
                                recording = null
                                _recordingState.update { RecordingState.Error(recordEvent.cause?.message) }
                            }
                        }
                    }
                }
        }
    }

    fun tapToFocus(tapCoordinates: Offset) {
        val point = surfaceOrientedMeteringPointFactory?.createPoint(tapCoordinates.x,tapCoordinates.y)
        if (point != null) {
            val meteringAction = FocusMeteringAction.Builder(point).build()
            if (camera?.cameraInfo?.isFocusMeteringSupported(meteringAction) == true){
                camera?.cameraControl?.startFocusAndMetering(meteringAction)
            }

        }

    }

    fun readManifest(media: Media): String? {
        var manifest: String? = null
       viewModelScope.launch {
           val tempFile = createTempFileFromUri(media.uri, context)
           if (tempFile != null) {
               try {
                   manifest = C2PA.read(tempFile)
               }finally {
                   tempFile.delete()
               }
           }
       }
        return manifest
    }


    fun pauseRecording() {
        recording?.pause()
    }

    fun resumeRecording() {
        recording?.resume()
    }

    override fun onCleared() {
        super.onCleared()
        recording?.stop()
        recording?.close()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }

    private fun generateName() = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
}
