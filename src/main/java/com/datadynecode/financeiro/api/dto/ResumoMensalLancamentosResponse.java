package com.datadynecode.financeiro.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record ResumoMensalLancamentosResponse(
    String mes,
    BigDecimal totalDespesas,
    BigDecimal totalReceitas,
    BigDecimal diferenca,
    List<LancamentoResponse> lancamentos
) {}
