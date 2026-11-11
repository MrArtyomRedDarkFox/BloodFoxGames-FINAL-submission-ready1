import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const app = readFileSync(new URL('../app.js', import.meta.url), 'utf8');
const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8');
const nginx = readFileSync(new URL('../nginx.conf', import.meta.url), 'utf8');

test('frontend JavaScript parses without syntax errors', () => {
  const result = spawnSync(process.execPath, ['--check', fileURLToPath(new URL('../app.js', import.meta.url))], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
});

test('all supported routes are explicitly whitelisted', () => {
  for (const route of ['home', 'catalog', 'library', 'favorites', 'profile', 'login', 'register']) {
    assert.match(app, new RegExp(`['"]${route}['"]`));
  }
});

test('delegated UI actions have concrete handlers', () => {
  for (const action of ['buy', 'favorite', 'logout', 'refresh', 'refresh-profile']) {
    assert.ok(app.includes(`action === "${action}"`), `missing handler for ${action}`);
  }
  assert.match(app, /addEventListener\("submit"/);
  assert.match(app, /addEventListener\("input"/);
});

test('auth and profile flows call backend endpoints', () => {
  for (const endpoint of ['/api/v1/users/register', '/api/v1/users/login', '/api/v1/users/me', '/api/v1/users/me/library', '/api/v1/users/me/profile', '/api/v1/users/me/preferences', '/api/v1/users/me/password']) {
    assert.ok(app.includes(endpoint), `missing endpoint ${endpoint}`);
  }
});

test('frontend uses same-origin API proxy and has SPA fallback', () => {
  assert.match(html, /app\.js/);
  assert.match(nginx, /location \/api\//);
  assert.match(nginx, /try_files \$uri \$uri\/ \/index\.html/);
});

test('authentication uses same-origin HttpOnly server session instead of localStorage identity', () => {
  assert.match(app, /credentials: \"same-origin\"/);
  assert.match(app, /restoreSession/);
  assert.doesNotMatch(app, /localStorage\.setItem\(\"bloodfox\.session\"/);
  const controller = readFileSync(new URL('../../game-store-service/src/main/java/com/example/gamestore/controller/UserController.java', import.meta.url), 'utf8');
  const properties = readFileSync(new URL('../../game-store-service/src/main/resources/application.properties', import.meta.url), 'utf8');
  assert.match(controller, /BLOODFOX_AUTH_USER_ID/);
  assert.match(controller, /requireSameUser/);
  assert.match(controller, /session\.invalidate/);
  assert.match(properties, /cookie\.http-only=true/);
  assert.match(properties, /cookie\.same-site=strict/);
});


test('wishlist and sorting features are wired to persistent browser state', () => {
  assert.match(app, /bloodfox\.favorites/);
  assert.match(app, /function toggleFavorite/);
  assert.match(app, /catalog-sort/);
  assert.match(app, /price-asc/);
  assert.match(app, /favoritesPage/);
});

test('S3 storage is configured as a separate persistent SeaweedFS service', () => {
  const compose = readFileSync(new URL('../../docker-compose.yml', import.meta.url), 'utf8');
  const pom = readFileSync(new URL('../../game-store-service/pom.xml', import.meta.url), 'utf8');
  const controller = readFileSync(new URL('../../game-store-service/src/main/java/com/example/gamestore/controller/StorageController.java', import.meta.url), 'utf8');
  assert.match(compose, /chrislusf\/seaweedfs:4\.48/);
  assert.match(compose, /s3_data:\n/);
  assert.match(compose, /S3_ENDPOINT: http:\/\/seaweedfs:8333/);
  assert.match(pom, /software\.amazon\.awssdk/);
  assert.match(controller, /X-Storage-Admin-Token/);
  assert.match(controller, /5\*1024\*1024/);
});


test('FOXGAME theme is selectable and remembered between visits', () => {
  assert.match(app, /bloodfox\.theme/);
  assert.match(app, /data-action="toggle-theme"/);
  assert.match(app, /function applyTheme/);
  assert.match(app, /function toggleTheme/);
  const css = readFileSync(new URL('../styles.css', import.meta.url), 'utf8');
  assert.match(css, /html\[data-theme="foxgame"\]/);
  assert.match(css, /@media \(max-width:700px\)/);
});

test('Compose uses unique generated container names and local-only published ports', () => {
  const compose = readFileSync(new URL('../../docker-compose.yml', import.meta.url), 'utf8');
  assert.doesNotMatch(compose, /container_name:/);
  assert.match(compose, /127\.0\.0\.1:3000:80/);
  assert.match(compose, /127\.0\.0\.1:8333:8333/);
});


test('profile settings include persistent avatar, privacy, notification and password endpoints', () => {
  for (const item of ['avatar-input', 'profile-form', 'preferences-form', 'password-form', 'remove-avatar']) assert.ok(app.includes(item));
  assert.match(app, /api\(\"\/api\/v1\/storage\/me\/avatar\"/);
  assert.match(app, /handleProfileForm/);
  assert.match(app, /handlePreferencesForm/);
  assert.match(app, /handlePasswordForm/);
  const avatarController = readFileSync(new URL('../../game-store-service/src/main/java/com/example/gamestore/controller/ProfileAvatarController.java', import.meta.url), 'utf8');
  assert.match(avatarController, /MAX_AVATAR_BYTES/);
  assert.match(avatarController, /detectImage/);
  assert.match(avatarController, /authenticatedUserId/);
});

test('profile settings endpoints are session protected in the backend', () => {
  const controller = readFileSync(new URL('../../game-store-service/src/main/java/com/example/gamestore/controller/UserController.java', import.meta.url), 'utf8');
  for (const mapping of ['/me/profile', '/me/preferences', '/me/password', '/me/library', '/me/buy/{gameId}']) assert.ok(controller.includes(mapping));
  assert.match(controller, /requireUserId\(session\)/);
});

test('Users Service participates in the account directory sync flow', () => {
  const storeService = readFileSync(new URL('../../game-store-service/src/main/java/com/example/gamestore/service/UserService.java', import.meta.url), 'utf8');
  const directoryController = readFileSync(new URL('../../users-service/src/main/java/com/example/users/UserController.java', import.meta.url), 'utf8');
  const directoryEntity = readFileSync(new URL('../../users-service/src/main/java/com/example/users/User.java', import.meta.url), 'utf8');
  const compose = readFileSync(new URL('../../docker-compose.yml', import.meta.url), 'utf8');
  assert.match(storeService, /syncUserDirectory\(created\)/);
  assert.match(storeService, /syncUserDirectory\(saved\)/);
  assert.match(storeService, /usersServiceUrl \+ "\/api\/v1\/users\/"/);
  assert.match(directoryController, /@PutMapping\("\/{accountId}"\)/);
  assert.match(directoryEntity, /@Column\(name = "account_id", unique = true\)/);
  assert.match(compose, /USERS_SERVICE_URL: http:\/\/users-service:8081/);
  assert.match(compose, /users-service:\n\s+condition: service_started/);
});
