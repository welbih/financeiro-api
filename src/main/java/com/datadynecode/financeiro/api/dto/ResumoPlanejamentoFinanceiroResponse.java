package com.datadynecode.financeiro.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ResumoPlanejamentoFinanceiroResponse(
    String mesFechamento,
    LocalDate dataInicial,
    LocalDate dataFinal,
    BigDecimal saldoInicialProjetado,
    BigDecimal totalReceitas,
    BigDecimal totalDespesas,
    BigDecimal receitasRecebidas,
    BigDecimal receitasPendentes,
    BigDecimal despesasPagas,
    BigDecimal despesasPendentes,
    BigDecimal resultadoCiclo,
    BigDecimal saldoFinalProjetado,
    List<LancamentoResponse> lancamentos,
    List<TotalPorCategoriaResponse> totaisPorCategoria
) {}
