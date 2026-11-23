package com.portafolio.auth.controller;

import com.portafolio.auth.dto.Requests.UpdateRolesRequest;
import com.portafolio.auth.dto.Requests.UpdateStatusRequest;
import com.portafolio.auth.dto.Responses.AuditEventResponse;
import com.portafolio.auth.dto.Responses.PageResponse;
import com.portafolio.auth.dto.Responses.StatsResponse;
import com.portafolio.auth.dto.Responses.UserResponse;
import com.portafolio.auth.entity.AuditEventType;
import com.portafolio.auth.entity.AuditOutcome;
import com.portafolio.auth.security.AuthenticatedUser;
import com.portafolio.auth.security.ClientInfo;
import com.portafolio.auth.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "3. Administración", description = "Solo ADMIN: usuarios, roles, estado y auditoría")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/users")
    @Operation(summary = "Listar usuarios", description = "Paginado; filtra por nombre/email (q) y estado (enabled).")
    public PageResponse<UserResponse> users(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) Boolean enabled,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return admin.listUsers(q, enabled, page, size);
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Obtener un usuario")
    public UserResponse user(@PathVariable Long id) {
        return admin.getUser(id);
    }

    @PutMapping("/users/{id}/roles")
    @Operation(summary = "Reemplazar roles", description = "USER se conserva siempre. Cierra las sesiones del usuario.")
    public UserResponse updateRoles(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                    @Valid @RequestBody UpdateRolesRequest req, HttpServletRequest http) {
        return admin.updateRoles(AuthenticatedUser.from(jwt), id, req.roles(), ClientInfo.from(http));
    }

    @PatchMapping("/users/{id}/status")
    @Operation(summary = "Habilitar o deshabilitar", description = "Deshabilitar cierra todas sus sesiones al instante.")
    public UserResponse updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                     @Valid @RequestBody UpdateStatusRequest req, HttpServletRequest http) {
        return admin.setEnabled(AuthenticatedUser.from(jwt), id, req.enabled(), ClientInfo.from(http));
    }

    @GetMapping("/audit-events")
    @Operation(summary = "Consultar auditoría", description = "Más recientes primero. Nunca contiene contraseñas ni tokens.")
    public PageResponse<AuditEventResponse> audit(@RequestParam(required = false) AuditEventType type,
                                                  @RequestParam(required = false) AuditOutcome outcome,
                                                  @RequestParam(required = false) Long userId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return admin.auditEvents(type, outcome, userId, page, size);
    }

    @GetMapping("/stats")
    @Operation(summary = "Indicadores del panel")
    public StatsResponse stats() {
        return admin.stats();
    }
}
