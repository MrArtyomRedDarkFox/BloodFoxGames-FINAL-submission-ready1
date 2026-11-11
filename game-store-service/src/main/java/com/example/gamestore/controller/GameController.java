package com.example.gamestore.controller;

import com.example.gamestore.model.Game;
import com.example.gamestore.repository.GameRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/games")
public class GameController {
    private final GameRepository gameRepository;

    public GameController(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    @GetMapping
    public List<Game> getCatalog() {
        return gameRepository.findAll();
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<Game> getGame(@PathVariable Long gameId) {
        return gameRepository.findById(gameId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
