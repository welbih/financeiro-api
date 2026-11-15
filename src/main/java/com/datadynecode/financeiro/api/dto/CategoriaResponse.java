package com.datadynecode.financeiro.api.dto;

import com.datadynecode.financeiro.api.model.TipoLancamento;

public record CategoriaResponse(Long id, String nome, TipoLancamento tipoLancamento, Long usuarioId) {}
