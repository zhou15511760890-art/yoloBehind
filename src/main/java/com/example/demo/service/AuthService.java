package com.example.demo.service;

import com.example.demo.entity.UserAccount;
import com.example.demo.repository.UserAccountRepository;
import com.example.demo.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService,
                       UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public String login(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
        if (!authentication.isAuthenticated()) {
            throw new IllegalStateException("Authentication failed");
        }
        return jwtService.generateToken(username);
    }

    public void createUserIfMissing(String username, String password) {
        Optional<UserAccount> existing = userAccountRepository.findByUsername(username);
        if (existing.isEmpty()) {
            UserAccount account = new UserAccount();
            account.setUsername(username);
            account.setPassword(passwordEncoder.encode(password));
            userAccountRepository.save(account);
        }
    }
}
