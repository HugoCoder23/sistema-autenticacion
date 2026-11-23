package com.portafolio.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    static final int MIN_LENGTH = 8;
    /** Límite de BCrypt: los bytes a partir del 73 se ignorarían silenciosamente. */
    static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return false;
        if (value.length() < MIN_LENGTH) return false;
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return false;
        if (!value.equals(value.strip())) return false;
        boolean lower = false, upper = false, digit = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLowerCase(c)) lower = true;
            else if (Character.isUpperCase(c)) upper = true;
            else if (Character.isDigit(c)) digit = true;
        }
        return lower && upper && digit;
    }
}
