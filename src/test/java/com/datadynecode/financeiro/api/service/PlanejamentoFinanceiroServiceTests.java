package com.datadynecode.financeiro.api.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import com.datadynecode.financeiro.api.dto.PlanejamentoFinanceiroRequest;
import com.datadynecode.financeiro.api.exception.CadastroInvalidoException;
import com.datadynecode.financeiro.api.exception.PlanejamentoNaoEncontradoException;
import com.datadynecode.financeiro.api.model.PlanejamentoFinanceiro;
import com.datadynecode.financeiro.api.model.Usuario;
import com.datadynecode.financeiro.api.model.Lancamento;
import com.datadynecode.financeiro.api.model.Categoria;
import com.datadynecode.financeiro.api.model.TipoLancamento;
import com.datadynecode.financeiro.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanejamentoFinanceiroServiceTests {
    final PlanejamentoFinanceiroRepository planejamentos = mock(PlanejamentoFinanceiroRepository.class);
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final LancamentoRepository lancamentos = mock(LancamentoRepository.class);
    final PlanejamentoFinanceiroService service = new PlanejamentoFinanceiroService(planejamentos, usuarios, lancamentos,
        java.time.Clock.fixed(java.time.Instant.parse("2026-11-20T12:00:00Z"), java.time.ZoneOffset.UTC));
    final PlanejamentoFinanceiro plano = new PlanejamentoFinanceiro();

    @BeforeEach
    void preparar() {
        Usuario usuario = new Usuario();
        usuario.setId(10L);
        when(usuarios.findByEmail("usuario@email.com")).thenReturn(Optional.of(usuario));
        plano.setUsuario(usuario);
        plano.setDiaFechamento(31);
        plano.setDataInicioControle(LocalDate.of(2028, 2, 1));
        plano.setSaldoInicial(new BigDecimal("-100.00"));
        when(planejamentos.findByUsuarioId(10L)).thenReturn(Optional.of(plano));
    }

    @Test
    void ajustaDia31ParaFevereiroBissextoSemDuplicarSaldo() {
        when(lancamentos.findPorPeriodo(10L, LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)))
            .thenReturn(List.of());
        var resumo = service.resumir("usuario@email.com", YearMonth.of(2028, 2));
        assertEquals(LocalDate.of(2028, 2, 29), resumo.dataFinal());
        assertEquals(new BigDecimal("-100.00"), resumo.saldoFinalProjetado());
        assertTrue(resumo.totaisPorCategoria().isEmpty());
        verify(lancamentos, never()).calcularResultadoEntre(any(), any(), any(), any());
    }

    @Test
    void cicloSeguinteComecaDepoisDoUltimoDiaDeFevereiro() {
        when(lancamentos.calcularResultadoEntre(10L, LocalDate.of(2028, 2, 1), LocalDate.of(2028, 3, 1), TipoLancamento.RECEITA))
            .thenReturn(new BigDecimal("50.00"));
        when(lancamentos.findPorPeriodo(10L, LocalDate.of(2028, 3, 1), LocalDate.of(2028, 3, 31)))
            .thenReturn(List.of());
        var resumo = service.resumir("usuario@email.com", YearMonth.of(2028, 3));
        assertEquals(LocalDate.of(2028, 3, 1), resumo.dataInicial());
        assertEquals(new BigDecimal("-50.00"), resumo.saldoInicialProjetado());
    }

    @Test
    void aceitaInicioAposFechamentoAjustadoEmAnoNaoBissexto() {
        when(planejamentos.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var resposta = service.configurar(new PlanejamentoFinanceiroRequest(31, LocalDate.of(2027, 3, 1), BigDecimal.ZERO), "usuario@email.com");
        assertEquals(LocalDate.of(2027, 3, 1), resposta.dataInicioControle());
        assertEquals(new BigDecimal("0.00"), resposta.saldoInicial());
    }

    @Test
    void rejeitaCicloAnteriorAoInicio() {
        assertThrows(CadastroInvalidoException.class, () -> service.resumir("usuario@email.com", YearMonth.of(2028, 1)));
        verifyNoInteractions(lancamentos);
    }

    @Test
    void exigeConfiguracaoDoProprioUsuario() {
        when(planejamentos.findByUsuarioId(10L)).thenReturn(Optional.empty());
        assertThrows(PlanejamentoNaoEncontradoException.class, () -> service.buscar("usuario@email.com"));
    }

    @ParameterizedTest
    @CsvSource({
        "9, 2026-11-08T12:00:00Z, 2026-11",
        "9, 2026-11-09T12:00:00Z, 2026-11",
        "9, 2026-11-10T02:59:59Z, 2026-11",
        "9, 2026-11-10T03:00:00Z, 2026-12",
        "9, 2026-12-10T12:00:00Z, 2027-01",
        "9, 2027-01-09T12:00:00Z, 2027-01",
        "9, 2027-01-10T12:00:00Z, 2027-02",
        "31, 2027-02-28T12:00:00Z, 2027-02",
        "31, 2027-03-01T02:59:59Z, 2027-02",
        "31, 2027-03-01T03:00:00Z, 2027-03",
        "31, 2028-02-28T12:00:00Z, 2028-02",
        "31, 2028-02-29T12:00:00Z, 2028-02",
        "31, 2028-03-01T03:00:00Z, 2028-03",
        "31, 2026-04-30T12:00:00Z, 2026-04",
        "31, 2026-05-01T12:00:00Z, 2026-05",
        "31, 2026-12-31T12:00:00Z, 2026-12",
        "31, 2027-01-01T03:00:00Z, 2027-01",
        "1, 2026-11-01T12:00:00Z, 2026-11",
        "1, 2026-11-02T03:00:00Z, 2026-12"
    })
    void cicloAtualRespeitaFechamentoEFuso(int dia, String instante, String esperado) {
        plano.setDiaFechamento(dia);
        var relogio = java.time.Clock.fixed(java.time.Instant.parse(instante), java.time.ZoneOffset.UTC);
        var serviceComRelogio = new PlanejamentoFinanceiroService(planejamentos, usuarios, lancamentos, relogio);
        assertEquals(esperado, serviceComRelogio.buscar("usuario@email.com").cicloAtual());
        verifyNoInteractions(lancamentos);
    }

    @ParameterizedTest
    @CsvSource({
        "9, 2026-11-10, 2026-12",
        "9, 2026-12-10, 2027-01",
        "31, 2027-02-01, 2027-02",
        "31, 2027-03-01, 2027-03",
        "31, 2028-03-01, 2028-03",
        "30, 2028-03-01, 2028-03",
        "1, 2026-12-02, 2027-01"
    })
    void primeiroCicloCorrespondeAoInicioValidadoDoControle(int dia, String inicio, String esperado) {
        when(planejamentos.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var configuracao = service.configurar(new PlanejamentoFinanceiroRequest(dia,
            LocalDate.parse(inicio), BigDecimal.ZERO), "usuario@email.com");
        assertEquals(esperado, configuracao.primeiroCicloDisponivel());
        assertEquals(esperado, service.buscar("usuario@email.com").primeiroCicloDisponivel());
        when(lancamentos.findPorPeriodo(eq(10L), any(), any())).thenReturn(List.of());
        var resumo = service.resumir("usuario@email.com", YearMonth.parse(esperado));
        assertEquals(LocalDate.parse(inicio), resumo.dataInicial());
        assertThrows(CadastroInvalidoException.class,
            () -> service.resumir("usuario@email.com", YearMonth.parse(esperado).minusMonths(1)));
    }

    @Test
    void inicioFuturoNaoAlteraCicloAtual() {
        plano.setDiaFechamento(9);
        plano.setDataInicioControle(LocalDate.of(2028, 12, 10));
        var resposta = service.buscar("usuario@email.com");
        assertEquals("2026-12", resposta.cicloAtual());
        assertEquals("2029-01", resposta.primeiroCicloDisponivel());
    }

    @Test
    void agrupaPorIdComReceitasEDespesasSeparadasSemIncluirSaldoAnterior() {
        Categoria primeira = new Categoria();
        primeira.setId(20L);
        primeira.setNome("Mesmo nome");
        Categoria segunda = new Categoria();
        segunda.setId(30L);
        segunda.setNome("Mesmo nome");
        when(lancamentos.calcularResultadoEntre(10L, LocalDate.of(2028, 2, 1), LocalDate.of(2028, 3, 1), TipoLancamento.RECEITA))
            .thenReturn(new BigDecimal("5000.00"));
        when(lancamentos.findPorPeriodo(10L, LocalDate.of(2028, 3, 1), LocalDate.of(2028, 3, 31)))
            .thenReturn(List.of(item(segunda, TipoLancamento.DESPESA, "0.20"),
                item(null, TipoLancamento.RECEITA, "0.10"), item(primeira, TipoLancamento.RECEITA, "10.10"),
                item(primeira, TipoLancamento.DESPESA, "2.30"), item(null, TipoLancamento.DESPESA, "0.30"),
                item(primeira, TipoLancamento.RECEITA, "0.20")));
        var resumo = service.resumir("usuario@email.com", YearMonth.of(2028, 3));
        var totais = resumo.totaisPorCategoria();
        assertEquals(5, totais.size());
        assertEquals(20L, totais.get(0).categoriaId());
        assertEquals(20L, totais.get(1).categoriaId());
        assertEquals(30L, totais.get(2).categoriaId());
        assertNull(totais.get(3).categoriaId());
        assertNull(totais.get(4).categoriaId());
        assertEquals("Sem categoria", totais.get(3).categoriaNome());
        assertEquals("Sem categoria", totais.get(4).categoriaNome());
        assertEquals(TipoLancamento.DESPESA, totais.get(0).tipoLancamento());
        assertEquals(TipoLancamento.RECEITA, totais.get(1).tipoLancamento());
        assertEquals(TipoLancamento.DESPESA, totais.get(2).tipoLancamento());
        assertEquals(TipoLancamento.DESPESA, totais.get(3).tipoLancamento());
        assertEquals(TipoLancamento.RECEITA, totais.get(4).tipoLancamento());
        assertEquals(new BigDecimal("2.30"), totais.get(0).total());
        assertEquals(new BigDecimal("10.30"), totais.get(1).total());
        assertEquals(new BigDecimal("0.20"), totais.get(2).total());
        assertEquals(new BigDecimal("0.30"), totais.get(3).total());
        assertEquals(new BigDecimal("0.10"), totais.get(4).total());
        assertEquals(new BigDecimal("4900.00"), resumo.saldoInicialProjetado());
        assertEquals(resumo.totalReceitas(), totais.stream().filter(t -> t.tipoLancamento() == TipoLancamento.RECEITA)
            .map(t -> t.total()).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(resumo.totalDespesas(), totais.stream().filter(t -> t.tipoLancamento() == TipoLancamento.DESPESA)
            .map(t -> t.total()).reduce(BigDecimal.ZERO, BigDecimal::add));
        verify(lancamentos).findPorPeriodo(10L, LocalDate.of(2028, 3, 1), LocalDate.of(2028, 3, 31));
    }

    private Lancamento item(Categoria categoria, TipoLancamento tipo, String valor) {
        Lancamento lancamento = new Lancamento();
        lancamento.setUsuario(plano.getUsuario());
        lancamento.setCategoria(categoria);
        lancamento.setTipoLancamento(tipo);
        lancamento.setValor(new BigDecimal(valor));
        return lancamento;
    }
}
