#!/usr/bin/env bash
# One-time move from the original release key (named "CN=Android Debug", which Play Protect treats
# as suspicious) to a new, properly named key. Writes app/signing/lineage.bin, the old key's signed
# permission for the new key to take over, so installed copies accept updates signed with it.
#
#   scripts/rotate-key.sh [new-keystore-path] ["CN=..., O=..., C=..."]
#
# Asks for the old keystore password and a new one. Nothing leaves this machine.
set -euo pipefail

die() { echo "rotate-key: $*" >&2; exit 1; }
cd "$(dirname "$0")/.."

ORIGINAL_CERT=d604326dee1286e824eb86b4e76b86d1f0810c988fa4e860f9e41f80f90bbbde
lineage=app/signing/lineage.bin
[[ -f $lineage ]] && die "$lineage already exists; the key was rotated already"

prop() { sed -n "s/^valitreeni\.$1=//p" "$HOME/.gradle/gradle.properties" 2>/dev/null | tail -1; }
old_store=$(prop storeFile)
old_alias=$(prop keyAlias)
old_alias=${old_alias:-valitreeni}
[[ -f $old_store ]] || die "old keystore not found; valitreeni.storeFile in ~/.gradle/gradle.properties must point at it"
new_store=${1:-$HOME/.config/valitreeni/release-2.jks}
dname=${2:-CN=Valitreeni, O=Niko Lehtovirta, C=FI}
[[ -e $new_store ]] && die "$new_store already exists; refusing to overwrite a key"

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
build_tools=$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)

read -r -s -p "Password of the OLD keystore ($old_store): " OLD_PASS; echo
read -r -s -p "Password for the NEW keystore (12+ characters): " NEW_PASS; echo
read -r -s -p "Same NEW password again: " again; echo
[[ $NEW_PASS == "$again" ]] || die "the new passwords don't match"
(( ${#NEW_PASS} >= 12 )) || die "use at least 12 characters; this key protects every installed copy"
export OLD_PASS NEW_PASS

mkdir -p "$(dirname "$new_store")"
echo "Creating $new_store for \"$dname\"."
# 4096-bit RSA and a 50-year validity: this key has to outlive every phone the app is installed on.
keytool -genkeypair -keystore "$new_store" -storetype PKCS12 -alias valitreeni \
  -storepass:env NEW_PASS -keyalg RSA -keysize 4096 -validity 18250 -dname "$dname" \
  || { rm -f "$new_store"; die "keytool couldn't create the key"; }

mkdir -p "$(dirname "$lineage")"
"$build_tools/apksigner" rotate --out "$lineage" \
  --old-signer --ks "$old_store" --ks-key-alias "$old_alias" --ks-pass env:OLD_PASS \
  --new-signer --ks "$new_store" --ks-key-alias valitreeni --ks-pass env:NEW_PASS 2>&1 | grep -v '^WARNING' || true
[[ -f $lineage ]] || { rm -f "$new_store"; die "apksigner rotate failed; removed the unused $new_store"; }

certs=$("$build_tools/apksigner" lineage --in "$lineage" --print-certs 2>/dev/null)
old_cert=$(sed -n 's/.*Signer #1 in lineage certificate SHA-256 digest: //p' <<<"$certs")
new_cert=$(sed -n 's/.*Signer #2 in lineage certificate SHA-256 digest: //p' <<<"$certs")
if [[ $old_cert != "$ORIGINAL_CERT" ]]; then
  # A handover from the wrong key is useless: phones would still see an unknown signer.
  rm -f "$lineage" "$new_store"
  die "$old_store holds $old_cert, not the original release key; removed $lineage and the unused $new_store"
fi
cat <<EOF

Done. New certificate SHA-256: $new_cert

Next:
  1. Back up $new_store and its password (keep the old one too).
  2. Point valitreeni.storeFile and valitreeni.storePassword in ~/.gradle/gradle.properties at the new keystore.
  3. Replace the repo secrets VALITREENI_KEYSTORE_BASE64 (base64 -w0 "$new_store") and VALITREENI_STORE_PASSWORD.
  4. Commit $lineage. It holds only public certificates and signatures, no private keys.
  5. Test the update over an installed release as described in CONTRIBUTING.md before releasing.
EOF
