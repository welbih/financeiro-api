package com.datadynecode.financeiro.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.datadynecode.financeiro.api.model.TipoLancamento;

public record LancamentoResponse(
    Long id,
    TipoLancamento tipoLancamento,
    Long usuarioId,
    String descricao,
    BigDecimal valor,
    boolean pago,
    LocalDate dataVencimento,
    LocalDate dataPrevisaoPagamento,
    LocalDate dataPagamento,
    Long categoriaId
) {}
