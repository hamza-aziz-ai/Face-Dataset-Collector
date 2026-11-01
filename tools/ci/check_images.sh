#!/usr/bin/env bash
# Fails when an image file is not listed in image-allowlist.txt, so that every new image gets a human review.
# Assumes it runs from the repo root. This reduces accidents; it does not prove the repo has no real photos, because it matches file extensions only.
set -euo pipefail

allowlist="tools/ci/image-allowlist.txt"

# Includes untracked files so the check also works before committing.
# WHY NUL-separated: git quotes a non-ASCII name in plain output, so "photo-é.jpg" would end in a quote and slip past the extension match.
# tr strips CR because git may check the allowlist out with CRLF on Windows.
unapproved=$(comm -z -23 \
  <(git ls-files -z --cached --others --exclude-standard \
    | grep -zaiE '\.(png|jpe?g|webp|gif|bmp|tiff?|heic|heif|avif|raw|dng)$' | LC_ALL=C sort -zu || true) \
  <(tr -d '\r' < "$allowlist" | tr '\n' '\0' | LC_ALL=C sort -zu) | tr '\0' '\n')

if [ -n "$unapproved" ]; then
  echo "Images not in $allowlist:" >&2
  echo "$unapproved" >&2
  echo "Real photos of people are never allowed. Review the file, then add it to the allowlist only if it is safe." >&2
  exit 1
fi
echo "Image check passed."
