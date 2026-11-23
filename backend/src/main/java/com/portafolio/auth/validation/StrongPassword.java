package com.portafolio.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Política de contraseñas: 8 a 72 caracteres (BCrypt ignora lo que pasa de 72 bytes), al menos una
 * minúscula, una mayúscula y un dígito, sin espacios al inicio o al final.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "La contraseña debe tener entre 8 y 72 caracteres, con al menos una mayúscula, "
            + "una minúscula y un número";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
