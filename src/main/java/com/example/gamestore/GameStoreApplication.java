package com.example.gamestore;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@SpringBootApplication
@RestController
@RequestMapping("/api/games")
public class GameStoreApplication {

    private final GameRepository gameRepository;

    public GameStoreApplication(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    public static void main(String[] args) {
        SpringApplication.run(GameStoreApplication.class, args);
    }

    @Bean
    CommandLineRunner initDatabase() {
        return args -> {
            if (gameRepository.count() == 0) {
                gameRepository.save(new Game("The Witcher 3: Wild Hunt", "RPG", "$29.99"));
                gameRepository.save(new Game("Cyberpunk 2077", "Action RPG", "$59.99"));
                gameRepository.save(new Game("Hades", "Rogue-like", "$24.99"));
            }
        };
    }

    @GetMapping
    public List<Game> getGames() {
        return gameRepository.findAll();
    }

    @PostMapping
    public Game createGame(@RequestBody Game game) {
        return gameRepository.save(game);
    }

    @GetMapping("/health")
    public Map<String, String> healthCheck() {
        return Map.of("status", "UP", "service", "Game Store API2");
    }
}
