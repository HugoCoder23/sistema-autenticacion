package com.portafolio.auth.security;

import com.portafolio.auth.config.AppProperties;
import com.portafolio.auth.entity.Session;
import com.portafolio.auth.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Emite access tokens JWT firmados (HS256). El payload solo se codifica en Base64, no se cifra, por eso
 * contiene lo mínimo: id de usuario, roles e id de sesión. Nunca email, nombre ni datos sensibles.
 */
@Service
public class JwtService {

    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_SESSION_ID = "sid";

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration ttl;
    private final String issuer;
    private final String audience;

    public JwtService(JwtEncoder encoder, Clock clock, AppProperties props) {
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = Duration.ofMinutes(props.jwt().accessTokenMinutes());
        this.issuer = props.jwt().issuer();
        this.audience = props.jwt().audience();
    }

    public IssuedToken issue(User user, Session session) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(expiresAt)
                .claim(CLAIM_ROLES, user.roleNames())
                .claim(CLAIM_SESSION_ID, session.getId())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt, ttl.toSeconds());
    }

    public record IssuedToken(String value, Instant expiresAt, long expiresInSeconds) {}
}
