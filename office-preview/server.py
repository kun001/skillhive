"""Internal-only preview worker with bounded uploads, queue, runtime and disk cache."""
import concurrent.futures
import json
import logging
import os
import re
import shutil
import signal
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

CACHE = Path(os.getenv("PREVIEW_CACHE", "/cache"))
CACHE.mkdir(parents=True, exist_ok=True)
# Failed/interrupted conversions are disposable and may succeed with a new renderer.
for cached in CACHE.iterdir():
    if cached.is_dir() and (cached / "state.json").is_file():
        try:
            if json.loads((cached / "state.json").read_text(encoding="utf-8"))["status"] != "READY":
                shutil.rmtree(cached)
        except (ValueError, KeyError):
            shutil.rmtree(cached)
LOCK = threading.RLock()
POOL = concurrent.futures.ThreadPoolExecutor(max_workers=1)
PENDING = set()
MAX_BYTES = 100 * 1024 * 1024
KEY = re.compile(r"^[a-f0-9]{64}$")


def write_state(root, state):
    temporary = root / "state.tmp"
    temporary.write_text(json.dumps(state, ensure_ascii=False), encoding="utf-8")
    temporary.replace(root / "state.json")


def render(key, source):
    root = source.parent
    process = None
    try:
        process = subprocess.Popen([sys.executable, "worker.py", str(source)], start_new_session=True,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        try:
            _, errors = process.communicate(timeout=90)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.communicate()
            raise RuntimeError("Preview timed out")
        if process.returncode:
            logging.warning("Preview %s failed: %s", key, errors.decode(errors="replace")[-1500:])
            raise RuntimeError("Cannot render document")
        state = json.loads((root / "result.json").read_text(encoding="utf-8"))
    except Exception:
        logging.exception("Preview generation failed for %s", key)
        state = {"status": "FAILED"}
    finally:
        if process is not None:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        # Keep only page images and the small manifest; never retain original uploads.
        for path in root.iterdir():
            if path.name not in ("state.json",) and path.suffix != ".jpg":
                shutil.rmtree(path) if path.is_dir() else path.unlink(missing_ok=True)
    with LOCK:
        write_state(root, state)
        PENDING.discard(key)


class Handler(BaseHTTPRequestHandler):
    def respond(self, status, payload):
        data = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def route(self):
        parts = urlsplit(self.path).path.strip("/").split("/")
        if len(parts) < 2 or parts[0] != "previews" or not KEY.fullmatch(parts[1]):
            return None
        return parts

    def do_GET(self):
        if self.path == "/health":
            return self.respond(200, {"status": "UP"})
        parts = self.route()
        if not parts:
            return self.respond(404, {})
        root = CACHE / parts[1]
        with LOCK:
            if not (root / "state.json").is_file():
                return self.respond(404, {})
            state = json.loads((root / "state.json").read_text(encoding="utf-8"))
            if state["status"] == "PROCESSING" and parts[1] not in PENDING:
                shutil.rmtree(root)
                return self.respond(404, {})
            os.utime(root, None)
            if len(parts) == 2:
                return self.respond(200, state)
            if len(parts) != 4 or parts[2] != "pages" or parts[3] not in {str(n) for n in range(1, 6)} or state["status"] != "READY":
                return self.respond(404, {})
            image = root / f"{parts[3]}.jpg"
            if not image.is_file():
                return self.respond(404, {})
            data = image.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "image/jpeg")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        parts = self.route()
        extension = parse_qs(urlsplit(self.path).query).get("extension", [""])[0]
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        if not parts or len(parts) != 2 or extension not in ("doc", "docx", "ppt", "pptx", "xls", "xlsx") or not 0 < length <= MAX_BYTES:
            self.close_connection = True
            return self.respond(400, {})
        key, root = parts[1], CACHE / parts[1]
        with LOCK:
            if (root / "state.json").is_file():
                self.close_connection = True
                return self.respond(200, json.loads((root / "state.json").read_text(encoding="utf-8")))
            if len(PENDING) >= 4:
                self.close_connection = True
                return self.respond(503, {})
            # LRU cap: at most 64 documents and 1 GiB of completed artifacts.
            completed = sorted((p for p in CACHE.iterdir() if p.is_dir() and p.name not in PENDING), key=lambda p: p.stat().st_mtime)
            total = sum(f.stat().st_size for p in completed for f in p.rglob("*") if f.is_file())
            while completed and (len(completed) >= 64 or total > 1024 * 1024 * 1024):
                victim = completed.pop(0)
                total -= sum(f.stat().st_size for f in victim.rglob("*") if f.is_file())
                shutil.rmtree(victim)
            root.mkdir(exist_ok=True)
            PENDING.add(key)
            write_state(root, {"status": "PROCESSING"})
        source = root / f"source.{extension}"
        try:
            self.connection.settimeout(30)
            with source.open("wb") as output:
                remaining = length
                while remaining:
                    chunk = self.rfile.read(min(remaining, 1024 * 1024))
                    if not chunk:
                        raise ValueError("Incomplete upload")
                    output.write(chunk)
                    remaining -= len(chunk)
            POOL.submit(render, key, source)
        except Exception:
            with LOCK:
                PENDING.discard(key)
                shutil.rmtree(root)
            self.close_connection = True
            return self.respond(400, {})
        self.respond(202, {"status": "PROCESSING"})


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8090), Handler).serve_forever()
