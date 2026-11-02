#!/usr/bin/env bash
# Signs an unsigned release APK with the current release key and the key-rotation lineage, then
# checks the result. Used by .github/workflows/release.yml and for local release builds.
#
#   scripts/sign-release.sh app/build/outputs/apk/release/app-release-unsigned.apk out.apk
#
# The keystore comes from VALITREENI_STORE_FILE and VALITREENI_STORE_PASSWORD, or from
# valitreeni.storeFile and valitreeni.storePassword in ~/.gradle/gradle.properties.
set -euo pipefail

die() { echo "sign-release: $*" >&2; exit 1; }

# The key every copy installed before the rotation was signed with. A lineage that doesn't start
# here would produce APKs that those phones refuse as a different app.
ORIGINAL_CERT=d604326dee1286e824eb86b4e76b86d1f0810c988fa4e860f9e41f80f90bbbde

in=${1:-}
out=${2:-}
[[ -n $in && -n $out ]] || die "usage: scripts/sign-release.sh <unsigned.apk> <signed.apk>"
[[ -f $in ]] || die "$in not found"
in=$(realpath "$in")
out=$(realpath -m "$out")
cd "$(dirname "$0")/.."
lineage=app/signing/lineage.bin
[[ -f $lineage ]] || die "$lineage is missing; run scripts/rotate-key.sh once first"

prop() { sed -n "s/^valitreeni\.$1=//p" "$HOME/.gradle/gradle.properties" 2>/dev/null | tail -1; }
store_file=${VALITREENI_STORE_FILE:-$(prop storeFile)}
store_password=${VALITREENI_STORE_PASSWORD:-$(prop storePassword)}
[[ -n $store_file && -n $store_password ]] || die "no keystore: set VALITREENI_STORE_FILE and VALITREENI_STORE_PASSWORD"

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
build_tools=$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)
apksigner() { "$build_tools/apksigner" "$@" 2>&1 | grep -v '^WARNING' || true; }

sha() { sed -n "s/.*$1 certificate SHA-256 digest: //p"; }
lineage_certs=$(apksigner lineage --in "$lineage" --print-certs | sha 'Signer #[0-9]* in lineage')
[[ $(head -1 <<<"$lineage_certs") == "$ORIGINAL_CERT" ]] || die "$lineage doesn't start at the original release key"

export STORE_PASSWORD=$store_password
rm -f "$out"
# --rotation-min-sdk-version 28 puts the new key in the plain v3 block, which every Android version the app
# supports reads; the default (33) would only use it in v3.1 and keep the old key in the v2/v3 blocks.
# v1 and v2 are off because Android 15, the oldest the app runs on, checks v3 first, and apksigner would
# need the old private key to sign them; keeping it around would undo the point of rotating.
if ! result=$("$build_tools/apksigner" sign \
  --ks "$store_file" --ks-key-alias valitreeni --ks-pass env:STORE_PASSWORD \
  --lineage "$lineage" --rotation-min-sdk-version 28 --v1-signing-enabled false --v2-signing-enabled false \
  --out "$out" "$in" 2>&1); then
  rm -f "$out"
  grep -v '^WARNING' <<<"$result" >&2
  die "apksigner sign failed; is $store_file the newest key in $lineage?"
fi

if ! verify=$("$build_tools/apksigner" verify --print-certs "$out" 2>&1); then
  rm -f "$out"
  grep -v '^WARNING' <<<"$verify" >&2
  die "the signed APK doesn't verify"
fi
signer=$(sha 'V3.0 Signer:' <<<"$verify" | head -1)
# apksigner accepts any key in the lineage, including the old one; only the newest may sign releases.
if [[ $signer != "$(tail -1 <<<"$lineage_certs")" ]]; then
  rm -f "$out"
  die "signed with $signer, not the newest key in $lineage"
fi
echo "Signed $out with $signer (rotated from $ORIGINAL_CERT)"
