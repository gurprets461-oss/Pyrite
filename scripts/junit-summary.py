"""Prints one line per test case from JUnit XML results under a directory."""

import pathlib
import sys
import xml.etree.ElementTree as ET

total = failed = 0
for path in sorted(pathlib.Path(sys.argv[1]).rglob("*.xml")):
    for case in ET.parse(path).getroot().iter("testcase"):
        total += 1
        problems = case.findall("failure") + case.findall("error")
        status = "FAIL" if problems else ("SKIP" if case.find("skipped") is not None else "PASS")
        failed += bool(problems)
        print(f"  {status}  {case.get('classname')}.{case.get('name')}  ({case.get('time')}s)")
        for p in problems:
            text = (p.text or p.get("message") or "")[:3000]
            print("    " + text.replace("\n", "\n    "))
print(f"{total} tests, {failed} failed")
