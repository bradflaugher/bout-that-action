#!/usr/bin/env python3
"""Checks the static website in site/ before it's published.

Every local href and src (and CSS url()) must resolve to a file in the site,
and nothing may load from another origin: links out are fine, but scripts,
styles, fonts and images all ship with the site. Paths starting with the
Pages base (/bout-that-action/) map onto the site root, as GitHub Pages
serves them.

Usage: check_site.py <site dir>
"""

import re
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlsplit, unquote

BASE = "/bout-that-action/"

# Attributes that make the browser fetch something (as opposed to <a href>).
LOADS = {("img", "src"), ("script", "src"), ("source", "src"), ("source", "srcset"),
         ("img", "srcset"), ("iframe", "src"), ("video", "src"), ("audio", "src")}


class Refs(HTMLParser):
    def __init__(self):
        super().__init__()
        self.refs = []  # (url, loads)
        self.ids = set()

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "id" in a:
            self.ids.add(a["id"])
        for name, value in attrs:
            if value is None:
                continue
            if name in ("href", "src", "srcset"):
                loads = (tag, name) in LOADS or (tag == "link" and a.get("rel") not in ("canonical",))
                for url in ([v.strip().split(" ")[0] for v in value.split(",")] if name == "srcset" else [value]):
                    self.refs.append((url, loads))


def resolve(site: Path, page: Path, url: str) -> Path | None:
    path = unquote(urlsplit(url).path)
    if path.startswith(BASE):
        target = site / path[len(BASE):]
    elif path.startswith("/"):
        return None  # outside the project site
    elif path == "":
        target = page
    else:
        target = page.parent / path
    if path.endswith("/") or target.is_dir():
        target = target / "index.html"
    return target


def main() -> int:
    site = Path(sys.argv[1]).resolve()
    errors = []
    for page in sorted(site.rglob("*.html")):
        parser = Refs()
        parser.feed(page.read_text(encoding="utf-8"))
        for url, loads in parser.refs:
            scheme = urlsplit(url).scheme
            if scheme in ("http", "https") or url.startswith("//"):
                if loads:
                    errors.append(f"{page.relative_to(site)}: loads from another origin: {url}")
                continue
            if scheme in ("mailto", "data") or url.startswith("#"):
                continue
            target = resolve(site, page, url)
            if target is None:
                errors.append(f"{page.relative_to(site)}: absolute path outside {BASE}: {url}")
            elif not target.resolve().is_relative_to(site) or not target.is_file():
                errors.append(f"{page.relative_to(site)}: broken link: {url}")
    for css in sorted(site.rglob("*.css")):
        for url in re.findall(r"url\(\s*['\"]?([^'\")]+)", css.read_text(encoding="utf-8")):
            if urlsplit(url).scheme or url.startswith("//"):
                if not url.startswith("data:"):
                    errors.append(f"{css.relative_to(site)}: loads from another origin: {url}")
                continue
            if not (css.parent / url).is_file():
                errors.append(f"{css.relative_to(site)}: broken url(): {url}")
    for e in errors:
        print(f"::error::{e}")
    print(f"Checked {sum(1 for _ in site.rglob('*.html'))} pages: {len(errors)} problems.")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
