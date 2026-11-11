package com.example.gamestore.dto;

import com.example.gamestore.model.Game;
import com.example.gamestore.model.User;

import java.util.Set;
import java.util.stream.Collectors;

public class UserProfileDto {
    private final Long id;
    private final String username;
    private final String email;
    private final String displayName;
    private final String bio;
    private final boolean hasAvatar;
    private final boolean publicProfile;
    private final boolean emailNotifications;
    private final boolean gameUpdates;
    private final Set<GameSummaryDto> library;

    public UserProfileDto(User user) {
        this(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(), user.getBio(),
                user.getAvatarKey() != null && !user.getAvatarKey().isBlank(), user.isPublicProfile(),
                user.isEmailNotifications(), user.isGameUpdates(), user.getLibrary());
    }

    /** Backwards-compatible constructor used by lightweight DTO tests. */
    public UserProfileDto(Long id, String username, String email, Set<Game> library) {
        this(id, username, email, username, "", false, true, true, true, library);
    }

    public UserProfileDto(Long id, String username, String email, String displayName, String bio,
                          boolean hasAvatar, boolean publicProfile, boolean emailNotifications,
                          boolean gameUpdates, Set<Game> library) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.displayName = displayName == null || displayName.isBlank() ? username : displayName;
        this.bio = bio == null ? "" : bio;
        this.hasAvatar = hasAvatar;
        this.publicProfile = publicProfile;
        this.emailNotifications = emailNotifications;
        this.gameUpdates = gameUpdates;
        this.library = library == null ? Set.of() : library.stream()
                .map(GameSummaryDto::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getBio() { return bio; }
    public boolean isHasAvatar() { return hasAvatar; }
    public boolean isPublicProfile() { return publicProfile; }
    public boolean isEmailNotifications() { return emailNotifications; }
    public boolean isGameUpdates() { return gameUpdates; }
    public Set<GameSummaryDto> getLibrary() { return library; }

    public static class GameSummaryDto {
        private final Long id;
        private final String title;
        private final String description;
        private final Double price;

        public GameSummaryDto(Game game) {
            this.id = game.getId();
            this.title = game.getTitle();
            this.description = game.getDescription();
            this.price = game.getPrice();
        }
        public Long getId() { return id; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public Double getPrice() { return price; }
    }
}
