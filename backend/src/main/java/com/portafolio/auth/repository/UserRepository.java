package com.portafolio.auth.repository;

import com.portafolio.auth.entity.RoleName;
import com.portafolio.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** El email debe llegar ya normalizado (ver {@code EmailNormalizer}). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Búsqueda paginada por nombre o email; {@code q} en minúsculas con comodines, o null para todos. */
    @Query("""
            select u from User u
            where (:q is null or lower(u.name) like :q or u.email like :q)
              and (:enabled is null or u.enabled = :enabled)
            """)
    Page<User> search(@Param("q") String q, @Param("enabled") Boolean enabled, Pageable pageable);

    @Query("select count(distinct u) from User u join u.roles r where r.name = :role")
    long countByRole(@Param("role") RoleName role);

    long countByEnabled(boolean enabled);
}
