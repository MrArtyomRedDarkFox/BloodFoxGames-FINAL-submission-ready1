package com.example.gamestore.controller;

import com.example.gamestore.service.UserService;
import com.example.gamestore.storage.S3StorageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/storage/me/avatar")
@ConditionalOnProperty(prefix = "app.storage", name = "enabled", havingValue = "true")
public class ProfileAvatarController {
    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;
    private final S3StorageService storage;
    private final UserService users;

    public ProfileAvatarController(S3StorageService storage, UserService users) {
        this.storage = storage;
        this.users = users;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestPart("file") MultipartFile file, HttpSession session) throws Exception {
        long userId = authenticatedUserId(session);
        if (file == null || file.isEmpty() || file.getSize() > MAX_AVATAR_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Изображение должно быть размером от 1 байта до 5 МБ");
        }

        byte[] bytes = file.getBytes();
        ImageType image = detectImage(bytes);
        if (image == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Разрешены только настоящие JPEG, PNG, WEBP и GIF изображения");
        }
        String declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!declared.isBlank() && !declared.equals(image.contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Тип файла не совпадает с содержимым изображения");
        }

        String newKey = storage.uploadAvatar(userId, bytes, image.contentType, image.extension);
        String previousKey = users.replaceAvatarKey(userId, newKey);
        if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(newKey)) {
            try { storage.delete(previousKey); }
            catch (RuntimeException ex) { System.err.println("Could not delete previous avatar object: " + ex.getMessage()); }
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("hasAvatar", true, "message", "Фотография профиля обновлена"));
    }

    @GetMapping
    public ResponseEntity<Void> view(HttpSession session) {
        long userId = authenticatedUserId(session);
        String key = users.getAvatarKey(userId);
        if (key == null || key.isBlank()) return ResponseEntity.notFound().build();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, storage.downloadUrl(key))
                .cacheControl(CacheControl.noStore())
                .build();
    }

    @DeleteMapping
    public ResponseEntity<Void> remove(HttpSession session) {
        long userId = authenticatedUserId(session);
        String previousKey = users.clearAvatar(userId);
        if (previousKey != null && !previousKey.isBlank()) {
            try { storage.delete(previousKey); }
            catch (RuntimeException ex) { System.err.println("Could not delete avatar object: " + ex.getMessage()); }
        }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private long authenticatedUserId(HttpSession session) {
        Object id = session == null ? null : session.getAttribute(UserController.SESSION_USER_ID);
        if (!(id instanceof Long userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Войдите в аккаунт");
        return userId;
    }

    private ImageType detectImage(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 0x50 && b[2] == 0x4e && b[3] == 0x47
                && b[4] == 0x0d && b[5] == 0x0a && b[6] == 0x1a && b[7] == 0x0a) return new ImageType("image/png", ".png");
        if (b.length >= 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff) return new ImageType("image/jpeg", ".jpg");
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
                && (b[4] == '7' || b[4] == '9') && b[5] == 'a') return new ImageType("image/gif", ".gif");
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return new ImageType("image/webp", ".webp");
        return null;
    }

    private record ImageType(String contentType, String extension) {}
}
