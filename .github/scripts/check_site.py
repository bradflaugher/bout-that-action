#!/usr/bin/env python3
"""Checks the static website in site/ before it's published.

Every local href and src (and CSS url() and @import) must resolve to a file
in the site, every #fragment to an id on its page, and nothing may load from
another origin: links out are fine, but styles, fonts and images all ship
with the site. There is no JavaScript at all (no <script>, no on* handlers,
no javascript: URLs), no <base>, CSS only in stylesheets, and the pages other places link to (the landing page,
the privacy policy the app and Play open, the 404 page) must exist. Paths starting with the
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
REQUIRED = ["index.html", "privacy/index.html", "404.html"]

# Attributes that make the browser fetch something (as opposed to <a href>).
LOADS = {("img", "src"), ("script", "src"), ("source", "src"), ("source", "srcset"),
         ("img", "srcset"), ("iframe", "src"), ("video", "src"), ("audio", "src"),
         ("video", "poster"), ("object", "data"), ("embed", "src"), ("track", "src"),
         ("input", "src")}
URL_ATTRS = {"href", "src", "srcset", "poster", "data"}
CSS_URLS = re.compile(r"url\(\s*['\"]?([^'\")]+)|@import\s+['\"]([^'\"]+)['\"]")


class Refs(HTMLParser):
    def __init__(self):
        super().__init__()
        self.refs = []  # (url, loads)
        self.ids = set()
        self.scripts = []

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "script":
            self.scripts.append("<script>")
        if tag == "base":
            self.scripts.append("<base> (it re-points every relative URL)")
        if tag == "style":
            self.scripts.append("<style> block (keep CSS in assets/, where it's checked)")
        if "url(" in a.get("style", "") or "@import" in a.get("style", ""):
            self.scripts.append(f"url() in a style attribute on <{tag}>")
        for name, value in attrs:
            if name.startswith("on"):
                self.scripts.append(f"{name}= on <{tag}>")
            elif value and value.strip().lower().startswith("javascript:"):
                self.scripts.append(f"javascript: URL on <{tag}>")
        if "id" in a:
            self.ids.add(a["id"])
        for name, value in attrs:
            if value is None:
                continue
            if name in URL_ATTRS and not (name == "data" and tag != "object"):
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
    parsed = {}
    for name in REQUIRED:
        if not (site / name).is_file():
            errors.append(f"missing {name}")
    for page in sorted(site.rglob("*.html")):
        parser = Refs()
        parser.feed(page.read_text(encoding="utf-8"))
        parsed[page.resolve()] = parser
    for page, parser in parsed.items():
        for js in parser.scripts:
            errors.append(f"{page.relative_to(site)}: not allowed: {js}")
        for url, loads in parser.refs:
            scheme = urlsplit(url).scheme
            if scheme in ("http", "https") or url.startswith("//"):
                if loads:
                    errors.append(f"{page.relative_to(site)}: loads from another origin: {url}")
                continue
            if scheme in ("mailto", "data", "javascript"):
                continue
            target = resolve(site, page, url)
            if target is None:
                errors.append(f"{page.relative_to(site)}: absolute path outside {BASE}: {url}")
            elif not target.resolve().is_relative_to(site) or not target.is_file():
                errors.append(f"{page.relative_to(site)}: broken link: {url}")
            else:
                fragment = urlsplit(url).fragment
                ids = parsed[target.resolve()].ids if target.suffix == ".html" else None
                if fragment and ids is not None and unquote(fragment) not in ids:
                    errors.append(f"{page.relative_to(site)}: no id for fragment: {url}")
    for css in sorted(site.rglob("*.css")):
        text = css.read_text(encoding="utf-8")
        for url in (a or b for a, b in CSS_URLS.findall(text)):
            if urlsplit(url).scheme or url.startswith("//"):
                if not url.startswith("data:"):
                    errors.append(f"{css.relative_to(site)}: loads from another origin: {url}")
                continue
            target = (css.parent / unquote(urlsplit(url).path)).resolve()
            if not target.is_relative_to(site) or not target.is_file():
                errors.append(f"{css.relative_to(site)}: broken url(): {url}")
    for e in errors:
        print(f"::error::{e}")
    print(f"Checked {sum(1 for _ in site.rglob('*.html'))} pages: {len(errors)} problems.")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
