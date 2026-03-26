package org.contentauth.android.sample.c2pa.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.maplibre.android.geometry.LatLng
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

@Composable
fun MapUI(latLng: LatLng,
          modifier: Modifier = Modifier
){
    val cameraState = rememberCameraState(firstPosition = CameraPosition(
        target = Position(latLng.longitude, latLng.latitude),
        zoom = 15.0
    ))


    MaplibreMap(
        baseStyle = BaseStyle.Uri("https://tiles.openfreemap.org/styles/liberty"),
        modifier = modifier,
        cameraState = cameraState,
        options = MapOptions(
            ornamentOptions = OrnamentOptions.AllEnabled
        )

    )
}