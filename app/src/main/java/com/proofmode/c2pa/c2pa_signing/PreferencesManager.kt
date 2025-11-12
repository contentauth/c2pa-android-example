package com.proofmode.c2pa.c2pa_signing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by
    preferencesDataStore(name = "c2pa_settings")

/**
 * Manages application preferences for C2PA signing using Jetpack DataStore.
 *
 * This class provides a type-safe, asynchronous API for storing and retrieving user settings
 * related to different C2PA signing modes (e.g., Keystore, Hardware-backed, Custom, Remote).
 * It encapsulates all interactions with the `preferencesDataStore`, exposing settings as reactive
 * [Flow]s and providing `suspend` functions for updates.
 *
 * This manager is designed to be a singleton provided by Hilt, ensuring a single source of
 * truth for all application settings.
 *
 * @param context The application context, required to initialize the `preferencesDataStore`.
 * @constructor Injected by Hilt to provide a singleton instance.
 *
 * Adapted from [ProofMode Android Project](https://gitlab.com/guardianproject/proofmode/proofmode-android/-/blob/dev/android-libproofmode/src/main/java/org/witness/proofmode/c2pa/C2PAManager.kt?ref_type=heads)
 */
class PreferencesManager(private val context: Context): IPreferencesManager {

    companion object {
        val SIGNING_MODE_KEY = stringPreferencesKey("signing_mode")
        val REMOTE_URL_KEY = stringPreferencesKey("remote_url")
        val REMOTE_TOKEN_KEY = stringPreferencesKey("remote_token")
        val CUSTOM_CERT_KEY = stringPreferencesKey("custom_cert")
        val CUSTOM_KEY_KEY = stringPreferencesKey("custom_key")
        val HARDWARE_KEY_ALIAS = stringPreferencesKey("hardware_key_alias")
        val SOFTWARE_CERT_KEY = stringPreferencesKey("software_cert")
        val SOFTWARE_KEY_KEY = stringPreferencesKey("software_key")
        val CUSTOM_KEY_HASH = stringPreferencesKey("custom_key_hash")
        val USER_NAME_KEY = stringPreferencesKey("user_name")
        val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        val LOCATION_SHARING_KEY = stringPreferencesKey("location_sharing")
    }

    override val signingMode: Flow<SigningMode> =
        context.dataStore.data.map { preferences ->
            val mode = preferences[SIGNING_MODE_KEY] ?: SigningMode.KEYSTORE.name
            SigningMode.fromString(mode)
        }

    override suspend fun setSigningMode(mode: SigningMode) {
        context.dataStore.edit { preferences -> preferences[SIGNING_MODE_KEY] = mode.name }
    }

    override val remoteUrl: Flow<String?> =
        context.dataStore.data.map { preferences -> preferences[REMOTE_URL_KEY] }

    override suspend fun setRemoteUrl(url: String) {
        context.dataStore.edit { preferences -> preferences[REMOTE_URL_KEY] = url }
    }

    override val remoteToken: Flow<String?> =
        context.dataStore.data.map { preferences -> preferences[REMOTE_TOKEN_KEY] }

    override suspend fun setRemoteToken(token: String) {
        context.dataStore.edit { preferences -> preferences[REMOTE_TOKEN_KEY] = token }
    }

    override val customCertificate: Flow<String?> =
        context.dataStore.data.map { preferences -> preferences[CUSTOM_CERT_KEY] }

    override suspend fun setCustomCertificate(cert: String) {
        context.dataStore.edit { preferences -> preferences[CUSTOM_CERT_KEY] = cert }
    }

    override val customPrivateKey: Flow<String?> =
        context.dataStore.data.map { preferences -> preferences[CUSTOM_KEY_KEY] }

    override suspend fun setCustomPrivateKey(key: String) {
        context.dataStore.edit { preferences -> preferences[CUSTOM_KEY_KEY] = key }
    }

    override suspend fun clearCustomCertificates() {
        context.dataStore.edit { preferences ->
            preferences.remove(CUSTOM_CERT_KEY)
            preferences.remove(CUSTOM_KEY_KEY)
        }
    }

    override val hardwareKeyAlias: Flow<String?> =
        context.dataStore.data.map { preferences -> preferences[HARDWARE_KEY_ALIAS] }

    override suspend fun setHardwareKeyAlias(alias: String) {
        context.dataStore.edit { preferences -> preferences[HARDWARE_KEY_ALIAS] = alias }
    }

    override val softwareCertificate: Flow<String?> =
        context.dataStore.data.map { preferences -> preferences[SOFTWARE_CERT_KEY] }

    override suspend fun setSoftwareCertificate(cert: String) {
        context.dataStore.edit { preferences -> preferences[SOFTWARE_CERT_KEY] = cert }
    }

    override val softwarePrivateKey: Flow<String?> =
        context.dataStore.data.map { preferences -> preferences[SOFTWARE_KEY_KEY] }

    override suspend fun setSoftwarePrivateKey(key: String) {
        context.dataStore.edit { preferences -> preferences[SOFTWARE_KEY_KEY] = key }
    }

    override val customKeyHash: Flow<String?> =
        context.dataStore.data.map { preferences -> preferences[CUSTOM_KEY_HASH] }

    override suspend fun setCustomKeyHash(hash: String) {
        context.dataStore.edit { preferences -> preferences[CUSTOM_KEY_HASH] = hash }
    }

    override val userName: Flow<String?> =
        context.dataStore.data.map { preferences -> preferences[USER_NAME_KEY] }

    override suspend fun setUserName(name: String) {
        context.dataStore.edit { preferences -> preferences[USER_NAME_KEY] = name }
    }

    override val userEmail: Flow<String?> =
        context.dataStore.data.map { preferences -> preferences[USER_EMAIL_KEY] }

    override suspend fun setUserEmail(email: String) {
        context.dataStore.edit { preferences -> preferences[USER_EMAIL_KEY] = email }
    }

    override val locationSharing: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LOCATION_SHARING_KEY]?.toBoolean() ?: false
    }


    override suspend fun setLocationSharing(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[LOCATION_SHARING_KEY] = enabled.toString()
        }
    }
}



interface IPreferencesManager {
    val signingMode: Flow<SigningMode>
    val remoteUrl: Flow<String?>
    val remoteToken: Flow<String?>
    val customCertificate: Flow<String?>
    val customPrivateKey: Flow<String?>
    val customKeyHash: Flow<String?>
    val hardwareKeyAlias: Flow<String?>
    val softwareCertificate: Flow<String?>
    val softwarePrivateKey: Flow<String?>

    suspend fun setSigningMode(mode: SigningMode)
    suspend fun setRemoteUrl(url: String)
    suspend fun setRemoteToken(token: String)
    suspend fun setCustomCertificate(cert: String)
    suspend fun setCustomPrivateKey(key: String)
    suspend fun clearCustomCertificates()
    suspend fun setHardwareKeyAlias(alias: String)
    suspend fun setSoftwareCertificate(cert: String)
    suspend fun setSoftwarePrivateKey(key: String)
    suspend fun setCustomKeyHash(hash: String)
    val userName: Flow<String?>
    suspend fun setUserName(name: String)
    val userEmail: Flow<String?>
    suspend fun setUserEmail(email: String)
    val locationSharing: Flow<Boolean>
    suspend fun setLocationSharing(enabled: Boolean)
}

