(() => {
  "use strict";
  const API = ""; // Nginx проксирует /api/* в game-store-service.
  const app = document.getElementById("app-root");
  const toast = document.getElementById("toast");
  // Discard the former client-stored user ID; only the server session authenticates now.
  localStorage.removeItem("bloodfox.session");
  const state = {
    page: (location.hash.replace(/^#\/?/, "") || "home"),
    user: null,
    profile: null,
    sessionChecked: false,
    games: [],
    library: [],
    favorites: readFavorites(),
    sort: "featured",
    search: "",
    loadingGames: false,
    loadingLibrary: false,
    gamesError: "",
    libraryError: "",
    nextPage: "home",
    theme: readTheme()
  };
  let toastTimer;


  function readTheme() {
    const saved = localStorage.getItem("bloodfox.theme");
    return saved === "foxgame" ? "foxgame" : "modern";
  }
  function applyTheme() {
    document.documentElement.dataset.theme = state.theme;
    document.documentElement.style.colorScheme = "dark";
  }
  function toggleTheme() {
    state.theme = state.theme === "foxgame" ? "modern" : "foxgame";
    localStorage.setItem("bloodfox.theme", state.theme);
    applyTheme();
    render();
    notify(state.theme === "foxgame" ? "Включён стиль FOXGAME." : "Включён современный стиль BloodFoxGames.");
  }

  function readFavorites() {
    try { const value = JSON.parse(localStorage.getItem("bloodfox.favorites") || "[]"); return Array.isArray(value) ? value.map(Number).filter(Number.isFinite) : []; }
    catch (_) { return []; }
  }
  function toggleFavorite(id) {
    const numericId = Number(id);
    state.favorites = state.favorites.includes(numericId) ? state.favorites.filter(item => item !== numericId) : [...state.favorites, numericId];
    localStorage.setItem("bloodfox.favorites", JSON.stringify(state.favorites));
  }
  function saveSession(user) {
    // Identity is always revalidated by the server-side HttpOnly session cookie.
    state.user = {
      id: Number(user.id), username: user.username, email: user.email || "",
      displayName: user.displayName || user.username, hasAvatar: Boolean(user.hasAvatar)
    };
  }
  function clearSession() {
    state.user = null;
    state.library = [];
    state.profile = null;
  }
  async function restoreSession() {
    try {
      const user = await api("/api/v1/users/me");
      if (!user || !user.id || !user.username) throw new Error("Invalid session profile");
      state.profile = user;
      saveSession(user);
    } catch (_) {
      clearSession();
    }
  }
  function esc(value) {
    return String(value ?? "").replace(/[&<>"']/g, char => ({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;", "'":"&#39;"}[char]));
  }
  function price(value) {
    const amount = Number(value);
    return Number.isFinite(amount) ? `$${amount.toFixed(2)}` : "Цена уточняется";
  }
  async function api(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    const isFormData = typeof FormData !== "undefined" && options.body instanceof FormData;
    if (options.body != null && !isFormData && !headers["Content-Type"] && !headers["content-type"]) {
      headers["Content-Type"] = "application/json";
    }
    const response = await fetch(`${API}${path}`, {
      ...options,
      credentials: "same-origin",
      headers
    });
    const text = await response.text();
    let payload = null;
    if (text) {
      try { payload = JSON.parse(text); } catch (_) { payload = text; }
    }
    if (!response.ok) {
      const detail = payload && typeof payload === "object"
        ? (payload.detail || payload.message || payload.error || payload.title)
        : payload;
      const error = new Error(detail || `Ошибка HTTP ${response.status}`);
      error.status = response.status;
      throw error;
    }
    return payload;
  }
  function notify(message, isError = false) {
    toast.textContent = message;
    toast.classList.toggle("error", isError);
    toast.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove("show"), 3600);
  }
  function routeTo(page) {
    const valid = ["home", "catalog", "library", "profile", "login", "register"];
    const target = valid.includes(page) ? page : "home";
    if (location.hash === `#${target}`) route();
    else location.hash = target;
  }
  async function loadGames() {
    state.loadingGames = true;
    state.gamesError = "";
    try {
      const response = await api("/api/v1/games");
      state.games = Array.isArray(response) ? response : [];
    } catch (error) {
      state.gamesError = `Не удалось получить каталог: ${error.message}. Проверь, что backend запущен.`;
    } finally { state.loadingGames = false; }
  }
  async function loadLibrary() {
    if (!state.user) { state.library = []; state.libraryError = ""; return; }
    state.loadingLibrary = true;
    state.libraryError = "";
    try {
      const response = await api("/api/v1/users/me/library");
      state.library = Array.isArray(response) ? response : [];
    } catch (error) {
      state.libraryError = `Не удалось загрузить библиотеку: ${error.message}`;
      if (error.status === 401) clearSession();
    } finally { state.loadingLibrary = false; }
  }
  function logo() {
    return `<a class="brand" href="#home" aria-label="BloodFoxGames — главная">
      <span class="brand-icon" aria-hidden="true"><img src="/assets/bloodfox-logo.png" alt=""></span>
      <span class="brand-word">BLOOD<span>FOX</span>GAMES<span class="brand-caption">PLAY BEYOND THE DARK</span></span>
    </a>`;
  }
  function header() {
    const nav = [{id:"home",label:"Главная"},{id:"catalog",label:"Магазин"},{id:"library",label:"Библиотека"},{id:"favorites",label:`Избранное${state.favorites.length ? ` · ${state.favorites.length}` : ""}`}];
    return `<header class="topbar"><div class="topbar-inner">
      ${logo()}
      <nav class="nav" id="main-nav" aria-label="Основная навигация">${nav.map(item => `<a class="nav-link ${state.page === item.id ? "active" : ""}" href="#${item.id}">${item.label}</a>`).join("")}</nav>
      <div class="topbar-spacer"></div>
      ${state.page === "catalog" ? `<label class="header-search"><span class="search-glyph">⌕</span><input id="header-search" aria-label="Поиск игр" placeholder="Найти игру…" value="${esc(state.search)}"></label>` : ""}
      <div class="account-area"><button class="btn btn-theme btn-small" data-action="toggle-theme" aria-label="Переключить оформление" title="Переключить стиль">${state.theme === "foxgame" ? "✦ FOXGAME" : "◈ MODERN"}</button>${state.user
        ? `<a class="user-chip" href="#profile" aria-label="Открыть профиль пользователя"><span class="avatar">${state.user.hasAvatar ? `<img src="/api/v1/storage/me/avatar" alt="">` : esc((state.user.displayName || state.user.username).slice(0,1).toUpperCase())}</span><span class="username">${esc(state.user.displayName || state.user.username)}</span></a><button class="btn btn-ghost btn-small" data-action="logout">Выйти</button>`
        : `<a class="btn btn-ghost btn-small" href="#login">Войти</a><a class="btn btn-primary btn-small" href="#register">Создать аккаунт</a>`}</div>
      <button class="mobile-menu" id="mobile-menu" aria-label="Открыть меню" aria-expanded="false">☰</button>
    </div></header>`;
  }
  function footer() {
    return `<footer class="footer"><div><div class="footer-brand">BLOOD<span>FOX</span>GAMES</div><div>Независимый учебный проект · магазин и игровая библиотека</div></div><div>Работает на Java · PostgreSQL · Docker Compose</div></footer>`;
  }
  function shell(content) { app.innerHTML = `${header()}<main>${content}${footer()}</main>`; }
  function gameArt(game) {
    const title = String(game.title || "GAME");
    const normalized = title.toLowerCase();
    const coverClass = normalized.includes("lost") || normalized.includes("night") ? "cover-night" : normalized.includes("fox") ? "cover-fox" : "cover-default";
    const code = normalized.includes("lost") ? "DARK / 02" : normalized.includes("fox") ? "ARCADE / 01" : "BFG / ORIGINAL";
    const coverTitle = normalized.includes("fox") ? "FOX<br>RACES" : normalized.includes("lost") ? "LOST<br>NIGHT" : esc(title).replace(/\s+/g,"<br>");
    return `<div class="game-cover ${coverClass}"><span class="cover-code">${code}</span><div class="cover-title">${coverTitle}<small>BLOODFOX ORIGINALS</small></div></div>`;
  }
  function gameCard(game, options = {}) {
    const owned = options.owned || false;
    const favorite = state.favorites.includes(Number(game.id));
    return `<article class="game-card">${gameArt(game)}<div class="game-body"><div class="game-title-row"><h3 class="game-title">${esc(game.title)}</h3><div class="game-card-tools"><button class="favorite-btn ${favorite ? "is-favorite" : ""}" data-action="favorite" data-game-id="${Number(game.id)}" aria-label="${favorite ? "Убрать из избранного" : "Добавить в избранное"}" aria-pressed="${favorite}">${favorite ? "♥" : "♡"}</button>${owned ? `<span class="badge-owned">В БИБЛИОТЕКЕ</span>` : ""}</div></div>
      <p class="game-desc">${esc(game.description || "Описание этой игры появится позже.")}</p>
      <div class="game-footer"><div><span class="price">${price(game.price)}</span><span class="price-caption">${owned ? "Ваша копия" : "Цифровая версия"}</span></div>
      ${owned ? `<button class="btn btn-secondary btn-small" data-action="owned" disabled>✓ В коллекции</button>` : `<button class="btn btn-primary btn-small" data-action="buy" data-game-id="${Number(game.id)}">${state.user ? "Купить игру" : "Войти и купить"} <span aria-hidden="true">→</span></button>`}</div>
    </div></article>`;
  }
  function warning() { return state.gamesError ? `<div class="api-warning">${esc(state.gamesError)}</div>` : ""; }
  function homePage() {
    const featured = state.games[0];
    return `<section class="hero"><div class="hero-content"><span class="eyebrow">ТВОЯ ИГРОВАЯ ВСЕЛЕННАЯ</span><h1>Игры, в которые<br>хочется <span>пропасть.</span></h1><p class="hero-copy">Открывай новые миры, собирай личную коллекцию и возвращайся к любимым приключениям. Всё, что ты покупаешь, остаётся в твоей библиотеке BloodFoxGames.</p>
      <div class="hero-actions"><a class="btn btn-primary" href="#catalog">Открыть магазин <span>↗</span></a><a class="btn btn-secondary" href="#library">Моя библиотека <span>→</span></a></div>
      <div class="hero-meta"><span><i class="pulse-dot"></i> Каталог и библиотека</span><span>✦ Каталог и коллекция</span></div></div>
      <div class="hero-art" aria-label="Арт BloodFoxGames"><div class="hero-ribbon"></div><div class="hero-game-card"><div class="hero-card-top"><span>BFG / 001</span><span>NEW WORLDS</span></div><div class="hero-card-bottom"><div><strong>${featured ? esc(featured.title) : "BLOODFOX"}</strong><small>FIND YOUR NEXT ADVENTURE</small></div><span>✦</span></div></div><div class="floating-tag"><strong>${state.games.length} ${state.games.length === 1 ? "игра" : "игр"} в каталоге</strong><span>И твоя история только начинается</span></div></div>
    </section>
    <section class="section"><div class="section-head"><div><h2 class="section-title">Всё для твоей игровой жизни</h2><p class="section-subtitle">Магазин, покупки и коллекция — в одном месте.</p></div><a href="#catalog" class="text-link">Посмотреть магазин <span>→</span></a></div>
      <div class="feature-row"><div class="feature-tile"><div class="feature-icon">⌕</div><div><strong>Находи новое</strong><p>Смотри каталог и выбирай приключение.</p></div></div><div class="feature-tile"><div class="feature-icon">↗</div><div><strong>Покупай в один клик</strong><p>Добавляй игры в свою коллекцию.</p></div></div><div class="feature-tile"><div class="feature-icon">▤</div><div><strong>Собирай библиотеку</strong><p>Все приобретённые игры всегда под рукой.</p></div></div></div>
    </section>
    <section class="section"><div class="section-head"><div><h2 class="section-title">Выбрано для тебя</h2><p class="section-subtitle">Первые игры из каталога BloodFoxGames.</p></div><a href="#catalog" class="text-link">Весь каталог <span>→</span></a></div>${warning()}${state.loadingGames ? `<div class="loading">Загружаем игры…</div>` : state.games.length ? `<div class="game-grid">${state.games.slice(0,3).map(game => gameCard(game,{owned: state.user && state.library.some(item => Number(item.id)===Number(game.id))})).join("")}</div>` : `<div class="empty-state"><div class="empty-symbol">✦</div><h2>Каталог загружается</h2><p>Когда API магазина будет доступен, здесь появятся игры из PostgreSQL.</p><button class="btn btn-secondary" data-action="refresh">Повторить загрузку</button></div>`}</section>`;
  }
  function catalogPage() {
    const search = state.search.trim().toLowerCase();
    let games = state.games.filter(game => `${game.title || ""} ${game.description || ""}`.toLowerCase().includes(search));
    if (state.sort === "price-asc") games.sort((a,b) => Number(a.price || 0) - Number(b.price || 0));
    if (state.sort === "price-desc") games.sort((a,b) => Number(b.price || 0) - Number(a.price || 0));
    if (state.sort === "title") games.sort((a,b) => String(a.title || "").localeCompare(String(b.title || ""), "ru"));
    return `<section class="page-banner"><span class="page-kicker">BLOODFOX STORE</span><h1>Магазин игр</h1><p>Выбери мир, который захочешь исследовать. Покупки сохраняются в твоей личной библиотеке.</p></section>
      ${warning()}<div class="catalog-toolbar"><label class="search-wide"><span>⌕</span><input id="catalog-search" placeholder="Поиск по названию или описанию…" value="${esc(state.search)}" aria-label="Поиск по каталогу"></label><label class="sort-control"><span>Сортировка</span><select id="catalog-sort"><option value="featured" ${state.sort === "featured" ? "selected" : ""}>Рекомендуем</option><option value="title" ${state.sort === "title" ? "selected" : ""}>По названию</option><option value="price-asc" ${state.sort === "price-asc" ? "selected" : ""}>Сначала дешевле</option><option value="price-desc" ${state.sort === "price-desc" ? "selected" : ""}>Сначала дороже</option></select></label><span class="result-count" id="result-count">Найдено игр: ${games.length}</span></div>
      <div id="catalog-grid">${state.loadingGames ? `<div class="loading">Загружаем каталог…</div>` : games.length ? `<div class="game-grid">${games.map(game => gameCard(game,{owned: state.user && state.library.some(item=>Number(item.id)===Number(game.id))})).join("")}</div>` : `<div class="empty-state"><div class="empty-symbol">⌕</div><h2>${state.games.length ? "Ничего не найдено" : "Пока нет игр"}</h2><p>${state.games.length ? "Попробуй изменить поисковый запрос." : "Не удалось получить список игр. Убедись, что Docker Compose запущен."}</p>${state.games.length ? "" : `<button class="btn btn-secondary" data-action="refresh">Повторить загрузку</button>`}</div>`}</div>`;
  }
  function favoritesPage() {
    const games = state.games.filter(game => state.favorites.includes(Number(game.id)));
    return `<section class="page-banner"><span class="page-kicker">YOUR WISHLIST</span><h1>Избранное</h1><p>Сохраняй игры, к которым хочешь вернуться. Список хранится в этом браузере.</p></section>${games.length ? `<div class="game-grid">${games.map(game => gameCard(game,{owned: state.user && state.library.some(item=>Number(item.id)===Number(game.id))})).join("")}</div>` : `<div class="empty-state"><div class="empty-symbol">♡</div><h2>Пока ничего не добавлено</h2><p>Нажми на сердечко в карточке игры, чтобы сохранить её здесь.</p><a class="btn btn-primary" href="#catalog">Открыть магазин →</a></div>`}`;
  }
  function libraryPage() {
    if (!state.user) return `<section class="page-banner"><span class="page-kicker">YOUR COLLECTION</span><h1>Твоя библиотека</h1><p>Всё, что ты приобрёл, собрано в одном месте.</p></section><div class="empty-state"><div class="empty-symbol">▤</div><h2>Войди, чтобы открыть библиотеку</h2><p>Авторизуйся или создай аккаунт. После покупки игра появится в твоей коллекции.</p><div class="hero-actions" style="justify-content:center;margin-top:0"><a class="btn btn-primary" href="#login">Войти</a><a class="btn btn-secondary" href="#register">Создать аккаунт</a></div></div>`;
    return `<section class="page-banner"><span class="page-kicker">YOUR COLLECTION</span><h1>Твоя библиотека</h1><p>Твои игры, твоя история. Продолжай приключение с того места, где остановился.</p></section>
      <div class="library-top"><div class="library-user"><div class="library-avatar">${state.user.hasAvatar ? `<img src="/api/v1/storage/me/avatar" alt="">` : esc((state.user.displayName || state.user.username).slice(0,1).toUpperCase())}</div><div><strong>${esc(state.user.displayName || state.user.username)}</strong><small>${esc(state.user.email || "Аккаунт BloodFoxGames")}</small></div></div><div class="library-stat"><strong>${state.library.length}</strong><span>игр в коллекции</span></div></div>
      ${state.libraryError ? `<div class="api-warning">${esc(state.libraryError)}</div>` : ""}
      ${state.loadingLibrary ? `<div class="loading">Загружаем библиотеку…</div>` : state.library.length ? `<div class="game-grid">${state.library.map(game => gameCard(game,{owned:true})).join("")}</div>` : `<div class="empty-state"><div class="empty-symbol">▤</div><h2>Библиотека пока пуста</h2><p>Найди игру в магазине и нажми «Купить игру». Она появится здесь автоматически.</p><a class="btn btn-primary" href="#catalog">Перейти в магазин <span>→</span></a></div>`}`;
  }
  function profilePage() {
    if (!state.user) return `<section class="page-banner"><span class="page-kicker">PLAYER PROFILE</span><h1>Профиль игрока</h1><p>Войди, чтобы управлять своим аккаунтом BloodFoxGames.</p></section><div class="empty-state"><h2>Требуется вход</h2><p>Профиль доступен только после авторизации.</p><div class="hero-actions" style="justify-content:center"><a class="btn btn-primary" href="#login">Войти</a><a class="btn btn-secondary" href="#register">Создать аккаунт</a></div></div>`;
    const profile = state.profile || state.user;
    const initial = String(profile.displayName || profile.username || "?").slice(0,1).toUpperCase();
    const avatar = profile.hasAvatar
      ? `<img src="/api/v1/storage/me/avatar" alt="Фотография профиля">`
      : `<span>${esc(initial)}</span>`;
    return `<section class="page-banner"><span class="page-kicker">PLAYER PROFILE</span><h1>Профиль игрока</h1><p>Управляй публичной информацией, фотографией и безопасностью аккаунта.</p></section>
      <section class="profile-panel profile-overview">
        <div class="profile-avatar">${avatar}</div>
        <div class="profile-details"><span class="profile-label">АККАУНТ BLOODFOXGAMES</span><h2>${esc(profile.displayName || profile.username)}</h2><p>@${esc(profile.username)} · ${esc(profile.email || "Email не указан")}</p><small>ID игрока: ${Number(profile.id || state.user.id)}</small></div>
        <div class="profile-actions"><a class="btn btn-secondary" href="#library">Открыть библиотеку</a><button class="btn btn-ghost" data-action="refresh-profile">Обновить</button></div>
      </section>
      <section class="profile-settings-grid">
        <article class="profile-settings-card profile-main-settings">
          <div class="settings-heading"><span class="settings-icon">◉</span><div><h2>Фотография и данные</h2><p>Изображение хранится в S3, данные — в PostgreSQL.</p></div></div>
          <div class="avatar-editor"><div class="avatar-preview">${avatar}</div><div class="avatar-editor-controls"><label class="btn btn-secondary" for="avatar-input">Загрузить фотографию</label><input id="avatar-input" type="file" accept="image/png,image/jpeg,image/webp,image/gif" hidden><button type="button" class="btn btn-ghost" data-action="remove-avatar" ${profile.hasAvatar ? "" : "disabled"}>Удалить фото</button><small>PNG, JPG, WEBP или GIF · до 5 МБ</small></div></div>
          <form id="profile-form" class="settings-form">
            <label class="form-field"><span>Отображаемое имя</span><input name="displayName" required minlength="1" maxlength="48" value="${esc(profile.displayName || profile.username)}" autocomplete="nickname"></label>
            <label class="form-field"><span>Никнейм</span><input name="username" required minlength="3" maxlength="40" pattern="[A-Za-z0-9_.-]+" value="${esc(profile.username)}" autocomplete="username"><small>3–40 символов: латиница, цифры, точка, дефис и подчёркивание.</small></label>
            <label class="form-field"><span>Email</span><input name="email" type="email" required maxlength="160" value="${esc(profile.email)}" autocomplete="email"></label>
            <label class="form-field"><span>О себе</span><textarea name="bio" rows="3" maxlength="240" placeholder="Расскажи о себе и любимых играх…">${esc(profile.bio || "")}</textarea><small>До 240 символов.</small></label>
            <button class="btn btn-primary form-submit" type="submit">Сохранить данные профиля</button>
          </form>
        </article>
        <article class="profile-settings-card">
          <div class="settings-heading"><span class="settings-icon">⚙</span><div><h2>Приватность и уведомления</h2><p>Настройки сохраняются в аккаунте.</p></div></div>
          <form id="preferences-form" class="settings-form">
            <label class="preference-row"><span><strong>Публичный профиль</strong><small>Разрешить показывать карточку профиля другим пользователям.</small></span><input type="checkbox" name="publicProfile" ${profile.publicProfile ? "checked" : ""}></label>
            <label class="preference-row"><span><strong>Email-уведомления</strong><small>Предпочтение для будущих писем об аккаунте. Доставка email пока не подключена.</small></span><input type="checkbox" name="emailNotifications" ${profile.emailNotifications ? "checked" : ""}></label>
            <label class="preference-row"><span><strong>Новости игр</strong><small>Получать новости и обновления игр внутри платформы, когда функция будет включена.</small></span><input type="checkbox" name="gameUpdates" ${profile.gameUpdates ? "checked" : ""}></label>
            <button class="btn btn-primary form-submit" type="submit">Сохранить настройки</button>
          </form>
          <div class="settings-note">Публичная страница и отправка уведомлений ещё не реализованы. Здесь сохраняются предпочтения аккаунта.</div>
        </article>
        <article class="profile-settings-card profile-security-card">
          <div class="settings-heading"><span class="settings-icon">♢</span><div><h2>Безопасность аккаунта</h2><p>Смени пароль, если считаешь, что он мог стать известен кому-то ещё.</p></div></div>
          <form id="password-form" class="settings-form">
            <label class="form-field"><span>Текущий пароль</span><input name="currentPassword" type="password" required autocomplete="current-password"></label>
            <label class="form-field"><span>Новый пароль</span><input name="newPassword" type="password" required minlength="12" maxlength="100" autocomplete="new-password"><small>Минимум 12 символов. После смены потребуется войти снова.</small></label>
            <button class="btn btn-primary form-submit" type="submit">Изменить пароль</button>
          </form>
        </article>
      </section>
      <section class="section"><div class="section-head"><div><h2 class="section-title">Твоя игровая статистика</h2><p class="section-subtitle">Данные загружены с сервера.</p></div></div><div class="profile-stat-grid"><div class="profile-stat"><strong>${state.library.length}</strong><span>игр в библиотеке</span></div><div class="profile-stat"><strong>${esc(profile.displayName || profile.username)}</strong><span>имя игрока</span></div></div></section>`;
  }
  async function loadProfile() {
    if (!state.user) { state.profile = null; return; }
    try {
      const profile = await api("/api/v1/users/me");
      if (!profile || Number(profile.id) !== Number(state.user.id)) throw new Error("Профиль не найден.");
      state.profile = profile;
      saveSession(profile);
    } catch (error) {
      state.profile = null;
      if (error.status === 401) clearSession();
      notify(`Не удалось загрузить профиль: ${error.message}`, true);
    }
  }
  async function handleAvatarUpload(input) {
    const file = input.files && input.files[0];
    if (!file) return;
    if (file.size > 5 * 1024 * 1024) { notify("Файл слишком большой. Максимум 5 МБ.", true); input.value = ""; return; }
    const form = new FormData();
    form.append("file", file);
    input.disabled = true;
    try {
      await api("/api/v1/storage/me/avatar", { method: "POST", body: form });
      await loadProfile();
      render();
      notify("Фотография профиля сохранена в S3.");
    } catch (error) {
      notify(`Не удалось загрузить фотографию: ${error.message}`, true);
    } finally { input.disabled = false; input.value = ""; }
  }
  async function handleProfileForm(form) {
    const data = Object.fromEntries(new FormData(form).entries());
    try {
      const profile = await api("/api/v1/users/me/profile", { method: "PUT", body: JSON.stringify(data) });
      state.profile = profile;
      saveSession(profile);
      render();
      notify("Данные профиля сохранены.");
    } catch (error) { notify(`Не удалось сохранить профиль: ${error.message}`, true); }
  }
  async function handlePreferencesForm(form) {
    const data = {
      publicProfile: form.elements.publicProfile.checked,
      emailNotifications: form.elements.emailNotifications.checked,
      gameUpdates: form.elements.gameUpdates.checked
    };
    try {
      const profile = await api("/api/v1/users/me/preferences", { method: "PUT", body: JSON.stringify(data) });
      state.profile = profile;
      saveSession(profile);
      render();
      notify("Настройки сохранены.");
    } catch (error) { notify(`Не удалось сохранить настройки: ${error.message}`, true); }
  }
  async function handlePasswordForm(form) {
    const data = Object.fromEntries(new FormData(form).entries());
    try {
      await api("/api/v1/users/me/password", { method: "PUT", body: JSON.stringify(data) });
      clearSession();
      notify("Пароль изменён. Войди с новым паролем.");
      routeTo("login");
    } catch (error) { notify(`Не удалось изменить пароль: ${error.message}`, true); }
  }
  function authPage(mode) {
    const register = mode === "register";
    return `<section class="auth-layout"><div class="auth-art"><a class="brand" href="#home"><span class="brand-icon"><img src="/assets/bloodfox-logo.png" alt=""></span><span class="brand-word">BLOOD<span>FOX</span>GAMES<span class="brand-caption">PLAY BEYOND THE DARK</span></span></a><div><h2>${register ? "Начни свою игровую историю." : "С возвращением, игрок."}</h2><p>${register ? "Создай аккаунт, открывай новые миры и собирай свою коллекцию." : "Войди в аккаунт, чтобы вернуться к играм из своей библиотеки."}</p></div><span class="eyebrow">YOUR NEXT ADVENTURE STARTS HERE</span></div>
      <div class="auth-form-wrap"><h1>${register ? "Создать аккаунт" : "Войти в BloodFoxGames"}</h1><p class="form-lead">${register ? "Всего пара полей — и можно выбирать первую игру." : "Введи данные своего аккаунта, чтобы открыть библиотеку."}</p>
      <form id="auth-form" data-mode="${mode}" autocomplete="${register ? "on" : "on"}">
        <label class="form-field"><span>Имя пользователя</span><input name="username" required minlength="3" maxlength="40" autocomplete="username" placeholder="Например, DarkFox" /></label>
        ${register ? `<label class="form-field"><span>Электронная почта</span><input name="email" type="email" required maxlength="160" autocomplete="email" placeholder="you@example.com" /></label>` : ""}
        <label class="form-field"><span>Пароль</span><input name="password" type="password" required minlength="${register ? 8 : 6}" maxlength="100" autocomplete="${register ? "new-password" : "current-password"}" placeholder="${register ? "Не менее 8 символов" : "Пароль аккаунта"}" /></label>
        <button class="btn btn-primary form-submit" type="submit">${register ? "Создать аккаунт" : "Войти в аккаунт"} <span>→</span></button>
      </form>
      <p class="form-bottom">${register ? `Уже есть аккаунт? <button type="button" data-route="login">Войти</button>` : `Впервые здесь? <button type="button" data-route="register">Создать аккаунт</button>`}</p>
      <p class="form-note">Учебный прототип. Используй только тестовые данные, не повторяй пароль от важных сервисов.</p></div></section>`;
  }
  function render() {
    let content;
    if (state.page === "catalog") content = catalogPage();
    else if (state.page === "library") content = libraryPage();
    else if (state.page === "favorites") content = favoritesPage();
    else if (state.page === "profile") content = profilePage();
    else if (state.page === "login" || state.page === "register") content = authPage(state.page);
    else { state.page = "home"; content = homePage(); }
    shell(content);
  }
  async function route() {
    state.page = location.hash.replace(/^#\/?/, "") || "home";
    if (!["home", "catalog", "library", "favorites", "profile", "login", "register"].includes(state.page)) state.page = "home";
    if (!state.sessionChecked) {
      await restoreSession();
      state.sessionChecked = true;
    }
    await loadGames();
    if (state.user) await loadLibrary();
    if (state.page === "profile") await loadProfile();
    render();
  }
  async function handleBuy(button) {
    if (!state.user) {
      state.nextPage = "catalog";
      notify("Войди или создай аккаунт, чтобы купить игру.");
      routeTo("login");
      return;
    }
    const id = Number(button.dataset.gameId);
    if (!Number.isFinite(id) || id <= 0) return;
    button.disabled = true;
    const oldText = button.textContent;
    button.textContent = "Обрабатываем…";
    try {
      const result = await api(`/api/v1/users/me/buy/${id}`, { method: "POST", body: "{}" });
      notify(typeof result === "string" ? result : "Игра добавлена в библиотеку!");
      await loadLibrary();
      render();
    } catch (error) {
      notify(`Не удалось купить игру: ${error.message}`, true);
      button.disabled = false;
      button.textContent = oldText;
    }
  }
  async function handleAuth(form) {
    const mode = form.dataset.mode;
    const data = Object.fromEntries(new FormData(form).entries());
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    button.textContent = mode === "register" ? "Создаём аккаунт…" : "Проверяем…";
    try {
      const path = mode === "register" ? "/api/v1/users/register" : "/api/v1/users/login";
      const user = await api(path, { method: "POST", body: JSON.stringify(data) });
      if (!user || !user.id || !user.username) throw new Error("Сервер вернул неполные данные аккаунта.");
      saveSession(user);
      await loadLibrary();
      notify(mode === "register" ? "Аккаунт создан! Добро пожаловать в BloodFoxGames." : `С возвращением, ${user.username}!`);
      const destination = state.nextPage || "home";
      state.nextPage = "home";
      routeTo(destination);
    } catch (error) {
      notify(error.message || "Не удалось выполнить запрос.", true);
      button.disabled = false;
      button.textContent = mode === "register" ? "Создать аккаунт →" : "Войти в аккаунт →";
    }
  }
  app.addEventListener("click", async event => {
    const routeButton = event.target.closest("[data-route]");
    if (routeButton) { routeTo(routeButton.dataset.route); return; }
    const actionButton = event.target.closest("[data-action]");
    if (!actionButton) return;
    const action = actionButton.dataset.action;
    if (action === "toggle-theme") { toggleTheme(); return; }
    if (action === "buy") await handleBuy(actionButton);
    if (action === "favorite") { toggleFavorite(actionButton.dataset.gameId); render(); }
    if (action === "logout") {
      try { await api("/api/v1/users/logout", { method: "POST" }); } catch (_) { /* local UI still clears on an expired server session */ }
      clearSession();
      notify("Ты вышел из аккаунта.");
      routeTo("home");
    }
    if (action === "refresh") { await loadGames(); render(); }
    if (action === "refresh-profile") { await loadProfile(); render(); }
    if (action === "remove-avatar") {
      try {
        await api("/api/v1/storage/me/avatar", { method: "DELETE" });
        await loadProfile(); render(); notify("Фотография удалена.");
      } catch (error) { notify(`Не удалось удалить фотографию: ${error.message}`, true); }
    }
  });
  app.addEventListener("change", async event => {
    if (event.target.id === "avatar-input") await handleAvatarUpload(event.target);
  });
  app.addEventListener("submit", async event => {
    if (event.target.id === "auth-form") {
      event.preventDefault();
      await handleAuth(event.target);
    } else if (event.target.id === "profile-form") {
      event.preventDefault();
      await handleProfileForm(event.target);
    } else if (event.target.id === "preferences-form") {
      event.preventDefault();
      await handlePreferencesForm(event.target);
    } else if (event.target.id === "password-form") {
      event.preventDefault();
      await handlePasswordForm(event.target);
    }
  });
  app.addEventListener("input", event => {
    const input = event.target;
    if (input.id === "catalog-sort") { state.sort = input.value; render(); return; }
    if (input.id === "catalog-search" || input.id === "header-search") {
      state.search = input.value;
      const current = input;
      const start = current.selectionStart;
      const end = current.selectionEnd;
      if (state.page === "catalog") {
        const holder = document.getElementById("catalog-grid");
        const count = document.getElementById("result-count");
        const search = state.search.trim().toLowerCase();
        const games = state.games.filter(game => `${game.title || ""} ${game.description || ""}`.toLowerCase().includes(search));
        if (state.sort === "price-asc") games.sort((a,b) => Number(a.price || 0) - Number(b.price || 0));
        if (state.sort === "price-desc") games.sort((a,b) => Number(b.price || 0) - Number(a.price || 0));
        if (state.sort === "title") games.sort((a,b) => String(a.title || "").localeCompare(String(b.title || ""), "ru"));
        if (holder) holder.innerHTML = games.length ? `<div class="game-grid">${games.map(game => gameCard(game,{owned: state.user && state.library.some(item=>Number(item.id)===Number(game.id))})).join("")}</div>` : `<div class="empty-state"><div class="empty-symbol">⌕</div><h2>Ничего не найдено</h2><p>Попробуй изменить поисковый запрос.</p></div>`;
        if (count) count.textContent = `Найдено игр: ${games.length}`;
      } else {
        render();
        const newInput = document.getElementById("header-search");
        if (newInput) { newInput.focus(); newInput.setSelectionRange(start, end); }
      }
    }
  });
  document.addEventListener("click", event => {
    const menu = document.getElementById("mobile-menu");
    if (menu && event.target.closest("#mobile-menu")) {
      const nav = document.getElementById("main-nav");
      const open = nav.classList.toggle("open");
      menu.setAttribute("aria-expanded", String(open));
    } else if (!event.target.closest("#main-nav") && menu) {
      const nav = document.getElementById("main-nav");
      nav?.classList.remove("open");
      menu.setAttribute("aria-expanded", "false");
    }
  });
  window.addEventListener("hashchange", route);
  applyTheme();
  route();
})();
