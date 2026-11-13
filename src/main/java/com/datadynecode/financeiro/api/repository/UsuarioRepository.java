package com.datadynecode.financeiro.api.repository;

import java.util.Optional;

import com.datadynecode.financeiro.api.model.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @EntityGraph(attributePaths = "roles.roles")
    Optional<Usuario> findByEmail(String email);
}
