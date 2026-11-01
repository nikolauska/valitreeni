# Välitreeni

An Android app (Kotlin, Jetpack Compose, Room, DataStore) that reminds the user to do short bodyweight rounds through the day. It's sideloaded from GitHub releases and updates itself, so every change ends up on phones whose data must survive the update. `README.md` is the user-facing introduction; `CONTRIBUTING.md` has build, update-flow, release and signing details. Read it before touching any of those.

## Layout

All code is under `app/src/main/java/app/fittracker/`. The package name is still `app.fittracker` from the app's old name; never change it or `applicationId`, because installed copies would stop receiving updates.

- `domain/`: plain-Kotlin rules (daily focus, rounds, streaks, reminder times), unit tested in `app/src/test`.
- `data/`: Room database, repository, settings DataStore, built-in move catalog.
- `reminders/`: exact alarms, notifications and their action receivers.
- `update/`: GitHub release check and self-install through `PackageInstaller`.
- `ui/`: one package per screen; `ui/theme/Theme.kt` holds the fixed "Pine" brand palette (`MaterialTheme.brand`) and fonts.

## Checks

Run from the repo root. They're local and safe to run anytime; fix failures your change causes and rerun.

```sh
./gradlew testDebugUnitTest lintDebug
```

Lint fails when a string is missing from `res/values-fi/strings.xml`, so add every new user-facing string in English and Finnish. Database or repository changes also need `./gradlew connectedDebugAndroidTest`, which needs a running emulator or connected phone. For UI changes, install the debug build on an emulator and look at the changed screen; tests don't cover the UI.

CI (`.github/workflows/ci.yml`) runs all of these plus an unsigned release build (R8) on every push and pull request, with the database tests on an Android 15 emulator.

## Rules that protect installed copies

- **Database:** the Room schema is exported to `app/schemas/`. Any entity change needs a version bump and a migration, never a destructive fallback: the database is the user's training history.
- **Built-in moves:** a move's `key` in `MoveCatalog` is stored on phones; never rename or remove one. Add new moves at the end of `MoveCatalog.all`: the database adds missing ones every time it opens, so updated phones get them too. Each needs a pose in `Poses.kt` and a name and tutorial in both languages.
- **Versions:** don't edit `valitreeni.version` in `gradle.properties` by hand; `scripts/release.sh` raises it.
- **Signing:** every build must be signed with the one release key. Never commit keystores or passwords; they live in `~/.gradle/gradle.properties` and repo secrets.
- **Network:** the update check is the only network use, and the README promises that. Don't add analytics, accounts or other calls.
- **Release workflow:** `.github/workflows/release.yml` publishes to every installed phone. Changes to it, `update/`, or the manifest's install permissions need the update flow tested as described in `CONTRIBUTING.md`.

## Needs the maintainer's go-ahead

Pushing, tagging, running `scripts/release.sh`, creating GitHub releases, and changing repo settings or secrets. Each of these reaches users or the public repo.

## Conventions

- Comments explain why the code does something, not what it does.
- Commit messages: a short summary line, then what changed and why.
- Keep README screenshots in `docs/screenshots/` up to date when a pictured screen changes visibly.
