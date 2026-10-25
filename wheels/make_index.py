"""Builds the static site for GitHub Pages: a PEP 503 simple index of Android wheels.

Usage: python make_index.py <wheel-dir> <site-dir> <index-url>

Layout:
    <site>/wheels/<file>.whl
    <site>/simple/index.html             one link per project
    <site>/simple/<project>/index.html   one link per wheel, with its sha256
    <site>/index.html                    how to use it
"""

import hashlib
import html
import os
import re
import shutil
import sys


def normalize(name):
    return re.sub(r"[-_.]+", "-", name).lower()


def page(title, body):
    return (
        "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"utf-8\">\n"
        f"<meta name=\"pypi:repository-version\" content=\"1.0\">\n<title>{html.escape(title)}</title>\n"
        f"</head>\n<body>\n{body}</body>\n</html>\n"
    )


def main(wheel_dir, site_dir, index_url):
    wheels = sorted(f for f in os.listdir(wheel_dir) if f.endswith(".whl"))
    os.makedirs(os.path.join(site_dir, "wheels"), exist_ok=True)
    projects = {}
    for name in wheels:
        dest = os.path.join(site_dir, "wheels", name)
        if os.path.abspath(os.path.join(wheel_dir, name)) != os.path.abspath(dest):
            shutil.copyfile(os.path.join(wheel_dir, name), dest)
        with open(dest, "rb") as f:
            digest = hashlib.sha256(f.read()).hexdigest()
        projects.setdefault(normalize(name.split("-")[0]), []).append((name, digest))

    simple = os.path.join(site_dir, "simple")
    shutil.rmtree(simple, ignore_errors=True)
    os.makedirs(simple)
    links = "".join(f'<a href="{p}/">{p}</a><br>\n' for p in sorted(projects))
    with open(os.path.join(simple, "index.html"), "w") as f:
        f.write(page("Simple index", links))
    for project, files in projects.items():
        os.makedirs(os.path.join(simple, project))
        body = f"<h1>Links for {html.escape(project)}</h1>\n" + "".join(
            f'<a href="../../wheels/{html.escape(n)}#sha256={d}">{html.escape(n)}</a><br>\n'
            for n, d in sorted(files)
        )
        with open(os.path.join(simple, project, "index.html"), "w") as f:
            f.write(page(f"Links for {project}", body))

    rows = "".join(
        f"<li><a href=\"simple/{p}/\">{p}</a>: {len(files)} wheel(s)</li>\n"
        for p, files in sorted(projects.items())
    )
    intro = (
        "<h1>Pyrite Android wheels</h1>\n"
        "<p>CPython 3.15 wheels for Android (arm64-v8a, x86_64) built with cibuildwheel "
        "for packages that don't publish Android wheels on PyPI yet.</p>\n"
        f"<pre>pip install --extra-index-url {html.escape(index_url)} numpy</pre>\n"
        f"<ul>\n{rows}</ul>\n"
    )
    with open(os.path.join(site_dir, "index.html"), "w") as f:
        f.write(page("Pyrite Android wheels", intro))
    # Serve files as-is (no Jekyll processing).
    open(os.path.join(site_dir, ".nojekyll"), "w").close()
    print(f"{len(wheels)} wheels, {len(projects)} projects")


if __name__ == "__main__":
    main(*sys.argv[1:4])
