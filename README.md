# Välitreeni

A personal Android app that reminds you to do short bodyweight workouts at home and counts what you did today. The name is Finnish for "in-between workout", which is what the mini rounds are.

- **Daily focus:** each day the app picks 4 of the moves (oldest first, one per body area), so all of them come around every few days.
- **Mini rounds:** at each reminder time a notification asks for one set of every focus move that is still short of its goal. "Done round" logs them all with one tap. In the app the round goes one move at a time, with a countdown for timed moves.
- **History:** current and best streak of goal days, a 7-day chart per move and a five-week goal-day grid.
- **Tutorials:** every built-in move has an animated stick figure, steps, common mistakes and easier/harder versions.
- **Languages:** English and Finnish. Follows the phone's language or can be set per app.

Your training data stays on the phone: no account, no sync. The app only goes online to check GitHub for a newer version and download it (see [Updates](#updates)); that can be turned off in Settings.

## Build

Needs the Android SDK (platform 37) and JDK 21. The app runs on Android 15 and newer. Gradle finds JDK 21 by itself (`gradle/gradle-daemon-jvm.properties`), even if your default Java is newer.

Tell Gradle where the SDK is, once:

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

Then:

```sh
./gradlew assembleDebug            # APK in app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # logic tests, no device needed
./gradlew connectedDebugAndroidTest  # database tests, needs a running emulator or phone
./gradlew lintDebug                # also fails on any string missing its Finnish translation
```

## Install

**For relatives (or any phone):** open the latest release at https://github.com/nikolauska/valitreeni/releases/latest on the phone, download the `.apk`, and open it. Android asks to allow installing from the browser once. After that the app keeps itself up to date.

**During development:** turn on Developer options and **USB debugging** on the phone, connect it, and run `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Updates

When the app opens, it asks GitHub for the newest release of this repo. If it's newer, the Today screen shows **Update**. The app downloads the APK, checks it against GitHub's SHA-256, and hands it to Android's installer. The first time, Android asks to allow Välitreeni to install apps, and to confirm the update. Later updates install without asking. Settings has **Check for updates** and a switch to stop the automatic check.

The check sends an ordinary request to `api.github.com`; nothing about the user or their training is sent.

To test the flow without publishing, build debug APKs that point at a local server, e.g. `./gradlew assembleDebug -Pvalitreeni.version=0.3.0 -Pvalitreeni.updateUrl=http://127.0.0.1:8000/latest.json`, serve a `latest.json` shaped like GitHub's release answer, and run `adb reverse tcp:8000 tcp:8000`. Only debug builds accept this override and plain http.

## Releasing

```sh
scripts/release.sh 0.3.0 "What changed"
```

The script raises `valitreeni.version` in `gradle.properties`, commits, tags `v0.3.0` and pushes. The **Release** workflow then tests, builds and signs the APK, checks the signature, and publishes the GitHub release. Phones get it the next time the app opens. The version must go up every time, because `versionCode` is derived from it and Android refuses an update with a lower one.

### Signing key

Every APK, debug or release, must be signed with the same key, or phones refuse the update and the only way out is uninstalling, which deletes the data.

- Locally the key is `~/.config/valitreeni/release.jks`, with its password in `~/.gradle/gradle.properties` (`valitreeni.storeFile`, `valitreeni.storePassword`, `valitreeni.keyAlias`). Debug builds on this machine use `~/.android/debug.keystore`, which holds the same key.
- The workflow reads it from the repo secrets `VALITREENI_KEYSTORE_BASE64` (the `.jks` file, base64) and `VALITREENI_STORE_PASSWORD`.
- Certificate SHA-256: `d604326dee1286e824eb86b4e76b86d1f0810c988fa4e860f9e41f80f90bbbde`. The workflow refuses to publish an APK signed with anything else.

Keep a backup of the `.jks` file and its password somewhere safe, such as a password manager. It can't be recovered from GitHub.

## First start on a OnePlus

OnePlus battery saving stops background apps aggressively. If it isn't turned off for this app, reminders silently stop.

1. Allow notifications when asked.
2. Tap **Fix** on the battery strip on the Today screen, then **Allow**.
3. Also open **Settings**, tap the battery chip next to **Reminders**, then **Battery**, and choose **Unrestricted** / allow background activity.

If a reminder ever fails to arrive, the Today screen says so.

## Notes

- The app was called Fit Tracker before. Its package ID is still `app.fittracker`, so new builds install as an update over the old app and keep its data. The repo was renamed from `fit-tracker` to `valitreeni`.
- The heading font is Bricolage Grotesque, bundled under the SIL Open Font License (`licenses/BricolageGrotesque-OFL.txt`).

- Reminders use exact alarms (`USE_EXACT_ALARM`), which arrive on the minute on stock Android. OxygenOS still widens them to a 1-hour window, so on a OnePlus a reminder can arrive up to an hour late. We chose that over `setAlarmClock`, which would be on time but shows an alarm icon and a "next alarm" on the lock screen. The app never replaces a late reminder that is still on its way, and only calls it missed after 75 minutes. The app is sideloaded, so this permission is granted at install; a Play Store release would need a different approach.
- Logs are one row per set. Goal changes are stored with the date they start, so old days are judged by the goal they had at the time.
- Android Auto Backup covers the database; there is no manual export yet.
