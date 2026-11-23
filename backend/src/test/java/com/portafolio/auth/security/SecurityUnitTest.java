package com.portafolio.auth.security;

import com.portafolio.auth.validation.EmailNormalizer;
import com.portafolio.auth.validation.StrongPasswordValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUnitTest {

    final StrongPasswordValidator validator = new StrongPasswordValidator();
    final SecureTokens tokens = new SecureTokens();

    @ParameterizedTest
    @ValueSource(strings = {"Secreta123", "Abcdefg1", "ÑandúVeloz2026"})
    void aceptaContrasenasQueCumplenLaPolitica(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Corta1", "sinmayusculas1", "SINMINUSCULAS1", "SinNumeros", " Espacio123",
            "Aa1-demasiado-larga-para-bcrypt-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void rechazaContrasenasDebiles(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void normalizaEmails() {
        assertThat(EmailNormalizer.normalize("  Ana.Garcia@Ejemplo.COM ")).isEqualTo("ana.garcia@ejemplo.com");
    }

    @Test
    void tokensOpacosSonAleatoriosYSeGuardanComoSha256() {
        String a = tokens.generate();
        String b = tokens.generate();
        assertThat(a).isNotEqualTo(b).hasSize(43); // 32 bytes en Base64URL sin relleno
        assertThat(tokens.hash(a)).hasSize(64).isEqualTo(tokens.hash(a)).isNotEqualTo(tokens.hash(b)).doesNotContain(a);
    }

    @Test
    void describeElDispositivoDesdeElUserAgent() {
        assertThat(ClientInfo.describe("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/130.0 Safari/537.36"))
                .isEqualTo("Chrome · Windows");
        assertThat(ClientInfo.describe("PostmanRuntime/7.39.0")).isEqualTo("Postman");
        assertThat(ClientInfo.describe(null)).isEqualTo("Dispositivo desconocido");
    }
}
