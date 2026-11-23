package com.portafolio.auth.repository;

import com.portafolio.auth.entity.Role;
import com.portafolio.auth.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);

    List<Role> findByNameIn(Collection<RoleName> names);
}
