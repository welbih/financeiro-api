package com.datadynecode.financeiro.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlterarSenhaRequest(
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank @Size(max = 72) String senhaAtual,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank @Size(min = 12, max = 72) String novaSenha
) {
    @Override
    public String toString() {
        return "AlterarSenhaRequest[credenciais omitidas]";
    }
}
