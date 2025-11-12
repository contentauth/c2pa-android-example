package com.proofmode.c2pa.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import com.proofmode.c2pa.ui.theme.ProofmodeC2paTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProofmodeC2paTheme {
                ProofAppNavigation()
            }
        }
    }
}



@Composable
fun ProofAppNavigation() {
    val controller = rememberNavController()
    val cameraViewModel: CameraViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()

    NavHost(navController = controller,
        startDestination = Destinations.Camera){
        composable<Destinations.Camera> {
            CameraScreen(viewModel = cameraViewModel, onNavigateToPreview = {
                controller.navigate(Destinations.Preview)
            }, onNavigateToSettings = {
                controller.navigate(Destinations.Settings)
            })

        }

        composable<Destinations.Preview> {
            MediaPreview(viewModel = cameraViewModel, onNavigateBack = {
                controller.popBackStack()
            })
        }
        composable<Destinations.Settings> {
            SettingsScreen(viewModel = settingsViewModel, onNavigateBack = {
                controller.popBackStack()
            })
        }

    }
}

object Destinations {
    @Serializable
    object Camera

    @Serializable
    object Preview

    @Serializable
    object Settings
}


