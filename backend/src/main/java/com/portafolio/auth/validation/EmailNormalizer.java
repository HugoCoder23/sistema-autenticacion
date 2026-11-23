package com.portafolio.auth.validation;

import java.util.Locale;

/**
 * Normaliza emails antes de guardarlos o buscarlos: sin espacios y en minúsculas. Junto con la restricción
 * única en base de datos evita cuentas duplicadas como {@code Ana@Mail.com} y {@code ana@mail.com}.
 */
public final class EmailNormalizer {

    private EmailNormalizer() {}

    public static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
