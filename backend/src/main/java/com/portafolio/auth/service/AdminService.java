package com.portafolio.auth.service;

import com.portafolio.auth.dto.Responses.AuditEventResponse;
import com.portafolio.auth.dto.Responses.PageResponse;
import com.portafolio.auth.dto.Responses.StatsResponse;
import com.portafolio.auth.dto.Responses.UserResponse;
import com.portafolio.auth.entity.*;
import com.portafolio.auth.exception.ApiException;
import com.portafolio.auth.mapper.AuthMapper;
import com.portafolio.auth.repository.AuditEventRepository;
import com.portafolio.auth.repository.RoleRepository;
import com.portafolio.auth.repository.SessionRepository;
import com.portafolio.auth.repository.UserRepository;
import com.portafolio.auth.security.AuthenticatedUser;
import com.portafolio.auth.security.ClientInfo;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Gestión de usuarios y roles. Protegido también aquí, no solo en la capa web. */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminService {

    public static final int MAX_PAGE_SIZE = 100;

    private final UserRepository users;
    private final RoleRepository roles;
    private final SessionRepository sessionRepository;
    private final AuditEventRepository auditEvents;
    private final SessionService sessions;
    private final AuditService audit;
    private final Clock clock;

    public AdminService(UserRepository users, RoleRepository roles, SessionRepository sessionRepository,
                        AuditEventRepository auditEvents, SessionService sessions, AuditService audit, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.sessionRepository = sessionRepository;
        this.auditEvents = auditEvents;
        this.sessions = sessions;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(String query, Boolean enabled, int page, int size) {
        String q = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase(Locale.ROOT) + "%";
        var pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.ASC, "id"));
        return PageResponse.of(users.search(q, enabled, pageable), AuthMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return AuthMapper.toResponse(load(id));
    }

    /**
     * Reemplaza los roles. USER se conserva siempre. Si cambian, se cierran las sesiones del usuario para
     * que ningún token emitido con los roles anteriores siga sirviendo.
     */
    @Transactional
    public UserResponse updateRoles(AuthenticatedUser actor, Long id, Set<RoleName> requested, ClientInfo client) {
        User user = load(id);
        Set<RoleName> target = EnumSet.copyOf(requested);
        target.add(RoleName.USER);
        if (user.getId().equals(actor.id()) && !target.contains(RoleName.ADMIN)) {
            throw ApiException.businessRule("CANNOT_REMOVE_OWN_ADMIN", "No puedes quitarte tu propio rol de administrador");
        }
        List<String> before = user.roleNames();
        Set<Role> newRoles = new HashSet<>(roles.findByNameIn(target));
        user.getRoles().clear();
        user.getRoles().addAll(newRoles);
        List<String> after = user.roleNames();
        if (!before.equals(after)) {
            users.flush();
            sessions.revokeAll(user.getId(), null, RevokeReason.ROLES_CHANGED);
            audit.record(AuditEventType.ROLES_CHANGE, AuditOutcome.SUCCESS, user.getId(), actor.id(), user.getEmail(),
                    client, String.join(", ", before) + " → " + String.join(", ", after));
        }
        return AuthMapper.toResponse(user);
    }

    @Transactional
    public UserResponse setEnabled(AuthenticatedUser actor, Long id, boolean enabled, ClientInfo client) {
        User user = load(id);
        if (user.getId().equals(actor.id()) && !enabled) {
            throw ApiException.businessRule("CANNOT_DISABLE_SELF", "No puedes deshabilitar tu propia cuenta");
        }
        if (user.isEnabled() != enabled) {
            user.setEnabled(enabled);
            users.flush();
            if (!enabled) sessions.revokeAll(user.getId(), null, RevokeReason.ACCOUNT_DISABLED);
            audit.record(AuditEventType.ACCOUNT_STATUS_CHANGE, AuditOutcome.SUCCESS, user.getId(), actor.id(),
                    user.getEmail(), client, enabled ? "Cuenta habilitada" : "Cuenta deshabilitada");
        }
        return AuthMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> auditEvents(AuditEventType type, AuditOutcome outcome, Long userId,
                                                        int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponse.of(auditEvents.search(type, outcome, userId, pageable), AuthMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        var now = clock.instant();
        long total = users.count();
        long enabled = users.countByEnabled(true);
        return new StatsResponse(total, enabled, total - enabled, users.countByRole(RoleName.ADMIN),
                sessionRepository.countActive(now),
                auditEvents.countByTypeAndOutcomeAndCreatedAtAfter(AuditEventType.LOGIN, AuditOutcome.FAILURE,
                        now.minus(Duration.ofHours(24))));
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Usuario no encontrado"));
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
