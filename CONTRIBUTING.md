# Contributing to Välitreeni

## Build

You need the Android SDK (platform 37) and JDK 21. Gradle finds JDK 21 by itself (`gradle/gradle-daemon-jvm.properties`), even if your default Java is newer.

Tell Gradle where the SDK is, once:

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

Then:

```sh
./gradlew assembleDebug              # APK in app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest          # logic tests, no device needed
./gradlew connectedDebugAndroidTest  # database tests, needs a running emulator or phone
./gradlew lintDebug                  # also fails on any string missing its Finnish translation
```

Every user-facing string must exist in both `res/values/strings.xml` and `res/values-fi/strings.xml`.

## Run on a phone

Turn on Developer options and **USB debugging** on the phone, connect it, and run:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` keeps the app's data. This only works if the APK is signed with the same key as the installed app (see [Signing key](#signing-key)).

## Updates

When the app opens, it asks `https://api.github.com/repos/nikolauska/valitreeni/releases/latest` for the newest release. If that version is higher, the Today screen shows **Update**. The app streams the APK straight into Android's `PackageInstaller`, checks it against the SHA-256 that GitHub publishes for the file, and commits the install. The first update needs the user to allow "install unknown apps" for Välitreeni. Every update shows Android's confirm screen: the app deliberately doesn't use `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, because silently installing a downloaded APK is what malware droppers do, and Play Protect flags it. The code is in `app/src/main/java/app/fittracker/update/`.

The check is the only network use. It runs once per app start, only from the activity, so reminder alarms never go online. It can be turned off in Settings.

To test the flow without publishing anything, point debug builds at a local server:

```sh
./gradlew assembleDebug -Pvalitreeni.version=0.3.0 -Pvalitreeni.updateUrl=http://127.0.0.1:8000/latest.json
adb reverse tcp:8000 tcp:8000
```

Serve a `latest.json` shaped like GitHub's release answer (`tag_name`, and `assets` with `name`, `size`, `digest` and `browser_download_url`) next to the APK. Only debug builds accept the override and plain http. `127.0.0.1` through `adb reverse` avoids Android's local network permission, which `10.0.2.2` would need.

## Releasing

```sh
scripts/release.sh 0.3.0 "What changed"
```

The script raises `valitreeni.version` in `gradle.properties`, commits, tags `v0.3.0` and pushes. The **Release** workflow (`.github/workflows/release.yml`) then runs the tests, builds the APK, signs it with `scripts/sign-release.sh` (which refuses anything but the current key and a lineage that starts at the original one), and publishes the GitHub release. Phones pick it up the next time the app opens. Without notes, GitHub writes them from the commits.

The version must go up every time. `versionCode` is derived from it (`major * 10000 + minor * 100 + patch`, each part 0–99), and Android refuses an update with a lower one. `versionCodeOf()` in the app uses the same formula to read release tags.

### Signing key

Every APK, debug or release, must be signed with the current release key. Otherwise phones refuse the update, and the only way out is uninstalling, which deletes the user's data.

The app started out with a key whose certificate is named `CN=Android Debug, O=Android, C=US` (SHA-256 `d604326dee1286e824eb86b4e76b86d1f0810c988fa4e860f9e41f80f90bbbde`). Play Protect treats debug-named certificates on sideloaded apps as suspicious, so the key is rotated with APK Signature Scheme v3:

- `app/signing/lineage.bin` is the old key's signed permission for the new key to take over. Every release APK carries it, so a phone that still knows only the old key accepts the update and keeps its data, even if it skipped versions. It holds only public certificates and signatures, so it's committed.
- Gradle builds releases unsigned. `scripts/sign-release.sh <unsigned.apk> <signed.apk>` signs them with the new key and the lineage, v3 only (Android 15 checks v3 first, and v1/v2 would need the old private key). It reads the keystore from `VALITREENI_STORE_FILE`/`VALITREENI_STORE_PASSWORD` or from `~/.gradle/gradle.properties`.
- Debug builds are signed by Gradle with whatever `valitreeni.storeFile` points at. After the rotation that is the new key, and a debug build installs over a release one only once the phone has received a rotated release. A build signed with the old key can't be installed over a rotated one.

#### Rotating, once

Until `app/signing/lineage.bin` exists, `scripts/release.sh` refuses to tag. To rotate:

1. Run `scripts/rotate-key.sh`. It asks for the old keystore's password and a new one, creates `~/.config/valitreeni/release-2.jks`, and writes the lineage. It refuses if the old keystore isn't the original key.
2. Back up the new `.jks` file and its password next to the old ones, for example in a password manager. Keep the old key too.
3. Point `valitreeni.storeFile` and `valitreeni.storePassword` in `~/.gradle/gradle.properties` at the new keystore. The alias is `valitreeni`.
4. Replace the repo secrets `VALITREENI_KEYSTORE_BASE64` (`base64 -w0 ~/.config/valitreeni/release-2.jks`) and `VALITREENI_STORE_PASSWORD`.
5. Test on an emulator: install the current GitHub release, open the app, then `./gradlew assembleRelease`, sign it with `scripts/sign-release.sh`, and `adb install -r` it. It must say `Success` and the app must still show its history.
6. Commit `app/signing/lineage.bin` and release as usual.

Keystores and passwords can't be downloaded back from GitHub, so the backups are the only copies.

A build on another machine without the key is signed with that machine's debug key, so it can't be installed over a release build. Uninstall first, or test on an emulator.

## Design notes

- The package ID is still `app.fittracker` from when the app was called Fit Tracker. Changing it would make Android treat it as a different app, and installed copies would lose their data. The repo was renamed from `fit-tracker` to `valitreeni`.
- Reminders use exact alarms (`USE_EXACT_ALARM`), which arrive on the minute on stock Android. OxygenOS still widens them to a 1-hour window, so on a OnePlus a reminder can arrive up to an hour late. We chose that over `setAlarmClock`, which would be on time but shows an alarm icon and a "next alarm" on the lock screen. The app never replaces a late reminder that is still on its way, and only calls it missed after 75 minutes. The app is sideloaded, so this permission is granted at install; a Play Store release would need a different approach.
- Logs are one row per set. Goal changes are stored with the date they start, so old days are judged by the goal they had at the time.
- Android Auto Backup covers the database; there is no manual export yet.
- README screenshots are in `docs/screenshots/`, taken on the emulator in English at 1080×2400 and saved as WebP 540 px wide.
