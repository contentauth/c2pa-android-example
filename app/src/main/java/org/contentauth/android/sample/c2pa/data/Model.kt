/**
 * Read-side models for a C2PA manifest.
 *
 * These mirror the JSON that `C2PA.readFile` returns for a signed asset — the view the
 * c2pa-rs native library produces when *verifying*. They are deliberately not the same
 * shape the app *writes*: the manifest handed to `Builder.fromJson` is assembled by hand
 * in `C2PAManager.createManifestJSON`, and c2pa-rs normalises it on the way back out
 * (for example `c2pa.actions` is read back as `c2pa.actions.v2`). Don't assume a field
 * written here appears verbatim on read, or the reverse.
 *
 * Everything is parsed with `ignoreUnknownKeys = true`, so unrecognized fields are
 * ignored and only *missing required* fields break parsing. Fields that different
 * c2pa-rs releases disagree about are therefore nullable with defaults; each one says so.
 */
package org.contentauth.android.sample.c2pa.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The whole document returned for an asset: every manifest in its provenance chain,
 * plus the verification verdict.
 *
 * @property activeManifest key into [manifests] for the manifest that signed this asset.
 *   The others are inherited from ingredients. Look it up rather than taking the first
 *   entry: `manifests[root.activeManifest]`.
 * @property validationState overall verdict, e.g. `Valid` / `Invalid`. [validationResults]
 *   carries the per-check detail behind it.
 */
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

/**
 * One signed claim about an asset.
 *
 * Most of what the UI shows lives in [assertions] rather than in named fields here; see
 * [extractMetadata], which pulls out the handful of assertions this app understands.
 *
 * @property claimGenerator legacy free-text generator string. [claimGeneratorInfo] is the
 *   structured form and the one to prefer.
 * @property label this manifest's own id, matching its key in [ManifestRoot.manifests].
 */
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

/**
 * The software that produced a claim — for this app, its own name and version.
 *
 * @property c2paRs the c2pa-rs version, which the native library stamps in alongside the
 *   app's own entry. Older releases spelled the key `org.cai.c2pa_rs`, so it is optional
 *   and simply reads null for assets signed with one of those.
 */
@Serializable
data class ClaimGeneratorInfo(
    val name: String,
    val version: String,

    @SerialName("org.contentauth.c2pa_rs")
    val c2paRs: String? = null
)

/**
 * A source asset this one was derived from.
 *
 * @property relationship how it relates to the asset, e.g. `parentOf`.
 * @property instanceId not emitted by current c2pa-rs releases on read, so nullable.
 * @property label the ingredient assertion's own id, e.g. `c2pa.ingredient.v2`. Absent on
 *   manifests written by older releases.
 */
@Serializable
data class Ingredient(
    val title: String,
    val format: String,

    @SerialName("instance_id")
    val instanceId: String? = null,

    val relationship: String,
    val label: String? = null
)

/**
 * One assertion: a labeled piece of signed metadata.
 *
 * [data] stays a raw [JsonElement] on purpose — its shape is defined by [label], and the
 * labels in one manifest have nothing in common structurally. Interpret it by label
 * rather than trying to give this class a typed payload; [extractMetadata] does that for
 * the three labels this app reads.
 *
 * Note [data] is not always an object. The `cawg.training-mining` assertion is a
 * [JsonObject] in current captures but a JSON-encoded *string* in ones signed by older
 * builds of this app, so both are handled.
 */
@Serializable
data class Assertion(
    val label: String,
    val data: JsonElement
)

/**
 * Who signed the claim and with what.
 *
 * @property alg signing algorithm as c2pa-rs spells it, e.g. `Es256` — note the casing,
 *   which is not the `ES256` of the C2PA spec.
 * @property time the timestamp authority's countersignature time, UTC ISO-8601 with an
 *   explicit offset. Render it with [toLocalizedDateTime].
 */
@Serializable
data class SignatureInfo(
    val alg: String,
    val issuer: String,

    @SerialName("cert_serial_number")
    val certSerialNumber: String,

    val time: String
)

/**
 * Verification results, grouped by the manifest they belong to.
 *
 */
@Serializable
data class ValidationResults(
    @SerialName("activeManifest")
    val activeManifest: ActiveManifest
)

/**
 * Per-check results for the active manifest. All three lists default to empty, since
 * which of them c2pa-rs emits varies with the asset and the release.
 */
@Serializable
data class ActiveManifest(
    val success: List<ValidationItem> = emptyList(),
    val informational: List<ValidationItem> = emptyList(),
    val failure: List<ValidationItem> = emptyList()
)

/**
 * A single verification check.
 *
 * @property code the C2PA status code, e.g. `claimSignature.validated`.
 * @property url a JUMBF URI pointing at the part of the manifest the check covered — an
 *   internal reference, not something to open.
 */
@Serializable
data class ValidationItem(
    val code: String,
    val url: String,
    val explanation: String
)

/**
 * The flattened view of a manifest that the Content Credentials UI renders.
 *
 * Unlike everything above, this is not a wire type — it is assembled by
 * [extractMetadata] from several assertions, which is why it is not [Serializable].
 * Every field is nullable because each depends on an assertion that may be absent: no
 * location assertion is written when location sharing is off, for instance.
 *
 * @property lat decimal degrees, converted from the DMS string in the assertion.
 * @property id the author's identifier — their email, from the creative work assertion.
 */
data class ManifestMetadata(
    val dateTime: String?,
    val lat: Double?,
    val lng: Double?,
    val author: String?,
    val id: String?,
    val cawgTrainingMining: CawgTrainingMining?
)

/**
 * The CAWG training and mining assertion: whether this asset may be used to train or
 * run AI models.
 *
 * @property entries keyed by CAWG use, e.g. `cawg.ai_inference` and
 *   `cawg.ai_generative_training`.
 */
@Serializable
data class CawgTrainingMining(
    val entries: Map<String, CawgEntry>
)

/** One CAWG entry, whose [use] is `allowed` or `notAllowed`. */
@Serializable
data class CawgEntry(
    val use: String
)

/**
 * Pulls the fields the UI shows out of a manifest's assertions.
 *
 * Reads three labels and ignores the rest:
 *  - `c2pa.assertion.metadata` — capture time and location
 *  - `c2pa.creative_work` — author name and identifier
 *  - `cawg.training-mining` — the AI use entries
 *
 * Nothing here is required: a missing or unparsable assertion leaves its fields null
 * rather than failing, so a manifest written by another tool still renders what it can.
 */
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
    // The data is a JSON object. Assets signed by older builds of this app stored it
    // as a JSON-encoded string instead, so that form is still accepted when reading.
    manifest.assertions
        .firstOrNull { it.label == "cawg.training-mining" }
        ?.data
        ?.let { element ->
            val json = Json { ignoreUnknownKeys = true }
            cawgTrainingMining = try {
                when (element) {
                    is JsonObject -> json.decodeFromJsonElement<CawgTrainingMining>(element)
                    is JsonPrimitive -> json.decodeFromString<CawgTrainingMining>(element.content)
                    else -> null
                }
            } catch (e: Exception) {
                null
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

/**
 * Converts a DMS latitude from the location assertion to decimal degrees.
 *
 * The assertion stores coordinates the way EXIF writes them — `34°58'40.16" N` — because
 * that is the form `C2PAManager` puts there; the map needs decimal degrees.
 *
 * Returns null when the string is not in that form, so an unreadable coordinate is
 * treated as absent rather than as a real position.
 */
fun String.toDecimalLatitude(): Double? {
    val regex = """(\d+)°(\d+)'([\d.]+)"\s*([NS])""".toRegex()
    val match = regex.find(this) ?: return null
    val (deg, min, sec, dir) = match.destructured
    var decimal = deg.toDouble() + min.toDouble() / 60 + sec.toDouble() / 3600
    if (dir == "S") decimal = -decimal
    return decimal
}

/**
 * Converts a DMS longitude from the location assertion to decimal degrees.
 *
 * As with [toDecimalLatitude], returns null when the string is not in that form. This
 * matters: `ManifestScreen` decides whether to show the map by null-checking the pair,
 * so a 0.0 fallback would put the marker at (0, 0) in the Gulf of Guinea instead of
 * hiding the map.
 */
fun String.toDecimalLongitude(): Double? {
    val regex = """-?(\d+)°(\d+)'([\d.]+)"\s*([EW])""".toRegex()
    val match = regex.find(this) ?: return null
    val (deg, min, sec, dir) = match.destructured
    var decimal = deg.toDouble() + min.toDouble() / 60 + sec.toDouble() / 3600
    if (dir == "W") decimal = -decimal
    return decimal
}


/**
 * Renders a C2PA timestamp in the device's own time zone and locale.
 *
 * Timestamps in a manifest are UTC ISO-8601, written either with a `Z` suffix
 * (`2026-10-30T03:22:39Z`, assertion metadata) or an explicit offset
 * (`2026-10-30T03:18:48+00:00`, signature time). Both parse as an [OffsetDateTime].
 *
 * Returns null if the value is not a timestamp this understands, so callers can fall
 * back to showing it verbatim rather than hiding it.
 */
fun String.toLocalizedDateTime(): String? = try {
    OffsetDateTime.parse(this)
        .atZoneSameInstant(ZoneId.systemDefault())
        .format(
            DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
        )
} catch (e: DateTimeParseException) {
    null
}
