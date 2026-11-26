#!/bin/bash
# Bloatless: make the release key once, then sign the TV apps with it and pin them.
#
#   scripts/bloatless-release-key.sh
#
# 1. Asks for a password (typed here, never shown or stored in the repo) and makes the key in
#    ~/.bloatless/bloatless-release.jks, if there is none yet. Keep that file and the password safe: an
#    app signed with another key cannot update over this one.
# 2. Writes keystore.properties (git ignores it) so ./gradlew assembleFullRelease signs the phone app.
# 3. Builds the TV launcher (tv/launcher), signs it and the curtain with the same key, copies both into the
#    phone app's assets and updates the pins (core/trust Pins.kt and the tests that name the signer).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIR="$HOME/.bloatless"
JKS="$DIR/bloatless-release.jks"
PASS="$DIR/release.pass"
ALIAS="bloatless"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/bloatless-toolchain/jdk}"
BT="$HOME/Library/Android/sdk/build-tools/37.0.0"
mkdir -p "$DIR"; chmod 700 "$DIR"

if [ ! -f "$JKS" ]; then
  echo "Choose a password for the Bloatless release key (at least 8 characters)."
  read -r -s -p "Password: " P1; echo
  read -r -s -p "Again:    " P2; echo
  [ "$P1" = "$P2" ] || { echo "The two passwords differ."; exit 1; }
  [ ${#P1} -ge 8 ] || { echo "Use at least 8 characters."; exit 1; }
  umask 077; printf '%s' "$P1" > "$PASS"; unset P1 P2
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$JKS" -storetype PKCS12 -alias "$ALIAS" -keyalg RSA -keysize 4096 \
    -validity 10000 -dname "CN=Bloatless by Ajim, O=Bloatless" -storepass:file "$PASS" -keypass:file "$PASS"
  echo "Made $JKS. Back it up with the password somewhere safe."
fi
[ -f "$PASS" ] || { echo "No $PASS: put the key's password in it (chmod 600) and run again."; exit 1; }

umask 077
cat > "$ROOT/keystore.properties" <<EOF
storeFile=$JKS
storePasswordFile=$PASS
keyAlias=$ALIAS
EOF

echo "Building the TV launcher..."
(cd "$ROOT/tv/launcher" && ./gradlew assembleRelease --console=plain -q)
ASSETS="$ROOT/android/platform/src/main/assets/bundled"
TMP="$(mktemp -d)"
# The key's password is the keystore's (one file, read once: apksigner reads a password file line by line).
sign() { "$BT/zipalign" -f -p 4 "$1" "$TMP/a.apk"; "$BT/apksigner" sign --ks "$JKS" --ks-pass "file:$PASS" --ks-key-alias "$ALIAS" \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --out "$2" "$TMP/a.apk"; }
sign "$ROOT/tv/launcher/app/build/outputs/apk/release/app-release.apk" "$ASSETS/cleanyourtv-launcher.apk"
sign "$ASSETS/cleanyourtv-curtain.apk" "$TMP/curtain.apk" && cp "$TMP/curtain.apk" "$ASSETS/cleanyourtv-curtain.apk"
rm -rf "$TMP"

SIGNER=$("$BT/apksigner" verify --print-certs "$ASSETS/cleanyourtv-launcher.apk" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)
L_SHA=$(shasum -a 256 "$ASSETS/cleanyourtv-launcher.apk" | cut -d' ' -f1); L_SIZE=$(stat -f %z "$ASSETS/cleanyourtv-launcher.apk")
C_SHA=$(shasum -a 256 "$ASSETS/cleanyourtv-curtain.apk" | cut -d' ' -f1); C_SIZE=$(stat -f %z "$ASSETS/cleanyourtv-curtain.apk")
OLD=$(sed -n 's/.*const val SIGNER_SHA256 = "\([0-9a-f]\{64\}\)".*/\1/p' "$ROOT/core/trust/src/main/kotlin/tv/cleanyour/core/trust/Pins.kt" | head -1)

python3 - "$ROOT" "$SIGNER" "$OLD" "$L_SHA" "$L_SIZE" "$C_SHA" "$C_SIZE" <<'PY'
import re, sys, pathlib
root, signer, old, lsha, lsize, csha, csize = sys.argv[1:]
pins = pathlib.Path(root, "core/trust/src/main/kotlin/tv/cleanyour/core/trust/Pins.kt")
s = pins.read_text()
def block(name, sha, size, s):
    i = s.index("object " + name)
    j = s.index("\n}", i)
    b = s[i:j]
    b = re.sub(r'const val SIZE = [0-9_]+L', 'const val SIZE = %sL' % format(int(size), "_"), b)
    b = re.sub(r'const val SHA256 = "[0-9a-f]{64}"', 'const val SHA256 = "%s"' % sha, b)
    return s[:i] + b + s[j:]
s = block("PinnedLauncher", lsha, lsize, s)
s = block("PinnedCurtain", csha, csize, s)
s = s.replace('"%s"' % old, '"%s"' % signer)
s = s.replace("Signed with the Android debug key for now (CN=Android Debug); a Bloatless release key replaces it before release.",
              "Signed with the Bloatless release key (scripts/bloatless-release-key.sh).")
pins.write_text(s)
for rel in ["core/probe/src/test/kotlin/tv/cleanyour/core/probe/SignerAndWifiTest.kt",
            "testing/fake-adbd/src/main/kotlin/tv/cleanyour/testing/fakeadbd/FakeTvServer.kt"]:
    p = pathlib.Path(root, rel)
    p.write_text(p.read_text().replace(old, signer))
print("Pinned launcher", lsha, lsize, "curtain", csha, csize, "signer", signer)
PY

echo "Checking the pins and building the signed release..."
(cd "$ROOT" && ./gradlew :core:trust:test :core:probe:test :app:assembleFullRelease --console=plain -q)
echo "Done: $ROOT/app/build/outputs/apk/full/release/bloatless-1.0.1-full-release.apk"
