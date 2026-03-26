package org.contentauth.android.sample.c2pa.ui

import android.net.Uri

sealed class RecordingState {
    object Idle : RecordingState()
    object Recording : RecordingState()
    object Paused : RecordingState()
    data class Finalized(val outputUri: Uri?) : RecordingState()
    data class Error(val message: String?) : RecordingState()
}