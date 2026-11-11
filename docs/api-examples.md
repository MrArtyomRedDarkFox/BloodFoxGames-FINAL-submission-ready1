# API-проверка BloodFoxGames через curl

Все команды запускаются в Ubuntu из корня проекта после `docker compose up -d --build`. Для каждой проверки используется отдельный тестовый аккаунт. Не используй настоящий пароль.

```bash
BASE=http://localhost:3000
COOKIE=/tmp/bfg-cookie.txt
USER="bfg$(date +%s)"
EMAIL="${USER}@example.invalid"
PASS='Bfg-test-password-2026!'
```

## 1. Каталог и S3 health

```bash
curl -i "$BASE/api/v1/games"
curl -i "$BASE/api/v1/storage/health"
```

Ожидается `200 OK`, каталог — JSON-массив с `id`, `title`, `description`, `price`; S3 health должен содержать `"ready":true`.

## 2. Регистрация и серверная сессия

```bash
curl -i -c "$COOKIE" -b "$COOKIE" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" \
  "$BASE/api/v1/users/register"

curl -i -b "$COOKIE" "$BASE/api/v1/users/me"
```

Ожидается `201 Created` при регистрации, `200 OK` при чтении профиля и cookie `BFGSESSION` с флагами `HttpOnly` и `SameSite=Strict`.

## 3. Изменение профиля и предпочтений

```bash
curl -i -b "$COOKIE" -c "$COOKIE" -X PUT \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USER\",\"email\":\"$EMAIL\",\"displayName\":\"Smoke Tester\",\"bio\":\"Учебная проверка BloodFoxGames\"}" \
  "$BASE/api/v1/users/me/profile"

curl -i -b "$COOKIE" -X PUT \
  -H 'Content-Type: application/json' \
  -d '{"publicProfile":true,"emailNotifications":false,"gameUpdates":true}' \
  "$BASE/api/v1/users/me/preferences"
```

После повторного `GET /api/v1/users/me` обновлённые значения должны вернуться из PostgreSQL.

## 4. Демо-покупка и библиотека

Возьми `id` одной игры из ответа каталога:

```bash
GAME_ID=1
curl -i -b "$COOKIE" -X POST "$BASE/api/v1/users/me/buy/$GAME_ID"
curl -i -b "$COOKIE" "$BASE/api/v1/users/me/library"
```

Ожидается `200 OK`, а приобретённая игра появляется в библиотеке. Повторная покупка той же игры должна вернуть `409 Conflict`. Покупка не списывает реальные деньги.

Проверка события уведомлений:

```bash
docker compose logs --since=5m notifications-service
```

В логе должна появиться запись `[NOTIFICATION SENT]` с `Game purchased`.

## 5. Синхронизация Users Service

После регистрации Game Store должен синхронизировать `accountId`, `username` и `email` с Users Service. Сервис не публикуется на host-порт; проверить проекцию можно через PostgreSQL:

```bash
# ACCOUNT_ID возьми из JSON GET /api/v1/users/me
docker compose exec -T db psql -U postgres -d app -c \
  "SELECT account_id, username, email FROM users WHERE account_id = ACCOUNT_ID;"
```

Запрос должен вернуть ровно одну запись. После изменения username/email в профиле значения проекции должны обновиться.

## 6. Аватар в S3

Пользовательский маршрут принимает JPEG, PNG, WEBP или GIF размером не более 5 MiB. Для smoke-test удобно загрузить реальный небольшой PNG через форму профиля. Затем:

```bash
curl -i -b "$COOKIE" "$BASE/api/v1/storage/me/avatar"
```

Ожидается `302 Found` с `Location`, содержащим краткоживущую подписанную ссылку. Прямой запрос к закрытому bucket без подписи может вернуть `403 AccessDenied` — это нормальное поведение.

## 7. Защита сессии

```bash
curl -i -b "$COOKIE" -X POST "$BASE/api/v1/users/logout"
curl -i -b "$COOKIE" "$BASE/api/v1/users/me"
```

После logout запрос `/me` должен вернуть `401 Unauthorized`. Доступ к `/api/v1/users/{чужой-id}` под другой сессией должен вернуть `403 Forbidden`.

## 8. Повторный запуск

```bash
docker compose restart game-store-service
# Войти повторно: in-memory сессия сбросится, но профиль и библиотека останутся в PostgreSQL.
```

После нового входа `GET /api/v1/users/me` и `GET /api/v1/users/me/library` должны вернуть ранее сохранённые данные.
