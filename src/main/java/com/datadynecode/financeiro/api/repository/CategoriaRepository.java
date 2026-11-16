package com.datadynecode.financeiro.api.repository;

import java.util.Optional;

import com.datadynecode.financeiro.api.model.Categoria;
import com.datadynecode.financeiro.api.model.TipoLancamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    Optional<Categoria> findByIdAndUsuarioId(Long id, Long usuarioId);
    Page<Categoria> findByUsuarioId(Long usuarioId, Pageable pageable);
    Page<Categoria> findByUsuarioIdAndTipoLancamento(Long usuarioId, TipoLancamento tipoLancamento, Pageable pageable);
    boolean existsByUsuarioIdAndNomeIgnoreCase(Long usuarioId, String nome);
    boolean existsByUsuarioIdAndNomeIgnoreCaseAndIdNot(Long usuarioId, String nome, Long id);
}
