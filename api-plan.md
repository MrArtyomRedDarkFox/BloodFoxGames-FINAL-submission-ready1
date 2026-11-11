# BloodFoxGames — API overview

Base URL (local): `http://localhost:3000/api/v1`

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/games` | List games |
| GET | `/games/{gameId}` | Game details |
| POST | `/users/register` | Register and create HttpOnly session |
| POST | `/users/login` | Login and create HttpOnly session |
| POST | `/users/logout` | Invalidate current session |
| GET | `/users/me` | Current account/profile |
| PUT | `/users/me/profile` | Update username, email, display name, bio |
| PUT | `/users/me/preferences` | Update privacy and notification preferences |
| PUT | `/users/me/password` | Change password and invalidate current session |
| GET | `/users/me/library` | Current user's game library |
| POST | `/users/me/buy/{gameId}` | Demo-purchase / add game to library |
| POST | `/storage/me/avatar` | Upload current user's avatar (multipart `file`) |
| GET | `/storage/me/avatar` | Redirect to a short-lived S3 signed URL |
| DELETE | `/storage/me/avatar` | Delete current user's avatar |
| GET | `/storage/health` | S3-compatible storage readiness |

Internal service APIs:

- Users Service: `PUT /api/v1/users/{accountId}` synchronizes a minimal directory projection from Game Store; `GET /api/v1/users` and `GET /api/v1/users/{accountId}` read it. This port is not published to the host.
- Notifications Service: `POST /api/v1/notifications` receives a user ID and message; `GET /health` is its health endpoint. Delivery is logged only.

For request examples see [`docs/api-examples.md`](docs/api-examples.md).
