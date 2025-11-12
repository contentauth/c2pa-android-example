package com.proofmode.c2pa.ui

import android.Manifest
import android.os.Build
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale
import com.proofmode.c2pa.R

private val permissions = mutableListOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
).apply {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(viewModel: CameraViewModel,modifier: Modifier = Modifier,
                 onNavigateToPreview: () -> Unit,
                 onNavigateToSettings: () -> Unit) {
    val permissionsState = rememberMultiplePermissionsState(permissions)
    var showRationaleDialog by remember { mutableStateOf(false) }

    val requiredPermissions = listOfNotNull(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) Manifest.permission.WRITE_EXTERNAL_STORAGE else null
    )

    val areRequiredPermissionsGranted = permissionsState.permissions.filter {
        it.permission in requiredPermissions
    }.all {
        it.status.isGranted
    }

    Scaffold(modifier = modifier) { paddingValues ->

        if (showRationaleDialog) {
            PermissionRationaleDialog(
                onConfirm = {
                    showRationaleDialog = false
                    permissionsState.launchMultiplePermissionRequest()
                },
                onDismiss = { showRationaleDialog = false }
            )
        }

        when {
            areRequiredPermissionsGranted -> {
                CameraCaptureScreen(viewModel,
                    onNavigateToPreview = onNavigateToPreview,
                    paddingValues = paddingValues,
                    onNavigateToSettings = onNavigateToSettings)
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.permission_request_message),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        if (permissionsState.permissions.any { !it.status.isGranted && it.status.shouldShowRationale }) {
                            showRationaleDialog = true
                        } else {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    }) {
                        Text(text = stringResource(id = R.string.grant_permissions))
                    }
                }
            }
        }

    }
}

@Composable
private fun PermissionRationaleDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.permissions_required)) },
        text = { Text(stringResource(R.string.permission_request_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.continue_))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}


@Composable
private fun CameraCaptureScreen(viewModel: CameraViewModel,
                                onNavigateToPreview: () -> Unit = {},
                                onNavigateToSettings: () -> Unit = {},
                                paddingValues: PaddingValues) {
    val previewUri by viewModel.thumbPreviewUri.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.bindToCamera(lifecycleOwner)
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
        CameraPreview(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        viewModel.flipCamera()
                    }, onTap = {

                    })
                }
            ,
            surfaceRequest = surfaceRequest
        )

        Row(modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(horizontal = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ){
            IconButton(onClick = {
                viewModel.flipCamera()
            },
            ) {
                Icon(Icons.Default.Cameraswitch,contentDescription = stringResource(R.string.switch_camera),
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            AnimatedVisibility(visible = (recordingState == RecordingState.Idle
                    || recordingState is RecordingState.Finalized)  ){
                IconButton(onClick = {
                    onNavigateToSettings()
                },
                ) {
                    Icon(Icons.Default.Settings,contentDescription = stringResource(R.string.switch_camera),
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.takePhoto() }) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = stringResource(id = R.string.take_photo),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(64.dp)
                )
            }

            when (recordingState) {
                is RecordingState.Idle, is RecordingState.Finalized -> {
                    IconButton(onClick = { viewModel.captureVideo() }) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = stringResource(id = R.string.start_recording),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                is RecordingState.Recording -> {
                    IconButton(onClick = { viewModel.pauseRecording() }) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = stringResource(id = R.string.pause_recording),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.captureVideo() }) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = stringResource(id = R.string.stop_recording),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                is RecordingState.Paused -> {
                    IconButton(onClick = { viewModel.resumeRecording() }) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(id = R.string.resume_recording),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.captureVideo() }) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = stringResource(id = R.string.stop_recording),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }

                else -> {}
            }

            AnimatedVisibility(visible = recordingState is RecordingState.Finalized || recordingState is RecordingState.Idle) {

                Box(modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF444444), CircleShape)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = Color.White,
                        shape = CircleShape
                    )
                ){
                    AnimatedContent(targetState = previewUri,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        }, modifier = Modifier.matchParentSize()) { media ->
                        if (media != null) {
                            ItemPreview(modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    onNavigateToPreview()
                                }
                                , media = media )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Photo,
                                contentDescription = "No media",
                                tint = Color.Gray,
                                modifier = Modifier
                                    .align(Alignment.Center)
                            )
                        }

                    }
                }

            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    surfaceRequest: SurfaceRequest?
) {

    surfaceRequest?.let {
        CameraXViewfinder(surfaceRequest = it,
            modifier = modifier)
    }
}
