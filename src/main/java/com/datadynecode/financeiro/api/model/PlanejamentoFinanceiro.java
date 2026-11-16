package com.datadynecode.financeiro.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "planejamentos_financeiros")
@Getter
@Setter
@NoArgsConstructor
public class PlanejamentoFinanceiro {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "planejamentos_financeiros_sequence_generator")
    @SequenceGenerator(name = "planejamentos_financeiros_sequence_generator", sequenceName = "planejamentos_financeiros_seq", allocationSize = 1)
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @NotNull
    @Min(1)
    @Max(31)
    @Column(name = "dia_fechamento", nullable = false)
    private Integer diaFechamento;

    @NotNull
    @Column(name = "data_inicio_controle", nullable = false)
    private LocalDate dataInicioControle;

    @NotNull
    @Digits(integer = 17, fraction = 2)
    @Column(name = "saldo_inicial", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoInicial;
}
