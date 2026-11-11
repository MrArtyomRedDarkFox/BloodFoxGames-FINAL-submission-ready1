# Чек-лист защиты финального проекта

## Что показать преподавателю

- [ ] Идея: игровой каталог, личная библиотека, профиль пользователя.
- [ ] Архитектура C4: Context, Container, Component (`docs/architecture.drawio`).
- [ ] `docker-compose.yml`: все шесть контейнеров, одна Docker network и два постоянных volume.
- [ ] `docker compose ps -a`: все сервисы `Up`, PostgreSQL `healthy`.
- [ ] Регистрация и вход; показать HttpOnly cookie `BFGSESSION`.
- [ ] Изменение профиля и настроек; обновить страницу и показать сохранённые значения.
- [ ] Покупка-демо; игра появляется в библиотеке.
- [ ] В логах Notifications Service появляется событие покупки.
- [ ] В PostgreSQL есть запись в `store_users`, `user_games` и проекции Users Service (`users.account_id`).
- [ ] S3 health возвращает `ready:true`; загрузка и чтение аватара работают через signed URL.
- [ ] После перезапуска Game Store выполнить вход снова и показать сохранность профиля/библиотеки.
- [ ] `cd frontend && npm run check`.
- [ ] `mvn -f game-store-service/pom.xml test`.
- [ ] `mvn -f users-service/pom.xml test`.
- [ ] `scripts/smoke-test.sh` завершился с `ALL CHECKS PASSED`.

## Скриншоты / материалы в репозитории

Сохраняй только свои реальные скриншоты и ответы команд в `docs/evidence/`:

1. `docker-compose-ps.png` — статусы всех контейнеров.
2. `api-games.png` — JSON каталога и HTTP 200.
3. `register-profile.png` — профиль после регистрации и входа (скрыть email, если он личный).
4. `library.png` — игра после демо-покупки.
5. `notifications.png` — запись уведомления в логах.
6. `s3-avatar.png` — успешная загрузка и открытие аватара.
7. `persistence-after-restart.png` — библиотека после перезапуска сервиса и повторного входа.
8. `test-results.png` — результаты Node, Maven и smoke-test.

Не добавляй в репозиторий `.env`, реальные пароли, GitHub PAT, S3 secret key или cookies.
