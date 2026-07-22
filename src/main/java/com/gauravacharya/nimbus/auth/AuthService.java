package com.gauravacharya.nimbus.auth;

import com.gauravacharya.nimbus.security.JwtService;
import com.gauravacharya.nimbus.user.Role;
import com.gauravacharya.nimbus.user.User;
import com.gauravacharya.nimbus.user.UserRepository;
import com.gauravacharya.nimbus.user.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long expirationMinutes;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       @Value("${nimbus.jwt.expiration-minutes}") long expirationMinutes) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.expirationMinutes = expirationMinutes;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new AuthException("Email already registered");
        }
        User user = new User();
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        users.save(user);
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email())
                .orElseThrow(() -> new AuthException("Invalid email or password"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new AuthException("Invalid email or password");
        }
        return issueToken(user);
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(
                user.getEmail(), user.getId().toString(), user.getRole().name());
        return AuthResponse.of(token, expirationMinutes);
    }
}
