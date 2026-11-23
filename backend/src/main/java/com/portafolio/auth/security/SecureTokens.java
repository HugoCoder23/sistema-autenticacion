package com.portafolio.auth.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos (refresh y recuperación): 256 bits aleatorios de {@link SecureRandom}. En la base solo se
 * guarda su SHA-256: tienen entropía completa, así que un hash rápido es suficiente (a diferencia de las
 * contraseñas) y permite buscarlos por índice. Si la base se filtra, los hashes no sirven para autenticarse.
 */
@Component
public class SecureTokens {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
