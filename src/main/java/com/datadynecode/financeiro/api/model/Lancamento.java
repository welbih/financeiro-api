package com.datadynecode.financeiro.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lancamentos")
@Getter
@Setter
@NoArgsConstructor
public class Lancamento {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lancamentos_sequence_generator")
    @SequenceGenerator(name = "lancamentos_sequence_generator", sequenceName = "lancamentos_seq", allocationSize = 1)
    private Long id;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "tipo_lancamento", nullable = false, length = 7)
    private TipoLancamento tipoLancamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @NotNull
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String descricao;

    @NotNull
    @Positive
    @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private boolean pago;

    @NotNull
    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Column(name = "data_previsao_pagamento")
    private LocalDate dataPrevisaoPagamento;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    public void pagarEm(LocalDate dataPagamento) {
        Objects.requireNonNull(dataPagamento, "Data de pagamento obrigatoria");
        if (pago) {
            throw new IllegalStateException("Lancamento ja esta pago");
        }
        this.dataPagamento = dataPagamento;
        this.pago = true;
    }

    public void reabrirPagamento() {
        pago = false;
        dataPagamento = null;
    }

    public void reagendarPagamentoPara(LocalDate dataPrevisaoPagamento) {
        this.dataPrevisaoPagamento = Objects.requireNonNull(dataPrevisaoPagamento, "Previsao obrigatoria");
    }

    public boolean estaEmAtrasoEm(LocalDate dataReferencia) {
        Objects.requireNonNull(dataReferencia, "Data de referencia obrigatoria");
        return !pago && dataVencimento.isBefore(dataReferencia);
    }
}
