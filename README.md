# C2PA Android example app

The [c2pa-android-example repository](https://github.com/contentauth/c2pa-android-example) contains a prototype Android application demonstrating how to capture photos and videos, embed Content Credentials, and then sign them.

The app is built using modern Android development practices with Jetpack Compose and CameraX.

## Key Features

- **Photo and video capture**: Uses CameraX for a robust camera implementation.
- **C2PA signing**: Signs captured media with ES256 (EC P-256) keys to create a C2PA manifest, ensuring content authenticity.
- **Multiple signing modes**: Android Keystore, hardware-backed (StrongBox/TEE), a user-supplied certificate and key, or a remote signing service.
- **In-place signing**: Correctly handles modern Android Scoped Storage by signing files from `content://` URIs in-place using a temporary file strategy.
- **Location tagging**: Fetches the device's current location and embeds it into the C2PA manifest, and shows it on a map when viewing the credentials.
- **AI training opt-out**: Writes a CAWG training/mining assertion recording whether the media may be used for AI training and inference.
- **Video recording control**: Supports starting, pausing, resuming, and stopping video recordings, with the UI reacting to the current state.
- **Runtime permissions**: Gracefully handles camera, audio, and location permissions.

## Requirements

- JDK 17 or newer (required by Android Gradle Plugin 9).
- Android SDK with API 37 installed. The app targets and compiles against SDK 37, and runs on API 28+.

## Tech stack and core libraries

- UI: 100% Jetpack Compose for a declarative and modern UI.
- Architecture: MVVM (Model-View-ViewModel).
- Dependency Injection: Hilt for managing dependencies.
- Camera: CameraX for camera operations, including preview, image capture, and video capture.
- Content Authenticity: the [c2pa-android](https://github.com/contentauth/c2pa-android) library for creating and embedding authenticity manifests, consumed from JitPack (`com.github.contentauth:c2pa-android`).
- Asynchronicity: Kotlin Coroutines and Flow for managing background tasks and reactive data streams.
- Permissions: Accompanist Permissions for a clean, composable-based permissions handling flow.
- Location: Google Play Services Fused Location Provider for accurate and efficient location fetching.
- Image Loading: Coil for displaying the thumbnail preview.
- MapLibre Compose: For showing location of captured media.
- Bouncy Castle: For generating the self-signed certificate chain used by the default signing mode.

## Project Architecture

The application's logic is centered around a few key files that demonstrate a clean separation of concerns.

### UI layer (/ui)

**[CameraScreen.kt](https://github.com/contentauth/c2pa-android-example/blob/main/app/src/main/java/org/contentauth/android/sample/c2pa/ui/CameraScreen.kt)**:

The main entry point for the camera UI that handles all runtime permission requests (Camera, Audio, Location):
- Displays either a permission request screen or the main `CameraCaptureScreen` that:
  - Observes state from `CameraViewModel` (`recordingState`, `thumbPreviewUri`).
  - Displays the `CameraXViewfinder` for the live camera preview. Provides `IconButton` controls for taking photos, starting/pausing/resuming/stopping video, and navigating to a media preview.
  - The thumbnail preview dynamically updates by observing `StateFlow` of the `thumbPreviewUri`.
- Hosts the settings `ModalBottomSheet`, so settings open over the live preview rather than replacing it. This keeps the camera bound while settings are open.

**[Settings.kt](https://github.com/contentauth/c2pa-android-example/blob/main/app/src/main/java/org/contentauth/android/sample/c2pa/ui/Settings.kt)**:

`SettingsViewModel` plus `SettingsSheetContent`, the rows shown in the bottom sheet. Settings (name, email, location sharing, AI training) are persisted to DataStore through `IPreferencesManager`, independently of the camera.

**[MediaPreview.kt](https://github.com/contentauth/c2pa-android-example/blob/main/app/src/main/java/org/contentauth/android/sample/c2pa/ui/MediaPreview.kt)** and **[ManifestScreen.kt](https://github.com/contentauth/c2pa-android-example/blob/main/app/src/main/java/org/contentauth/android/sample/c2pa/ui/ManifestScreen.kt)**:

A pager over captured media, with a Content Credentials sheet that reads the signed manifest back and renders it — including the capture location on a MapLibre map with a marker, and timestamps formatted in the device's time zone and locale.

### ViewModel layer (/ui)

**[CameraViewModel.kt](https://github.com/contentauth/c2pa-android-example/blob/main/app/src/main/java/org/contentauth/android/sample/c2pa/ui/CameraViewModel.kt):**

The core of the application's logic; acts as the bridge between the UI and the data/domain layers and:
- Manages the camera's lifecycle via `bindToCamera()`.
- Handles user actions like `takePhoto()`, `captureVideo()`, `flipCamera()` and `tapToFocus()`.
- Manages the recording state through the `RecordingState` sealed class, exposing it as a `StateFlow` for the UI to observe.
- Fetches the device's location before a capture, when location sharing is enabled.
- After a capture is saved, it triggers signing through `C2PAManager`.
- Reads a signed manifest back for display via `readManifest()`.

### C2PA signing logic (/c2pa_signing)

**[C2PAManager.kt](https://github.com/contentauth/c2pa-android-example/blob/main/app/src/main/java/org/contentauth/android/sample/c2pa/c2pa_signing/C2PAManager.kt):**

Contains the `signMediaFile()` function, which is the heart of the content signing process:
- Scoped Storage Solution: To sign a file from a `content://` URI, it first creates a temporary file in the app's cache, signs that file in-place, and then writes the modified (signed) file back to the original URI.
- Constructs the C2PA manifest JSON, adding assertions for the action (`c2pa.created` or `c2pa.placed`), software agent, digital source type, author, location, and CAWG training/mining preferences.
- Builds a `Signer` for the selected `SigningMode`, timestamped against DigiCert's TSA.

**[SigningMode.kt](https://github.com/contentauth/c2pa-android-example/blob/main/app/src/main/java/org/contentauth/android/sample/c2pa/c2pa_signing/SigningMode.kt)**: the four supported modes — `KEYSTORE` (default), `HARDWARE`, `CUSTOM` and `REMOTE`.

**[selfsign/CertificateSigningService.kt](https://github.com/contentauth/c2pa-android-example/blob/main/app/src/main/java/org/contentauth/android/sample/c2pa/c2pa_signing/selfsign/CertificateSigningService.kt)**: a local stand-in for a certificate authority. It builds a self-signed root and intermediate chain with Bouncy Castle and signs CSRs from the app, so the demo can produce valid certificates without a real CA.

**[Utils.kt](https://github.com/contentauth/c2pa-android-example/blob/main/app/src/main/java/org/contentauth/android/sample/c2pa/c2pa_signing/Utils.kt)**: signing-adjacent helpers — `getLatitudeAsDMS()` and `getLongitudeAsDMS()` format coordinates the way the location assertion stores them, and `shareMedia()` shares a captured file.

### Data layer (/data)

**[Model.kt](https://github.com/contentauth/c2pa-android-example/blob/main/app/src/main/java/org/contentauth/android/sample/c2pa/data/Model.kt)**: the serializable models for a manifest read back from a signed asset, plus `extractMetadata()`, which pulls the fields the Content Credentials screen displays out of the manifest's assertions.

### General utilities (/utils)

**[Utils.kt](https://github.com/contentauth/c2pa-android-example/blob/main/app/src/main/java/org/contentauth/android/sample/c2pa/utils/Utils.kt)**:

- `getCurrentLocation()`: A suspend function that uses the Fused Location Provider to fetch the device's location one time.
- `getMediaFlow()`: A function that queries the Android MediaStore to retrieve all photos and videos created by the app, exposing them as a Kotlin Flow.
- `getMedia()`: The one-shot equivalent of `getMediaFlow()`.
