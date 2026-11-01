#!/usr/bin/env bash
# Fails when an image file is not listed in image-allowlist.txt, so that every new image gets a human review.
# Assumes it runs from the repo root. This reduces accidents; it does not prove the repo has no real photos, because it matches file extensions only.
set -euo pipefail

allowlist="tools/ci/image-allowlist.txt"

# Includes untracked files so the check also works before committing.
# tr strips CR because git may check the allowlist out with CRLF on Windows.
unapproved=$(comm -23 \
  <(git ls-files --cached --others --exclude-standard \
    | grep -iE '\.(png|jpe?g|webp|gif|bmp|tiff?|heic|heif|avif|raw|dng)$' | LC_ALL=C sort -u || true) \
  <(tr -d '\r' < "$allowlist" | LC_ALL=C sort -u))

if [ -n "$unapproved" ]; then
  echo "Images not in $allowlist:" >&2
  echo "$unapproved" >&2
  echo "Real photos of people are never allowed. Review the file, then add it to the allowlist only if it is safe." >&2
  exit 1
fi
echo "Image check passed."
