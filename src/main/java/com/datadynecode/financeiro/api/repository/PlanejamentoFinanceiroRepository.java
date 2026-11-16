package com.datadynecode.financeiro.api.repository;

import java.util.Optional;

import com.datadynecode.financeiro.api.model.PlanejamentoFinanceiro;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanejamentoFinanceiroRepository extends JpaRepository<PlanejamentoFinanceiro, Long> {
    Optional<PlanejamentoFinanceiro> findByUsuarioId(Long usuarioId);
}
