#!/usr/bin/env python3
"""Gera assets/search-index.js a partir das paginas HTML do manual.

Uso (na pasta docs/manual):  python3 build-search.py
Rode novamente sempre que editar o texto das paginas.
"""
import glob
import json
import os
import re
from html.parser import HTMLParser

HERE = os.path.dirname(os.path.abspath(__file__))


class P(HTMLParser):
    def __init__(self):
        super().__init__()
        self.title = ""
        self.entries = []          # {h, a, t}
        self.cur = None
        self.in_h = None
        self.in_article = False
        self.skip = 0

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag == "article":
            self.in_article = True
        if not self.in_article:
            return
        if tag in ("script", "style", "pre"):
            self.skip += 1
        if tag in ("h1", "h2", "h3"):
            self.in_h = tag
            self.cur = {"h": "", "a": a.get("id", ""), "t": ""}
            self.entries.append(self.cur)

    def handle_endtag(self, tag):
        if tag in ("script", "style", "pre") and self.skip:
            self.skip -= 1
        if tag in ("h1", "h2", "h3"):
            self.in_h = None
        if tag == "article":
            self.in_article = False

    def handle_data(self, data):
        if not self.in_article or self.skip or self.cur is None:
            return
        if self.in_h:
            self.cur["h"] += data
        else:
            self.cur["t"] += " " + data


def clean(s):
    return re.sub(r"\s+", " ", s).strip()


index = []
for path in sorted(glob.glob(os.path.join(HERE, "*.html"))):
    name = os.path.basename(path)
    p = P()
    p.feed(open(path, encoding="utf-8").read())
    if not p.entries:
        continue
    page_title = clean(p.entries[0]["h"])
    for e in p.entries:
        text = clean(e["t"])[:420]
        index.append({"p": name, "pt": page_title, "h": clean(e["h"]), "a": e["a"], "t": text})

out = os.path.join(HERE, "assets", "search-index.js")
with open(out, "w", encoding="utf-8") as f:
    f.write("window.MANUAL_INDEX = " + json.dumps(index, ensure_ascii=False) + ";\n")
print(f"{len(index)} entradas -> {os.path.relpath(out, HERE)}")
