package com.proofmode.c2pa.c2pa_signing

import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import com.proofmode.c2pa.data.Media
import org.contentauth.c2pa.Reader
import org.contentauth.c2pa.Stream
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream


fun getLatitudeAsDMS(location: Location, decimalPlace: Int): String {
    var strLatitude = Location.convert(location.latitude, Location.FORMAT_SECONDS)
    strLatitude = replaceDelimiters(strLatitude, decimalPlace)
    strLatitude = "$strLatitude N"
    return strLatitude
}

fun getLongitudeAsDMS(location: Location, decimalPlace: Int): String {
    var strLongitude = Location.convert(location.longitude, Location.FORMAT_SECONDS)
    strLongitude = replaceDelimiters(strLongitude, decimalPlace)
    return "$strLongitude W"

}

private fun replaceDelimiters(str: String, decimalPlace: Int): String {
    var str = str
    str = str.replaceFirst(":".toRegex(), "°")
    str = str.replaceFirst(":".toRegex(), "'")
    val pointIndex = str.indexOf(".")
    val endIndex = pointIndex + 1 + decimalPlace
    if (endIndex < str.length) {
        str = str.take(endIndex)
    }
    str += "\""
    return str
}

private fun shareMedia(context: Context, uri: Uri,mimeType:String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share file"))
}

fun shareMedia(context: Context,media: Media) {
    val mime = if (media.isVideo) "video/*" else "image/*"
    shareMedia(context = context, uri = media.uri, mimeType = mime)

}

internal fun createTempFileFromUri(uri: Uri, context: Context): File? {
    return try {
        val mime = context.contentResolver.getType(uri)
        val ext = when (mime) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "video/mp4" -> ".mp4"
            else -> null
        }

        if (ext == null) {
            Timber.e("Unsupported MIME type for C2PA: $mime")
            return null
        }

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val tempFile = File.createTempFile("c2pa_temp_", ext, context.cacheDir)
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            tempFile
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to create temp file from URI")
        null
    }
}




