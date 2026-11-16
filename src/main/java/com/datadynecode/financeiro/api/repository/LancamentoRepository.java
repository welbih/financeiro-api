package com.datadynecode.financeiro.api.repository;

import com.datadynecode.financeiro.api.model.Lancamento;
import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import java.math.BigDecimal;
import com.datadynecode.financeiro.api.model.TipoLancamento;
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
    List<Lancamento> findPorPeriodo(
        @Param("usuarioId") Long usuarioId, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    @Query("""
        select coalesce(sum(case when lancamento.tipoLancamento = :tipoReceita
            then lancamento.valor else -lancamento.valor end), 0)
        from Lancamento lancamento
        where lancamento.usuario.id = :usuarioId
          and coalesce(lancamento.dataPrevisaoPagamento, lancamento.dataVencimento) >= :inicio
          and coalesce(lancamento.dataPrevisaoPagamento, lancamento.dataVencimento) < :fimExclusivo
        """)
    BigDecimal calcularResultadoEntre(@Param("usuarioId") Long usuarioId,
        @Param("inicio") LocalDate inicio, @Param("fimExclusivo") LocalDate fimExclusivo,
        @Param("tipoReceita") TipoLancamento tipoReceita);
}
