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
import com.datadynecode.financeiro.api.model.TipoLancamento;
import com.datadynecode.financeiro.api.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanejamentoFinanceiroServiceTests {
    final PlanejamentoFinanceiroRepository planejamentos = mock(PlanejamentoFinanceiroRepository.class);
    final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    final LancamentoRepository lancamentos = mock(LancamentoRepository.class);
    final PlanejamentoFinanceiroService service = new PlanejamentoFinanceiroService(planejamentos, usuarios, lancamentos);
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
}
