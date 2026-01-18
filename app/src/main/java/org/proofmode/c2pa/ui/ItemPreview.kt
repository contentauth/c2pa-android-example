package org.proofmode.c2pa.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.size.Size
import org.proofmode.c2pa.data.Media

@Composable
fun ItemPreview(modifier: Modifier = Modifier, media: Media){
    val context = LocalContext.current
    Box(modifier = modifier, contentAlignment = Alignment.Center){
        val painter = rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(media.uri).apply {
                    if (media.isVideo) {
                        decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                    }
                    size(Size.ORIGINAL)
                }.build()
        )

        Image(painter = painter, contentDescription = null,
            contentScale = ContentScale.Fit,)
        if (painter.state is AsyncImagePainter.State.Loading) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp),
                strokeWidth = 2.dp)
        }
    }
}