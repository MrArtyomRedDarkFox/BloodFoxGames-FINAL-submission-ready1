# BloodFoxGames frontend and profile setup

The frontend is served by Nginx on `http://localhost:3000`. Nginx proxies `/api/*` to `game-store-service` over the internal Docker network. Browser requests use same-origin credentials; authentication is carried by the server-issued HttpOnly `BFGSESSION` cookie.

## Apply the current version

This is a full project archive, not an overlay installer. Extract it into a working directory, preserve your existing untracked `.env`, and run the commands in the repository root:

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps -a
```

Do not replace or publish `.env`. The Compose file uses generated project-scoped container names and local-only host port bindings for PostgreSQL, S3 and the frontend.

## Profile features

After registering or logging in, use the account chip in the header to open **Профиль**. The page supports avatar upload/removal, editable display name, username, email and bio, persisted account preferences, and password change. Avatars are stored in SeaweedFS via its S3-compatible endpoint. Images are limited to 5 MB and validated by file signature.

## Local and production cookie settings

For local `http://localhost`, keep:

```env
SESSION_COOKIE_SECURE=false
```

For production behind HTTPS, set `SESSION_COOKIE_SECURE=true`. Never expose the backend or PostgreSQL directly to the public internet; route browser traffic through HTTPS Nginx/reverse proxy.

## Limitations

- User sessions are in-memory in one Game Store instance; restart requires a new login. Use Redis/Spring Session before scaling to multiple replicas.
- SameSite Strict and HttpOnly cookies are enabled, but separate CSRF token/Origin checks and login rate limiting are still needed for a public release.
- Email delivery, email confirmation, password reset, 2FA and public profile pages are not implemented. Preference toggles are persisted but do not send messages.
- Purchases are demo library additions and do not charge money.
