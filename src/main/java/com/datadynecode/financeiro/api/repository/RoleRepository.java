package com.datadynecode.financeiro.api.repository;

import java.util.Optional;

import com.datadynecode.financeiro.api.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByAuthority(String authority);
}
