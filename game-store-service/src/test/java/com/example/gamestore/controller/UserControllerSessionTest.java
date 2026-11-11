package com.example.gamestore.controller;

import com.example.gamestore.dto.ChangePasswordDto;
import com.example.gamestore.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserControllerSessionTest {
    @Test
    void legacyIdBasedProfileCannotReadAnotherUsersData() {
        UserController controller = new UserController(mock(UserService.class));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserController.SESSION_USER_ID, 10L);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.getProfile(11L, session));
        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void profileRequiresAnAuthenticatedSession() {
        UserController controller = new UserController(mock(UserService.class));
        MockHttpSession session = new MockHttpSession();
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.getCurrentProfile(session));
        assertEquals(401, error.getStatusCode().value());
    }

    @Test
    void passwordChangeInvalidatesCurrentSession() {
        UserService service = mock(UserService.class);
        UserController controller = new UserController(service);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserController.SESSION_USER_ID, 10L);
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setCurrentPassword("old password");
        dto.setNewPassword("a-new-strong-password");
        controller.changePassword(dto, session);
        verify(service).changePassword(10L, dto);
        assertTrue(session.isInvalid());
    }
}
