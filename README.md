# Välitreeni

A personal Android app that reminds you to do short bodyweight workouts at home and counts what you did today. The name is Finnish for "in-between workout", which is what the mini rounds are.

- **Daily focus:** each day the app picks 4 of the moves (oldest first, one per body area), so all of them come around every few days.
- **Mini rounds:** at each reminder time a notification asks for one set of every focus move that is still short of its goal. "Done round" logs them all with one tap. In the app the round goes one move at a time, with a countdown for timed moves.
- **History:** current and best streak of goal days, a 7-day chart per move and a five-week goal-day grid.
- **Tutorials:** every built-in move has an animated stick figure, steps, common mistakes and easier/harder versions.
- **Languages:** English and Finnish. Follows the phone's language or can be set per app.

Everything stays on the phone: no account, no network.

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

## Install on the phone

1. On the phone, turn on Developer options and **USB debugging**, then connect it.
2. Run `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

The APK is signed with this machine's debug key (`~/.android/debug.keystore`). Android only installs updates signed with the same key, so keep that file. If it's lost, uninstall the app first, which also deletes its data.

## First start on a OnePlus

OnePlus battery saving stops background apps aggressively. If it isn't turned off for this app, reminders silently stop.

1. Allow notifications when asked.
2. Tap **Fix** on the battery strip on the Today screen, then **Allow**.
3. Also open **Settings**, tap the battery chip next to **Reminders**, then **Battery**, and choose **Unrestricted** / allow background activity.

If a reminder ever fails to arrive, the Today screen says so.

## Notes

- The app was called Fit Tracker before. Its package ID is still `app.fittracker`, so new builds install as an update over the old app and keep its data.
- The heading font is Bricolage Grotesque, bundled under the SIL Open Font License (`licenses/BricolageGrotesque-OFL.txt`).

- Reminders use exact alarms (`USE_EXACT_ALARM`), which arrive on the minute on stock Android. OxygenOS still widens them to a 1-hour window, so on a OnePlus a reminder can arrive up to an hour late. We chose that over `setAlarmClock`, which would be on time but shows an alarm icon and a "next alarm" on the lock screen. The app never replaces a late reminder that is still on its way, and only calls it missed after 75 minutes. The app is sideloaded, so this permission is granted at install; a Play Store release would need a different approach.
- Logs are one row per set. Goal changes are stored with the date they start, so old days are judged by the goal they had at the time.
- Android Auto Backup covers the database; there is no manual export yet.
