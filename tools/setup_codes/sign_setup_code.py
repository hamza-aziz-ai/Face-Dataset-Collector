#!/usr/bin/env python3
"""Signs a setup QR code payload for one client, offline, on the machine that holds the private key (D-020).

The private key never enters this repo. The output is not secret: put it in any offline QR generator.

Usage:
  python tools/setup_codes/sign_setup_code.py --key PATH --kid k1 --base-url https://client.example.com --client-id client-a --client-name "Client A"
"""
import argparse
import base64
import json
import os
import re
import subprocess
import sys
from urllib.parse import urlsplit

PREFIX = "enrolment-setup-v1"
# Git Bash has openssl on its PATH, but PowerShell and Anaconda Prompt usually do not.
OPENSSL = os.environ.get("OPENSSL", "openssl")
HOST_LABEL = re.compile(r"[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")


def message(base_url, client_id, client_name):
    """The exact bytes the app verifies. See setupCodeMessage in the app."""
    return f"{PREFIX}\n{base_url}\n{client_id}\n{client_name}".encode("utf-8")


def validate(base_url, client_id, client_name):
    parts = urlsplit(base_url)
    if parts.scheme != "https" or not parts.hostname or parts.path not in ("", "/") or "?" in base_url or "#" in base_url or parts.username:
        raise ValueError("base_url must be a plain https origin")
    parts.port  # Raises ValueError outside 0 to 65535.
    if parts.port == 0 or not all(HOST_LABEL.fullmatch(label) for label in parts.hostname.split(".")):
        raise ValueError("base_url has a host or port the app would refuse")
    if not client_id or any(c.isspace() for c in client_id) or any(c.isspace() for c in base_url):
        raise ValueError("base_url and client_id must not contain spaces or new lines")
    if not client_name.strip() or len(client_name) > 80 or any(not c.isprintable() for c in client_name):
        raise ValueError("client_name must be 1 to 80 printable characters")


def sign(key_path, kid, base_url, client_id, client_name):
    validate(base_url, client_id, client_name)
    result = subprocess.run(
        [OPENSSL, "dgst", "-sha256", "-sign", key_path],
        input=message(base_url, client_id, client_name), capture_output=True, check=True,
    )
    return {
        "base_url": base_url,
        "client_id": client_id,
        "client_name": client_name,
        "kid": kid,
        "sig": base64.b64encode(result.stdout).decode("ascii"),
    }


def main():
    parser = argparse.ArgumentParser(description="Sign a setup QR code payload.")
    parser.add_argument("--key", required=True, help="path to the private key, kept offline")
    parser.add_argument("--kid", required=True, help="key ID, as listed in SetupKeys.kt")
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--client-id", required=True)
    parser.add_argument("--client-name", required=True, help="the name staff see, 80 characters at most")
    args = parser.parse_args()
    try:
        print(json.dumps(sign(args.key, args.kid, args.base_url, args.client_id, args.client_name), separators=(",", ":")))
    except FileNotFoundError:
        sys.exit("Could not sign: openssl was not found. Run this from Git Bash, or set OPENSSL to the full path of openssl.")
    except (ValueError, subprocess.CalledProcessError) as error:
        sys.exit(f"Could not sign: {error}")


if __name__ == "__main__":
    main()
