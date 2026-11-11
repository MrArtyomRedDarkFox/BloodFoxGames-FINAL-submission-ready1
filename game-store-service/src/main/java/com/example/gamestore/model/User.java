package com.example.gamestore.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity(name = "StoreUser")
@Table(name = "store_users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40, unique = true)
    private String username;

    @Column(nullable = false, length = 160, unique = true)
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String password;

    @Column(name = "display_name", length = 48)
    private String displayName;

    @Column(length = 240)
    private String bio;

    @Column(name = "avatar_key", length = 512)
    private String avatarKey;

    @Column(name = "public_profile", nullable = false)
    private boolean publicProfile = true;

    @Column(name = "email_notifications", nullable = false)
    private boolean emailNotifications = true;

    @Column(name = "game_updates", nullable = false)
    private boolean gameUpdates = true;

    @ManyToMany
    @JoinTable(
        name = "user_games",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "game_id")
    )
    private Set<Game> games = new HashSet<>();

    public User() {}

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.displayName = username;
    }

    @PrePersist
    @PreUpdate
    private void normalizeDisplayName() {
        if (displayName == null || displayName.isBlank()) displayName = username;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDisplayName() { return displayName == null || displayName.isBlank() ? username : displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getAvatarKey() { return avatarKey; }
    public void setAvatarKey(String avatarKey) { this.avatarKey = avatarKey; }
    public boolean isPublicProfile() { return publicProfile; }
    public void setPublicProfile(boolean publicProfile) { this.publicProfile = publicProfile; }
    public boolean isEmailNotifications() { return emailNotifications; }
    public void setEmailNotifications(boolean emailNotifications) { this.emailNotifications = emailNotifications; }
    public boolean isGameUpdates() { return gameUpdates; }
    public void setGameUpdates(boolean gameUpdates) { this.gameUpdates = gameUpdates; }
    public Set<Game> getGames() { return games; }
    public void setGames(Set<Game> games) { this.games = games == null ? new HashSet<>() : games; }
    public Set<Game> getLibrary() { return games; }
    public void setLibrary(Set<Game> games) { setGames(games); }
}
