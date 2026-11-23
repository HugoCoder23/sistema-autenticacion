package com.portafolio.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Auth Service API",
                version = "1.0.0",
                description = """
                        Servicio de identidad y acceso: registro, inicio de sesión, **access token JWT** de corta \
                        duración, **refresh token rotatorio** en cookie HttpOnly, roles `USER`/`ADMIN` y \
                        recuperación de contraseña con token de un solo uso. Proyecto de portafolio: datos ficticios.

                        **Cómo probarla**
                        1. `POST /api/auth/login` con una cuenta demo (`admin@auth.demo` o `ana@auth.demo`, \
                        contraseña `Demo1234!`).
                        2. Copia `accessToken` y pulsa **Authorize**.
                        3. El refresh token llega en la cookie `refresh_token` (HttpOnly, `Path=/api/auth`): \
                        `POST /api/auth/refresh` lo rota y devuelve un access token nuevo.
                        4. En el perfil local, los correos de recuperación se ven en `GET /api/dev/mailbox`.
                        """),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
