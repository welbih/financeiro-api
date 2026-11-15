package com.datadynecode.financeiro.api.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record UsuarioRequest(
    @NotBlank @Size(max = 100) String nome,
    @NotBlank @Size(max = 100) String sobrenome,
    @NotBlank @Email @Size(max = 254) String email,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank @Size(min = 12, max = 72) String senha,
    @NotEmpty Set<@NotBlank @Size(max = 100) String> roles
) {}
