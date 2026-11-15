package com.datadynecode.financeiro.api.repository;

import java.util.Collection;
import java.util.List;

import com.datadynecode.financeiro.api.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    List<Role> findByAuthorityIn(Collection<String> authorities);
}
