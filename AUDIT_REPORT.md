# BloodFoxGames — аудит и статус релиза

Дата подготовки: 10 ноября 2026 г.

## Текущее состояние

BloodFoxGames является учебным функциональным прототипом игрового каталога и библиотеки на Java/Spring Boot, PostgreSQL, Nginx, Python/FastAPI и SeaweedFS (S3-compatible).

### Реализованные пользовательские функции

- Регистрация и вход с BCrypt-хешированием.
- Серверные сессии через HttpOnly cookie `BFGSESSION`; профиль/библиотека доступны через `/me` endpoints.
- Проверка ID в сохранённых legacy-маршрутах, запрет доступа к чужому профилю.
- Обновление профиля, предпочтений, смена пароля.
- Загрузка/просмотр/удаление аватара с проверкой JPEG/PNG/WEBP/GIF сигнатуры и ограничением 5 MiB.
- Хранение аватаров в приватном S3-compatible bucket и выдача краткоживущих signed URLs.
- Каталог игр, demo-purchase и библиотека.

### Интеграция микросервисов

- Game Store — основной источник истины для аккаунта и пароля.
- Game Store синхронизирует `accountId`, `username`, `email` в отдельную таблицу Users Service по HTTP при регистрации и изменении профиля. Users Service — отдельная directory projection, а не второй auth provider.
- При добавлении игры в библиотеку Game Store отправляет HTTP POST в Notifications Service; тот подтверждает приём и записывает событие в лог.
- Game Store работает с PostgreSQL через JPA и с SeaweedFS через S3 API.

## Изменения данного этапа

- Добавлена HTTP-синхронизация Game Store → Users Service с upsert по `account_id`.
- В Users Service добавлены `PUT /api/v1/users/{accountId}` и `GET /api/v1/users/{accountId}`; основная запись аккаунта остаётся в Game Store.
- Compose задаёт `USERS_SERVICE_URL` и гарантирует запуск Users Service до Game Store.
- Обновлена актуальная C4-документация: Context, Container, Component; добавлен редактируемый diagrams.net файл.
- Добавлены экспорт PNG, Graphviz исходник и скрипт регенерации диаграммы.
- Добавлены пошаговые curl-примеры, чек-лист защиты и автоматизированный `scripts/smoke-test.sh`.
- Добавлен GitHub Actions workflow для Node.js, Maven, Python, Compose и интеграционного smoke-test.
- Добавлен статический frontend test на наличие взаимодействия с Users Service; набор расширен до 13 тестов.

## Проверки, выполненные в доступной среде подготовки

| Проверка | Результат |
|---|---|
| `node --check frontend/app.js` через `npm run check` | PASS |
| `cd frontend && npm test` | PASS — 13/13 |
| `bash -n scripts/smoke-test.sh` | PASS |
| `bash -n scripts/render-architecture.sh` | PASS |
| Python `compileall` для Notifications Service | PASS |
| XML parse root/service Maven POM и diagrams.net XML | PASS |
| YAML parse Compose и GitHub Actions workflow | PASS; Compose содержит 6 сервисов |
| Graphviz export PNG/PDF/SVG | PASS |
| Maven tests / Java package | NOT RUN в среде подготовки: Maven не установлен |
| Docker Compose build/up и новый интеграционный smoke-test | NOT RUN в среде подготовки: Docker CLI недоступен |

На машине пользователя ранее уже подтверждались запуск Compose-сервисов, готовность PostgreSQL, успешный GET каталога, S3 health и upload/download PNG через signed URL. Это подтверждение относится к предыдущей сборке до новой синхронизации Users Service; после обновления требуется повторить полный `scripts/smoke-test.sh` в Ubuntu.

## Публикация изменений

Обновления подготовлены в архиве `BloodFoxGames-FINAL-submission-ready.zip`. Запись через подключённую GitHub-интеграцию была отклонена ответом 403 `Resource not accessible by integration`, поэтому `main` пока не изменён. Примени архив поверх существующего Git checkout и выполни команды из `UPLOAD_TO_GITHUB.md`; затем проверь три CI job в Actions.

## Остаточные ограничения / production checklist

1. Game Store sessions хранятся in-memory; после рестарта нужна повторная авторизация. Для нескольких реплик — Redis/Spring Session.
2. В production обязательно HTTPS и `SESSION_COOKIE_SECURE=true`.
3. Нужны CSRF-токены/Origin validation и rate limiting на auth endpoints.
4. Нет подтверждения email, восстановления пароля, 2FA и управления сессиями на устройствах.
5. Notifications Service только логирует; реальная email/push-доставка отсутствует.
6. Покупка — демо-действие, не платёж. Нет платёжного провайдера, заказов, возвратов и webhooks.
7. `ddl-auto=update` нужно заменить на Flyway/Liquibase с миграциями и тестом обновления существующей БД.
8. Для production нужны резервные копии/restore tests, централизованные логи, аудит, метрики и управление секретами.
9. Синхронизация Users Service является best-effort projection без transactional outbox/retry queue; для production добавить гарантированную доставку/reconciliation.

## Команды проверки перед сдачей

```bash
cd frontend && npm run check
cd ..
mvn -f game-store-service/pom.xml test
mvn -f users-service/pom.xml test
docker compose config --quiet
docker compose up -d --build
scripts/smoke-test.sh
```

Положи реальные скриншоты результатов в `docs/evidence/`. Не публикуй `.env`, ключи, cookies и реальные пароли.
