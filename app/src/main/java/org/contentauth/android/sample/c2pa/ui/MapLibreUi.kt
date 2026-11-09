package org.contentauth.android.sample.c2pa.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.createMapRuntime
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

@Composable
fun MapUI(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
) {
    val runtime = remember { createMapRuntime() }
    DisposableEffect(runtime) {
        onDispose { runtime.close() }
    }

    val position = remember(latitude, longitude) { Position(longitude, latitude) }

    val mapState = rememberMapState(
        runtime,
        BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
        CameraPosition(
            center = position,
            zoom = 15.0,
        ),
    )

    MaplibreMap(
        modifier = modifier,
        state = mapState,
    ) {
        // Bottom-center of the pin sits on the capture coordinates.
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = "Capture location",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(40.dp)
                .placedAt(position, Alignment.BottomCenter),
        )
    }
}
