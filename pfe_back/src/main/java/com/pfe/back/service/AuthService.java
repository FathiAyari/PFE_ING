package com.pfe.back.service;

import com.pfe.back.dto.AuthResponse;
import com.pfe.back.dto.LoginRequest;
import com.pfe.back.dto.RegisterRequest;
import com.pfe.back.entity.Role;
import com.pfe.back.entity.User;
import com.pfe.back.repository.UserRepository;
import com.pfe.back.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditLogService audit;

    public AuthResponse login(LoginRequest req) {
        User u = users.findByUsername(req.username())
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .filter(User::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        String token = jwt.generateToken(u.getUsername(), u.getRole().name(), u.getId());
        audit.log("LOGIN", u.getUsername(), "user#" + u.getId(), "Login successful", "OK");
        return new AuthResponse(token, jwt.expirationMs(),
                new AuthResponse.UserInfo(u.getId(), u.getUsername(), u.getEmail(), u.getRole().name()));
    }

    public AuthResponse register(RegisterRequest req) {
        if (users.existsByUsername(req.username())) {
            throw new IllegalArgumentException("Username already taken");
        }
        User u = users.save(User.builder()
                .username(req.username())
                .passwordHash(encoder.encode(req.password()))
                .email(req.email())
                .role(Role.CLOUD_ADMIN)
                .enabled(true)
                .createdAt(Instant.now())
                .build());

        String token = jwt.generateToken(u.getUsername(), u.getRole().name(), u.getId());
        audit.log("REGISTER", u.getUsername(), "user#" + u.getId(), "Account created", "OK");
        return new AuthResponse(token, jwt.expirationMs(),
                new AuthResponse.UserInfo(u.getId(), u.getUsername(), u.getEmail(), u.getRole().name()));
    }

    public AuthResponse.UserInfo me(String username) {
        User u = users.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return new AuthResponse.UserInfo(u.getId(), u.getUsername(), u.getEmail(), u.getRole().name());
    }
}
