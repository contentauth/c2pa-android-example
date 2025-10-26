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
import com.proofmode.c2pa.c2pa.data.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import android.util.Base64
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/*fun generateOrGetKeyPair(alias: String): KeyPair {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)

    // If the key already exists, just return it
    if (keyStore.containsAlias(alias)) {
        val privateKey = keyStore.getKey(alias, null) as java.security.PrivateKey
        val publicKey = keyStore.getCertificate(alias).publicKey
        return KeyPair(publicKey, privateKey)
    }

    val start = Calendar.getInstance()
    val end = Calendar.getInstance()
    end.add(Calendar.YEAR, 25) // valid for 25 years

    val spec = KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
    )
        .setAlgorithmParameterSpec(java.security.spec.RSAKeyGenParameterSpec(2048, java.math.BigInteger.valueOf(65537)))
        .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
        .setCertificateSubject(javax.security.auth.x500.X500Principal("CN=$alias"))
        .setCertificateSerialNumber(java.math.BigInteger.ONE)
        .setCertificateNotBefore(start.time)
        .setCertificateNotAfter(end.time)
        .build()

    val keyPairGenerator = KeyPairGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_RSA,
        "AndroidKeyStore"
    )
    keyPairGenerator.initialize(spec)
    return keyPairGenerator.generateKeyPair()
}*/

/*fun getKeyPair(alias: String): KeyPair? {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    val privateKey = keyStore.getKey(alias, null) as? java.security.PrivateKey ?: return null
    val publicKey = keyStore.getCertificate(alias).publicKey
    return KeyPair(publicKey, privateKey)
}*/



fun getOrGenerateKeyPair(
    directory: File,
    alias: String
): KeyPair {
    val publicKeyFile = File(directory, "$alias-public.pem")
    val privateKeyFile = File(directory, "$alias-private.pem")

    // If both files exist, load existing keys
    if (publicKeyFile.exists() && privateKeyFile.exists()) {
        val publicKey = loadPublicKey(publicKeyFile)
        val privateKey = loadPrivateKey(privateKeyFile)
        return KeyPair(publicKey, privateKey)
    }

    // Otherwise generate and save new key pair
    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
    keyPairGenerator.initialize(2048)
    val keyPair = keyPairGenerator.generateKeyPair()

    saveKeyToPem(publicKeyFile, keyPair.public.encoded, "PUBLIC KEY")
    saveKeyToPem(privateKeyFile, keyPair.private.encoded, "PRIVATE KEY")

    return keyPair
}


private fun saveKeyToPem(file: File, keyBytes: ByteArray, type: String) {
    val base64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
    // Split into 64-character lines for PEM format
    val pemBody = base64.chunked(64).joinToString("\n")
    val pem = "-----BEGIN $type-----\n$pemBody\n-----END $type-----"
    file.writeText(pem)
}

private fun loadPublicKey(file: File): PublicKey {
    val keyBytes = readPemBytes(file)
    val spec = X509EncodedKeySpec(keyBytes)
    val factory = KeyFactory.getInstance("RSA")
    return factory.generatePublic(spec)
}

private fun loadPrivateKey(file: File): PrivateKey {
    val keyBytes = readPemBytes(file)
    val spec = PKCS8EncodedKeySpec(keyBytes)
    val factory = KeyFactory.getInstance("RSA")
    return factory.generatePrivate(spec)
}

private fun readPemBytes(file: File): ByteArray {
    val pem = readPemString(file)
    return Base64.decode(pem, Base64.DEFAULT)
}

fun readPemString(file: File): String {
    return file.readText()
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("\\s".toRegex(), "")
}

fun getPublicKeyFile(directory: File, alias: String): File {
    return File(directory, "$alias-public.pem")
}

fun getPrivateKeyFile(directory: File, alias: String): File {
    return File(directory, "$alias-private.pem")
}



fun certificatePEMFromKeyPair(keyPair: KeyPair):  String? {
    return keyPair.public?.encoded?.let {
        val pem = StringBuilder()
        pem.appendLine("-----BEGIN PUBLIC KEY-----")
        pem.appendLine(java.util.Base64.getEncoder().encodeToString(it).chunked(64).joinToString("\n"))
        pem.appendLine("-----END PUBLIC KEY-----")
        pem.toString()
    }
}


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

fun Context.getAppVersionName(): String? {
    return try {
        val packageInfo = packageManager.getPackageInfo(packageName,0)
        packageInfo.versionName
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
        null
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

    const val KEY_FILES_DIR = "key_files_dir"
}
