# CONSTRAINTS

Draft by Claude Opus 5.5 on 2026-10-31, pending owner review. Hard rules for AI assistants on this project. Stop and ask before breaking any of them.

## Face data

- Never capture, store, or upload a face image without the person's informed consent.
- Never send face images or face-derived data off the device unless the owner has approved the destination in `DECISIONS.md`.
- Never log image data, face data, or anything that identifies a person.
- Never commit face images or datasets to git, including test fixtures.
- Always exclude face data from Android backup and device transfer before adding any code that saves it.
- Always provide a way to delete a person's data before adding any code that stores it.

## Protected areas

Pause and get owner approval before changing:

- Camera and storage permissions in `AndroidManifest.xml`.
- `backup_rules.xml` and `data_extraction_rules.xml`.
- Build files, `gradle/`, and signing configuration.
- Any new network call or external host.

## Code

- No new dependency without owner approval and a logged reason, maintainer, and pinned version.
- Face detection stays on-device unless the owner decides otherwise in `DECISIONS.md`.
- Never disable or delete tests or checks to make something pass.

## Git and access

- Never commit secrets, keystores, `local.properties`, or `.env` files.
- Never force push or rewrite history.
- Commit only changes the owner has accepted.
- AI tools never get production credentials or live data.
