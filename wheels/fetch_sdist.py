"""Downloads a package's sdist from PyPI and checks its sha256.

Usage: python fetch_sdist.py <name> <version> <dest-dir>   (prints the file path)
"""

import hashlib
import json
import os
import sys
import urllib.request


def main(name, version, dest):
    with urllib.request.urlopen(f"https://pypi.org/pypi/{name}/{version}/json", timeout=60) as r:
        release = json.load(r)
    sdist = next(u for u in release["urls"] if u["packagetype"] == "sdist")
    os.makedirs(dest, exist_ok=True)
    path = os.path.join(dest, sdist["filename"])
    with urllib.request.urlopen(sdist["url"], timeout=300) as r, open(path, "wb") as f:
        f.write(r.read())
    with open(path, "rb") as f:
        digest = hashlib.sha256(f.read()).hexdigest()
    if digest != sdist["digests"]["sha256"]:
        sys.exit(f"sha256 mismatch for {path}: {digest}")
    print(path)


if __name__ == "__main__":
    main(*sys.argv[1:4])
