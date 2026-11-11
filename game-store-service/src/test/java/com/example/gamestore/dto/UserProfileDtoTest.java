package com.example.gamestore.dto;

import com.example.gamestore.model.Game;
import com.example.gamestore.model.User;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserProfileDtoTest {
    @Test
    void profileContainsOnlyPublicUserFieldsAndGameSummaries() {
        Game game = new Game("Night Run", "A dark arcade adventure", 9.99);
        User user = new User("fox", "fox@example.test", "never-serialize-this");
        user.setLibrary(Set.of(game));

        UserProfileDto dto = new UserProfileDto(user.getId(), user.getUsername(), user.getEmail(), user.getLibrary());

        assertEquals("fox", dto.getUsername());
        assertEquals("fox@example.test", dto.getEmail());
        assertEquals(1, dto.getLibrary().size());
        assertEquals("Night Run", dto.getLibrary().iterator().next().getTitle());
        assertFalse(java.util.Arrays.stream(dto.getClass().getDeclaredFields())
                .anyMatch(field -> field.getName().equalsIgnoreCase("password")));
    }

    @Test
    void nullLibraryIsRepresentedAsAnEmptyCollection() {
        UserProfileDto dto = new UserProfileDto(1L, "fox", "fox@example.test", null);
        assertNotNull(dto.getLibrary());
        assertTrue(dto.getLibrary().isEmpty());
    }

    @Test
    void profileIncludesEditableSettingsButNeverAvatarObjectKeyOrPassword() {
        User user = new User("fox", "fox@example.test", "hashed-secret");
        user.setDisplayName("Fox Player");
        user.setBio("Indie game fan");
        user.setAvatarKey("avatars/5/private-object.png");
        user.setPublicProfile(false);
        user.setEmailNotifications(false);
        user.setGameUpdates(true);

        UserProfileDto dto = new UserProfileDto(user);
        assertEquals("Fox Player", dto.getDisplayName());
        assertEquals("Indie game fan", dto.getBio());
        assertTrue(dto.isHasAvatar());
        assertFalse(dto.isPublicProfile());
        assertFalse(dto.isEmailNotifications());
        assertTrue(dto.isGameUpdates());
        assertFalse(java.util.Arrays.stream(dto.getClass().getDeclaredFields())
                .anyMatch(field -> field.getName().toLowerCase().contains("password")
                        || field.getName().toLowerCase().contains("avatarkey")));
    }
}
