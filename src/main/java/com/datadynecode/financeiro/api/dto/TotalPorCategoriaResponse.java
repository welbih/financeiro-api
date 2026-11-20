package com.datadynecode.financeiro.api.dto;

import java.math.BigDecimal;
import com.datadynecode.financeiro.api.model.TipoLancamento;

public record TotalPorCategoriaResponse(Long categoriaId, String categoriaNome,
    TipoLancamento tipoLancamento, BigDecimal total) {}
