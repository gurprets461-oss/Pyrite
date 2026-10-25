"""Code analysis for the editor, in its own long-lived process.

Protocol: one JSON object per line on stdin, one JSON reply per line on stdout.
  {"id": 1, "type": "check", "source": "..."}
      -> {"id": 1, "errors": [{"line", "col", "end_line", "end_col", "msg"}]}   (1-based)
  {"id": 2, "type": "complete", "source": "...", "line": 3, "col": 8, "path": "..."}
      -> {"id": 2, "items": [{"name", "type", "desc"}]}
  {"id": 3, "type": "signature", "source": "...", "line": 3, "col": 8, "path": "..."}
      -> {"id": 3, "signatures": [{"name", "params": [...], "index"}]}
Errors come back as {"id": n, "error": "..."}.
"""

import ast
import json
import os
import sys
import warnings

MAX_COMPLETIONS = 150


def check(source, path="<editor>"):
    """Syntax errors, using this interpreter's own compiler (exactly what Run will see)."""
    try:
        compile(source, path, "exec", flags=ast.PyCF_ONLY_AST, dont_inherit=True)
        return []
    except SyntaxError as e:
        line = e.lineno or 1
        col = e.offset or 1
        end_line = getattr(e, "end_lineno", None) or line
        end_col = getattr(e, "end_offset", None) or col
        if (end_line, end_col) <= (line, col):
            end_line, end_col = line, col + 1
        return [{"line": line, "col": col, "end_line": end_line, "end_col": end_col,
                 "msg": f"{type(e).__name__}: {e.msg}"}]
    except (ValueError, OverflowError) as e:  # e.g. NUL bytes
        return [{"line": 1, "col": 1, "end_line": 1, "end_col": 2, "msg": str(e)}]


_environment = None


def _env():
    """jedi must analyze in-process: on Android there's no python executable to spawn
    (sys.executable is a placeholder), and jedi would otherwise try to run it."""
    global _environment
    if _environment is None:
        import jedi

        _environment = jedi.InterpreterEnvironment()
    return _environment


def _script(req):
    import jedi

    path = req.get("path") or None
    project = None
    if path:
        project = jedi.Project(os.path.dirname(path), added_sys_path=[os.path.dirname(path)])
    return jedi.Script(req["source"], path=path, project=project, environment=_env())


def complete(req):
    items = []
    for c in _script(req).complete(req["line"], req["col"])[:MAX_COMPLETIONS]:
        items.append({"name": c.name, "type": c.type, "desc": c.description if c.type != "keyword" else ""})
    return items


def signatures(req):
    out = []
    for s in _script(req).get_signatures(req["line"], req["col"]):
        out.append({"name": s.name, "params": [p.to_string() for p in s.params], "index": s.index})
    return out


def handle(req):
    kind = req.get("type")
    if kind == "check":
        return {"errors": check(req["source"], req.get("path") or "<editor>")}
    if kind == "complete":
        return {"items": complete(req)}
    if kind == "signature":
        return {"signatures": signatures(req)}
    if kind == "ping":
        return {"pong": True}
    raise ValueError(f"unknown request type {kind!r}")


def _warm_up():
    """Loading jedi and its stubs takes seconds; do it before the first keystroke needs it."""
    try:
        import jedi

        jedi.Script("import os\nos.path.", environment=_env()).complete(2, 8)
    except Exception:
        pass


def serve(stdin=None, stdout=None):
    stdin = stdin or sys.stdin
    out = stdout or sys.stdout
    # Nothing but replies may go to the reply stream.
    sys.stdout = sys.stderr
    warnings.simplefilter("ignore")
    # jedi isn't thread-safe: warm up here, while early requests wait in the pipe.
    _warm_up()
    for line in stdin:
        line = line.strip()
        if not line:
            continue
        req_id = None
        try:
            req = json.loads(line)
            req_id = req.get("id")
            reply = handle(req)
        except Exception as e:
            reply = {"error": f"{type(e).__name__}: {e}"}
        reply["id"] = req_id
        out.write(json.dumps(reply) + "\n")
        out.flush()
