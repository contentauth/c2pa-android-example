package com.proofmode.c2pa.c2pa_signing
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.WrappedKeyEntry
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.contentauth.c2pa.Builder
import org.contentauth.c2pa.C2PA
import org.contentauth.c2pa.CertificateManager
import org.contentauth.c2pa.FileStream
import org.contentauth.c2pa.KeyStoreSigner
import org.contentauth.c2pa.Signer
import org.contentauth.c2pa.SigningAlgorithm
import org.contentauth.c2pa.Stream
import org.contentauth.c2pa.StrongBoxSigner
import org.contentauth.c2pa.manifest.Action
import org.contentauth.c2pa.manifest.AttestationBuilder
import org.contentauth.c2pa.manifest.C2PAActions
import org.contentauth.c2pa.manifest.C2PAFormats
import org.contentauth.c2pa.manifest.C2PARelationships
import org.contentauth.c2pa.manifest.DigitalSourceTypes
import org.contentauth.c2pa.manifest.Ingredient
import org.contentauth.c2pa.manifest.ManifestBuilder
import org.contentauth.c2pa.manifest.SoftwareAgent
import org.contentauth.c2pa.manifest.TimestampAuthorities
import org.json.JSONObject
import org.witness.proofmode.c2pa.selfsign.CertificateSigningService
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPrivateKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import javax.security.auth.x500.X500Principal

/**
 * Manages all C2PA (Content Authenticity Initiative) signing operations for the application.
 *
 * This class is a Hilt-managed singleton that provides a centralized and simplified interface
 * for signing media files. It abstracts the complexity of different signing backends,
 * including the Android Keystore, hardware-backed keys (StrongBox), custom user-provided keys,
 * and remote signing services.
 *
 * Its primary public function, `signMediaFile`, handles the entire process of signing a media
 * file from a content `Uri` in-place, which is crucial for working with modern Android
 * scoped storage. It uses a safe, temporary file pattern to avoid data corruption.
 *
 * @property context The application context, used for file operations and accessing system services.
 * @property preferencesManager A manager to retrieve user preferences, such as the desired [SigningMode].
 * Adapted from [ProofMode Android](https://gitlab.com/guardianproject/proofmode/proofmode-android/-/blob/dev/android-libproofmode/src/main/java/org/witness/proofmode/c2pa/PreferencesManager.kt?ref_type=heads)
 */

@Singleton
class C2PAManager @Inject constructor (private val context: Context, private val preferencesManager: IPreferencesManager) {
    companion object {
        private const val TAG = "C2PAManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS_PREFIX = "C2PA_KEY_"

        private val iso8601 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private lateinit var defaultSigner:Signer

    /**
     * Signs a media file from a URI and overwrites the original file with the signed version.
     *
     * This function does the entire "in-place" signing process for a given media file,
     * making it safe to use with `content://` URIs from sources like MediaStore or CameraX.
     * It uses a robust, two-step temporary file pattern to prevent data corruption:
     *
     * 1.  The original content from the [uri] is copied to a temporary source file.
     * 2.  A second, empty temporary file is created as the destination for the signing operation.
     * 3.  The internal `signStream` function is called to read from the source temp file and write
     *     the signed output to the destination temp file.
     * 4.  If signing is successful, the content of the signed destination temp file is streamed
     *     back to the original [uri], overwriting it.
     * 5.  Both temporary files are deleted at the end even if an error occurs.
     *
     * @param uri The `Uri` of the media file to sign. This serves as both the input for the
     *            original content and the final destination for the signed content.
     * @param contentType The MIME type of the media file (e.g., "image/jpeg", "video/mp4").
     *                    This is required for the C2PA library to process the file correctly.
     * @param location The `Location` where the media was captured.
     * @return A [Result] indicating the outcome. On success, it returns `Result.success(Unit)`.
     *         On failure, it returns `Result.failure(Exception)` containing the error.
     */

    suspend fun signMediaFile(uri: Uri, contentType: String,location: Location?): Result<Unit> = withContext(Dispatchers.IO) {
        // 1. Create a temporary file from the source URI to work with.
        val tempSourceFile = createTempFileFromUri(uri)
            ?: return@withContext Result.failure(IOException("Could not create temporary file from Uri: $uri"))

        // 2. Create a second temporary file for the signed output.
        val tempSignedFile = File.createTempFile("c2pa_signed_output_", ".tmp", context.cacheDir)
        try {
            // Get current signing mode
            val signingMode = preferencesManager.signingMode.first()
            val name = preferencesManager.userName.first()?:""
            val email = preferencesManager.userEmail.first()?:""
            val creatorBuilder = StringBuilder()
            if (name.isNotBlank()) creatorBuilder.append(name)
            if (email.isNotBlank()) {
                if (creatorBuilder.isNotBlank()) creatorBuilder.append(", ")
                creatorBuilder.append(email)
            }

            val creator = if (!creatorBuilder.isBlank()) creatorBuilder.toString() else "proofmode-test@email.com"

            // Create manifest JSON
            val manifestJSON = createManifestJSON(context, "info@proofmode.org", uri.lastPathSegment ?: "media", contentType, location, true)

            // Create appropriate signer based on mode
            if (!::defaultSigner.isInitialized)
                defaultSigner = createSigner(signingMode, TimestampAuthorities.DIGICERT)

            val fileStream = FileStream(tempSourceFile)
            val outStream = FileStream(tempSignedFile)
            signStream(fileStream, contentType, outStream, manifestJSON, defaultSigner)
            // 7. Write the content of the signed temporary file back to the original content URI.
            context.contentResolver.openOutputStream(uri, "w")?.use { originalUriStream ->
                tempSignedFile.inputStream().use { signedDataStream ->
                    signedDataStream.copyTo(originalUriStream)
                }
            } ?: throw IOException("Failed to open output stream for original Uri: $uri")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error signing media")
            Result.failure(e)
        } finally {
            tempSourceFile.delete()
            tempSignedFile.delete()
        }
    }


    private suspend fun createSigner(mode: SigningMode, tsaUrl: String): Signer = withContext(Dispatchers.IO) {
        when (mode) {
            SigningMode.KEYSTORE -> createKeystoreSigner(tsaUrl)
            SigningMode.HARDWARE -> createHardwareSigner(tsaUrl)
            SigningMode.CUSTOM -> createCustomSigner(tsaUrl)
            SigningMode.REMOTE -> createRemoteSigner()
        }
    }

    private suspend fun createKeystoreSigner(tsaUrl: String): Signer {
        val keyAlias = "C2PA_SOFTWARE_KEY_SECURE"
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        var certChain = ""

        // Create or get the keystore key
        if (!keyStore.containsAlias(keyAlias)) {
            Timber.tag(TAG).d("Creating new keystore key")
            createKeystoreKey(keyAlias, false)

            // Get certificate chain from signing server
            certChain = enrollHardwareKeyCertificate(keyAlias)

            val fileCert = File(context.filesDir,"$keyAlias.cert")
            fileCert.writeText(certChain)
        }
        else{
            // Get certificate chain from signing server

            val fileCert = File(context.filesDir,"$keyAlias.cert")
            if (fileCert.exists())
                certChain = fileCert.readText()
            else {
                certChain = enrollHardwareKeyCertificate(keyAlias)
                fileCert.writeText(certChain)
            }

        }


        Timber.tag(TAG).d("Using KeyStoreSigner with keyAlias: $keyAlias")

        // Use the new KeyStoreSigner class
        return KeyStoreSigner.createSigner(
            algorithm = SigningAlgorithm.ES256,
            certificateChainPEM = certChain,
            keyAlias = keyAlias,
            tsaURL = tsaUrl
        )
    }

    private suspend fun createHardwareSigner(tsaUrl: String): Signer {
        val alias =
            preferencesManager.hardwareKeyAlias.first()
                ?: "$KEYSTORE_ALIAS_PREFIX${SigningMode.HARDWARE.name}"

        // Get or create hardware-backed key
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(alias)) {
            // Create StrongBox config
            val config = StrongBoxSigner.Config(keyTag = alias, requireUserAuthentication = false)

            // Create key using StrongBoxSigner (will use StrongBox if available, TEE otherwise)
            try {
                StrongBoxSigner.createKey(config)
                preferencesManager.setHardwareKeyAlias(alias)
            } catch (e: Exception) {
                Timber.tag(TAG)
                    .w("StrongBox key creation failed, falling back to hardware-backed key")
                createKeystoreKey(alias, true)
                preferencesManager.setHardwareKeyAlias(alias)
            }
        }

        // Get certificate chain from signing server
        val certChain = enrollHardwareKeyCertificate(alias)

        Timber.tag(TAG).d("Creating StrongBoxSigner")

        // Create StrongBox config
        val config = StrongBoxSigner.Config(keyTag = alias, requireUserAuthentication = false)

        // Use the new StrongBoxSigner class
        return StrongBoxSigner.createSigner(
            algorithm = SigningAlgorithm.ES256,
            certificateChainPEM = certChain,
            config = config,
            tsaURL = tsaUrl
        )
    }

    private suspend fun createCustomSigner(tsaUrl: String): Signer {
        val certPEM =
            preferencesManager.customCertificate.first()
                ?: throw IllegalStateException("Custom certificate not configured")
        val keyPEM =
            preferencesManager.customPrivateKey.first()
                ?: throw IllegalStateException("Custom private key not configured")

        val keyAlias = "C2PA_CUSTOM_KEY_SECURE"
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        // Check if we need to reimport (e.g., user uploaded new key)
        val lastKeyHash = preferencesManager.customKeyHash.first()
        val currentKeyHash = keyPEM.hashCode().toString()

        if (!keyStore.containsAlias(keyAlias) || lastKeyHash != currentKeyHash) {
            Timber.tag(TAG).d("Importing custom private key into Android Keystore")

            // Remove old key if exists
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }

            // Try to import, fallback to direct key usage if it fails
            try {
                importKeySecurely(keyAlias, keyPEM)
                Timber.tag(TAG).d("Successfully imported custom key using Secure Key Import")
                preferencesManager.setCustomKeyHash(currentKeyHash)
            } catch (e: Exception) {
                Timber.tag(TAG).w("Custom key import failed, using direct key: ${e.message}")
                // Fallback to direct key usage
                return Signer.fromKeys(
                    certsPEM = certPEM,
                    privateKeyPEM = keyPEM,
                    algorithm = SigningAlgorithm.ES256,
                    tsaURL = tsaUrl
                )
            }
        }

        Timber.tag(TAG).d("Creating custom signer with KeyStoreSigner")

        // Use the new KeyStoreSigner class
        return KeyStoreSigner.createSigner(
            algorithm = SigningAlgorithm.ES256,
            certificateChainPEM = certPEM,
            keyAlias = keyAlias,
            tsaURL = tsaUrl,
        )
    }

    private suspend fun createRemoteSigner(): Signer {
        val remoteUrl =
            preferencesManager.remoteUrl.first()
                ?: throw IllegalStateException("Remote signing URL not configured")
        val bearerToken = preferencesManager.remoteToken.first()

        val configUrl =
            if (remoteUrl.contains("/api/v1/c2pa/configuration")) {
                remoteUrl
            } else {
                "$remoteUrl/api/v1/c2pa/configuration"
            }

        Timber.tag(TAG).d("Creating WebServiceSigner with URL: $configUrl")

        // Use the new WebServiceSigner class
        val webServiceSigner =
            org.contentauth.c2pa.WebServiceSigner(
                configurationURL = configUrl,
                bearerToken = bearerToken
            )

        return webServiceSigner.createSigner()
    }

    private fun createKeystoreKey(alias: String, useHardware: Boolean) {
        val keyPairGenerator =
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)

        val paramSpec =
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .apply {
                    setDigests(KeyProperties.DIGEST_SHA256)
                    setAlgorithmParameterSpec(
                        ECGenParameterSpec("secp256r1"),
                    )

                    if (useHardware) {
                        // Request hardware backing (StrongBox if available, TEE otherwise)
                        setIsStrongBoxBacked(true)
                    }

                    // Self-signed certificate validity
                    setCertificateSubject(
                        X500Principal("CN=C2PA Android User, O=C2PA Example, C=US"),
                    )
                    setCertificateSerialNumber(
                        BigInteger.valueOf(System.currentTimeMillis()),
                    )
                    setCertificateNotBefore(Date())
                    setCertificateNotAfter(
                        Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000),
                    )
                }
                .build()

        keyPairGenerator.initialize(paramSpec)
        keyPairGenerator.generateKeyPair()
    }

    private suspend fun enrollHardwareKeyCertificate(alias: String): String {

        // Generate CSR for the hardware key
        val csr = generateCSR(alias)

        // Submit CSR to signing server
        val csrResp = CertificateSigningService().signCSR(csr)
        val certChain = csrResp.certificate_chain
        val certId = csrResp.certificate_id

        Timber.tag(TAG).d("Certificate enrolled successfully. ID: $certId")

        return certChain
    }

    private fun generateCSR(alias: String): String {
        try {
            // Use the library's CertificateManager to generate a proper CSR
            val config =
                CertificateManager.CertificateConfig(
                    commonName = "Proofmode C2PA Hardware Key",
                    organization = "Proofmode.org",
                    organizationalUnit = "Mobile",
                    country = "US",
                    state = "New York",
                    locality = "New York",
                )

            // Generate CSR using the library
            val csr = CertificateManager.createCSR(alias, config)

            Timber.tag(TAG).d("Generated proper CSR for alias $alias")
            return csr
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to generate CSR")
            throw RuntimeException("Failed to generate CSR: ${e.message}", e)
        }
    }

    /** Import key using Secure Key Import (API 28+) Throws exception if import fails */
    private fun importKeySecurely(keyAlias: String, privateKeyPEM: String) {
        try {
            Timber.tag(TAG).d("Starting key import for alias: $keyAlias")
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)

            // Parse the private key from PEM
            val privateKeyBytes = parsePrivateKeyFromPEM(privateKeyPEM)
            val keyFactory = KeyFactory.getInstance("EC")
            val privateKey =
                keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes)) as
                    ECPrivateKey

            Timber.tag(TAG).d("Private key parsed, algorithm: ${privateKey.algorithm}")

            // Create wrapping key for import (using ENCRYPT/DECRYPT which is more widely supported)
            val wrappingKeyAlias = "${keyAlias}_WRAPPER_TEMP"

            // Clean up any existing wrapper key
            if (keyStore.containsAlias(wrappingKeyAlias)) {
                keyStore.deleteEntry(wrappingKeyAlias)
            }

            // Generate RSA wrapping key with ENCRYPT purpose (more compatible than WRAP_KEY)
            val keyGenSpec =
                KeyGenParameterSpec.Builder(
                    wrappingKeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(2048)
                    .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                    .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
                    .build()

            val keyPairGenerator =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
            keyPairGenerator.initialize(keyGenSpec)
            val wrappingKeyPair = keyPairGenerator.generateKeyPair()
            Timber.tag(TAG).d("Wrapping key generated")

            // Get the public key for wrapping
            val publicKey = wrappingKeyPair.public

            // Wrap the private key
            val cipher = Cipher.getInstance("RSA/ECB/OAEPPadding")
            cipher.init(Cipher.WRAP_MODE, publicKey)
            val wrappedKeyBytes = cipher.wrap(privateKey)
            Timber.tag(TAG).d("Key wrapped, bytes length: ${wrappedKeyBytes.size}")

            // Import using WrappedKeyEntry
            val importSpec =
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(
                        ECGenParameterSpec("secp256r1"),
                    )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()

            val wrappedKeyEntry =
                WrappedKeyEntry(
                    wrappedKeyBytes,
                    wrappingKeyAlias,
                    "RSA/ECB/OAEPPadding",
                    importSpec,
                )

            keyStore.setEntry(keyAlias, wrappedKeyEntry, null)
            Timber.tag(TAG).d("Key imported to keystore")

            // Clean up wrapping key
            keyStore.deleteEntry(wrappingKeyAlias)

            // Verify import
            if (keyStore.containsAlias(keyAlias)) {
                Timber.tag(TAG).d("Key successfully imported and verified in keystore")
            } else {
                throw IllegalStateException("Key not found after import")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Key import failed")
            Timber.tag(TAG).e("Exception: ${e.javaClass.name}: ${e.message}")
            // Don't generate a wrong key - just fail and let the caller handle it
            throw IllegalStateException(
                "Failed to import key using Secure Key Import: ${e.message}",
                e,
            )
        }
    }

    /** Parse private key from PEM format */
    private fun parsePrivateKeyFromPEM(pem: String): ByteArray {
        val pemContent =
            pem.replace("-----BEGIN EC PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END EC PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")

        return Base64.decode(pemContent, Base64.NO_WRAP)
    }

    private fun signStream(sourceStream: Stream, contentType: String, destStream: Stream, manifestJSON: String, signer: Signer) {
        Timber.tag(TAG).d("Creating Builder from JSON")
        val builder = Builder.fromJson(manifestJSON)

        try {
            builder.sign(
                format = contentType,
                source = sourceStream,
                dest = destStream,
                signer = signer,
            )

            Timber.tag(TAG).d("builder.sign() completed successfully")

        } catch (e: Exception) {
            Timber.tag(TAG).e("Error message: ${e.message}")
            throw e
        } finally {
            // Make sure to close streams
            Timber.tag(TAG).d("Closing streams")
            sourceStream.close()
            destStream.close()
        }
    }

    private fun createManifestJSON(context: Context, creator: String, fileName: String, contentType: String, location: Location?, isDirectCapture: Boolean): String {

        val appLabel = getAppName(context)
        val appVersion = getAppVersionName(context)

        val softwareAgent = SoftwareAgent("$appLabel $appVersion", Build.VERSION.SDK_INT.toString(), Build.VERSION.CODENAME)

        val currentTs = iso8601.format(Date())
        val thumbnailId = "$fileName-thumb.jpg"

        val mb = ManifestBuilder()
        mb.claimGenerator(appLabel, version = appVersion)
        mb.timestampAuthorityUrl(TimestampAuthorities.DIGICERT)
      //  mb.timestampAuthorityUrl(TSA_SSL_COM)
        mb.title(fileName)
        mb.format(contentType)
        //   mb.addThumbnail(Thumbnail(C2PAFormats.JPEG, thumbnailId))

        val sAgent = SoftwareAgent(appLabel, appVersion, Build.PRODUCT)

        if (isDirectCapture)
        {
            //add created
            mb.addAction(Action(C2PAActions.CREATED, currentTs, softwareAgent, digitalSourceType = DigitalSourceTypes.DIGITAL_CAPTURE))
        }
        else
        {
            //add placed
            mb.addAction(Action(C2PAActions.PLACED, currentTs, softwareAgent))

        }

        val ingredient = Ingredient(
            title = fileName,
            format = C2PAFormats.JPEG,
            relationship = C2PARelationships.PARENT_OF,
            //thumbnail = Thumbnail(C2PAFormats.JPEG, thumbnailId)
        )

        mb.addIngredient(ingredient)

        val attestationBuilder = AttestationBuilder()

        attestationBuilder.addCreativeWork {
            addAuthor(creator)
            dateCreated(Date())
        }
        if (location  != null) {
            val exifLat = getLatitudeAsDMS(location, 3)
            val exifLong = getLongitudeAsDMS(location, 3)

            val locationJson = JSONObject().apply {
                put("@type", "Place")
                put("latitude", exifLat)
                put("longitude", exifLong)
                //    put("name", "Somewhere")
            }

            attestationBuilder.addAssertionMetadata {
                dateTime(currentTs)
                location(locationJson)
            }


        }



        /**
        val customAttestationJson = JSONObject().apply {
        put("@type", "Integrity")
        put("nonce", "something")
        put("response", "b64encodedresponse")
        }

        attestationBuilder.addCustomAttestation("app.integrity", customAttestationJson)

        attestationBuilder.addCAWGIdentity {
            validFromNow()
            addSocialMediaIdentity(pgpFingerprint, webLink, currentTs, appLabel, appLabel)
        }
         **/



        attestationBuilder.buildForManifest(mb)

        val manifestJson = mb.buildJson()


        return manifestJson
    }

    private fun verifySignedImage(imageData: ByteArray) {
        try {
            // Create a temporary file for verification
            val tempFile = File.createTempFile("verify", ".jpg", context.cacheDir)
            tempFile.writeBytes(imageData)

            // Read and verify using C2PA
            val manifestJSON = C2PA.readFile(tempFile.absolutePath, null)

            Timber.tag(TAG).d("C2PA VERIFICATION SUCCESS")
            Timber.tag(TAG).d("Manifest JSON length: ${manifestJSON.length} characters")

            // Parse and log key information
            val manifest = JSONObject(manifestJSON)
            manifest.optJSONObject("active_manifest").let { activeManifest ->
                Timber.tag(TAG).d("Active manifest found")
                activeManifest?.optString("claim_generator").let {
                    Timber.tag(TAG).d("Claim generator: $it")
                }
                activeManifest?.optString("title")?.let { Timber.tag(TAG).d("Title: $it") }
                activeManifest?.optJSONObject("signature_info")?.let { sigInfo ->
                    Timber.tag(TAG).d("Signature info present")
                    sigInfo.optString("alg").let { Timber.tag(TAG).d("Algorithm: $it") }
                    sigInfo.optString("issuer").let { Timber.tag(TAG).d("Issuer: $it") }
                }
            }

            // Clean up temp file
            tempFile.delete()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "C2PA VERIFICATION FAILED")
        }
    }

    private fun createLocationAssertion(location: Location): JSONObject {
        val timestamp = formatIsoTimestamp(Date(location.time))
        val metadata =
            JSONObject().apply {
                put("exif:GPSLatitude", location.latitude.toString())
                put("exif:GPSLongitude", location.longitude.toString())
                put("exif:GPSAltitude", location.altitude.toString())
                put("exif:GPSTimeStamp", timestamp)
                put(
                    "@context",
                    JSONObject().apply { put("exif", "http://ns.adobe.com/exif/1.0/") },
                )
            }
        return JSONObject().apply {
            put("label", "c2pa.metadata")
            put("data", metadata)
        }
    }

    private fun formatIsoTimestamp(date: Date): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(date)
    }

    /**
     * Helper functions for getting app name and version
     */
    fun getAppVersionName(context: Context): String {

        var appVersionName = ""
        try {
            appVersionName =
                context.packageManager.getPackageInfo(context.packageName, 0).versionName?:""

        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return appVersionName
    }

    fun getAppName(context: Context): String {
        var applicationInfo: ApplicationInfo? = null
        try {
            applicationInfo = context.packageManager.getApplicationInfo(context.applicationInfo.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.tag("TAG").d(e, "The package with the given name cannot be found on the system.")
        }
        return (if (applicationInfo != null) context.packageManager.getApplicationLabel(applicationInfo) else "Unknown") as String

    }

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val tempFile = File.createTempFile("c2pa_temp_", uri.lastPathSegment, context.cacheDir)
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
}
