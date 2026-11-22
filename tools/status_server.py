#!/usr/bin/env python3
"""Serve the transcript status page on this computer only (http://localhost:8765)."""

import http.server
import os

TOOLS = os.path.dirname(os.path.abspath(__file__))
PAGE = os.path.join(TOOLS, "status.html")
STATUS = os.path.join(TOOLS, "out", "status.json")


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path in ("/", "/index.html"):
            self.send(PAGE, "text/html; charset=utf-8")
        elif self.path.startswith("/status.json"):
            self.send(STATUS, "application/json")
        else:
            self.send_error(404)

    def send(self, path, kind):
        try:
            with open(path, "rb") as source:
                body = source.read()
        except FileNotFoundError:
            body, kind = b'{"state":"idle"}', "application/json"
        self.send_response(200)
        self.send_header("Content-Type", kind)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    # Loopback only: nothing on the network can see this page.
    http.server.ThreadingHTTPServer(("127.0.0.1", 8765), Handler).serve_forever()
