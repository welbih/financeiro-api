package com.datadynecode.financeiro.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties({"usuario", "usuarioId", "id"})
public record PlanejamentoFinanceiroRequest(
    @NotNull @Min(1) @Max(31) Integer diaFechamento,
    @NotNull LocalDate dataInicioControle,
    @NotNull @Digits(integer = 17, fraction = 2) BigDecimal saldoInicial
) {}
