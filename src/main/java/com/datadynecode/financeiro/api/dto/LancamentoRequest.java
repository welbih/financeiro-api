package com.datadynecode.financeiro.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.datadynecode.financeiro.api.model.TipoLancamento;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@JsonIgnoreProperties({"usuario", "usuarioId"})
public record LancamentoRequest(
    @NotNull TipoLancamento tipoLancamento,
    @NotBlank @Size(max = 500) String descricao,
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal valor,
    Boolean pago,
    @NotNull LocalDate dataVencimento,
    LocalDate dataPrevisaoPagamento,
    LocalDate dataPagamento,
    @Positive Long categoriaId,
    @Min(0) @Max(12) Integer quantidadeRecorrencias
) {}
