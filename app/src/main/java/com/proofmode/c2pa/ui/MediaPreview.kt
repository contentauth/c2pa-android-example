package com.proofmode.c2pa.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.decode.VideoFrameDecoder
import coil.load
import com.proofmode.c2pa.R
import com.proofmode.c2pa.c2pa_signing.shareMedia
import com.proofmode.c2pa.data.Manifest
import com.proofmode.c2pa.data.ManifestRoot
import com.proofmode.c2pa.data.Media
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPreview(viewModel: CameraViewModel, modifier: Modifier = Modifier,
                 onNavigateBack: (() -> Unit)? = null){
    val context = LocalContext.current
    val mediaItems by viewModel.mediaFiles.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = {
        mediaItems.size
    })

    val json = Json { ignoreUnknownKeys = true }
    var manifest: Manifest? by remember { mutableStateOf(null) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    var isSheetOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()




    /*BackHandler(enabled = onNavigateBack != null) {
        onNavigateBack?.invoke()

    }*/
    Scaffold(modifier = modifier.fillMaxSize(), topBar = {
        TopAppBar(title = {}, navigationIcon = {
            IconButton(onClick = {
                //onNavigateBack?.invoke()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
            }
        },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary,
                navigationIconContentColor = Color.White,
                titleContentColor = Color.White))
    }, bottomBar = {
        BottomAppBar(containerColor = MaterialTheme.colorScheme.onSurface) {
            Row(horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = {
                    val currentItem = mediaItems.getOrNull(pagerState.currentPage)
                    currentItem?.let {
                        shareMedia(context = context, media = it)
                    }
                }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share media", tint = Color.White)
                }

            }
        }
    }) {

        HorizontalPager(state = pagerState,
            modifier = Modifier.padding(it)) { itemIdx->
            val media = mediaItems[itemIdx]
            MediaView(media = media) {
                val manifestJson = viewModel.readManifest(media)
                if (manifestJson != null) {
                    val manifestRoot = json.decodeFromString<ManifestRoot>(manifestJson)
                    manifest = manifestRoot.manifests[manifestRoot.activeManifest]
                    isSheetOpen = true
                }
            }
        }

        /*if (showCredentialsDialog) {
            CredentialsDialog(onDismiss = {
                showCredentialsDialog = false
            }, manifestJson = manifestJson?:"")
        }*/

        if(isSheetOpen) {
            ModalBottomSheet(onDismissRequest = {
                isSheetOpen = false
            }, sheetState = sheetState,
                modifier = Modifier.fillMaxSize()) {
                Scaffold(topBar = {
                    TopAppBar(title = {
                        Text(stringResource(R.string.content_credentials))
                    },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch { sheetState.hide()}
                                    .invokeOnCompletion {
                                        if (!sheetState.isVisible) {
                                            isSheetOpen = false
                                        }
                                    }
                            }) { }
                        })
                }) { innerPadding->
                    manifest?.let {
                        ManifestDetailsScreen(manifest = it, modifier = Modifier.padding(innerPadding))
                    }

                }
            }
        }



    }

}


@Composable
fun MediaView(media: Media,modifier: Modifier = Modifier,
              onCredentialsClick: () -> Unit) {


    Box(modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center){
        when(media.isVideo) {
            true -> SimpleVideoView(videoUri = media.uri, modifier = Modifier)
            else -> ImagePreview(modifier = Modifier, media = media)
        }
        IconButton(onClick = onCredentialsClick,
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(painter = painterResource(R.drawable.contentcredentials), contentDescription = null)
        }
    }
}


@Composable
fun SimpleVideoView(videoUri:Uri,modifier: Modifier = Modifier) {
    AndroidView(modifier = modifier, factory = {context->
        VideoView(context).also { videoView->
            val mediaController = MediaController(context).also{ controller->
                controller.setAnchorView(videoView)
            }
            videoView.apply {
                setMediaController(mediaController)
                setVideoURI(videoUri)
                setOnPreparedListener { mediaPlayer->
                    mediaPlayer.isLooping = false
                }
                start()
            }

        }


    })
}


@Composable
fun ImagePreview(modifier: Modifier,media: Media){
    AndroidView(factory = { context->

        val imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
        }
        imageView.load(media.uri) {
            if (media.isVideo) {
                decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
            }
        }
        imageView

    }, modifier = modifier)

}

@Composable
fun CredentialsDialog(onDismiss: () -> Unit,
                      manifestJson: String) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.content_credentials)) },
        text = { Text(manifestJson) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}