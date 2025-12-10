# ProofMode C2PA Demo

This repository contains a prototype Android application demonstrating how to capture photos and videos and sign them with C2PA (Content Authenticity Initiative) credentials.
The app is built using modern Android development practices with Jetpack Compose and CameraX.

## Key Features
- Photo & Video Capture: Uses CameraX for a robust camera implementation.
- C2PA Signing: Signs captured media with RSA keys to create a C2PA manifest, ensuring content authenticity.
- In- Place Signing: Correctly handles modern Android Scoped Storage by signing files from content:// URIs in- place using a temporary file strategy.
- Location Tagging: Fetches the device's current location and embeds it into the C2PA manifest.
- Video Recording Control: Supports starting, pausing, resuming, and stopping video recordings, with the UI reacting to the current state.
- Runtime Permissions: Gracefully handles camera, audio, and location permissions.

## Tech Stack & Core Libraries

- UI: 100% Jetpack Compose for a declarative and modern UI.
- Architecture: MVVM (Model-View-ViewModel).- Dependency Injection: Hilt for managing dependencies.
- Camera: CameraX for camera operations, including preview, image capture, and video capture.
- Content Authenticity: C2PA Android Library for creating and embedding authenticity manifests.
- Asynchronicity: Kotlin Coroutines and Flow for managing background tasks and reactive data streams.
- Permissions: Accompanist Permissions for a clean, composable-based permissions handling flow.
- Location: Google Play Services Fused Location Provider for accurate and efficient location fetching.
- Image Loading: Coil for displaying the thumbnail preview.
- Maps Compose: For showing location of captured media(ensure to get a Google maps key and place it in local.defaults.properties and app/secrets.properties)

## Project Architecture
The application's logic is centered around a few key files that demonstrate a clean separation of concerns.

### 1. UI Layer (/ui)

**[CameraScreen.kt](./app/src/main/java/com/proofmode/c2pa/ui/CameraScreen.kt)**:
- The main entry point for the camera UI.
- Handles all runtime permission requests (Camera, Audio, Location).
- Displays either a permission request screen or the main CameraCaptureScreen.
- CameraCaptureScreen (within CameraScreen.kt):
- Observes state from CameraViewModel (recordingState, thumbPreviewUri).
- Displays the CameraXViewfinder for the live camera preview.Provides IconButton controls for taking photos, starting/pausing/resuming/stopping video, and navigating to a media preview.
- The thumbnail preview dynamically updates by observing the thumbPreviewUri StateFlow.

### 2. ViewModel Layer (/ui)

**[CameraViewModel.kt](./app/src/main/java/com/proofmode/c2pa/ui/CameraViewModel.kt):**
- The core of the application's logic, acting as the bridge between the UI and the data/domain layers.
- Manages the camera's lifecycle via bindToCamera.- Handles user actions like takePhoto() and captureVideo().
- Manages the recording state through the RecordingState sealed class, exposing it as a StateFlow for the UI to observe.
- Fetches the device's location using getCurrentLocation() before a capture.
- After a capture is saved, it triggers the signWithC2PA() function. 

### 3. C2PA Signing Logic (/c2pa)

**[Utils.kt](./app/src/main/java/com/proofmode/c2pa/c2pa/Utils.kt):**
- Contains the signWithC2PA() function, which is the heart of the content signing process.
- Scoped Storage Solution: To sign a file from a content:// URI, it first creates a temporary file in the app's cache, signs that file in-place, and then writes the modified (signed) file back to the original URI.
- Constructs the C2PA manifest, adding claims for the action (c2pa.created), software agent, and location data.
- Configures the SignerInfo object using RSA keys stored locally as PEM files.

### 3. General Utilities (/utils)

**[Utils.kt](./app/src/main/java/com/proofmode/c2pa/utils/Utils.kt)**:
- getOrGenerateKeyPair(), saveKeyToPem(), etc.: A set of functions for generating an RSA KeyPair and saving it to disk in the standard PEM format.
- readPemString(): A robust function to read PEM files, correctly stripping headers/footers.
- getCurrentLocation(): A suspend function that uses the Fused Location Provider to fetch the device's location one time.
- getMediaFlow(): A function that queries the Android MediaStore to retrieve all photos and videos created by the app, exposing them as a Kotlin Flow.

