package com.portafolio.auth.dto;

import com.portafolio.auth.entity.RoleName;
import com.portafolio.auth.validation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.util.Set;

/** Cuerpos de petición. Las contraseñas solo se reciben; ninguna respuesta las devuelve. */
public final class Requests {

    private Requests() {}

    public record RegisterRequest(
            @Schema(example = "María López")
            @NotBlank(message = "El nombre es obligatorio")
            @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
            String name,

            @Schema(example = "maria@ejemplo.dev")
            @NotBlank(message = "El email es obligatorio")
            @Email(message = "El email no tiene un formato válido")
            @Size(max = 160, message = "El email no puede superar 160 caracteres")
            String email,

            @Schema(example = "Secreta123")
            @StrongPassword
            String password) {

        public RegisterRequest {
            email = strip(email);
        }

        @Override
        public String toString() { return "RegisterRequest[email=" + email + "]"; }
    }

    public record LoginRequest(
            @Schema(example = "ana@auth.demo")
            @NotBlank(message = "El email es obligatorio")
            @Size(max = 160)
            String email,

            @Schema(example = "Demo1234!")
            @NotBlank(message = "La contraseña es obligatoria")
            @Size(max = 200)
            String password) {

        public LoginRequest {
            email = strip(email);
        }

        @Override
        public String toString() { return "LoginRequest[email=" + email + "]"; }
    }

    public record ForgotPasswordRequest(
            @Schema(example = "ana@auth.demo")
            @NotBlank(message = "El email es obligatorio")
            @Email(message = "El email no tiene un formato válido")
            @Size(max = 160)
            String email) {

        public ForgotPasswordRequest {
            email = strip(email);
        }
    }

    /** Los espacios accidentales alrededor del email no deben hacer fallar la validación de formato. */
    static String strip(String value) {
        return value == null ? null : value.strip();
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "El token es obligatorio")
            @Size(max = 200, message = "El token no es válido")
            String token,

            @Schema(example = "NuevaClave123")
            @StrongPassword
            String newPassword) {

        @Override
        public String toString() { return "ResetPasswordRequest[***]"; }
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "La contraseña actual es obligatoria")
            @Size(max = 200)
            String currentPassword,

            @StrongPassword
            String newPassword) {

        @Override
        public String toString() { return "ChangePasswordRequest[***]"; }
    }

    public record UpdateProfileRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
            String name) {}

    public record UpdateRolesRequest(
            @Schema(example = "[\"USER\", \"ADMIN\"]")
            @NotEmpty(message = "Indica al menos un rol")
            Set<@NotNull RoleName> roles) {}

    public record UpdateStatusRequest(
            @NotNull(message = "Indica si la cuenta queda habilitada")
            Boolean enabled) {}
}
