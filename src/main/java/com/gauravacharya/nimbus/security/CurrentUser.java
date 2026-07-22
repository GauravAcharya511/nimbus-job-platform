package com.gauravacharya.nimbus.security;

import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;

/** Resolves the authenticated user's id from the JWT-populated security context. */
public final class CurrentUser {
    private CurrentUser() {}

    public static UUID id() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString((String) auth.getDetails());
    }
}