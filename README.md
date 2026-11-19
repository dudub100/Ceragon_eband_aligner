# E-band Aligner (Android)

Camera + sensor antenna alignment tool with live RSL polling from the radio over REST.

## Build the APK

Option A: GitHub (no install needed)
1. Create a new GitHub repository and upload this folder's contents.
2. Open the Actions tab; the "Build APK" workflow runs automatically.
3. When it finishes, download `eband-aligner-apk` from the run's Artifacts.

Option B: Android Studio
1. File > Open > select this folder. Let Gradle sync.
2. Build > Build App Bundle(s) / APK(s) > Build APK(s).
3. APK is in `app/build/outputs/apk/debug/`.

The first build downloads OpenCV.js and Chart.js into `app/src/main/assets/www/lib/`
so the app works offline at site.

## Using it
Open "Radio connection", set IP, username and password (default admin / admin),
the RSL REST path and the JSON path to the RSL value. Tap "Test RSL read".
Use the REST console to try other commands. Then tick "Enable Live Radio RSL Polling".

Authentication is HTTP Basic. If your radio uses a login/token endpoint instead,
adapt `doRequest()` in `RadioBridge.kt`.

## App pages (v2)
Menu (☰): Home · Antenna Alignment (Location, Alignment Parameters, Coarse Alignment, Fine Alignment) · Radio Connection.
- Location: local/remote coordinates (DD, DDM or DMS), GPS, map; computes target azimuth (true north), elevation (4/3 earth) and distance.
- Alignment Parameters: target RSL, elevation tilt offset, magnetic declination (automatic from Android's WMM), sweep check, camera/tracking.
- Coarse Alignment: camera view with target crosshair from compass + tilt sensors.
- Fine Alignment: RSL vs optical angle per axis, Find Best Angle (centroid), Auto delay, sweep check.
