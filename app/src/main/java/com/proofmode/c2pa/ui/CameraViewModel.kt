package com.proofmode.c2pa.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.location.Location
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.PermissionChecker
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proofmode.c2pa.c2pa.data.Media
import com.proofmode.c2pa.c2pa.signWithC2PA
import com.proofmode.c2pa.c2pa_signing.C2PAManager
import com.proofmode.c2pa.utils.Constants
import com.proofmode.c2pa.utils.getCurrentLocation
import com.proofmode.c2pa.utils.getMediaFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(@ApplicationContext private val context: Context,
    private val c2paManager: C2PAManager
) : ViewModel() {

    private val outputDirectory = Constants.outputDirectory
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    // Video recording state
    private var recording: Recording? = null

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState

    private val cameraPreviewUseCase = Preview.Builder().build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequest.update { newSurfaceRequest }
        }
    }

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
        val cameraProvider = ProcessCameraProvider.awaitInstance(context)

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                cameraPreviewUseCase,
                imageCaptureUseCase,
                videoCaptureUseCase
            )
            awaitCancellation()
        } finally {
            cameraProvider.unbindAll()
        }
    }

    fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCaptureUseCase ?: return
        var currentLocation: Location? = null
        viewModelScope.launch {
            currentLocation = getCurrentLocation(context)

            Timber.d("takePhoto: $currentLocation")
        }

        // Create time stamped name and MediaStore entry.

        // Create output options object which contains file + metadata
        val outputOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis())
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, outputDirectory)
            }

            val contentResolver = context.contentResolver

            // Create the output uri
            val contentUri =
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            ImageCapture.OutputFileOptions.Builder(contentResolver, contentUri, contentValues)
        } else {

            File(outputDirectory).mkdirs()
            val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis())
            val fileMedia = File(outputDirectory, "$name.jpg")
            ImageCapture.OutputFileOptions.Builder(fileMedia)
        }
            .apply {
                setMetadata(
                    ImageCapture.Metadata()
                    .apply {
                        location = currentLocation
                    }
                )
            }
            .build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults){
                    // sign with C2PA
                    viewModelScope.launch {
                        output.savedUri?.let {
                            c2paManager.signMediaFile(it, "image/jpeg")
                        }
                        // Reload the files to get the latest thumbnail
                        loadFiles()
                    }
                }
            }
        )
    }

    fun captureVideo() {
        val videoCapture = this.videoCaptureUseCase ?: return
        var location: Location? = null
        viewModelScope.launch {
            location = getCurrentLocation(context)
        }
        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            _recordingState.update { RecordingState.Idle }
            return
        }

        // create and start a new recording session
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, outputDirectory)

        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setLocation(location)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(context, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(context,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()

                }
            }
            .start(cameraExecutor) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        _recordingState.update { RecordingState.Recording }
                    }
                    is VideoRecordEvent.Pause -> {
                        _recordingState.update { RecordingState.Paused }
                    }
                    is VideoRecordEvent.Resume -> {
                        _recordingState.update { RecordingState.Recording }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            _recordingState.update { RecordingState.Finalized(recordEvent.outputResults.outputUri) }
                            viewModelScope.launch {
                                c2paManager.signMediaFile(recordEvent.outputResults.outputUri,"video/mp4")
                                // Reload the files to get the latest thumbnail
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

    fun pauseRecording() {
        recording?.pause()
    }

    fun resumeRecording() {
        recording?.resume()
    }

    override fun onCleared() {
        super.onCleared()
        cameraExecutor.shutdown()
    }
}
