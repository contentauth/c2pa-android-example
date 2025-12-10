package com.proofmode.c2pa.utils

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.proofmode.c2pa.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


suspend fun getCurrentLocation(context: Context): Location? {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    if (!hasPermission) {
        return null
    }

    return suspendCoroutine { continuation ->
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { location ->
            continuation.resume(location)
        }.addOnFailureListener {
            continuation.resume(null)
        }
    }
}





fun getMediaFlow(context: Context, outputDirectory: String): Flow<List<Media>> = flow {
    emit(emptyList())
    val media = getMedia(context,outputDirectory)
    emit(media)
}.flowOn(Dispatchers.IO)


suspend fun getMedia(context: Context, outputDirectory: String): List<Media> = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        getMediaQPlus(context, outputDirectory)
    } else {
        getMediaQMinus(context, outputDirectory)
    }
}

private fun getMediaQPlus(context: Context, outputDirectory: String): List<Media> {
    val items = mutableListOf<Media>()
    val contentResolver = context.applicationContext.contentResolver

    contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.DATE_TAKEN,
        ),
        null,
        null,
        "${MediaStore.Video.Media.DATE_TAKEN} DESC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val path = cursor.getString(pathColumn)
            val date = cursor.getLong(dateColumn)

            val contentUri: Uri =
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

            if (path == outputDirectory) {
                items.add(Media(contentUri, true, date))
            }
        }
    }

    contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_TAKEN,
        ),
        null,
        null,
        "${MediaStore.Images.Media.DATE_TAKEN} DESC"
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
        val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val path = cursor.getString(pathColumn)
            val date = cursor.getLong(dateColumn)

            val contentUri: Uri =
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

            if (path == outputDirectory) {
                items.add(Media(contentUri, false, date))
            }
        }
    }


    return items.sortedByDescending { it.date }
}

private fun getMediaQMinus(context: Context, outputDirectory: String): List<Media> {
    val items = mutableListOf<Media>()
    File(outputDirectory).listFiles()?.forEach {
        val authority = context.applicationContext.packageName + ".provider"
        val mediaUri = FileProvider.getUriForFile(context, authority, it)
        items.add(Media(mediaUri, it.extension == "mp4", it.lastModified()))
    }

    return items
}



object Constants {
    val outputDirectory: String by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${Environment.DIRECTORY_DCIM}/ProofModeC2pa/"
        } else {
            "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)}/ProofModeC2pa/"
        }
    }
}

