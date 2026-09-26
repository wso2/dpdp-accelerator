#!/usr/bin/env python3
"""
DPDP Reference Webhook Listener (Python Standard Library - Zero External Dependencies)

A simple, lightweight receiver for verifying subscriptions and inspecting delivered events.
Logs all verification challenges and event payloads directly to the console.

Requirements:
  Python 3.9+ (no pip packages or virtual environment required)

Usage:
  # Optional: set shared secret to verify HMAC signatures
  export SHARED_SECRET="my-shared-secret"

  # Optional overrides (defaults: PORT=8443, HOST=127.0.0.1, CALLBACK_PATH=/dpdp/events)
  export PORT=8443
  export CALLBACK_PATH="/dpdp/events"

  python3 docs/static/examples/webhook-listener.py
"""

import sys
import os
import json
import base64
import hmac
import hashlib
from http.server import HTTPServer, BaseHTTPRequestHandler
from datetime import datetime

PORT = int(os.environ.get("PORT", "8443"))
HOST = os.environ.get("HOST", "127.0.0.1")
CALLBACK_PATH = os.environ.get("CALLBACK_PATH", "/dpdp/events")
SHARED_SECRET = os.environ.get("SHARED_SECRET", "").strip()


def log(level, message):
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{ts}] [{level}] {message}", flush=True)


class WebhookRequestHandler(BaseHTTPRequestHandler):
    server_version = "DPDPWebhookListener/1.0"

    def _handle_unsupported_method(self):
        log("INFO", f"--> Incoming {self.command} {self.path} from {self.client_address[0]}")
        log("WARN", f"<-- Method '{self.command}' not allowed. Webhook endpoints accept POST only. Returning HTTP 405.")
        self.send_error(405, "Method Not Allowed")

    def __getattr__(self, name):
        # Catch-all for any non-POST HTTP method (GET, PUT, DELETE, TRACE, CONNECT, etc.)
        if name.startswith("do_"):
            return self._handle_unsupported_method
        raise AttributeError(name)

    def do_POST(self):
        log("INFO", f"--> Incoming POST {self.path} from {self.client_address[0]}")

        # 1. Validate route path
        if self.path.split("?")[0] != CALLBACK_PATH:
            log("WARN", f"<-- Route not found: '{self.path}' (expected '{CALLBACK_PATH}'). Returning HTTP 404.")
            self.send_error(404, "Unknown path")
            return

        # 2. Check Content-Type
        content_type = self.headers.get("Content-Type", "")
        if "application/json" not in content_type:
            log("WARN", f"<-- Unsupported Content-Type: '{content_type}' (expected 'application/json'). Returning HTTP 415.")
            self.send_error(415, "Content-Type must be application/json")
            return

        # 3. Read raw request body
        try:
            content_length = int(self.headers.get("Content-Length", 0))
        except (ValueError, TypeError):
            log("WARN", "<-- Invalid Content-Length header. Returning HTTP 400.")
            self.send_error(400, "Invalid Content-Length")
            return

        if content_length > 1024 * 1024:
            log("WARN", "<-- Request body exceeds 1 MiB limit. Returning HTTP 413.")
            self.send_error(413, "Payload too large (max 1 MiB)")
            return

        raw_body = self.rfile.read(content_length)

        try:
            message = json.loads(raw_body.decode("utf-8"))
        except Exception:
            log("ERROR", "<-- Malformed JSON body. Returning HTTP 400.")
            self.send_error(400, "Malformed JSON body")
            return

        # 4. Handle Subscription Verification
        if isinstance(message, dict) and message.get("type") == "subscription.verification":
            self.handle_verification(message)
            return

        # 5. Handle Event Delivery
        self.handle_event_delivery(raw_body, message)

    def handle_verification(self, message):
        log("INFO", "================ [SUBSCRIPTION VERIFICATION] ================")
        log("INFO", f"Verification Payload:\n{json.dumps(message, indent=2)}")

        challenge = message.get("challenge")
        if not challenge or not isinstance(challenge, str):
            log("WARN", "<-- Verification rejected: invalid or missing challenge. Returning HTTP 400.")
            self.send_error(400, "Invalid or missing challenge")
            return

        sub_id = message.get("subscriptionId", "unknown")
        topics = message.get("topics", [])

        # Respond HTTP 200 with raw challenge text
        body_bytes = challenge.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body_bytes)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body_bytes)

        log("INFO", f"Verified subscription '{sub_id}' for topics: {topics}")
        log("INFO", f"<-- Returned HTTP 200 OK with challenge: '{challenge}'")
        log("INFO", "=============================================================")

    def handle_event_delivery(self, raw_body, message):
        delivery_id = self.headers.get("Delivery-Id", "unknown")
        event_signature = self.headers.get("Event-Signature", "")

        log("INFO", "==================== [EVENT DELIVERY] ====================")
        log("INFO", f"Delivery-Id:     {delivery_id}")
        log("INFO", f"Event-Signature: {event_signature or '(none)'}")

        # Check HMAC-SHA256 signature if SHARED_SECRET is configured
        if SHARED_SECRET:
            if not event_signature.startswith("sha256="):
                log("ERROR", "<-- Missing or invalid Event-Signature header format. Returning HTTP 401.")
                self.send_error(401, "Missing or invalid Event-Signature header format")
                return

            expected_hmac = hmac.new(SHARED_SECRET.encode("utf-8"), raw_body, hashlib.sha256).hexdigest()
            received_hmac = event_signature[len("sha256="):].strip()

            if not hmac.compare_digest(expected_hmac.lower(), received_hmac.lower()):
                log("ERROR", "<-- HMAC-SHA256 signature verification failed. Returning HTTP 401.")
                self.send_error(401, "HMAC-SHA256 signature mismatch")
                return

            log("INFO", "HMAC-SHA256 signature verified successfully.")
        else:
            log("INFO", "SHARED_SECRET not set; skipped HMAC signature check.")

        # If signedPayload is present, decode compact JWS claims for inspection
        if isinstance(message, dict) and "signedPayload" in message:
            log("INFO", f"Raw Signed JWS:\n{message['signedPayload']}")
            try:
                parts = message["signedPayload"].split(".")
                if len(parts) >= 2:
                    padded = parts[1] + "=" * ((4 - len(parts[1]) % 4) % 4)
                    claims = json.loads(base64.urlsafe_b64decode(padded).decode("utf-8"))
                    log("INFO", f"Decoded Event Payload:\n{json.dumps(claims, indent=2)}")
            except Exception as e:
                log("WARN", f"Could not decode JWS claims: {e}")
        else:
            log("INFO", f"Event Body:\n{json.dumps(message, indent=2)}")

        # Respond HTTP 202 Accepted
        self.send_response(202)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(b'{"status":"accepted"}')
        log("INFO", "<-- Returned HTTP 202 Accepted")
        log("INFO", "==========================================================")

    def log_message(self, format, *args):
        # Capture unhandled internal server messages (e.g. syntax errors or unhandled HTTP verbs)
        msg = format % args
        if any(msg.startswith(prefix) for prefix in ("code 4", "code 5", "Unsupported")):
            log("WARN", f"[HTTP Server] {msg}")


def main():
    server_address = (HOST, PORT)
    httpd = HTTPServer(server_address, WebhookRequestHandler)
    print(f"=============================================================")
    print(f"  DPDP Reference Webhook Listener")
    print(f"  Listening on: http://{HOST}:{PORT}{CALLBACK_PATH}")
    if SHARED_SECRET:
        print(f"  HMAC Verification: ENABLED (shared secret configured)")
    else:
        print(f"  HMAC Verification: DISABLED (set SHARED_SECRET to enable)")
    print(f"  Press Ctrl+C to stop.")
    print(f"=============================================================")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down listener.")
        httpd.server_close()


if __name__ == "__main__":
    main()
