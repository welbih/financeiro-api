package com.datadynecode.financeiro.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "categorias", uniqueConstraints = @UniqueConstraint(
    name = "uk_categorias_id_usuario_tipo", columnNames = {"id", "usuario_id", "tipo_lancamento"}))
@Getter
@Setter
@NoArgsConstructor
public class Categoria {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "categorias_sequence_generator")
    @SequenceGenerator(name = "categorias_sequence_generator", sequenceName = "categorias_seq", allocationSize = 1)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String nome;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_lancamento", nullable = false, length = 7)
    private TipoLancamento tipoLancamento;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
}
