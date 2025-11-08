package com.proofmode.c2pa.c2pa

import android.content.Context
import android.location.Location
import android.net.Uri
import androidx.annotation.RawRes
import com.proofmode.c2pa.BuildConfig
import com.proofmode.c2pa.R
import com.proofmode.c2pa.utils.getAppVersionName
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.SignerInfo
import org.contentauth.c2pa.SigningAlgorithm
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/**
 * Creates a temporary local file from a given content URI.
 */
private fun createTempFileFromUri(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        // Create a temporary file in the app's cache directory
        val tempFile = File.createTempFile("c2pa_temp", uri.lastPathSegment, context.cacheDir)
        // Copy the content
        FileOutputStream(tempFile).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        inputStream.close()
        tempFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Reads a PEM file from the res/raw directory and returns its Base64 content
 * with headers and footers stripped out.
 */
private fun readPemContentFromRawResource(context: Context, @RawRes resourceId: Int): String {
    return context.resources.openRawResource(resourceId).bufferedReader().use { reader ->
        reader.readLines()
            .filter { line ->
                !line.startsWith("-----") // Filter out header and footer lines
            }
            .joinToString("") // Join the Base64 lines into a single string
    }
}

fun signWithC2PA(uri: Uri, context: Context, fileFormat: String, location: Location?) {
    // 1. Create a temporary file from the source URI to work with
    val tempFile = createTempFileFromUri(context, uri) ?: return

    // 2. Read the key content from the res/raw directory
    // IMPORTANT: Make sure your key files are named `sample_public_key.pem` and `sample_private_key.pem` in `res/raw`
    val publicKeyPEM = readPemContentFromRawResource(context, R.raw.certificate_rs256)
    val privateKeyPEM = readPemContentFromRawResource(context, R.raw.rs256)

    val signerInfo = SignerInfo(SigningAlgorithm.PS256, publicKeyPEM, privateKeyPEM)

    // Create the manifest
    val manifest = ManifestBuilder(
        "${context.packageName}/${context.getAppVersionName() ?: ""}",
        format = fileFormat
    )
        .addAction(
            "c2pa.created",
            whenIso = Instant.now().toString(),
            softwareAgent = "${BuildConfig.APPLICATION_ID}/${context.getAppVersionName() ?: ""}"
        )
        .addAuthorInfo(
            "ProofMode C2PA Demo",
            description = "This content was captured by the ProofMode C2PA Demo app."
        )
        .apply {
            location?.let {
                addLocationInfo(it.latitude, it.longitude)
            }
        }
        .toJson()

    try {
        // 3. Sign the temporary file in-place
        C2PA.signFile(tempFile.absolutePath, tempFile.absolutePath, manifest, signerInfo)

        // 4. Write the signed temporary file back to the original URI
        context.contentResolver.openOutputStream(uri, "w")?.use { outputStream ->
            tempFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    } catch (e: Exception) {
        // Handle potential signing or writing errors
        Timber.e(e, "C2PA signing failed")
    } finally {
        // 5. Clean up the temporary file
        tempFile.delete()
    }
}

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
