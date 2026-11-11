# Как применить обновления и отправить их в GitHub

Архив содержит обновлённые файлы проекта, но не содержит `.env`, `.git` и локальные Docker volumes. Поэтому извлекай его поверх существующей рабочей копии Git-репозитория — так сохранятся remote и история коммитов.

Из Ubuntu:

```bash
# 1) Перейти в существующий Git-репозиторий
cd ~/Downloads/BloodFoxGames-profile-settings/BloodFoxGames-FOXGAME

# 2) Убедиться, что remote — нужный репозиторий и рабочая копия чистая
git remote -v
git status --short

# 3) Сделать резервную копию локальных секретов (если .env существует)
cp -n .env .env.backup 2>/dev/null || true

# 4) Распаковать скачанный архив поверх текущего проекта
unzip -o ~/Downloads/BloodFoxGames-FINAL-submission-ready.zip -d .

# 5) Убедиться, что .env не добавлен и whitespace-проверка проходит
git status --short
git diff --check
git check-ignore .env

# 6) Добавить файлы, проверить staged diff и сделать коммит
git add -A
git diff --cached --check
git diff --cached --stat
git commit -m "Complete BloodFoxGames final project checks and C4 docs"

# 7) Отправить обновление в GitHub
git push origin main
```

Если GitHub запросит пароль при HTTPS push, используй Personal Access Token с правом записи в этот репозиторий вместо пароля аккаунта. Не вставляй токен в чат и не сохраняй его в `.env` или исходниках.

После push открой вкладку **Actions** в репозитории `BloodFoxGames_Final_version`. Workflow `BloodFoxGames CI` должен выполнить три задачи: Frontend checks, Java and Python checks, Docker Compose integration. Для сдачи дождись зелёного статуса всех трёх задач.

## Если push отклонён

Сначала выполни `git pull --rebase origin main`, разреши возможные конфликты, затем повтори `git push origin main`. Не используй `git push --force` для этой работы.
