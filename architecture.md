# BloodFoxGames — архитектура C4

> Документ описывает именно текущую реализацию из `docker-compose.yml`, а не планируемые компоненты. Редактируемая схема находится в `docs/architecture.drawio`, экспорт PNG — `docs/architecture.png`; PDF также генерируется локальным скриптом рендеринга.

## 1. Назначение и основной сценарий

BloodFoxGames — учебный веб-прототип игрового каталога и личной библиотеки. Пользователь открывает сайт, регистрирует аккаунт, просматривает каталог, добавляет игру в библиотеку, меняет профиль и загружает аватар. Покупка в этом проекте демонстрационная: она добавляет игру в библиотеку и не списывает деньги.

Основной сценарий:

1. Браузер загружает frontend через Nginx.
2. Frontend обращается к `/api/...` по тому же origin; Nginx проксирует запрос в Game Store Service.
3. Game Store проверяет серверную сессию `BFGSESSION`, выполняет бизнес-операцию и сохраняет аккаунт/библиотеку в PostgreSQL.
4. При регистрации и изменении профиля Game Store синхронизирует минимальную запись каталога пользователей с Users Service по HTTP.
5. При добавлении игры в библиотеку Game Store посылает HTTP-событие в Notifications Service; текущая реализация сервиса записывает событие в лог, а не отправляет email/push.
6. Аватары передаются через Game Store в SeaweedFS по S3 API и выдаются браузеру по краткоживущей подписанной ссылке.

## 2. C4 Level 1 — Context

**Акторы и внешние системы:**

- **Игрок** — использует сайт в браузере.
- **BloodFoxGames** — система каталога, аккаунтов, профилей и библиотеки.
- **PostgreSQL** — постоянное хранение аккаунтов магазина, игр, связей библиотеки и каталога пользователей.
- **SeaweedFS (S3-compatible)** — объектное хранилище аватаров и других файлов.

Игрок взаимодействует с BloodFoxGames через веб-клиент. Система использует PostgreSQL для структурированных данных и S3 API для файлов.

## 3. C4 Level 2 — Container

| Контейнер | Технологии | Ответственность | Порт/доступ |
|---|---|---|---|
| Frontend / Nginx | HTML, CSS, JavaScript, Nginx | UI, статические файлы, same-origin proxy `/api/` | `127.0.0.1:3000 → 80` |
| Game Store Service | Java 17, Spring Boot, Spring Data JPA | Каталог, регистрация/вход, серверные сессии, профиль, библиотека, S3-интеграция | `8080`, только Docker network |
| Users Service | Java 17, Spring Boot, Spring Data JPA | Отдельный каталог-проекция аккаунтов по внешнему `accountId`; синхронизация вызывается Game Store | `8081`, только Docker network |
| Notifications Service | Python, FastAPI, Uvicorn | Приём HTTP-событий и запись уведомлений в лог | `8082`, только Docker network |
| PostgreSQL | PostgreSQL 16 | Персистентные таблицы `store_users`, `games`, `user_games` и `users` | `127.0.0.1:5432` |
| SeaweedFS | SeaweedFS 4.48, S3 API | Bucket `bloodfox-assets`, хранение объектов | `127.0.0.1:8333` |

**Связи контейнеров:**

- Browser → Nginx: HTTPS в production / HTTP в локальной лаборатории.
- Nginx → Game Store: HTTP proxy по `/api/`.
- Game Store → PostgreSQL: JDBC/JPA.
- Game Store → Users Service: `PUT /api/v1/users/{accountId}` при регистрации и изменении профиля. Users Service хранит проекцию `accountId`, `username`, `email`; источник истины по учётным данным — Game Store.
- Game Store → Notifications Service: `POST /api/v1/notifications` после добавления игры в библиотеку.
- Game Store → SeaweedFS: S3 API `PutObject`, `GetObject` через presigned URL, `DeleteObject`.
- Users Service → PostgreSQL: JDBC/JPA для каталога пользователей.

Внутренние сервисы не публикуют порты на внешние интерфейсы хоста; они доступны по именам сервисов в Docker Compose network. PostgreSQL и SeaweedFS опубликованы только на loopback (`127.0.0.1`).

## 4. C4 Level 3 — Component (Game Store Service)

- **GameController** — чтение каталога и карточки игры.
- **UserController** — регистрация, вход, выход, текущий профиль, настройки, смена пароля, библиотека и демо-покупка.
- **ProfileAvatarController** — загрузка, просмотр и удаление аватара текущего пользователя.
- **StorageController** — health S3 и административная загрузка изображения; административный токен не должен попадать во frontend.
- **UserService** — правила регистрации/входа, BCrypt, обновление профиля, управление библиотекой, синхронизация каталога Users Service и отправка события Notifications Service.
- **S3StorageService** — bucket, загрузка/удаление объектов и создание краткоживущих presigned URL.
- **UserRepository / GameRepository** — доступ к PostgreSQL через Spring Data JPA.

## 5. Хранение данных

- `store_users` — учётные записи магазина, BCrypt-хеш пароля, данные профиля и настройки.
- `games` — каталог игр.
- `user_games` — связь пользователя с играми личной библиотеки.
- `users` — отдельная проекция каталога Users Service, сопоставленная с магазином через `account_id`.
- `db_data` — Docker volume PostgreSQL.
- `s3_data` — Docker volume SeaweedFS.

## 6. Границы и ограничения прототипа

- Покупка демонстрационная: реального платёжного провайдера, денежных списаний, возвратов и webhooks нет.
- Notifications Service логирует события; реальная почтовая/push-доставка не настроена.
- Сессии Game Store хранятся в памяти одного процесса; после перезапуска пользователю нужно войти снова. Данные аккаунта и библиотеки при этом сохраняются в PostgreSQL.
- Используется `spring.jpa.hibernate.ddl-auto=update`; для production нужны контролируемые миграции Flyway/Liquibase.
- Для публичного HTTPS-развёртывания нужно установить `SESSION_COOKIE_SECURE=true`, добавить CSRF/Origin protection, rate limiting, резервное копирование, мониторинг и управление секретами.

## 7. Как открыть и изменить схему

1. Открой `docs/architecture.drawio` через [diagrams.net](https://app.diagrams.net/).
2. В файле три вкладки: Context, Container и Component.
3. После правок экспортируй Container/overview в PNG и PDF и замени `docs/architecture.png` / `docs/architecture.pdf`.
