# Files are written next to the script (it runs in its own folder).
import json
from pathlib import Path

data = {"language": "Python", "app": "Pyrite", "squares": [n * n for n in range(10)]}
path = Path("example_output.json")
path.write_text(json.dumps(data, indent=2))
print(f"Wrote {path.resolve()} ({path.stat().st_size} bytes)")

loaded = json.loads(path.read_text())
print("Squares:", loaded["squares"])
print("Check the Files tab: example_output.json is there.")
