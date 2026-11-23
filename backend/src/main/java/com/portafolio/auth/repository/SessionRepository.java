package com.portafolio.auth.repository;

import com.portafolio.auth.entity.RevokeReason;
import com.portafolio.auth.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, String> {

    /** Usado en cada petición autenticada: la sesión del token sigue viva y el usuario habilitado. */
    @Query("""
            select count(s) > 0 from Session s
            where s.id = :id and s.user.id = :userId and s.revokedAt is null
              and s.expiresAt > :now and s.user.enabled = true
            """)
    boolean isActive(@Param("id") String id, @Param("userId") Long userId, @Param("now") Instant now);

    @Query("""
            select s from Session s
            where s.user.id = :userId and s.revokedAt is null and s.expiresAt > :now
            order by s.lastUsedAt desc
            """)
    List<Session> findActiveByUser(@Param("userId") Long userId, @Param("now") Instant now);

    Optional<Session> findByIdAndUserId(String id, Long userId);

    @Query("select count(s) from Session s where s.revokedAt is null and s.expiresAt > :now")
    long countActive(@Param("now") Instant now);

    /** Revoca todas las sesiones activas del usuario, salvo {@code keepId} (puede ser null). */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Session s set s.revokedAt = :now, s.revokeReason = :reason
            where s.user.id = :userId and s.revokedAt is null and (:keepId is null or s.id <> :keepId)
            """)
    int revokeAllForUser(@Param("userId") Long userId, @Param("keepId") String keepId,
                         @Param("now") Instant now, @Param("reason") RevokeReason reason);

    @Modifying
    @Query("delete from Session s where s.expiresAt < :before or s.revokedAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
