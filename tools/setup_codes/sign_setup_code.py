#!/usr/bin/env python3
"""Signs a setup QR code payload for one client, offline, on the machine that holds the private key (D-020).

The private key never enters this repo. The output is not secret: put it in any offline QR generator.

Usage:
  python tools/setup_codes/sign_setup_code.py --key PATH --kid k1 --base-url https://client.example.com --client-id client-a
"""
import argparse
import base64
import json
import subprocess
import sys
from urllib.parse import urlsplit

PREFIX = "enrolment-setup-v1"


def message(base_url, client_id):
    """The exact bytes the app verifies. See setupCodeMessage in the app."""
    return f"{PREFIX}\n{base_url}\n{client_id}".encode("utf-8")


def validate(base_url, client_id):
    parts = urlsplit(base_url)
    if parts.scheme != "https" or not parts.hostname or parts.path not in ("", "/") or parts.query or parts.fragment or parts.username:
        raise ValueError("base_url must be a plain https origin")
    if not client_id or any(c.isspace() for c in client_id) or any(c.isspace() for c in base_url):
        raise ValueError("base_url and client_id must not contain spaces or new lines")


def sign(key_path, kid, base_url, client_id):
    validate(base_url, client_id)
    result = subprocess.run(
        ["openssl", "dgst", "-sha256", "-sign", key_path],
        input=message(base_url, client_id), capture_output=True, check=True,
    )
    return {
        "base_url": base_url,
        "client_id": client_id,
        "kid": kid,
        "sig": base64.b64encode(result.stdout).decode("ascii"),
    }


def main():
    parser = argparse.ArgumentParser(description="Sign a setup QR code payload.")
    parser.add_argument("--key", required=True, help="path to the private key, kept offline")
    parser.add_argument("--kid", required=True, help="key ID, as listed in SetupKeys.kt")
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--client-id", required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(sign(args.key, args.kid, args.base_url, args.client_id), separators=(",", ":")))
    except (ValueError, subprocess.CalledProcessError) as error:
        sys.exit(f"Could not sign: {error}")


if __name__ == "__main__":
    main()
