# ProCam

ProCam is a Kotlin/Jetpack Compose Android camera application with an authenticated private area. Package: `com.procam.camera`.

## Requirements

- Android Studio (current stable)
- JDK 17 or newer (Gradle Android plugin uses Java 17 toolchain)
- Android SDK Platform 35 and a compatible Build Tools installation
- A physical Android device is recommended for camera and biometric testing

## Open and build

1. Extract `ProCam-source.zip`.
2. Open the `ProCam` directory in Android Studio.
3. Allow Gradle sync and install any SDK components Android Studio requests.
4. Connect an Android 8.0+ device, enable USB debugging, and press **Run**.
5. To create a debug APK, choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**. Android Studio writes it under `app/build/outputs/apk/debug/`.
6. For a release build, create a signing key in **Build > Generate Signed Bundle / APK**. Keep the keystore and passwords private; never commit them to source control.

Command line (after Android SDK and Gradle are configured):

```bash
gradle :app:assembleDebug
gradle :app:assembleRelease
```

## Implemented functionality

- CameraX live preview, rear/front switch, still capture, video recording, flash on/off, zoom and optional grid.
- Configurable 3–10 second secret shutter hold (default 5 seconds); an early release captures a normal photo.
- PIN setup and verification using a salted PBKDF2-HMAC-SHA256 verifier. The PIN itself is not persisted.
- Optional AndroidX BiometricPrompt authentication with device-credential fallback.
- Android Keystore AES-256-GCM encryption with a fresh nonce per media item and streaming I/O.
- Photo/video import using Android's document picker; encrypted app-private storage; list, rename, export, trash, restore and best-effort permanent deletion.
- Room metadata database with migration from the initial schema.
- Persistent private app selection, installed-app discovery through a launcher intent query, app launching and removal.
- DataStore-backed preferences for biometric toggle, lock delay, shutter hold duration, grid, haptics and theme.
- Screenshot protection while Secret Space is open. Leaving ProCam locks the session on return; a background timeout is also applied.

## Platform and security limitations

- Android does not let an ordinary app hide or freeze other installed packages from the system launcher, Settings, notifications, Recents or system search. ProCam's launcher is a private in-app list only.
- Exported files are ordinary unencrypted copies. The source remains encrypted in the vault.
- Previewing a vault item decrypts a temporary cache copy so another Android viewer can open it. Cache copies are cleaned only by normal app/cache lifecycle; do not use this preview path for unusually sensitive content on a shared device.
- Deletion overwrites the encrypted file on a best-effort basis before unlinking it. Flash storage, wear levelling, snapshots and backups can retain physical remnants; forensic unrecoverability is not guaranteed.
- Biometric availability depends on device hardware, enrollment and Android security configuration. PIN remains the fallback.
- The current app launcher intentionally uses PackageManager launch intents; it does not clone, modify, disable or alter other apps.

## Permissions

- Camera: required for camera preview and capture.
- Microphone: requested only when the user starts video recording with audio.
- No broad storage permission is requested. User-selected media is accessed through Android's document picker.

## Manual test checklist

1. Grant camera access and verify the live preview.
2. Tap the shutter once; verify a photo appears in `Pictures/ProCam`.
3. Hold the shutter for five seconds; verify no photo is captured and authentication opens.
4. Release before five seconds; verify a normal photo is captured.
5. Create a PIN, close and reopen Secret Space, and verify incorrect/correct PIN behavior.
6. Enable biometrics and verify BiometricPrompt on a device with enrolled biometrics.
7. Import one image and one video; verify they appear in Private Media and files in `files/private_vault` are encrypted.
8. Export each item through the Android document picker and verify the exported copy opens.
9. Rename an item, move it to Trash, restore it, then permanently delete a test item.
10. Add an installed app to Private Apps, leave Secret Space, reopen it and verify the selection persists; launch it and return with Android navigation.
11. Try a selected app after uninstalling it; verify the app reports that it is unavailable.
12. Change auto-lock settings, background ProCam, and verify timeout/return locking.
13. Rotate the device, switch cameras, deny permissions and retry.

## Build verification status

This source package was updated in the ChatGPT workspace. It has **not** been compiled or run against an Android SDK/device in this environment, so a successful APK build and runtime test are not claimed. Complete the build and checklist above in Android Studio before using it with real private data.
