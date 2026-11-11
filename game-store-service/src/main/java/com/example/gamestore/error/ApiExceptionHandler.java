package com.example.gamestore.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleExpected(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Запрос не выполнен" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("error", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception exception) {
        // Detailed exception information stays in server logs, never in the browser response.
        System.err.println("Unhandled API exception: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Внутренняя ошибка сервера. Попробуй позже."));
    }
}
