package com.portafolio.auth.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Identidad extraída de un access token ya validado. */
public record AuthenticatedUser(Long id, String sessionId) {

    public static AuthenticatedUser from(Jwt jwt) {
        return new AuthenticatedUser(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(JwtService.CLAIM_SESSION_ID));
    }
}
