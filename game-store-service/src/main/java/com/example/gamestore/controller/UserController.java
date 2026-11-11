package com.example.gamestore.controller;

import com.example.gamestore.dto.ChangePasswordDto;
import com.example.gamestore.dto.LoginDto;
import com.example.gamestore.dto.PreferencesUpdateDto;
import com.example.gamestore.dto.ProfileUpdateDto;
import com.example.gamestore.dto.RegisterDto;
import com.example.gamestore.dto.UserProfileDto;
import com.example.gamestore.dto.UserProfileDto.GameSummaryDto;
import com.example.gamestore.model.User;
import com.example.gamestore.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    public static final String SESSION_USER_ID = "BLOODFOX_AUTH_USER_ID";
    private final UserService userService;

    public UserController(UserService userService) { this.userService = userService; }

    @PostMapping("/register")
    public ResponseEntity<UserProfileDto> register(@RequestBody RegisterDto dto, HttpServletRequest request) {
        User user = userService.register(dto);
        establishSession(request, user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(userService.toProfile(user));
    }

    @PostMapping("/login")
    public ResponseEntity<UserProfileDto> login(@RequestBody LoginDto dto, HttpServletRequest request) {
        User user = userService.login(dto);
        establishSession(request, user.getId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(userService.toProfile(user));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getCurrentProfile(HttpSession session) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(userService.getProfile(requireUserId(session)));
    }

    @PutMapping("/me/profile")
    public ResponseEntity<UserProfileDto> updateProfile(@RequestBody ProfileUpdateDto dto, HttpSession session) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(userService.updateProfile(requireUserId(session), dto));
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<UserProfileDto> updatePreferences(@RequestBody PreferencesUpdateDto dto, HttpSession session) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(userService.updatePreferences(requireUserId(session), dto));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(@RequestBody ChangePasswordDto dto,
                                                               HttpSession session) {
        userService.changePassword(requireUserId(session), dto);
        session.invalidate();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("message", "Пароль изменён. Войдите снова."));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileDto> getProfile(@PathVariable Long userId, HttpSession session) {
        requireSameUser(userId, session);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(userService.getProfile(userId));
    }

    @PostMapping("/me/buy/{gameId}")
    public ResponseEntity<Map<String, String>> buyCurrentUserGame(@PathVariable Long gameId, HttpSession session) {
        userService.buyGame(requireUserId(session), gameId);
        return ResponseEntity.ok(Map.of("message", "Игра добавлена в библиотеку"));
    }

    /** Legacy path is retained for compatibility, but the path ID is never trusted as identity. */
    @PostMapping("/{userId}/buy/{gameId}")
    public ResponseEntity<Map<String, String>> buyGame(@PathVariable Long userId, @PathVariable Long gameId, HttpSession session) {
        requireSameUser(userId, session);
        userService.buyGame(userId, gameId);
        return ResponseEntity.ok(Map.of("message", "Игра добавлена в библиотеку"));
    }

    @GetMapping("/me/library")
    public ResponseEntity<Set<GameSummaryDto>> getCurrentLibrary(HttpSession session) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(userService.getLibrary(requireUserId(session)));
    }

    /** Legacy path is retained but protected by the authenticated session. */
    @GetMapping("/{userId}/library")
    public ResponseEntity<Set<GameSummaryDto>> getLibrary(@PathVariable Long userId, HttpSession session) {
        requireSameUser(userId, session);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(userService.getLibrary(userId));
    }

    private void establishSession(HttpServletRequest request, Long userId) {
        HttpSession existing = request.getSession(false);
        if (existing != null) existing.invalidate();
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(SESSION_USER_ID, userId);
        session.setMaxInactiveInterval(12 * 60 * 60);
    }

    private Long requireUserId(HttpSession session) {
        Object id = session == null ? null : session.getAttribute(SESSION_USER_ID);
        if (!(id instanceof Long userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Войдите в аккаунт");
        }
        return userId;
    }

    private void requireSameUser(Long requestedUserId, HttpSession session) {
        if (!requireUserId(session).equals(requestedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к этому аккаунту");
        }
    }
}
