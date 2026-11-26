#!/usr/bin/env python3
"""The Bloatless module index bundled in the app (android/platform/src/main/assets/bundled/).

Writes keys.json (sequence from --keys-sequence) and index.json (Retro from upstream, unchanged, plus
the apps in apps.json), then signs both with scripts/BloatlessMinisign.java: keys.json with the root
key, index.json with the index key (secrets in ~/.bloatless/index). Each app's APK must be in --apks
as <id>.apk so its size and sha256 are read from the file; its signer and version are in apps.json
and checked against `apksigner`/`aapt2` by hand when an app is added or updated.

    tools/bloatless-index/make.py --apks <dir> --sequence 3 --keys-sequence 2 --days 365
Then raise TrustRoots.BUNDLED_KEYS_SEQUENCE to the keys sequence. Stdlib only.
"""
import argparse, datetime, hashlib, json, os, pathlib, subprocess

ROOT = pathlib.Path(__file__).resolve().parents[2]
BUNDLED = ROOT / "android/platform/src/main/assets/bundled"
HERE = pathlib.Path(__file__).resolve().parent
BC = os.environ.get("BC_JAR") or str(next(pathlib.Path.home().glob(".gradle/caches/modules-2/files-2.1/org.bouncycastle/bcprov-jdk18on/1.86/*/bcprov-jdk18on-1.86.jar")))
JAVA = os.environ.get("JAVA") or str(pathlib.Path.home() / ".local/bloatless-toolchain/jdk/bin/java")


def tool(*args):
    return subprocess.run([JAVA, "-cp", BC, str(ROOT / "scripts/BloatlessMinisign.java"), *args], check=True, capture_output=True, text=True).stdout.strip()


def iso(t):
    return t.strftime("%Y-%m-%dT%H:%M:%SZ")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apks", required=True)
    ap.add_argument("--sequence", type=int, required=True)
    ap.add_argument("--keys-sequence", type=int, required=True)
    ap.add_argument("--days", type=int, default=365)
    a = ap.parse_args()
    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)

    keys = {"schema": 1, "sequence": a.keys_sequence, "generated": iso(now),
            "keys": [{"id": tool("id", "index"), "public_key": tool("pub", "index"), "status": "active"}]}
    (BUNDLED / "keys.json").write_text(json.dumps(keys, indent=1) + "\n")

    old = json.loads((BUNDLED / "index.json").read_text())
    retro = [m for m in old["modules"] if m["id"] == "retro"]
    apps = json.loads((HERE / "apps.json").read_text())["modules"]
    for m in apps:
        f = pathlib.Path(a.apks) / (m["id"] + ".apk")
        data = f.read_bytes()
        v = m["versions"][0]
        v["apk"]["size"] = len(data)
        v["apk"]["sha256"] = hashlib.sha256(data).hexdigest()
    index = {"schema": 1, "sequence": a.sequence, "generated": iso(now),
             "expires": iso(now + datetime.timedelta(days=a.days)), "modules": retro + apps, "revoked": []}
    (BUNDLED / "index.json").write_text(json.dumps(index, indent=1, ensure_ascii=False) + "\n")

    stamp = int(now.timestamp())
    print(tool("sign", "root", str(BUNDLED / "keys.json"), f"timestamp:{stamp}\tfile:keys.json\tsequence:{a.keys_sequence}"))
    print(tool("sign", "index", str(BUNDLED / "index.json"), f"timestamp:{stamp}\tfile:index.json\tsequence:{a.sequence}"))
    print("root public key (TrustRoots.INDEX_ROOT_PUBLIC_KEY):", tool("pub", "root"), "id", tool("id", "root"))


if __name__ == "__main__":
    main()
