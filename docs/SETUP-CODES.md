# SETUP CODES

How setup QR codes are signed (D-020). Only someone holding the private key can make a code a release build accepts.

## Format

The QR code holds this JSON: `{"base_url", "client_id", "client_name", "kid", "sig"}`.

- `sig` is base64 of an ECDSA P-256 SHA-256 signature.
- It covers these UTF-8 bytes, joined by new lines: `enrolment-setup-v1`, `base_url`, `client_id`, `client_name`.
- Staff see the signed `client_name`, not the name the server gives, so a genuine code for one client cannot pose as another.
- `kid` names a public key in `SetupKeys.kt`.

Codes are not secret. A signed code cannot be changed to point elsewhere, but anyone who copies it can read it.

## Create the key, once

Do this on a machine you control, with the file saved outside this repo:

```bash
openssl ecparam -name prime256v1 -genkey -noout -out setup-signing-key.pem
openssl ec -in setup-signing-key.pem -pubout -outform DER | base64 -w0
```

The second command prints the public key. Give only that to the developer, to add to `SetupKeys.kt` under a key ID such as `k1`. Then keep `setup-signing-key.pem` offline, with one backup, and never send it to anyone or any AI tool.

## Sign a code for a client

```bash
python tools/setup_codes/sign_setup_code.py --key PATH --kid k1 --base-url https://client.example.com --client-id client-a --client-name "Client A"
```

Put the printed JSON in any offline QR generator, and hand the code to the client's administrator.

## If the key is lost or leaked

A leaked key can sign a code for any server, so treat it as an emergency. Create a new key, add it under a new key ID, remove the old one, and ship a new app build. Devices stay bound to the server they were set up for, and the app does not re-check them, so any device set up while the key was leaked must be set up again. Reissue codes for any client who still needs one.

## What a code does not stop

Codes never expire, and any genuine code works on any device. Someone holding a real code for client A can set up client B's device for A, after confirming `Use a different server`. Staff would then see client A's name and address at sign-in, so they can notice, but nothing blocks it. Hand out codes as you would hand out a door key.

## Rules by build

- Release: signed codes only. The typed address is gone. With `SetupKeys.kt` empty, every code is refused.
- Debug: signed codes, a typed address, and unsigned codes for the local mock only.
