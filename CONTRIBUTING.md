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

When the app opens, it asks `https://api.github.com/repos/nikolauska/valitreeni/releases/latest` for the newest release. If that version is higher, the Today screen shows **Update**. The app streams the APK straight into Android's `PackageInstaller`, checks it against the SHA-256 that GitHub publishes for the file, and commits the install. The first update needs the user to allow "install unknown apps" for Välitreeni and to confirm. Later ones install without asking, thanks to `UPDATE_PACKAGES_WITHOUT_USER_ACTION`. The code is in `app/src/main/java/app/fittracker/update/`.

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

The script raises `valitreeni.version` in `gradle.properties`, commits, tags `v0.3.0` and pushes. The **Release** workflow (`.github/workflows/release.yml`) then runs the tests, builds and signs the APK, checks that it's signed with the release key, and publishes the GitHub release. Phones pick it up the next time the app opens. Without notes, GitHub writes them from the commits.

The version must go up every time. `versionCode` is derived from it (`major * 10000 + minor * 100 + patch`, each part 0–99), and Android refuses an update with a lower one. `versionCodeOf()` in the app uses the same formula to read release tags.

### Signing key

Every APK, debug or release, must be signed with the same key. Otherwise phones refuse the update, and the only way out is uninstalling, which deletes the user's data.

- Locally the key is `~/.config/valitreeni/release.jks`, with its settings in `~/.gradle/gradle.properties` (`valitreeni.storeFile`, `valitreeni.storePassword`, `valitreeni.keyAlias`). Debug builds on the maintainer's machine use `~/.android/debug.keystore`, which holds the same key.
- The workflow reads it from the repo secrets `VALITREENI_KEYSTORE_BASE64` (the `.jks` file, base64) and `VALITREENI_STORE_PASSWORD`.
- Certificate SHA-256: `d604326dee1286e824eb86b4e76b86d1f0810c988fa4e860f9e41f80f90bbbde`. The workflow refuses to publish an APK signed with anything else.

Keep a backup of the `.jks` file and its password somewhere safe, such as a password manager. It can't be downloaded back from GitHub.

A build on another machine is signed with that machine's debug key, so it can't be installed over a release build. Uninstall first, or test on an emulator.

## Design notes

- The package ID is still `app.fittracker` from when the app was called Fit Tracker. Changing it would make Android treat it as a different app, and installed copies would lose their data. The repo was renamed from `fit-tracker` to `valitreeni`.
- Reminders use exact alarms (`USE_EXACT_ALARM`), which arrive on the minute on stock Android. OxygenOS still widens them to a 1-hour window, so on a OnePlus a reminder can arrive up to an hour late. We chose that over `setAlarmClock`, which would be on time but shows an alarm icon and a "next alarm" on the lock screen. The app never replaces a late reminder that is still on its way, and only calls it missed after 75 minutes. The app is sideloaded, so this permission is granted at install; a Play Store release would need a different approach.
- Logs are one row per set. Goal changes are stored with the date they start, so old days are judged by the goal they had at the time.
- Android Auto Backup covers the database; there is no manual export yet.
- README screenshots are in `docs/screenshots/`, taken on the emulator in English at 1080×2400 and saved as WebP 540 px wide.
