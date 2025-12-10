package com.proofmode.c2pa.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class ManifestRoot(
    @SerialName("active_manifest")
    val activeManifest: String,

    val manifests: Map<String, Manifest>,

    @SerialName("validation_results")
    val validationResults: ValidationResults,

    @SerialName("validation_state")
    val validationState: String
)

@Serializable
data class Manifest(
    @SerialName("claim_generator")
    val claimGenerator: String,

    @SerialName("claim_generator_info")
    val claimGeneratorInfo: List<ClaimGeneratorInfo>,

    val title: String,
    val format: String,

    @SerialName("instance_id")
    val instanceId: String,

    val ingredients: List<Ingredient> = emptyList(),

    val assertions: List<Assertion> = emptyList(),

    @SerialName("signature_info")
    val signatureInfo: SignatureInfo,

    val label: String
)

@Serializable
data class ClaimGeneratorInfo(
    val name: String,
    val version: String,

    @SerialName("org.cai.c2pa_rs")
    val c2paRs: String
)

@Serializable
data class Ingredient(
    val title: String,
    val format: String,

    @SerialName("instance_id")
    val instanceId: String,

    val relationship: String,
    val label: String
)

@Serializable
data class Assertion(
    val label: String,
    val data: JsonElement
)

@Serializable
data class SignatureInfo(
    val alg: String,
    val issuer: String,

    @SerialName("cert_serial_number")
    val certSerialNumber: String,

    val time: String
)

@Serializable
data class ValidationResults(
    @SerialName("activeManifest")
    val activeManifest: ActiveManifest
)

@Serializable
data class ActiveManifest(
    val success: List<ValidationItem>,
    val informational: List<ValidationItem>,
    val failure: List<ValidationItem>
)

@Serializable
data class ValidationItem(
    val code: String,
    val url: String,
    val explanation: String
)

data class ManifestMetadata(
    val dateTime: String?,
    val lat: Double?,
    val lng: Double?,
    val author: String?,
    val id: String?,
    val cawgTrainingMining: CawgTrainingMining?
)

@Serializable
data class CawgTrainingMining(
    val entries: Map<String, CawgEntry>
)

@Serializable
data class CawgEntry(
    val use: String
)

fun extractMetadata(manifest: Manifest): ManifestMetadata {
    var dateTime: String? = null
    var lat: Double? = null
    var lng: Double? = null
    var author: String? = null
    var id: String? = null
    var cawgTrainingMining: CawgTrainingMining? = null

    // METADATA ASSERTION (dateTime + location)
    manifest.assertions
        .firstOrNull { it.label == "c2pa.assertion.metadata" }
        ?.data
        ?.jsonObject
        ?.let { meta ->
            dateTime = meta["dateTime"]?.jsonPrimitive?.content

            meta["location"]?.jsonObject?.let { loc ->
                val latStr = loc["latitude"]?.jsonPrimitive?.content
                val lngStr = loc["longitude"]?.jsonPrimitive?.content

                lat = latStr?.toDecimalLatitude()
                lng = lngStr?.toDecimalLongitude()
            }
        }

    // CREATIVE WORK ASSERTION (author)
    manifest.assertions
        .firstOrNull { it.label == "c2pa.creative_work" }
        ?.data
        ?.jsonObject
        ?.let { cw ->
            val authorObj = cw["author"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
            author = authorObj
                ?.get("name")
                ?.jsonPrimitive
                ?.content
            id = authorObj
                ?.get("@id")
                ?.jsonPrimitive
                ?.content
        }

    // CAWG TRAINING MINING ASSERTION
    manifest.assertions
        .firstOrNull { it.label == "cawg.training-mining" }
        ?.data
        ?.jsonPrimitive
        ?.content
        ?.let {
            val json = Json { ignoreUnknownKeys = true }
            try {
                cawgTrainingMining = json.decodeFromString<CawgTrainingMining>(it)
            } catch (e: Exception) {
                // handle exception if needed, for now just leave it null
            }
        }

    return ManifestMetadata(
        dateTime = dateTime,
        lat = lat,
        lng = lng,
        author = author,
        id = id,
        cawgTrainingMining = cawgTrainingMining
    )
}

fun String.toDecimalLatitude(): Double {
    val regex = """(\d+)°(\d+)'([\d.]+)"\s*([NS])""".toRegex()
    val match = regex.find(this) ?: return 0.0
    val (deg, min, sec, dir) = match.destructured
    var decimal = deg.toDouble() + min.toDouble() / 60 + sec.toDouble() / 3600
    if (dir == "S") decimal = -decimal
    return decimal
}

fun String.toDecimalLongitude(): Double {
    val regex = """-?(\d+)°(\d+)'([\d.]+)"\s*([EW])""".toRegex()
    val match = regex.find(this) ?: return 0.0
    val (deg, min, sec, dir) = match.destructured
    var decimal = deg.toDouble() + min.toDouble() / 60 + sec.toDouble() / 3600
    if (dir == "W") decimal = -decimal
    return decimal
}

