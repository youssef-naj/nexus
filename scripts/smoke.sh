#!/usr/bin/env bash
# Smoke test of the running stack (ADR-0034): static site, headers, proxy rules, and one real
# user journey (register, verify by email, sign in, sign out). Needs the demo stack (Mailpit).
set -euo pipefail

BASE="${BASE:-http://localhost:8088}"
MAILPIT="${MAILPIT:-http://localhost:8026}"
JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT

step() { printf 'ok    %s\n' "$1"; }
fail() { printf 'FAIL  %s\n' "$1" >&2; exit 1; }

status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
header() { curl -s -D - -o /dev/null "$1" | tr -d '\r' | grep -i "^$2:" | head -n 1 | cut -d' ' -f2- || true; }
csrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$JAR" | tail -n 1; }

expect_status() { # description, expected status, curl arguments...
  local what="$1" want="$2"
  shift 2
  local got
  got="$(status "$@")"
  [[ "$got" == "$want" ]] || fail "$what: expected $want, got $got"
  step "$what"
}

post_json() { # path, json -> prints the status code
  curl -s -o /dev/null -w '%{http_code}' -b "$JAR" -c "$JAR" \
    -H "X-XSRF-TOKEN: $(csrf)" -H 'Content-Type: application/json' -d "$2" "$BASE$1"
}

# ---- the site ----------------------------------------------------------------------------
expect_status "web server health" 200 "$BASE/healthz"
expect_status "home page" 200 "$BASE/"
csp="$(header "$BASE/" Content-Security-Policy)"
[[ "$csp" == *"script-src 'self'"* ]] || fail "home page has no script policy ($csp)"
step "home page carries the content security policy"
[[ "$(header "$BASE/" Cache-Control)" == *no-store* ]] || fail "home page can be cached"
step "home page is never cached"
expect_status "deep link falls back to the app" 200 "$BASE/orgs/00000000-0000-0000-0000-000000000000/requests"

asset="$(curl -s "$BASE/" | grep -o '/assets/[^"]*\.js' | head -n 1 || true)"
[[ -n "$asset" ]] || fail "no script found in the home page"
[[ "$(header "$BASE$asset" Cache-Control)" == *immutable* ]] || fail "hashed files are not cached for long"
step "hashed files are cached as immutable"
expect_status "a missing file is a 404, not the app" 404 "$BASE/assets/does-not-exist.js"

# ---- the API behind the proxy -----------------------------------------------------------
expect_status "api ping" 200 "$BASE/api/system/ping"
api_csp="$(header "$BASE/api/system/ping" Content-Security-Policy)"
[[ "$api_csp" == *"default-src 'none'"* ]] || fail "api has no strict policy ($api_csp)"
step "api responses carry the strict policy"
expect_status "health endpoints are not exposed through the proxy" 404 "$BASE/api/actuator/health/readiness"
expect_status "tenant api needs authentication" 401 "$BASE/api/orgs"
expect_status "platform api needs authentication" 401 "$BASE/api/platform/organizations"

# ---- a real user journey ----------------------------------------------------------------
EMAIL="smoke-$RANDOM$RANDOM@example.com"
PASSWORD="Smoke-test-passphrase-$RANDOM"

curl -s -o /dev/null -c "$JAR" "$BASE/api/system/ping"
[[ -n "$(csrf)" ]] || fail "no CSRF cookie was issued"
step "CSRF cookie issued"

code="$(post_json /api/auth/register "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"displayName\":\"Smoke Test\"}")"
[[ "$code" == 202 ]] || fail "register returned $code"
step "register"

token=""
for _ in $(seq 1 30); do
  id="$(curl -s -G "$MAILPIT/api/v1/search" --data-urlencode "query=to:$EMAIL" | grep -o '"ID":"[^"]*"' | head -n 1 | cut -d'"' -f4 || true)"
  if [[ -n "$id" ]]; then
    token="$(curl -s "$MAILPIT/api/v1/message/$id" | grep -o 'token=[A-Za-z0-9_.~-]*' | head -n 1 | cut -d= -f2 || true)"
    [[ -n "$token" ]] && break
  fi
  sleep 1
done
[[ -n "$token" ]] || fail "no verification link arrived in Mailpit ($MAILPIT)"
step "verification email delivered"

code="$(post_json /api/auth/verify-email "{\"token\":\"$token\"}")"
[[ "$code" == 204 ]] || fail "verify-email returned $code"
step "email verified"

code="$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR" -c "$JAR" -H "X-XSRF-TOKEN: $(csrf)" \
  --data-urlencode "email=$EMAIL" --data-urlencode "password=$PASSWORD" "$BASE/api/auth/login")"
[[ "$code" == 204 ]] || fail "login returned $code"
step "sign in"

me="$(curl -s -b "$JAR" "$BASE/api/auth/me")"
[[ "$me" == *"$EMAIL"* ]] || fail "current user is wrong: $me"
step "current user"

code="$(post_json /api/auth/logout '')"
[[ "$code" == 204 ]] || fail "logout returned $code"
expect_status "session is gone after sign out" 401 -b "$JAR" "$BASE/api/auth/me"

echo "Smoke test passed"
