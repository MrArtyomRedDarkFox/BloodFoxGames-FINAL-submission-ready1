package com.example.gamestore;

import com.example.gamestore.model.Game;
import com.example.gamestore.repository.GameRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GameStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameStoreApplication.class, args);
    }

    @Bean
    public CommandLineRunner demo(GameRepository repository) {
        return (args) -> {
            if (repository.count() == 0) {
                repository.save(new Game("Fox Races", "2D Pixel Art Runner", 19.99));
                repository.save(new Game("LostNight", "Dark Fantasy Adventure", 29.99));
            }
        };
    }
}
