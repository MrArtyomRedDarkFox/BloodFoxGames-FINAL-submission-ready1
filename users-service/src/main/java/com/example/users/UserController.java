package com.example.users;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserRepository repository;

    public UserController(UserRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<User> getAllUsers() {
        return repository.findAll();
    }

    @PostMapping
    public User createUser(@RequestBody User user) {
        return repository.save(user);
    }

    /** Internal read-model synchronization called by Game Store over the Compose network. */
    @PutMapping("/{accountId}")
    public User syncUser(@PathVariable Long accountId, @RequestBody User payload) {
        if (accountId == null || accountId <= 0 || payload == null
                || payload.getUsername() == null || payload.getUsername().isBlank()
                || payload.getEmail() == null || payload.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountId, username and email are required");
        }
        User user = repository.findByAccountId(accountId).orElseGet(User::new);
        user.setAccountId(accountId);
        user.setUsername(payload.getUsername().trim());
        user.setEmail(payload.getEmail().trim().toLowerCase());
        return repository.save(user);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<User> getDirectoryEntry(@PathVariable Long accountId) {
        User user = repository.findByAccountId(accountId).orElse(null);
        if (user == null) return ResponseEntity.<User>notFound().build();
        return ResponseEntity.ok(user);
    }
}
