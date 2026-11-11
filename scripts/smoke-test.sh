#!/usr/bin/env bash
set -Eeuo pipefail

BASE_URL="${BASE_URL:-http://localhost:3000}"
RESTART_GAME_STORE="${RESTART_GAME_STORE:-1}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

for cmd in curl python3 docker; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "ERROR: required command not found: $cmd" >&2; exit 2; }
done

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
cookie_one="$tmp_dir/user-one.cookies"
cookie_two="$tmp_dir/user-two.cookies"

pass="Bfg-smoke-$(date +%s)-Strong!"
user_one="bfg$(date +%s)${RANDOM}"
email_one="${user_one}@example.invalid"
user_two="bfg2$(date +%s)${RANDOM}"
email_two="${user_two}@example.invalid"
new_pass="Bfg-new-smoke-$(date +%s)-Strong!"

pass_count=0
fail() { echo "FAIL: $*" >&2; exit 1; }
ok() { pass_count=$((pass_count + 1)); printf 'PASS %02d: %s\n' "$pass_count" "$*"; }

wait_http() {
  local url="$1" description="$2" attempts="${3:-60}"
  for ((i=1; i<=attempts; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      ok "$description"
      return 0
    fi
    sleep 2
  done
  fail "$description did not become ready: $url"
}

json_id() {
  python3 -c 'import json,sys; d=json.load(sys.stdin); print(d["id"])'
}

# Ensure that the current Compose project is running.
docker compose ps >/dev/null 2>&1 || fail "docker compose is unavailable or this directory is not the project root"
wait_http "$BASE_URL/api/v1/games" "frontend proxy and Game Store API are reachable"

# Catalog and S3.
games_json="$(curl -fsS "$BASE_URL/api/v1/games")" || fail "could not load game catalog"
game_id="$(printf '%s' "$games_json" | python3 -c 'import json,sys; a=json.load(sys.stdin); print(a[0]["id"] if a else "")')"
[[ -n "$game_id" ]] || fail "catalog is empty; seed at least one game before smoke testing"
ok "game catalog contains at least one game (selected id=$game_id)"

storage_json="$(curl -fsS "$BASE_URL/api/v1/storage/health")" || fail "S3 health endpoint failed"
printf '%s' "$storage_json" | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("enabled") is True and d.get("ready") is True, d'
ok "S3-compatible storage reports ready=true"

# User 1: registration, server session and profile.
register_one="$(curl -fsS -D "$tmp_dir/register-one.headers" -c "$cookie_one" -b "$cookie_one" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$user_one\",\"email\":\"$email_one\",\"password\":\"$pass\"}" \
  "$BASE_URL/api/v1/users/register")" || fail "registration for first smoke user failed"
account_one="$(printf '%s' "$register_one" | json_id)"
grep -qi 'BFGSESSION=.*HttpOnly' "$tmp_dir/register-one.headers" || fail "BFGSESSION cookie is missing HttpOnly"
ok "registration created account id=$account_one and issued HttpOnly session cookie"

profile_one="$(curl -fsS -c "$cookie_one" -b "$cookie_one" "$BASE_URL/api/v1/users/me")" || fail "GET /users/me failed"
printf '%s' "$profile_one" | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("id") is not None and d.get("username"), d'
ok "current profile is available through the server-side session"

user_one_updated="edit${user_one:0:24}"
email_one_updated="updated-${user_one}@example.invalid"
profile_update="$(curl -fsS -c "$cookie_one" -b "$cookie_one" -X PUT \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$user_one_updated\",\"email\":\"$email_one_updated\",\"displayName\":\"Smoke Tester\",\"bio\":\"Automated BloodFoxGames integration check\"}" \
  "$BASE_URL/api/v1/users/me/profile")" || fail "profile update failed"
printf '%s' "$profile_update" | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("displayName")=="Smoke Tester", d'
user_one="$user_one_updated"
email_one="$email_one_updated"
ok "profile fields and account directory identifiers were updated"

prefs_update="$(curl -fsS -c "$cookie_one" -b "$cookie_one" -X PUT \
  -H 'Content-Type: application/json' \
  -d '{"publicProfile":true,"emailNotifications":false,"gameUpdates":true}' \
  "$BASE_URL/api/v1/users/me/preferences")" || fail "preference update failed"
printf '%s' "$prefs_update" | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("publicProfile") is True and d.get("emailNotifications") is False and d.get("gameUpdates") is True, d'
ok "privacy and notification preferences were saved"

# Users Service projection is a separate HTTP service with its own database entity.
for ((i=1; i<=30; i++)); do
  dir_row="$(docker compose exec -T db psql -U postgres -d app -Atc \
    "SELECT username || '|' || email FROM users WHERE account_id = ${account_one};" 2>/dev/null || true)"
  [[ "$dir_row" == "${user_one}|${email_one}" ]] && break
  sleep 1
done
[[ "${dir_row:-}" == "${user_one}|${email_one}" ]] || fail "Users Service directory projection missing or stale: ${dir_row:-no row}"
ok "Users Service received the account projection over HTTP"

# Game purchase -> library -> Notifications Service.
curl -fsS -c "$cookie_one" -b "$cookie_one" -X POST \
  "$BASE_URL/api/v1/users/me/buy/$game_id" >/dev/null || fail "demo-purchase endpoint failed"
library_json="$(curl -fsS -c "$cookie_one" -b "$cookie_one" "$BASE_URL/api/v1/users/me/library")" || fail "library endpoint failed"
printf '%s' "$library_json" | python3 -c 'import json,sys; a=json.load(sys.stdin); gid=int(sys.argv[1]); assert any(int(g["id"])==gid for g in a), a' "$game_id"
ok "demo-purchase persisted the game in the user's library"

for ((i=1; i<=20; i++)); do
  if docker compose logs --no-color --since=3m notifications-service 2>/dev/null | grep -q 'Game purchased:'; then break; fi
  sleep 1
done
docker compose logs --no-color --since=3m notifications-service 2>/dev/null | grep -q 'Game purchased:' \
  || fail "Notifications Service did not log the game-purchase event"
ok "Game Store sent the purchase event to Notifications Service"

# Avatar: create a standards-compliant 1x1 PNG, upload, follow the short-lived signed URL.
python3 - "$tmp_dir/avatar.png" <<'PY'
import struct, sys, zlib

def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
raw = b'\x00\xff\x32\x14\xff'  # filter byte + one RGBA pixel
png = b'\x89PNG\r\n\x1a\n'
png += chunk(b'IHDR', struct.pack('>IIBBBBB', 1, 1, 8, 6, 0, 0, 0))
png += chunk(b'IDAT', zlib.compress(raw))
png += chunk(b'IEND', b'')
open(sys.argv[1], 'wb').write(png)
PY
curl -fsS -c "$cookie_one" -b "$cookie_one" -F "file=@$tmp_dir/avatar.png;type=image/png" \
  "$BASE_URL/api/v1/storage/me/avatar" >/dev/null || fail "avatar upload failed"
avatar_code="$(curl -sS -o /dev/null -w '%{http_code}' -c "$cookie_one" -b "$cookie_one" \
  "$BASE_URL/api/v1/storage/me/avatar")"
[[ "$avatar_code" == "302" ]] || fail "avatar view should redirect to a signed URL (got HTTP $avatar_code)"
curl -fsSL -c "$cookie_one" -b "$cookie_one" "$BASE_URL/api/v1/storage/me/avatar" -o "$tmp_dir/avatar-downloaded.png" \
  || fail "download via avatar signed URL failed"
cmp -s "$tmp_dir/avatar.png" "$tmp_dir/avatar-downloaded.png" || fail "downloaded avatar content differs"
ok "avatar upload and signed-URL download round-trip succeeded"

# User 2 and cross-account access check.
curl -fsS -D "$tmp_dir/register-two.headers" -c "$cookie_two" -b "$cookie_two" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$user_two\",\"email\":\"$email_two\",\"password\":\"$pass\"}" \
  "$BASE_URL/api/v1/users/register" >"$tmp_dir/register-two.json" || fail "registration for second smoke user failed"
account_two="$(cat "$tmp_dir/register-two.json" | json_id)"
foreign_code="$(curl -sS -o /dev/null -w '%{http_code}' -c "$cookie_one" -b "$cookie_one" \
  "$BASE_URL/api/v1/users/$account_two")"
[[ "$foreign_code" == "403" ]] || fail "cross-account profile access should return 403 (got $foreign_code)"
ok "cross-account profile access is blocked with HTTP 403"

# Logout must invalidate the session.
curl -fsS -c "$cookie_one" -b "$cookie_one" -X POST "$BASE_URL/api/v1/users/logout" >/dev/null \
  || fail "logout endpoint failed"
unauth_code="$(curl -sS -o /dev/null -w '%{http_code}' -c "$cookie_one" -b "$cookie_one" \
  "$BASE_URL/api/v1/users/me")"
[[ "$unauth_code" == "401" ]] || fail "GET /users/me after logout should return 401 (got $unauth_code)"
ok "logout invalidates the server-side session"

# Login and password rotation.
curl -fsS -c "$cookie_one" -b "$cookie_one" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$user_one\",\"password\":\"$pass\"}" \
  "$BASE_URL/api/v1/users/login" >/dev/null || fail "login after logout failed"
curl -fsS -c "$cookie_one" -b "$cookie_one" -X PUT -H 'Content-Type: application/json' \
  -d "{\"currentPassword\":\"$pass\",\"newPassword\":\"$new_pass\"}" \
  "$BASE_URL/api/v1/users/me/password" >/dev/null || fail "password change failed"
old_login_code="$(curl -sS -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
  -d "{\"username\":\"$user_one\",\"password\":\"$pass\"}" "$BASE_URL/api/v1/users/login")"
[[ "$old_login_code" == "401" ]] || fail "old password should be rejected (got $old_login_code)"
curl -fsS -c "$cookie_one" -b "$cookie_one" -H 'Content-Type: application/json' \
  -d "{\"username\":\"$user_one\",\"password\":\"$new_pass\"}" \
  "$BASE_URL/api/v1/users/login" >/dev/null || fail "new password login failed"
ok "password change invalidates the previous credential and accepts the new one"

# Restart the account service and verify durable account/library state (sessions are intentionally in-memory).
if [[ "$RESTART_GAME_STORE" == "1" ]]; then
  docker compose restart game-store-service >/dev/null || fail "could not restart Game Store Service"
  wait_http "$BASE_URL/api/v1/games" "Game Store recovered after restart" 60
  curl -fsS -c "$cookie_one" -b "$cookie_one" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$user_one\",\"password\":\"$new_pass\"}" \
    "$BASE_URL/api/v1/users/login" >/dev/null || fail "login after Game Store restart failed"
  persisted_profile="$(curl -fsS -c "$cookie_one" -b "$cookie_one" "$BASE_URL/api/v1/users/me")" || fail "profile did not survive Game Store restart"
  printf '%s' "$persisted_profile" | python3 -c 'import json,sys; d=json.load(sys.stdin); assert d.get("displayName")=="Smoke Tester" and d.get("emailNotifications") is False and d.get("hasAvatar") is True, d'
  persisted_library="$(curl -fsS -c "$cookie_one" -b "$cookie_one" "$BASE_URL/api/v1/users/me/library")" || fail "library did not survive Game Store restart"
  printf '%s' "$persisted_library" | python3 -c 'import json,sys; a=json.load(sys.stdin); gid=int(sys.argv[1]); assert any(int(g["id"])==gid for g in a), a' "$game_id"
  ok "profile, preferences, avatar reference and library persisted after Game Store restart"
else
  echo "SKIP: Game Store restart/persistence check disabled (RESTART_GAME_STORE=0)"
fi

printf '\nALL CHECKS PASSED (%s assertions).\n' "$pass_count"
