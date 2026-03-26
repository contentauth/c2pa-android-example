package org.contentauth.android.sample.c2pa.data

import android.net.Uri

data class Media(
    val uri: Uri,
    val isVideo: Boolean,
    val date: Long,
)