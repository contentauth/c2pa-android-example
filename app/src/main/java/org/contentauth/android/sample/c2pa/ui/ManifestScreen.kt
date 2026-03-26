package org.contentauth.android.sample.c2pa.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.contentauth.android.sample.c2pa.data.Manifest
import org.contentauth.android.sample.c2pa.data.extractMetadata
import java.util.Locale

@Composable
fun InfoRow(label: String, value: String?, modifier: Modifier = Modifier) {
    if (value.isNullOrBlank()) return

    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ManifestDetailsScreen(
    manifest: Manifest,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Extract metadata + location + creative work
    val metadata = remember(manifest) { extractMetadata(manifest) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // ───────────────────────
        // DATE & TIME
        // ───────────────────────
        Text(
            text = metadata.dateTime ?: "No date",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ───────────────────────
        // MAP Libre LOCATION
        // ───────────────────────
        if (metadata.lat != null && metadata.lng != null) {
            val latLng = org.maplibre.android.geometry.LatLng(metadata.lat, metadata.lng)
            MapUI(latLng = latLng,
                modifier = Modifier.fillMaxWidth()
                    .height(220.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // ───────────────────────
        // BASIC MANIFEST INFO
        // ───────────────────────
        InfoRow(label = "Title", value = manifest.title)
        InfoRow(label = "Format", value = manifest.format)
        InfoRow(label = "Claim Generator", value = manifest.claimGenerator)
        InfoRow(label = "Author", value = "${metadata.author}, ${metadata.id?:""}")

        Spacer(modifier = Modifier.height(16.dp))

        // ───────────────────────
        // AI TRAINING
        // ───────────────────────
        Text(
            text = "AI Training/mining",
            style = MaterialTheme.typography.titleMedium
        )
        metadata.cawgTrainingMining?.entries?.forEach {
            val title = it.key.split("cawg.", ignoreCase = true).last().replace("_"," ").capitalize(
                Locale.getDefault())
            val value = it.value.use
            InfoRow(label = title, value = value)
        }
        Spacer(modifier = Modifier.height(16.dp))

        // ───────────────────────
        // INGREDIENTS
        // ───────────────────────
        Text(
            text = "Ingredients",
            style = MaterialTheme.typography.titleMedium
        )

        manifest.ingredients.forEach {
            InfoRow(label = it.title, value = it.format)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ───────────────────────
        // SIGNATURE INFO
        // ───────────────────────
        Text(
            text = "Signature",
            style = MaterialTheme.typography.titleMedium
        )
        InfoRow(label = "Algorithm", value = manifest.signatureInfo.alg)
        InfoRow(label = "Issuer", value = manifest.signatureInfo.issuer)
        InfoRow(label = "Serial Number", value = manifest.signatureInfo.certSerialNumber)
        InfoRow(label = "Time", value = manifest.signatureInfo.time)
    }
}
