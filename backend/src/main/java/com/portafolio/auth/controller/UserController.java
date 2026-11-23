package com.portafolio.auth.controller;

import com.portafolio.auth.dto.Requests.ChangePasswordRequest;
import com.portafolio.auth.dto.Requests.UpdateProfileRequest;
import com.portafolio.auth.dto.Responses.MessageResponse;
import com.portafolio.auth.dto.Responses.SessionResponse;
import com.portafolio.auth.dto.Responses.UserResponse;
import com.portafolio.auth.security.AuthenticatedUser;
import com.portafolio.auth.security.ClientInfo;
import com.portafolio.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "2. Mi cuenta", description = "Perfil, contraseña y sesiones del usuario autenticado")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "Obtener mi perfil")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.me(AuthenticatedUser.from(jwt));
    }

    @PatchMapping
    @Operation(summary = "Actualizar mi nombre")
    public UserResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest req,
                               HttpServletRequest http) {
        return users.updateProfile(AuthenticatedUser.from(jwt), req, ClientInfo.from(http));
    }

    @PostMapping("/password")
    @Operation(summary = "Cambiar contraseña", description = "Exige la contraseña actual y cierra las demás sesiones.")
    public MessageResponse changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest req,
                                          HttpServletRequest http) {
        int closed = users.changePassword(AuthenticatedUser.from(jwt), req, ClientInfo.from(http));
        return new MessageResponse(closed == 0 ? "Contraseña actualizada"
                : "Contraseña actualizada. Se cerraron " + closed + " sesión(es) en otros dispositivos");
    }

    @GetMapping("/sessions")
    @Operation(summary = "Listar mis sesiones activas")
    public List<SessionResponse> sessions(@AuthenticationPrincipal Jwt jwt) {
        return users.sessions(AuthenticatedUser.from(jwt));
    }

    @DeleteMapping("/sessions/{id}")
    @Operation(summary = "Cerrar una de mis sesiones", description = "Revoca la sesión de otro dispositivo (o la actual).")
    public ResponseEntity<Void> revokeSession(@AuthenticationPrincipal Jwt jwt, @PathVariable String id,
                                              HttpServletRequest http) {
        users.revokeSession(AuthenticatedUser.from(jwt), id, ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }
}
