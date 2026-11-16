package com.datadynecode.financeiro.api.mapper;

import com.datadynecode.financeiro.api.dto.PlanejamentoFinanceiroResponse;
import com.datadynecode.financeiro.api.model.PlanejamentoFinanceiro;

public final class PlanejamentoFinanceiroMapper {
    private PlanejamentoFinanceiroMapper() {}

    public static PlanejamentoFinanceiroResponse toResponse(PlanejamentoFinanceiro planejamento) {
        return new PlanejamentoFinanceiroResponse(planejamento.getId(), planejamento.getDiaFechamento(),
            planejamento.getDataInicioControle(), planejamento.getSaldoInicial(), planejamento.getUsuario().getId());
    }
}
