"""Summarizes `am instrument -r` output: one PASS/FAIL line per test, failures' stack traces.

Usage: python am-instrument-summary.py <am-instrument.log>
Exit status 0 only if the run finished and every test passed.
"""

import sys

CODES = {0: "PASS", -1: "ERROR", -2: "FAIL", -3: "SKIP", -4: "SKIP"}


def main(path):
    status, results, key, finished_ok = {}, [], None, False
    for line in open(path, encoding="utf-8", errors="replace").read().splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, _, value = line[len("INSTRUMENTATION_STATUS: "):].partition("=")
            status[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.split(":")[1])
            if code != 1 and "test" in status:
                results.append((CODES.get(code, f"CODE{code}"), status.get("class", "?"), status["test"],
                                status.get("stack", "")))
            status, key = {}, None
        elif line.startswith("INSTRUMENTATION_"):
            key = None
        elif line.startswith("OK ("):
            finished_ok = True
        elif key is not None:  # continuation of a multi-line value (stack traces)
            status[key] += "\n" + line
    failed = [r for r in results if r[0] in ("FAIL", "ERROR")]
    for verdict, cls, test, _ in results:
        print(f"  {verdict:5} {cls}.{test}")
    for _, cls, test, stack in failed:
        print(f"\n--- {cls}.{test}\n" + "\n".join(stack.strip().splitlines()[:25]))
    print(f"{len(results)} tests, {len(failed)} failed" + ("" if finished_ok or failed else " (run did not finish)"))
    return 0 if finished_ok and not failed and results else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
