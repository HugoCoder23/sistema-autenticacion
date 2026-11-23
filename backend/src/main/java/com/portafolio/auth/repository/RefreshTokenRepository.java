package com.portafolio.auth.repository;

import com.portafolio.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Bloquea la fila: dos peticiones simultáneas con el mismo refresh token no pueden rotarlo dos veces.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t join fetch t.session s join fetch s.user where t.tokenHash = :hash")
    Optional<RefreshToken> findByHashForUpdate(@Param("hash") String hash);

    @Query("select t from RefreshToken t join fetch t.session where t.tokenHash = :hash")
    Optional<RefreshToken> findByHash(@Param("hash") String hash);

    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.session.id = :sessionId and t.revokedAt is null")
    int revokeAllForSession(@Param("sessionId") String sessionId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.revokedAt is null and t.session.id in (
                select s.id from Session s where s.user.id = :userId and (:keepId is null or s.id <> :keepId))
            """)
    int revokeAllForUser(@Param("userId") Long userId, @Param("keepId") String keepId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
