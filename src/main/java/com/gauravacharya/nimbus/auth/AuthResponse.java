package com.gauravacharya.nimbus.auth;

public record AuthResponse(String token, String tokenType, long expiresInMinutes) {
    public static AuthResponse of(String token, long minutes) {
        return new AuthResponse(token, "Bearer", minutes);
    }
}
