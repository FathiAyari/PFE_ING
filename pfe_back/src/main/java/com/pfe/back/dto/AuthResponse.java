package com.pfe.back.dto;

public record AuthResponse(
        String token,
        long expiresAt,
        UserInfo user
) {
    public record UserInfo(Long id, String username, String email, String role) {}
}
