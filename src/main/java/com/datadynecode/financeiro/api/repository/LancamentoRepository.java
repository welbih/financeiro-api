package com.datadynecode.financeiro.api.repository;

import com.datadynecode.financeiro.api.model.Lancamento;
import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {
    boolean existsByCategoriaId(Long categoriaId);

    Page<Lancamento> findByUsuarioId(Long usuarioId, Pageable pageable);

    Optional<Lancamento> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Query("""
        select lancamento from Lancamento lancamento
        where lancamento.usuario.id = :usuarioId
          and coalesce(lancamento.dataPrevisaoPagamento, lancamento.dataVencimento) between :inicio and :fim
        order by coalesce(lancamento.dataPrevisaoPagamento, lancamento.dataVencimento), lancamento.id
        """)
    List<Lancamento> findParaResumoMensal(
        @Param("usuarioId") Long usuarioId, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
