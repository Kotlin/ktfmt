#!/usr/bin/env python3

import argparse
import json
import os
import subprocess
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
PORT = 8000

def do_format(binary, code, experimental):
    args = [binary]
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
    binary = ""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(SCRIPT_DIR), **kwargs)

    def do_POST(self):
        if self.path != "/format":
            self.send_error(404)
            return

        length = int(self.headers.get("Content-Length") or 0)
        code = self.rfile.read(length).decode("utf-8", "replace")

        default_ok, default_text = do_format(self.binary, code, experimental=False)
        experimental_ok, experimental_text = do_format(self.binary, code, experimental=True)
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
    parser.add_argument("binary")
    args = parser.parse_args()

    if not os.path.isfile(args.binary) or not os.access(args.binary, os.X_OK):
        parser.error(f"not an executable ktfmt binary: {args.binary}")

    PlaygroundHandler.binary = args.binary
    print(f"ktfmt playground on http://localhost:{PORT}  (binary: {args.binary})")
    try:
        HTTPServer(("127.0.0.1", PORT), PlaygroundHandler).serve_forever()
    except KeyboardInterrupt:
        print()


if __name__ == "__main__":
    main()
