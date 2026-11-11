# BloodFoxGames — тестирование и проверка результата

## 1. Frontend

Требуется Node.js 20+:

```bash
cd frontend
npm test
npm run check
```

Набор из 12 Node.js тестов проверяет синтаксис, маршруты/обработчики UI, same-origin API, серверную сессию, профильные endpoints, S3-конфигурацию и обе темы оформления.

## 2. Backend unit tests

Требуется JDK 17+ и Maven 3.9+:

```bash
mvn -f game-store-service/pom.xml test
mvn -f users-service/pom.xml test
```

## 3. Docker Compose configuration и запуск

Создай `.env` из `.env.example`, замени все шаблонные пароли/токены уникальными значениями, затем:

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps -a
```

Сайт: `http://localhost:3000`. PostgreSQL и S3 endpoints привязаны к `127.0.0.1`; внутренние API сервисов доступны внутри Docker network.

## 4. Полный интеграционный smoke-test

Из корня проекта (требуется `curl`, `python3`, Docker Compose и запущенный стек):

```bash
scripts/smoke-test.sh
```

Сценарий использует временные тестовые аккаунты и проверяет каталог, регистрацию/вход, серверную сессию, профиль, настройки, доступ к чужому профилю, демо-покупку, библиотеку, синхронизацию Users Service, событие Notifications Service, S3 health/avatar и сохранность данных после перезапуска Game Store.

Сценарий перезапускает только `game-store-service`; пользователь будет разлогинен и должен войти повторно. PostgreSQL и SeaweedFS volumes не удаляются. Для пропуска перезапуска: `RESTART_GAME_STORE=0 scripts/smoke-test.sh`.

## 5. Проверка сохранности данных вручную

```bash
docker compose restart game-store-service
# войди снова в браузере и проверь профиль и библиотеку
```

Не выполняй `docker compose down -v`, если нужно сохранить данные локальной базы и S3.

## 6. GitHub Actions

Workflow `.github/workflows/ci.yml` выполняет Node.js checks, Maven tests для обоих Java-сервисов, проверку Python-синтаксиса, проверку Compose и интеграционный запуск стека со smoke-test.

## 7. Ограничения интерпретации результатов

- `docker compose ps` со статусом `Up` доказывает запуск контейнера, но не корректность бизнес-сценариев.
- Notifications Service сейчас логирует события, не отправляет email/push.
- Покупка демонстрационная, реального платёжного провайдера нет.
- In-memory sessions теряются при перезапуске Game Store; профиль/библиотека в PostgreSQL сохраняются.
- Для production нужны HTTPS, `SESSION_COOKIE_SECURE=true`, CSRF/Origin protection, rate limiting, восстановление пароля, миграции Flyway/Liquibase, бэкапы и мониторинг.
