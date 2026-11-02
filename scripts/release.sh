#!/usr/bin/env bash
# Starts a new Välitreeni release: raises the version, tags it and pushes. The GitHub workflow
# (.github/workflows/release.yml) then builds the signed APK and publishes the release that
# installed apps update from.
#
#   scripts/release.sh 0.3.0 ["What changed, shown on the release page"]
set -euo pipefail

version=${1:-}
notes=${2:-}

die() { echo "release: $*" >&2; exit 1; }

[[ $version =~ ^[0-9]{1,2}\.[0-9]{1,2}\.[0-9]{1,2}$ ]] || die "usage: scripts/release.sh <major.minor.patch> [notes]"
cd "$(dirname "$0")/.."

[[ $(git branch --show-current) == main ]] || die "release from main"
[[ -z $(git status --porcelain) ]] || die "commit or stash your changes first"
git fetch -q --tags origin main
git merge-base --is-ancestor origin/main HEAD || die "origin/main has commits you don't have; pull first"
git rev-parse -q --verify "refs/tags/v$version" >/dev/null && die "v$version already exists"
# The release workflow signs with this; without it the tag would only produce a failed build.
[[ -f app/signing/lineage.bin ]] || die "app/signing/lineage.bin is missing; see \"Signing key\" in CONTRIBUTING.md"

code() { IFS=. read -r a b c <<<"$1"; echo $((10#$a * 10000 + 10#$b * 100 + 10#$c)); }
current=$(sed -n 's/^valitreeni.version=//p' gradle.properties)
# Phones only accept an update with a higher versionCode, which is derived from this.
(( $(code "$version") > $(code "$current") )) || die "$version must be newer than $current"

echo "Commits since the last release:"
git log --oneline "$(git describe --tags --abbrev=0 2>/dev/null || git rev-list --max-parents=0 HEAD)..HEAD" | sed 's/^/  /'
read -r -p "Release $current -> $version and push to GitHub? [y/N] " answer
[[ $answer == [yY] ]] || { echo "Nothing changed."; exit 0; }

sed -i "s/^valitreeni.version=.*/valitreeni.version=$version/" gradle.properties
git commit -q -m "Release $version" gradle.properties
# The tag message becomes the release notes; an empty one lets GitHub list the merged changes instead.
git tag -a "v$version" -m "${notes:-Välitreeni $version}"
git push -q --atomic origin main "v$version"
echo "Pushed v$version. Follow the build with: gh run watch --repo nikolauska/valitreeni"
