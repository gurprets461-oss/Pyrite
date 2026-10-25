"""Code analysis for the editor, in its own long-lived process.

Protocol: one JSON object per line on stdin, one JSON reply per line on stdout.
  {"id": 1, "type": "check", "source": "..."}
      -> {"id": 1, "errors": [{"line", "col", "end_line", "end_col", "msg"}]}   (1-based)
  {"id": 2, "type": "complete", "source": "...", "line": 3, "col": 8, "path": "..."}
      -> {"id": 2, "items": [{"name", "type", "desc"}]}
  {"id": 3, "type": "signature", "source": "...", "line": 3, "col": 8, "path": "..."}
      -> {"id": 3, "signatures": [{"name", "params": [...], "index"}]}
Errors come back as {"id": n, "error": "..."}.

Requests are answered in order, but a "check", "complete" or "signature" request that
already has a newer one of the same type waiting behind it is answered with
{"id": n, "superseded": true} without running: while typing, the editor sends one
completion request per keystroke and only wants the last answer.
"""

import ast
import io
import json
import os
import select
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


# Request types where only the newest pending request matters.
_LATEST_ONLY = {"check", "complete", "signature"}


class _Batches:
    """Reads request lines from a pipe: waits for at least one, then takes all that are
    already available, so stale requests can be skipped."""

    def __init__(self, stream):
        self.fd = stream.fileno()
        self.buf = b""
        self.eof = False

    def _read(self, wait):
        if not wait and not select.select([self.fd], [], [], 0)[0]:
            return False
        data = os.read(self.fd, 1 << 16)
        if not data:
            self.eof = True
            return False
        self.buf += data
        return True

    def __iter__(self):
        while True:
            while b"\n" not in self.buf and not self.eof:
                self._read(wait=True)
            while self._read(wait=False):
                pass
            *lines, self.buf = self.buf.split(b"\n")
            if self.eof and self.buf.strip():
                lines.append(self.buf)
                self.buf = b""
            if lines:
                yield [line.decode("utf-8", "replace") for line in lines]
            if self.eof:
                return


def _batches(stdin):
    try:
        return iter(_Batches(stdin))
    except (AttributeError, OSError, io.UnsupportedOperation):  # not a real pipe (tests)
        return ([line] for line in stdin)


def serve(stdin=None, stdout=None):
    stdin = stdin or sys.stdin
    out = stdout or sys.stdout
    # Nothing but replies may go to the reply stream.
    sys.stdout = sys.stderr
    warnings.simplefilter("ignore")
    # jedi isn't thread-safe: warm up here, while early requests wait in the pipe.
    _warm_up()
    for batch in _batches(stdin):
        requests = []
        for line in batch:
            line = line.strip()
            if not line:
                continue
            try:
                req = json.loads(line)
                requests.append((req, None))
            except Exception as e:
                requests.append(({}, e))
        newest = {req.get("type"): i for i, (req, _) in enumerate(requests)}
        for i, (req, error) in enumerate(requests):
            kind = req.get("type")
            try:
                if error is not None:
                    raise error
                if kind in _LATEST_ONLY and newest[kind] != i:
                    reply = {"superseded": True}
                else:
                    reply = handle(req)
            except Exception as e:
                reply = {"error": f"{type(e).__name__}: {e}"}
            reply["id"] = req.get("id")
            out.write(json.dumps(reply) + "\n")
            out.flush()
