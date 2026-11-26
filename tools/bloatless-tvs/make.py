#!/usr/bin/env python3
"""The Bloatless TV list page (ajimverse.com/bloatless/tvs/), made from the upstream page in site/tvs/.

Bloatless is built on Clean Your TV 0.4.8, so the upstream list (site/tvs/tvs.json, from that
release and its reports) says what Bloatless does on each TV too. This script copies the page,
renames it to Bloatless by Ajim, points the app, code and privacy links at the Bloatless repo, says
plainly that "Next test build" and "Coming soon" are Clean Your TV's coming versions, and lays the
TVs Bloatless itself confirmed (tools/bloatless-tvs/confirmed.json) over the list.

    tools/bloatless-tvs/make.py            # writes build/bloatless-tvs/ (index.html, tvs.json, assets/)

Then upload build/bloatless-tvs/ to public_html/bloatless/tvs/. Stdlib only.
"""
import json
import pathlib
import re
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "site" / "tvs"
OUT = ROOT / "build" / "bloatless-tvs"
HERE = pathlib.Path(__file__).resolve().parent

REPO = "https://github.com/theonlyajim/bloatless"
UPSTREAM_SITE = "https://udhaykumarbala.github.io/cleanyourtv-app/"
UPSTREAM_REPO = "https://github.com/udhaykumarbala/cleanyourtv-app"


def app_version():
    m = re.search(r'versionName\s*=\s*"([^"]+)"', (ROOT / "app" / "build.gradle.kts").read_text())
    return m.group(1) if m else "?"


def sub(text, old, new, count=1):
    if old not in text:
        sys.exit(f"make.py: the upstream page changed, cannot find: {old[:80]!r}")
    return text.replace(old, new) if count == 0 else text.replace(old, new, count)


def page(html, version):
    html = sub(html, "<title>TV list - Clean Your TV</title>", "<title>TV list - Bloatless by Ajim</title>")
    html = sub(html, 'content="Every TV the Clean Your TV phone app has seen',
               'content="Every TV Bloatless by Ajim knows')
    html = sub(html, "../assets/", "assets/", 0)
    html = sub(html, "</head>", "<style>header.top{flex-wrap:wrap;row-gap:6px;column-gap:14px}header.top a{white-space:nowrap}nav.top{gap:14px}</style>\n</head>")
    html = sub(html, '<a class="brand" href="../"><img src="assets/icon.png" alt="" width="36" height="36">Clean Your TV</a>',
               f'<a class="brand" href="{REPO}"><img src="assets/icon.png" alt="" width="36" height="36">Bloatless <small style="font-weight:400;opacity:.7">by Ajim</small></a>')
    html = sub(html, f'<nav class="top" aria-label="Main"><a href="../">Home</a><a class="opt" href="../testers/">Testers</a><a href="{UPSTREAM_REPO}">GitHub</a></nav>',
               f'<nav class="top" aria-label="Main"><a href="{REPO}/releases/latest">Get the app</a><a href="{REPO}">GitHub</a></nav>')
    html = sub(html, "<p class=\"lead\">Every TV the app has seen, from its tested lists and from the reports people sent. Find yours to see whether it gets the new home screen, and if not, why.</p>",
               "<p class=\"lead\">Every TV the app knows, from its tested lists and from the reports people sent. Find yours to see whether it gets the new home screen, and if not, why.</p>\n"
               f"    <p class=\"small\" style=\"opacity:.8\">Bloatless {version} is built on <a href=\"{UPSTREAM_REPO}\">Clean Your TV</a> 0.4.8, so this list comes from "
               "the Clean Your TV community's reports. <b>Next test build</b> and <b>Coming soon</b> are Clean Your TV's coming versions: "
               "Bloatless gets them after they are released. TVs marked <b>confirmed with Bloatless</b> were checked with Bloatless itself.</p>")
    # Footer and the links under the list.
    html = sub(html, f'<a href="{UPSTREAM_REPO}/releases/latest">Get the app</a> &middot; <a href="../testers/">The testers who made these lists safer</a>',
               f'<a href="{REPO}/releases/latest">Get Bloatless</a> &middot; <a href="{UPSTREAM_SITE}testers/">The Clean Your TV testers who made these lists safer</a>')
    html = sub(html, f'<p>Clean Your TV app &middot; MIT licence &middot; <a href="https://cleanyour.tv/app/android/privacy">Privacy</a> &middot; <a href="{UPSTREAM_REPO}">GitHub</a> &middot; <a href="../testers/">Testers</a></p>',
               f'<p>Bloatless by Ajim &middot; MIT licence &middot; built on the <a href="{UPSTREAM_REPO}">Clean Your TV</a> app &middot; <a href="{REPO}/blob/bloatless/PRIVACY.md">Privacy</a> &middot; <a href="{REPO}">GitHub</a></p>')
    # Words in the script: the test builds are Clean Your TV's.
    html = sub(html, '"Keeps its own home screen for now: works in our test builds."',
               '"Keeps its own home screen for now: works in Clean Your TV\'s test builds."')
    html = sub(html, "why = 'Our test builds switch it with the setup wizard left on, and a release follows once testers confirm it.';",
               "why = 'Clean Your TV\\'s test builds switch it with the setup wizard left on, and a release follows once testers confirm it.';")
    html = sub(html, ": 'Coming to everyone in a later release once testers confirm it on this brand.';",
               ": 'Comes to Clean Your TV once testers confirm it on this brand, then to Bloatless.';")
    html = sub(html, "text: 'The test build' }", "text: 'Clean Your TV test build' }")
    html = sub(html, "Release ${D.release}, updated ${D.generated_at}.`",
               "Bloatless ${D.bloatless}, built on Clean Your TV ${D.release}; updated ${D.generated_at}.`")
    html = sub(html, "  meta.push(t.reports ? plural(t.reports, 'report', 'reports') : 'tested list');",
               "  meta.push(t.reports ? plural(t.reports, 'report', 'reports') : 'tested list');\n"
               "  if (t.bloatless) meta.push('confirmed with Bloatless');")
    html = sub(html, "links.append(el('a', { href: D.links.issues + encodeURIComponent(t.models[0] || t.name),",
               "links.append(el('a', { href: (t.bloatless ? D.links.bloatless_issues : D.links.issues) + encodeURIComponent(t.models[0] || t.name),")
    return html


def data(d, confirmed, version):
    d["bloatless"] = version
    d["links"]["app"] = REPO + "/releases/latest"
    d["links"]["bloatless_issues"] = REPO + "/issues?q="
    for s in d["statuses"]:
        if s["id"] == "test":
            s["means"] = "Works in Clean Your TV's test builds; comes to Bloatless after Clean Your TV releases it for your brand."
    byid = {t["id"]: i for i, t in enumerate(d["tvs"])}
    for t in confirmed:
        if t["id"] in byid:
            d["tvs"][byid[t["id"]]].update(t)
        else:
            d["tvs"].append(t)
    d["tvs"].sort(key=lambda t: (t["brand"].lower(), t["name"].lower(), t["id"]))
    counts = {}
    for t in d["tvs"]:
        counts[t["status"]] = counts.get(t["status"], 0) + 1
    for s in d["statuses"]:
        s["tvs"] = counts.get(s["id"], 0)
    return d


def main():
    version = app_version()
    if OUT.exists():
        shutil.rmtree(OUT)
    (OUT / "assets").mkdir(parents=True)
    for f in ("style.css", "outfit.ttf"):
        shutil.copy(ROOT / "site" / "assets" / f, OUT / "assets" / f)
    shutil.copy(ROOT / "fastlane" / "metadata" / "android" / "en-US" / "images" / "icon.png", OUT / "assets" / "icon.png")
    (OUT / "index.html").write_text(page((SRC / "index.html").read_text(), version))
    confirmed = json.loads((HERE / "confirmed.json").read_text())["tvs"]
    d = data(json.loads((SRC / "tvs.json").read_text()), confirmed, version)
    # One TV per line, as upstream writes it.
    head = {k: v for k, v in d.items() if k != "tvs"}
    body = json.dumps(head, indent=1, ensure_ascii=False)[:-2]
    rows = ",\n".join("  " + json.dumps(t, ensure_ascii=False, separators=(",", ":")) for t in d["tvs"])
    (OUT / "tvs.json").write_text(body + ',\n "tvs": [\n' + rows + "\n ]\n}\n")
    json.loads((OUT / "tvs.json").read_text())   # still JSON
    print(f"wrote {OUT} (Bloatless {version}, {len(d['tvs'])} TVs, {sum(1 for t in d['tvs'] if t['status'] == 'new')} with the new home screen)")


if __name__ == "__main__":
    main()
