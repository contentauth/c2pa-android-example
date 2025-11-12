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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.decode.VideoFrameDecoder
import coil.load
import com.proofmode.c2pa.c2pa_signing.shareMedia
import com.proofmode.c2pa.data.Media

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

    BackHandler(enabled = onNavigateBack != null) {
        onNavigateBack?.invoke()

    }
    Scaffold(modifier = modifier.fillMaxSize(), topBar = {
        TopAppBar(title = {}, navigationIcon = {
            IconButton(onClick = {
                onNavigateBack?.invoke()
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

        HorizontalPager(state = pagerState) { itemIdx->
            when(mediaItems[itemIdx].isVideo) {
                true -> SimpleVideoView(videoUri = mediaItems[itemIdx].uri, modifier = Modifier.fillMaxSize())
                false -> ImagePreview(modifier = Modifier.fillMaxSize(), media = mediaItems[itemIdx])
            }
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
    Box(modifier = modifier,
        contentAlignment = Alignment.Center){
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

        }, modifier = Modifier.fillMaxSize())
    }

}