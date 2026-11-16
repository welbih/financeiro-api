package com.datadynecode.financeiro.api.dto;

import com.datadynecode.financeiro.api.model.TipoLancamento;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties({"usuario", "usuarioId"})
public record CategoriaRequest(
    @NotBlank @Size(max = 100) String nome,
    @NotNull TipoLancamento tipoLancamento
) {}
