package com.datadynecode.financeiro.api.mapper;

import com.datadynecode.financeiro.api.dto.LancamentoResponse;
import com.datadynecode.financeiro.api.model.Lancamento;

public final class LancamentoMapper {
    private LancamentoMapper() {}

    public static LancamentoResponse toResponse(Lancamento lancamento) {
        return new LancamentoResponse(lancamento.getId(), lancamento.getTipoLancamento(),
            lancamento.getUsuario().getId(), lancamento.getDescricao(), lancamento.getValor(),
            lancamento.isPago(), lancamento.getDataVencimento(), lancamento.getDataPrevisaoPagamento(),
            lancamento.getDataPagamento(), lancamento.getCategoria() == null ? null : lancamento.getCategoria().getId());
    }
}
