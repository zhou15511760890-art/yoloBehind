package com.example.demo.config;

import com.example.demo.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Value("${app.default-user.username:admin}")
    private String defaultUsername;

    @Value("${app.default-user.password:password}")
    private String defaultPassword;

    @Bean
    public CommandLineRunner seedUser(AuthService authService) {
        return args -> authService.createUserIfMissing(defaultUsername, defaultPassword);
    }
}
