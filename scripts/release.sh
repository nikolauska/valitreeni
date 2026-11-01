#!/usr/bin/env bash
# Publishes a new Välitreeni version as a GitHub release; installed apps pick it up from there.
#
#   scripts/release.sh 0.3.0 ["What changed, shown on the release page"]
#
# Needs: a clean main branch, the signing key from ~/.gradle/gradle.properties, and gh logged in.
set -euo pipefail

version=${1:-}
notes=${2:-}
repo=nikolauska/valitreeni
# The key every installed copy was signed with. A different key would make phones refuse the update,
# so the script stops before publishing anything signed otherwise.
expected_cert=d604326dee1286e824eb86b4e76b86d1f0810c988fa4e860f9e41f80f90bbbde

die() { echo "release: $*" >&2; exit 1; }

[[ $version =~ ^[0-9]{1,2}\.[0-9]{1,2}\.[0-9]{1,2}$ ]] || die "usage: scripts/release.sh <major.minor.patch> [notes]"
cd "$(dirname "$0")/.."

[[ $(git branch --show-current) == main ]] || die "release from main"
[[ -z $(git status --porcelain) ]] || die "commit or stash your changes first"
git fetch -q origin main
git merge-base --is-ancestor origin/main HEAD || die "origin/main has commits you don't have; pull first"
git rev-parse -q --verify "refs/tags/v$version" >/dev/null && die "v$version already exists"

code() { IFS=. read -r a b c <<<"$1"; echo $((10#$a * 10000 + 10#$b * 100 + 10#$c)); }
current=$(sed -n 's/^valitreeni.version=//p' gradle.properties)
(( $(code "$version") > $(code "$current") )) || die "$version must be newer than $current"

sdk=${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties)}
build_tools=$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)
# Gradle can't run on the machine's default JDK when it's newer than Gradle supports.
if [[ -z ${JAVA_HOME:-} && -d /usr/lib/jvm/java-21-openjdk ]]; then export JAVA_HOME=/usr/lib/jvm/java-21-openjdk; fi

sed -i "s/^valitreeni.version=.*/valitreeni.version=$version/" gradle.properties
./gradlew -q clean assembleRelease || { git checkout gradle.properties; die "build failed"; }

apk=app/build/outputs/apk/release/app-release.apk
cert=$("$build_tools/apksigner" verify --print-certs "$apk" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)
[[ $cert == "$expected_cert" ]] || { git checkout gradle.properties; die "APK is not signed with the release key (got '${cert:-unsigned}')"; }
built=$("$build_tools/aapt2" dump badging "$apk" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")
[[ $built == "$version" ]] || { git checkout gradle.properties; die "built $built, expected $version"; }

out=build/release/valitreeni-$version.apk
mkdir -p build/release
cp "$apk" "$out"
echo "Built $out ($(du -h "$out" | cut -f1)), signed with the release key."

read -r -p "Publish v$version to github.com/$repo? [y/N] " answer
[[ $answer == [yY] ]] || { git checkout gradle.properties; echo "Nothing published."; exit 0; }

git commit -q -m "Release $version" gradle.properties
git tag -a "v$version" -m "Välitreeni $version"
git push -q origin main "v$version"
if [[ -n $notes ]]; then
  gh release create "v$version" "$out" --repo "$repo" --title "Välitreeni $version" --notes "$notes"
else
  gh release create "v$version" "$out" --repo "$repo" --title "Välitreeni $version" --generate-notes
fi
echo "Published. Phones see it next time the app opens."
