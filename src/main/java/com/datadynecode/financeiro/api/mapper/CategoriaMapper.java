package com.datadynecode.financeiro.api.mapper;

import com.datadynecode.financeiro.api.dto.CategoriaResponse;
import com.datadynecode.financeiro.api.model.Categoria;

public final class CategoriaMapper {
    private CategoriaMapper() {}

    public static CategoriaResponse toResponse(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNome(), categoria.getTipoLancamento(),
            categoria.getUsuario().getId());
    }
}
