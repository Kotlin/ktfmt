#!/usr/bin/env python3

import argparse
import json
import os
import shutil
import subprocess
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
PORT = 8000

def do_format(jar, code, experimental):
    args = ["java", "-jar", jar]
    if experimental:
        args.append("--experimental-engine")
    args.append("-")
    result = subprocess.run(
        args,
        input=code.encode("utf-8"),
        capture_output=True,
    )

    if result.returncode != 0:
        message = result.stderr.decode("utf-8", "replace").strip()
        return False, message or f"ktfmt exited with code {result.returncode}"
    return True, result.stdout.decode("utf-8", "replace")


class PlaygroundHandler(SimpleHTTPRequestHandler):
    jar = ""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(SCRIPT_DIR), **kwargs)

    def do_POST(self):
        if self.path != "/format":
            self.send_error(404)
            return

        length = int(self.headers.get("Content-Length") or 0)
        code = self.rfile.read(length).decode("utf-8", "replace")

        default_ok, default_text = do_format(self.jar, code, experimental=False)
        experimental_ok, experimental_text = do_format(self.jar, code, experimental=True)
        payload = json.dumps(
            {
                "default": {"ok": default_ok, "text": default_text},
                "experimental": {"ok": experimental_ok, "text": experimental_text},
            }
        ).encode("utf-8")

        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format, *args):
        pass


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("jar")
    args = parser.parse_args()

    if not os.path.isfile(args.jar):
        parser.error(f"not a ktfmt jar: {args.jar}")
    if shutil.which("java") is None:
        parser.error("'java' not found on PATH")

    PlaygroundHandler.jar = args.jar
    print(f"ktfmt playground on http://localhost:{PORT}  (jar: {args.jar})")
    try:
        HTTPServer(("127.0.0.1", PORT), PlaygroundHandler).serve_forever()
    except KeyboardInterrupt:
        print()


if __name__ == "__main__":
    main()
