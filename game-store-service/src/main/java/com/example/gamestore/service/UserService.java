package com.example.gamestore.service;

import com.example.gamestore.dto.ChangePasswordDto;
import com.example.gamestore.dto.LoginDto;
import com.example.gamestore.dto.PreferencesUpdateDto;
import com.example.gamestore.dto.ProfileUpdateDto;
import com.example.gamestore.dto.RegisterDto;
import com.example.gamestore.dto.UserProfileDto;
import com.example.gamestore.model.Game;
import com.example.gamestore.model.User;
import com.example.gamestore.repository.GameRepository;
import com.example.gamestore.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UserService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{3,40}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.users-service.url:http://users-service:8081}")
    private String usersServiceUrl;

    public UserService(UserRepository userRepository, GameRepository gameRepository) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
    }

    @Transactional
    public User register(RegisterDto dto) {
        if (dto == null || dto.getUsername() == null || dto.getEmail() == null || dto.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите имя, email и пароль");
        }
        String username = dto.getUsername().trim();
        String email = dto.getEmail().trim().toLowerCase();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Имя должно содержать 3–40 символов: латинские буквы, цифры, точку, дефис или подчёркивание");
        }
        if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите корректный email");
        }
        if (dto.getPassword().length() < 8 || dto.getPassword().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароль должен содержать от 8 до 100 символов");
        }
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Имя пользователя уже занято");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email уже занят");
        }
        User created = userRepository.save(new User(username, email, passwordEncoder.encode(dto.getPassword())));
        syncUserDirectory(created);
        return created;
    }

    @Transactional(readOnly = true)
    public User login(LoginDto dto) {
        if (dto == null || dto.getUsername() == null || dto.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверное имя пользователя или пароль");
        }
        User user = userRepository.findByUsernameIgnoreCase(dto.getUsername().trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Неверное имя пользователя или пароль"));
        if (user.getPassword() == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверное имя пользователя или пароль");
        }
        // Initialize the lazy library while the read-only transaction is still open.
        user.getLibrary().size();
        return user;
    }

    public UserProfileDto toProfile(User user) { return new UserProfileDto(user); }

    @Transactional(readOnly = true)
    public UserProfileDto getProfile(Long userId) {
        return new UserProfileDto(findUser(userId));
    }

    @Transactional
    public UserProfileDto updateProfile(Long userId, ProfileUpdateDto dto) {
        User user = findUser(userId);
        if (dto == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пустые данные профиля");

        String username = dto.getUsername() == null ? "" : dto.getUsername().trim();
        String email = dto.getEmail() == null ? "" : dto.getEmail().trim().toLowerCase();
        String displayName = dto.getDisplayName() == null ? "" : dto.getDisplayName().trim();
        String bio = dto.getBio() == null ? "" : dto.getBio().trim();

        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный никнейм (3–40 символов)");
        }
        if (!EMAIL_PATTERN.matcher(email).matches() || email.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите корректный email");
        }
        if (displayName.isBlank() || displayName.length() > 48) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Отображаемое имя должно содержать 1–48 символов");
        }
        if (bio.length() > 240) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Описание профиля не должно превышать 240 символов");
        }
        userRepository.findByUsernameIgnoreCase(username)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Этот никнейм уже занят"); });
        userRepository.findByEmailIgnoreCase(email)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "Этот email уже используется"); });

        user.setUsername(username);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setBio(bio);
        User saved = userRepository.save(user);
        syncUserDirectory(saved);
        return new UserProfileDto(saved);
    }

    @Transactional
    public UserProfileDto updatePreferences(Long userId, PreferencesUpdateDto dto) {
        User user = findUser(userId);
        if (dto == null || dto.getPublicProfile() == null || dto.getEmailNotifications() == null || dto.getGameUpdates() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не все настройки переданы");
        }
        user.setPublicProfile(dto.getPublicProfile());
        user.setEmailNotifications(dto.getEmailNotifications());
        user.setGameUpdates(dto.getGameUpdates());
        return new UserProfileDto(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordDto dto) {
        User user = findUser(userId);
        if (dto == null || dto.getCurrentPassword() == null || dto.getNewPassword() == null
                || !passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Текущий пароль указан неверно");
        }
        if (dto.getNewPassword().length() < 12 || dto.getNewPassword().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Новый пароль должен содержать от 12 до 100 символов");
        }
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Новый пароль должен отличаться от текущего");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public String replaceAvatarKey(Long userId, String newKey) {
        User user = findUser(userId);
        String previous = user.getAvatarKey();
        user.setAvatarKey(newKey);
        userRepository.save(user);
        return previous;
    }

    @Transactional
    public String clearAvatar(Long userId) {
        User user = findUser(userId);
        String previous = user.getAvatarKey();
        user.setAvatarKey(null);
        userRepository.save(user);
        return previous;
    }

    @Transactional(readOnly = true)
    public String getAvatarKey(Long userId) {
        return findUser(userId).getAvatarKey();
    }

    @Transactional
    public void buyGame(Long userId, Long gameId) {
        User user = findUser(userId);
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Игра не найдена"));
        if (user.getLibrary() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Библиотека пользователя не инициализирована");
        }
        if (user.getLibrary().contains(game)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Эта игра уже есть в библиотеке");
        }
        user.getLibrary().add(game);
        userRepository.save(user);
        try {
            restTemplate.postForEntity(
                    "http://notifications-service:8082/api/v1/notifications",
                    Map.of("user_id", userId, "message", "Game purchased: " + game.getTitle()),
                    String.class
            );
        } catch (RestClientException exception) {
            System.err.println("Notification failed: " + exception.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Set<UserProfileDto.GameSummaryDto> getLibrary(Long userId) {
        User user = findUser(userId);
        return user.getLibrary() == null ? Set.of() : user.getLibrary().stream()
                .map(UserProfileDto.GameSummaryDto::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Keeps the Users Service directory projection in sync. Game Store remains the
     * source of truth for credentials and account data; this service stores only
     * the account ID, username and email needed by the separate directory API.
     */
    private void syncUserDirectory(User user) {
        try {
            restTemplate.put(
                    usersServiceUrl + "/api/v1/users/" + user.getId(),
                    Map.of("username", user.getUsername(), "email", user.getEmail())
            );
        } catch (RestClientException exception) {
            // Account creation/profile editing must remain available if the read-model is temporarily offline.
            System.err.println("Users directory sync failed for account " + user.getId() + ": " + exception.getMessage());
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }
}
