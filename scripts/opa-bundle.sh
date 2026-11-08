#!/usr/bin/env bash
# Copyright 2026 Vasantha Kumar
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# @author Vasantha Kumar <vasantha.kumar@hotmail.com>

# Builds the authorisation policy bundle (docs/runbooks/opa-policy-bundles.md, ADR-068): checks and tests the Rego, builds
# a versioned bundle of it (no test files), signs it when it is given a key, proves the signature verifies, and writes a manifest
# next to it. The same script runs on a laptop and in CI, so what CI publishes is what you can build and check yourself.
#
#   scripts/opa-bundle.sh [--out dist/opa] [--version 2026.11.8-abc1234]
#
#   OPA_BIN             a local `opa` to use; by default the image the platform runs (openpolicyagent/opa, run with Docker)
#   OPA_IMAGE           that image (default: the one in infra/docker-compose.yml)
#   OPA_SIGNING_KEY     path to an RSA private key (PEM): signs the bundle. Without it the bundle is unsigned and says so
#   OPA_SIGNING_KEY_ID  the name the verifying side gives the public key in its configuration (default: cm-policy); recorded in the
#                       manifest, since the bundle itself does not carry it
#   OPA_VERIFICATION_KEY  path to the matching public key (PEM); when signing, the signature is verified with it (default: derived
#                       from the private key, so a bundle that does not verify never leaves the script)
#
# Prints names, versions and checksums; never a key.
set -euo pipefail
cd "$(dirname "$0")/.."

out="dist/opa"
version=""
while [ $# -gt 0 ]; do
  case "$1" in
    --out) out="$2"; shift 2 ;;
    --version) version="$2"; shift 2 ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

: "${OPA_IMAGE:=$(grep -o 'openpolicyagent/opa:[0-9.]*' infra/docker-compose.yml | head -1)}"
: "${OPA_SIGNING_KEY_ID:=cm-policy}"
policies="infra/opa/policies"
mkdir -p "$out"

# opa, here or in the image. In Docker the repository is mounted read-only at /repo and the output at /out; paths given to opa are
# the ones inside the container, so callers write them as /repo/... and /out/...
opa() {
  if [ -n "${OPA_BIN:-}" ]; then
    "$OPA_BIN" "$@"
  else
    # as the caller's own user: the image's user is another one, which cannot read a private key that is mode 600 and ours
    # (openssl writes keys that way), nor write into a directory we made
    MSYS_NO_PATHCONV=1 docker run --rm --user "$(id -u):$(id -g)" -v "$PWD:/repo:ro" -v "$PWD/$out:/out" ${EXTRA_MOUNTS:-} "$OPA_IMAGE" "$@"
  fi
}
# a path as opa sees it
if [ -n "${OPA_BIN:-}" ]; then repo="$PWD"; outp="$PWD/$out"; else repo="/repo"; outp="/out"; fi

sha() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }

echo "Checking the policies ..."
opa check --strict "$repo/$policies"
echo "Testing them ..."
opa test "$repo/$policies" > "$out/test-report.txt" || { cat "$out/test-report.txt" >&2; echo "The policy tests failed" >&2; exit 1; }
tail -1 "$out/test-report.txt"

revision="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
if [ -z "$version" ]; then
  version="$(date -u +%Y.%-m.%-d)-$(printf '%s' "$revision" | cut -c1-7)"
  # a bundle built from policies that are not what was committed says so, so it is never mistaken for the committed one
  if [ -n "$(git status --porcelain -- "$policies" 2>/dev/null)" ]; then version="$version-dirty"; fi
fi
bundle="cm-authz-$version.tar.gz"

sign=()
signed=false
if [ -n "${OPA_SIGNING_KEY:-}" ]; then
  [ -f "$OPA_SIGNING_KEY" ] || { echo "OPA_SIGNING_KEY is not a file" >&2; exit 2; }
  # the key is handed to opa as a file in the output directory's own scratch mount, never on the command line
  cp "$OPA_SIGNING_KEY" "$out/.signing-key.pem"
  trap 'rm -f "$out/.signing-key.pem" "$out/.verification-key.pem"' EXIT
  sign=(--signing-alg RS256 --signing-key "$outp/.signing-key.pem")
  signed=true
fi

echo "Building $bundle ($([ "$signed" = true ] && echo signed || echo unsigned)) ..."
opa build -b "$repo/$policies" --ignore '*_test.rego' --revision "$revision" "${sign[@]}" -o "$outp/$bundle"

if [ "$signed" = true ]; then
  if [ -n "${OPA_VERIFICATION_KEY:-}" ]; then
    cp "$OPA_VERIFICATION_KEY" "$out/.verification-key.pem"
  else
    openssl rsa -in "$OPA_SIGNING_KEY" -pubout -out "$out/.verification-key.pem" 2> /dev/null
  fi
  # a bundle whose signature does not verify must not leave here: rebuilding it from the tarball checks the signature
  echo "Verifying the signature ..."
  opa build --bundle "$outp/$bundle" --verification-key "$outp/.verification-key.pem" --verification-key-id "$OPA_SIGNING_KEY_ID" \
    --signing-alg RS256 -o "$outp/.verified.tar.gz" > /dev/null
  rm -f "$out/.verified.tar.gz"
  cp "$out/.verification-key.pem" "$out/cm-authz-$version.public.pem"
fi

printf '{"name":"cm-authz","version":"%s","revision":"%s","file":"%s","sha256":"%s","signed":%s,"keyId":%s,"builtAt":"%s"}\n' \
  "$version" "$revision" "$bundle" "$(sha "$out/$bundle")" "$signed" "$([ "$signed" = true ] && printf '"%s"' "$OPA_SIGNING_KEY_ID" || echo null)" \
  "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$out/manifest-$version.json"
cp "$out/manifest-$version.json" "$out/manifest-latest.json"
echo "Bundle: $out/$bundle"
echo "Manifest: $out/manifest-$version.json"
