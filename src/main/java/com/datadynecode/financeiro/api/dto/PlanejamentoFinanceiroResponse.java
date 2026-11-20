package com.datadynecode.financeiro.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PlanejamentoFinanceiroResponse(Long id, Integer diaFechamento,
    LocalDate dataInicioControle, BigDecimal saldoInicial, Long usuarioId,
    String cicloAtual, String primeiroCicloDisponivel) {}
