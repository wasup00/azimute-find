# Azimute find

Android app (Kotlin + Jetpack Compose + Material 3) that shows a satellite map with a true-north compass and up to four azimuth lines.

- Hybrid / satellite / terrain / map layers (Google Maps SDK via Maps Compose)
- Compass rose at the map center, rotating with the phone (rotation-vector sensor, corrected for magnetic declination)
- Lock a point (your position or the map center): azimuth lines are drawn from it while you keep moving the map
- Up to 4 azimuths, typed in degrees, each with its own color on the compass and on the map

## Build

Requirements: Android Studio (AGP 9), JDK 17+, a device or emulator with Google Play services.

1. Create a Google Maps API key with the **Maps SDK for Android** enabled, restricted to package `com.watsup.azimute_find` and your signing SHA-1.
2. Add it to `local.properties` (not committed):
   ```
   MAPS_API_KEY=your_key
   ```
3. Run the `app` configuration, or `./gradlew installDebug`.

Unit tests: `./gradlew testDebugUnitTest`.

## Releases

Pushing a tag like `v1.2.0` runs [.github/workflows/release.yml](.github/workflows/release.yml): it runs the unit tests, builds a signed release APK (version name from the tag, version code from the run number) and publishes a GitHub release with `azimute-find-1.2.0.apk` attached.

```
git tag v1.2.0
git push origin v1.2.0
```

Repository secrets used: `MAPS_API_KEY`, `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
