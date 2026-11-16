# Kodak 32HDX7XPRO Remote (Unofficial remote app)

Unofficial remote app for Kodak 32HDX7XPRO Android TV. Not affiliated with or endorsed by Kodak.
No Kodak logo or trademark artwork is used. Commands go only over the local Wi-Fi network (Android TV
Remote v2 protocol) or by infrared. Nothing is ever sent through a cloud server.

## Build the APK (Android Studio)

1. Install **Android Studio Ladybug (2024.2) or newer** (it bundles JDK 17 and the SDK manager).
2. `File > Open` and pick this `KodakRemote` folder. Let Gradle sync finish (needs internet once to
   download plugins/libraries). If asked, install **Android SDK Platform 35**.
3. Debug APK: `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
   Output: `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the phone and install
   (allow "install unknown apps").
4. Release APK: `Build > Generate Signed App Bundle / APK > APK`, create a keystore, choose `release`.
5. Command line (after Android Studio created the wrapper, or run `gradle wrapper --gradle-version 8.9` once):
   `./gradlew assembleDebug`

Min Android 8.0 (API 26), target 35. Languages: English, Hindi (switch in Settings).

## How it works

* **Compatibility check** (first run, also in the top bar): Wi-Fi connected? Android TV remote service
  (`_androidtvremote2._tcp`) found by mDNS (or manual IP)? Ports 6466 and 6467 reachable? IR blaster present?
  If not, it shows: "This Kodak TV model may not support Wi-Fi remote control. Use an Android phone with an IR blaster."
  Even when all checks pass the app only says control is *not guaranteed* until pairing succeeds.
* **Wi-Fi remote**: pairing on port 6467 (code shown on TV), control on port 6466. Client certificate is
  generated inside the Android Keystore. Paired TV and settings are saved with DataStore.
* **IR remote**: Android `ConsumerIrManager`, NEC 38 kHz. Profile screen has an address field and a hex code
  per button with a Test button. **No IR codes are pre-filled**: I could not verify the real codes of this
  TV, so you must enter them (from the original remote's code list / a LIRC or IRDB entry for Kodak).
* **Permissions**: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_MULTICAST_STATE (all normal,
  no runtime prompt) and TRANSMIT_IR (normal). On newer Android versions that show a system
  "local network" prompt, allow it.

## Honest limitations

* This project was written without an Android SDK available, so **it was not compiled or run on a device
  or TV**. Expect to fix a few compile errors on first sync.
* Whether the 32HDX7XPRO exposes the Android TV Remote service depends on its firmware. Some Kodak
  models do not; then use IR.
* "Exit" is sent as KEYCODE_ESCAPE and Subtitle as KEYCODE_CAPTIONS; some TVs ignore keys they do not map.
* Play Store listing description: "Unofficial remote app for Kodak 32HDX7XPRO Android TV."

## Build on GitHub (no Android Studio needed)

1. Create a new GitHub repository and upload everything in this folder to the repo root
   (the `.github` folder must be at the root).
2. Open the **Actions** tab > **Build APK** > **Run workflow** (it also runs on every push to `main`).
3. When it finishes (about 5-8 minutes), open the run and download the artifact
   **Kodak-32HDX7XPRO-Remote-debug-apk**. Unzip it to get `app-debug.apk`.
4. If the run fails, open the failed step; the Kotlin error lines are in the log.
