package com.portafolio.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.portafolio.auth.entity.Role;
import com.portafolio.auth.entity.RoleName;
import com.portafolio.auth.entity.User;
import com.portafolio.auth.repository.RoleRepository;
import com.portafolio.auth.repository.UserRepository;
import com.portafolio.auth.service.AttemptLimiter;
import com.portafolio.auth.service.mail.DevOutboxMailService;
import com.portafolio.auth.service.mail.MailMessage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pruebas de aceptación de la guía (sección 7), de punta a punta contra la API con H2 en memoria. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuthApiIntegrationTest {

    static final String PASSWORD = "Password123";
    static final String COOKIE = "refresh_token";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired AttemptLimiter limiter;
    @Autowired DevOutboxMailService outbox;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired JwtDecoder jwtDecoder;

    Long anaId;
    Long adminId;

    @BeforeEach
    void setUp() {
        for (String table : List.of("audit_events", "password_reset_tokens", "refresh_tokens", "sessions",
                "user_roles", "users")) {
            jdbc.update("delete from " + table);
        }
        limiter.clear();
        outbox.clear();
        anaId = createUser("Ana Test", "ana@test.local", true, RoleName.USER);
        adminId = createUser("Admin Test", "admin@test.local", true, RoleName.USER, RoleName.ADMIN);
    }

    // ------------------------------------------------------------------ registro

    @Nested
    @DisplayName("Registro")
    class Register {

        @Test
        void registroCorrectoDevuelve201SinHashYConRolUser() throws Exception {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("name", "María López", "email", "  Maria@Ejemplo.DEV ", "password", "Secreta123"))))
                    .andExpect(status().isCreated())
                    .andExpect(header().string(HttpHeaders.LOCATION, "/api/users/me"))
                    .andExpect(jsonPath("$.email").value("maria@ejemplo.dev"))
                    .andExpect(jsonPath("$.roles", contains("USER")))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.password").doesNotExist());

            String stored = jdbc.queryForObject("select password_hash from users where email = 'maria@ejemplo.dev'", String.class);
            assertThat(stored).startsWith("$2a$12$").isNotEqualTo("Secreta123");
        }

        @Test
        void rechazaEmailDuplicadoAunqueCambieMayusculas() throws Exception {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("name", "Otra Ana", "email", "ANA@test.local", "password", "Secreta123"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EMAIL_IN_USE"));
        }

        @Test
        void validaEmailYPoliticaDeContrasena() throws Exception {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("name", "X", "email", "no-es-email", "password", "corta"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("name", "email", "password")));

            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("name", "Sin Mayus", "email", "x@test.local", "password", "solominusculas1"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        }
    }

    // ------------------------------------------------------------------ login

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        void loginValidoEmiteAccessTokenYCookieSegura() throws Exception {
            MvcResult result = login("ana@test.local", PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900))
                    .andExpect(jsonPath("$.user.email").value("ana@test.local"))
                    .andExpect(jsonPath("$.refreshToken").doesNotExist())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                    .andReturn();

            String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).contains("HttpOnly", "SameSite=Strict", "Path=/api/auth", "Max-Age=");
            Cookie cookie = result.getResponse().getCookie(COOKIE);
            assertThat(cookie).isNotNull();
            assertThat(result.getResponse().getContentAsString()).doesNotContain(cookie.getValue());

            // Payload mínimo: sin email, nombre ni datos sensibles.
            Map<String, Object> payload = decodePayload(field(result, "accessToken"));
            assertThat(payload.keySet()).containsExactlyInAnyOrder("iss", "aud", "sub", "iat", "nbf", "exp", "jti", "roles", "sid");
            assertThat(payload.get("sub")).isEqualTo(String.valueOf(anaId));
        }

        @Test
        void loginInvalidoNoRevelaSiFalloElEmailOLaContrasena() throws Exception {
            String wrongPassword = login("ana@test.local", "Incorrecta123").andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();
            String unknownEmail = login("nadie@test.local", PASSWORD).andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            JsonNode a = json.readTree(wrongPassword);
            JsonNode b = json.readTree(unknownEmail);
            assertThat(a.get("code").asText()).isEqualTo("INVALID_CREDENTIALS").isEqualTo(b.get("code").asText());
            assertThat(a.get("message").asText()).isEqualTo(b.get("message").asText());
        }

        @Test
        void cuentaDeshabilitadaRecibeElMismoErrorGenerico() throws Exception {
            createUser("Carla", "carla@test.local", false, RoleName.USER);
            login("carla@test.local", PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        void bloqueaTrasVariosIntentosFallidosExistaONoLaCuenta() throws Exception {
            for (int i = 0; i < 5; i++) login("ana@test.local", "Incorrecta123").andExpect(status().isUnauthorized());
            login("ana@test.local", PASSWORD)
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                    .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));

            for (int i = 0; i < 5; i++) login("fantasma@test.local", "Incorrecta123").andExpect(status().isUnauthorized());
            login("fantasma@test.local", PASSWORD).andExpect(status().isTooManyRequests());
        }

        @Test
        void registraAuditoriaDeIntentos() throws Exception {
            login("ana@test.local", "Incorrecta123");
            login("ana@test.local", PASSWORD);
            List<String> outcomes = jdbc.queryForList(
                    "select outcome from audit_events where event_type = 'LOGIN' order by id", String.class);
            assertThat(outcomes).containsExactly("FAILURE", "SUCCESS");
        }
    }

    // ------------------------------------------------------------------ acceso con token

    @Nested
    @DisplayName("Endpoints protegidos")
    class Protected {

        @Test
        void rechazaPeticionSinTokenYAceptaTokenValido() throws Exception {
            mvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

            Tokens ana = loginOk("ana@test.local");
            mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, ana.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(anaId))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        @Test
        void usuarioUserNoPuedeAccederAEndpointAdmin() throws Exception {
            Tokens ana = loginOk("ana@test.local");
            for (String path : List.of("/api/admin/users", "/api/admin/audit-events", "/api/admin/stats")) {
                mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, ana.bearer()))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            }
            mvc.perform(patch("/api/admin/users/" + anaId + "/status").header(HttpHeaders.AUTHORIZATION, ana.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                    .andExpect(status().isForbidden());

            Tokens admin = loginOk("admin@test.local");
            mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(content().string(not(containsString("passwordHash"))))
                    .andExpect(content().string(not(containsString("$2a$"))));
        }

        @Test
        void tokenExpiradoAlteradoOMalFirmadoEsRechazado() throws Exception {
            Tokens ana = loginOk("ana@test.local");
            Jwt valid = jwtDecoder.decode(ana.access());
            Instant now = Instant.now();

            String expired = sign(jwtEncoder, claims(valid).issuedAt(now.minus(Duration.ofMinutes(30)))
                    .notBefore(now.minus(Duration.ofMinutes(30))).expiresAt(now.minus(Duration.ofMinutes(5))).build());
            String[] parts = ana.access().split("\\.");
            String tamperedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                            .replace("\"USER\"", "\"ADMIN\"").getBytes(StandardCharsets.UTF_8));
            String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];
            SecretKey otherKey = new SecretKeySpec("otra-clave-que-no-es-la-del-servidor-1234567".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            String wrongKey = sign(new NimbusJwtEncoder(new ImmutableSecret<>(otherKey)), claims(valid).build());
            String wrongIssuer = sign(jwtEncoder, claims(valid).issuer("otro-emisor").build());
            String wrongAudience = sign(jwtEncoder, claims(valid).audience(List.of("otra-app")).build());
            String unsigned = parts[0].replace(parts[0], Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8))) + "." + parts[1] + ".";

            for (String token : List.of(expired, tampered, wrongKey, wrongIssuer, wrongAudience, unsigned, "basura")) {
                mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
            }
        }

        @Test
        void adminDeshabilitaUsuarioYSuTokenDejaDeServirAlInstante() throws Exception {
            Tokens ana = loginOk("ana@test.local");
            Tokens admin = loginOk("admin@test.local");
            mvc.perform(patch("/api/admin/users/" + anaId + "/status").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.enabled").value(false));

            mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, ana.bearer()))
                    .andExpect(status().isUnauthorized());
            refresh(ana.refresh()).andExpect(status().isUnauthorized());
        }

        @Test
        void adminNoPuedeDeshabilitarseNiQuitarseSuRol() throws Exception {
            Tokens admin = loginOk("admin@test.local");
            mvc.perform(patch("/api/admin/users/" + adminId + "/status").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("CANNOT_DISABLE_SELF"));
            mvc.perform(put("/api/admin/users/" + adminId + "/roles").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"USER\"]}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_OWN_ADMIN"));
        }

        @Test
        void promoverAAdminCierraSesionesYElNuevoLoginLlevaElRol() throws Exception {
            Tokens ana = loginOk("ana@test.local");
            Tokens admin = loginOk("admin@test.local");
            mvc.perform(put("/api/admin/users/" + anaId + "/roles").header(HttpHeaders.AUTHORIZATION, admin.bearer())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"ADMIN\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles", contains("USER", "ADMIN")));

            mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, ana.bearer()))
                    .andExpect(status().isUnauthorized());
            Tokens again = loginOk("ana@test.local");
            mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, again.bearer()))
                    .andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------ refresh y logout

    @Nested
    @DisplayName("Refresh token y logout")
    class RefreshAndLogout {

        @Test
        void refreshTokenSeRotaYElAnteriorDejaDeServir() throws Exception {
            Tokens first = loginOk("ana@test.local");
            MvcResult rotated = refresh(first.refresh())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andReturn();
            String second = rotated.getResponse().getCookie(COOKIE).getValue();
            assertThat(second).isNotEqualTo(first.refresh());

            refresh(first.refresh())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
            refresh(second).andExpect(status().isOk());
        }

        @Test
        void reutilizarUnRefreshTokenRotadoRevocaLaSesionCompleta() throws Exception {
            Tokens first = loginOk("ana@test.local");
            MvcResult rotated = refresh(first.refresh()).andExpect(status().isOk()).andReturn();
            String second = rotated.getResponse().getCookie(COOKIE).getValue();
            String access = field(rotated, "accessToken");

            // Simula que el token viejo se reutiliza pasado el margen de gracia (posible robo).
            jdbc.update("update refresh_tokens set used_at = ? where used_at is not null",
                    java.sql.Timestamp.from(Instant.now().minus(Duration.ofMinutes(5))));
            refresh(first.refresh()).andExpect(status().isUnauthorized());

            refresh(second).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
                    .andExpect(status().isUnauthorized());
            assertThat(jdbc.queryForObject("select revoke_reason from sessions", String.class)).isEqualTo("TOKEN_REUSE");
        }

        @Test
        void refreshSinCookieOConTokenDesconocidoDevuelve401() throws Exception {
            mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
            refresh("token-inventado").andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        }

        @Test
        void logoutRevocaLaSesion() throws Exception {
            Tokens ana = loginOk("ana@test.local");
            mvc.perform(post("/api/auth/logout").cookie(new Cookie(COOKIE, ana.refresh())))
                    .andExpect(status().isNoContent())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

            refresh(ana.refresh()).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, ana.bearer()))
                    .andExpect(status().isUnauthorized());
            // Idempotente
            mvc.perform(post("/api/auth/logout").cookie(new Cookie(COOKIE, ana.refresh())))
                    .andExpect(status().isNoContent());
        }

        @Test
        void listaYCierraSesionesDeOtrosDispositivos() throws Exception {
            Tokens laptop = loginOk("ana@test.local");
            Tokens phone = loginOk("ana@test.local");
            String phoneSid = jwtDecoder.decode(phone.access()).getClaimAsString("sid");

            mvc.perform(get("/api/users/me/sessions").header(HttpHeaders.AUTHORIZATION, laptop.bearer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[?(@.current == true)]", hasSize(1)));

            mvc.perform(delete("/api/users/me/sessions/" + phoneSid).header(HttpHeaders.AUTHORIZATION, laptop.bearer()))
                    .andExpect(status().isNoContent());
            mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, phone.bearer()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, laptop.bearer()))
                    .andExpect(status().isOk());
        }

        @Test
        void cambiarContrasenaCierraLasDemasSesiones() throws Exception {
            Tokens current = loginOk("ana@test.local");
            Tokens other = loginOk("ana@test.local");

            mvc.perform(post("/api/users/me/password").header(HttpHeaders.AUTHORIZATION, current.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("currentPassword", "Mala1234", "newPassword", "NuevaClave123"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));

            mvc.perform(post("/api/users/me/password").header(HttpHeaders.AUTHORIZATION, current.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("currentPassword", PASSWORD, "newPassword", "NuevaClave123"))))
                    .andExpect(status().isOk());

            mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, current.bearer())).andExpect(status().isOk());
            mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, other.bearer())).andExpect(status().isUnauthorized());
            login("ana@test.local", "NuevaClave123").andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------ recuperación

    @Nested
    @DisplayName("Recuperación de contraseña")
    class PasswordReset {

        @Test
        void respondeIgualExistaONoLaCuenta() throws Exception {
            String existing = forgot("ana@test.local").andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString();
            String missing = forgot("nadie@test.local").andExpect(status().isAccepted())
                    .andReturn().getResponse().getContentAsString();
            assertThat(existing).isEqualTo(missing);
            assertThat(outbox.messages()).hasSize(1).first().extracting(MailMessage::to).isEqualTo("ana@test.local");
        }

        @Test
        void tokenValidoCambiaLaContrasenaCierraSesionesYNoSePuedeReutilizar() throws Exception {
            Tokens before = loginOk("ana@test.local");
            forgot("ana@test.local");
            String token = lastResetToken();

            reset(token, "Recuperada123").andExpect(status().isOk());
            reset(token, "OtraVez12345")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));

            mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, before.bearer()))
                    .andExpect(status().isUnauthorized());
            login("ana@test.local", PASSWORD).andExpect(status().isUnauthorized());
            login("ana@test.local", "Recuperada123").andExpect(status().isOk());
        }

        @Test
        void tokenExpiradoEsRechazado() throws Exception {
            forgot("ana@test.local");
            String token = lastResetToken();
            jdbc.update("update password_reset_tokens set expires_at = ?",
                    java.sql.Timestamp.from(Instant.now().minus(Duration.ofMinutes(1))));
            reset(token, "Recuperada123")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        }

        @Test
        void soloElUltimoEnlaceEsValido() throws Exception {
            forgot("ana@test.local");
            String first = lastResetToken();
            forgot("ana@test.local");
            String second = lastResetToken();
            reset(first, "Recuperada123").andExpect(status().isBadRequest());
            reset(second, "Recuperada123").andExpect(status().isOk());
        }

        @Test
        void tokenInventadoEsRechazado() throws Exception {
            reset("no-existe", "Recuperada123").andExpect(status().isBadRequest());
        }
    }

    // ------------------------------------------------------------------ fugas de secretos

    @Test
    @DisplayName("Logs y respuestas nunca contienen passwordHash ni tokens")
    void logsYRespuestasNoContienenSecretos(CapturedOutput output) throws Exception {
        StringBuilder responses = new StringBuilder();
        MvcResult reg = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("name", "Leak Test", "email", "leak@test.local", "password", "Secreta123")))).andReturn();
        responses.append(reg.getResponse().getContentAsString());

        MvcResult log = login("leak@test.local", "Secreta123").andReturn();
        responses.append(log.getResponse().getContentAsString());
        String refreshToken = log.getResponse().getCookie(COOKIE).getValue();
        String access = field(log, "accessToken");

        MvcResult refreshed = refresh(refreshToken).andReturn();
        responses.append(refreshed.getResponse().getContentAsString());
        String refreshToken2 = refreshed.getResponse().getCookie(COOKIE).getValue();

        responses.append(mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + field(refreshed, "accessToken")))
                .andReturn().getResponse().getContentAsString());
        responses.append(forgot("leak@test.local").andReturn().getResponse().getContentAsString());
        String resetToken = lastResetToken();
        responses.append(reset(resetToken, "Recuperada123").andReturn().getResponse().getContentAsString());
        login("leak@test.local", "Mala12345");

        Tokens admin = loginOk("admin@test.local");
        responses.append(mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andReturn().getResponse().getContentAsString());
        responses.append(mvc.perform(get("/api/admin/audit-events").param("size", "100").header(HttpHeaders.AUTHORIZATION, admin.bearer()))
                .andReturn().getResponse().getContentAsString());

        String hash = jdbc.queryForObject("select password_hash from users where email = 'leak@test.local'", String.class);
        assertThat(responses.toString())
                .doesNotContain(hash, "passwordHash", "$2a$", refreshToken, refreshToken2, resetToken, "Secreta123", "Recuperada123");
        assertThat(output.getAll())
                .doesNotContain(hash, "$2a$", refreshToken, refreshToken2, resetToken, access, "Secreta123", "Recuperada123", "Mala12345");
        assertThat(jdbc.queryForList("select coalesce(detail, '') from audit_events", String.class))
                .noneMatch(d -> d.contains(refreshToken) || d.contains(resetToken) || d.contains("$2a$"));
    }

    // ------------------------------------------------------------------ helpers

    Long createUser(String name, String email, boolean enabled, RoleName... roleNames) {
        User user = new User(name, email, encoder.encode(PASSWORD));
        user.setEnabled(enabled);
        for (RoleName r : roleNames) {
            Role role = roles.findByName(r).orElseThrow();
            user.getRoles().add(role);
        }
        return users.save(user).getId();
    }

    ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", password))));
    }

    Tokens loginOk(String email) throws Exception {
        MvcResult r = login(email, PASSWORD).andExpect(status().isOk()).andReturn();
        return new Tokens(field(r, "accessToken"), r.getResponse().getCookie(COOKIE).getValue());
    }

    ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, refreshToken)));
    }

    ResultActions forgot(String email) throws Exception {
        return mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email))));
    }

    ResultActions reset(String token, String newPassword) throws Exception {
        return mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("token", token, "newPassword", newPassword))));
    }

    String lastResetToken() {
        String link = outbox.messages().stream().filter(m -> m.actionUrl() != null).findFirst().orElseThrow().actionUrl();
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    String field(MvcResult result, String name) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get(name).asText();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> decodePayload(String jwt) throws Exception {
        return json.readValue(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), Map.class);
    }

    static JwtClaimsSet.Builder claims(Jwt valid) {
        return JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(valid.getClaimAsString("iss"))
                .audience(valid.getAudience())
                .subject(valid.getSubject())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofMinutes(10)))
                .claim("roles", valid.getClaimAsStringList("roles"))
                .claim("sid", valid.getClaimAsString("sid"));
    }

    static String sign(JwtEncoder encoder, JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    record Tokens(String access, String refresh) {
        String bearer() { return "Bearer " + access; }
    }
}
