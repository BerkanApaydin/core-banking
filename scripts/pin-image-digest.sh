#!/usr/bin/env bash
# Pins k8s/bank-app.yaml to the exact pushed image digest.
# Usage: scripts/pin-image-digest.sh ghcr.io/<owner>/bank-app:v0.0.2
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <registry-repo:tag>" >&2
    exit 2
fi
image_tag="$1"
manifest="k8s/bank-app.yaml"
digest_file="k8s/.image-digest"

command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }

docker build -t "$image_tag" .
docker push "$image_tag"
digest="$(docker inspect --format '{{index .RepoDigests 0}}' "$image_tag")"
if [ -z "$digest" ]; then
    echo "Could not resolve RepoDigest for $image_tag (push may have failed)" >&2
    exit 1
fi

# Replace the image: line with the pinned digest (awk, portable BSD/GNU).
tmp="$(mktemp)"
awk -v img="$digest" '{ if ($0 ~ /^[[:space:]]*image:/) print "          image: " img; else print }' "$manifest" > "$tmp"
mv "$tmp" "$manifest"

{
    echo "$digest"
    echo "pinned_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "git_sha=$(git rev-parse HEAD)"
} > "$digest_file"

echo "Pinned $manifest to $digest"
echo "Recorded in $digest_file"
