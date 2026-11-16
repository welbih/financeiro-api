package com.datadynecode.financeiro.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record UsuarioRequest(
    @NotBlank @Size(max = 100) String nome,
    @NotBlank @Size(max = 100) String sobrenome,
    @NotBlank @Email @Size(max = 254) String email,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank @Size(min = 12, max = 72) String senha
) {
    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("Campo nao permitido no cadastro de usuario");
    }
}
