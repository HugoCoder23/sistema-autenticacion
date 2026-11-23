package com.portafolio.auth.repository;

import com.portafolio.auth.entity.AuditEvent;
import com.portafolio.auth.entity.AuditEventType;
import com.portafolio.auth.entity.AuditOutcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    @Query("""
            select e from AuditEvent e
            where (:type is null or e.type = :type)
              and (:outcome is null or e.outcome = :outcome)
              and (:userId is null or e.userId = :userId)
            """)
    Page<AuditEvent> search(@Param("type") AuditEventType type, @Param("outcome") AuditOutcome outcome,
                            @Param("userId") Long userId, Pageable pageable);

    long countByTypeAndOutcomeAndCreatedAtAfter(AuditEventType type, AuditOutcome outcome, Instant after);
}
