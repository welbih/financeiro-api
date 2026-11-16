package com.datadynecode.financeiro.api.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.datadynecode.financeiro.api.dto.LancamentoRequest;
import com.datadynecode.financeiro.api.exception.CadastroInvalidoException;
import com.datadynecode.financeiro.api.exception.UsuarioAutenticadoNaoEncontradoException;
import com.datadynecode.financeiro.api.exception.LancamentoNaoEncontradoException;
import com.datadynecode.financeiro.api.model.Lancamento;
import com.datadynecode.financeiro.api.model.TipoLancamento;
import com.datadynecode.financeiro.api.model.Usuario;
import com.datadynecode.financeiro.api.repository.LancamentoRepository;
import com.datadynecode.financeiro.api.repository.UsuarioRepository;
import com.datadynecode.financeiro.api.repository.CategoriaRepository;
import com.datadynecode.financeiro.api.model.Categoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LancamentoServiceTests {
    private final LancamentoRepository lancamentos = mock(LancamentoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final CategoriaRepository categorias = mock(CategoriaRepository.class);
    private final LancamentoService service = new LancamentoService(lancamentos, usuarios, categorias);
    private final Usuario usuario = new Usuario();
    private final Categoria categoria = new Categoria();
    private final LocalDate vencimento = LocalDate.of(2026, 12, 10);

    @BeforeEach
    void setup() {
        usuario.setId(10L);
        categoria.setId(30L);
        categoria.setNome("Energia");
        categoria.setTipoLancamento(TipoLancamento.DESPESA);
        categoria.setUsuario(usuario);
        when(categorias.findByIdAndUsuarioId(30L, 10L)).thenReturn(Optional.of(categoria));
        when(usuarios.findByEmail("dono@example.com")).thenReturn(Optional.of(usuario));
        when(lancamentos.saveAndFlush(any())).thenAnswer(invocation -> {
            Lancamento lancamento = invocation.getArgument(0);
            if (lancamento.getId() == null) {
                lancamento.setId(20L);
            }
            return lancamento;
        });
    }

    private LancamentoRequest request(Boolean pago, LocalDate pagamento) {
        return new LancamentoRequest(TipoLancamento.DESPESA, " Energia ", new BigDecimal("150.25"),
            pago, vencimento, null, pagamento, 30L);
    }

    @Test
    void cadastraPendenteParaUsuarioAutenticado() {
        var response = service.cadastrar(request(null, null), "dono@example.com");
        ArgumentCaptor<Lancamento> captor = ArgumentCaptor.forClass(Lancamento.class);
        verify(lancamentos).saveAndFlush(captor.capture());
        assertSame(usuario, captor.getValue().getUsuario());
        assertEquals(10L, response.usuarioId());
        assertEquals(20L, response.id());
        assertEquals("Energia", response.descricao());
        assertFalse(response.pago());
        assertNull(response.dataPagamento());
    }

    @Test
    void cadastraPagoComData() {
        var response = service.cadastrar(request(true, vencimento), "dono@example.com");
        assertTrue(response.pago());
        assertEquals(vencimento, response.dataPagamento());
    }

    @Test
    void rejeitaPagamentoSemData() {
        assertThrows(CadastroInvalidoException.class,
            () -> service.cadastrar(request(true, null), "dono@example.com"));
        verify(lancamentos, never()).saveAndFlush(any());
    }

    @Test
    void rejeitaDataParaLancamentoPendente() {
        assertThrows(CadastroInvalidoException.class,
            () -> service.cadastrar(request(false, vencimento), "dono@example.com"));
        verify(lancamentos, never()).saveAndFlush(any());
    }

    @Test
    void rejeitaUsuarioDoTokenQueNaoExisteNoBanco() {
        when(usuarios.findByEmail("ausente@example.com")).thenReturn(Optional.empty());
        assertThrows(UsuarioAutenticadoNaoEncontradoException.class,
            () -> service.cadastrar(request(false, null), "ausente@example.com"));
        verify(lancamentos, never()).saveAndFlush(any());
    }

    @Test
    void rejeitaCategoriaDeOutroUsuarioOuInexistente() {
        when(categorias.findByIdAndUsuarioId(30L, 10L)).thenReturn(Optional.empty());
        assertThrows(com.datadynecode.financeiro.api.exception.CategoriaNaoEncontradaException.class,
            () -> service.cadastrar(request(false, null), "dono@example.com"));
        verify(lancamentos, never()).saveAndFlush(any());
    }

    @Test
    void rejeitaCategoriaDeTipoDiferente() {
        categoria.setTipoLancamento(TipoLancamento.RECEITA);
        assertThrows(CadastroInvalidoException.class, () -> service.cadastrar(request(false, null), "dono@example.com"));
        verify(lancamentos, never()).saveAndFlush(any());
    }

    @Test
    void cadastraSemCategoria() {
        var request = new LancamentoRequest(TipoLancamento.DESPESA, "Energia", new BigDecimal("150.25"),
            false, vencimento, null, null, null);
        var response = service.cadastrar(request, "dono@example.com");
        assertNull(response.categoriaId());
        verifyNoInteractions(categorias);
    }

    @Test
    void atualizacaoPermiteRemoverCategoria() {
        Lancamento lancamento = existente();
        var request = new LancamentoRequest(TipoLancamento.DESPESA, "Energia", new BigDecimal("150.25"),
            false, vencimento, null, null, null);
        var response = service.atualizar(20L, request, "dono@example.com");
        assertNull(response.categoriaId());
        assertNull(lancamento.getCategoria());
        verifyNoInteractions(categorias);
    }

    private Lancamento existente() {
        Lancamento lancamento = new Lancamento();
        lancamento.setId(20L);
        lancamento.setUsuario(usuario);
        lancamento.setCategoria(categoria);
        lancamento.setTipoLancamento(TipoLancamento.DESPESA);
        lancamento.setDescricao("Energia");
        lancamento.setValor(new BigDecimal("150.25"));
        lancamento.setDataVencimento(vencimento);
        when(lancamentos.findByIdAndUsuarioId(20L, 10L)).thenReturn(Optional.of(lancamento));
        return lancamento;
    }

    @Test
    void listaSomentePaginaDoUsuarioAutenticado() {
        Lancamento lancamento = existente();
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20,
            org.springframework.data.domain.Sort.by("dataVencimento", "id"));
        when(lancamentos.findByUsuarioId(10L, pageable)).thenReturn(
            new org.springframework.data.domain.PageImpl<>(java.util.List.of(lancamento), pageable, 1));
        var response = service.listar("dono@example.com", 0, 20);
        assertEquals(1, response.totalElements());
        assertEquals(10L, response.content().getFirst().usuarioId());
        verify(lancamentos).findByUsuarioId(10L, pageable);
    }

    @Test
    void atualizaPagoParaPendenteSemTrocarProprietario() {
        Lancamento lancamento = existente();
        lancamento.pagarEm(vencimento);
        var response = service.atualizar(20L, request(false, null), "dono@example.com");
        assertFalse(response.pago());
        assertNull(response.dataPagamento());
        assertSame(usuario, lancamento.getUsuario());
        assertEquals(20L, response.id());
    }

    @Test
    void atualizaDataDeLancamentoJaPago() {
        Lancamento lancamento = existente();
        lancamento.pagarEm(vencimento);
        LocalDate novaData = vencimento.plusDays(1);
        var response = service.atualizar(20L, request(true, novaData), "dono@example.com");
        assertTrue(response.pago());
        assertEquals(novaData, response.dataPagamento());
    }

    @Test
    void naoAtualizaLancamentoForaDoEscopoDoUsuario() {
        when(lancamentos.findByIdAndUsuarioId(99L, 10L)).thenReturn(Optional.empty());
        assertThrows(LancamentoNaoEncontradoException.class,
            () -> service.atualizar(99L, request(false, null), "dono@example.com"));
        verify(lancamentos, never()).saveAndFlush(any());
    }

    @Test
    void atualizacaoInvalidaPreservaPagamentoAnterior() {
        Lancamento lancamento = existente();
        lancamento.pagarEm(vencimento);
        assertThrows(CadastroInvalidoException.class,
            () -> service.atualizar(20L, request(true, null), "dono@example.com"));
        assertTrue(lancamento.isPago());
        assertEquals(vencimento, lancamento.getDataPagamento());
        verify(lancamentos, never()).saveAndFlush(any());
    }

    @Test
    void removeSomenteLancamentoDoUsuario() {
        Lancamento lancamento = existente();
        service.remover(20L, "dono@example.com");
        verify(lancamentos).delete(lancamento);
    }

    @Test
    void naoRemoveLancamentoForaDoEscopoDoUsuario() {
        when(lancamentos.findByIdAndUsuarioId(99L, 10L)).thenReturn(Optional.empty());
        assertThrows(LancamentoNaoEncontradoException.class,
            () -> service.remover(99L, "dono@example.com"));
        verify(lancamentos, never()).delete(any());
    }

    @Test
    void resumoSomaReceitasDespesasPagasEPendentesComPrecisaoDecimal() {
        java.time.YearMonth mes = java.time.YearMonth.of(2028, 2);
        Lancamento despesaPaga = lancamentoResumo(21L, TipoLancamento.DESPESA, "100.10", mes.atDay(1));
        despesaPaga.pagarEm(mes.atDay(1));
        Lancamento despesaPendente = lancamentoResumo(22L, TipoLancamento.DESPESA, "50.20", mes.atDay(15));
        Lancamento receita = lancamentoResumo(23L, TipoLancamento.RECEITA, "200.35", mes.atEndOfMonth());
        when(lancamentos.findPorPeriodo(10L, mes.atDay(1), mes.atEndOfMonth()))
            .thenReturn(java.util.List.of(despesaPaga, despesaPendente, receita));

        var response = service.resumirMes("dono@example.com", mes);

        assertEquals("2028-02", response.mes());
        assertEquals(new BigDecimal("150.30"), response.totalDespesas());
        assertEquals(new BigDecimal("200.35"), response.totalReceitas());
        assertEquals(new BigDecimal("50.05"), response.diferenca());
        assertEquals(3, response.lancamentos().size());
        verify(lancamentos).findPorPeriodo(10L,
            LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29));
    }

    @Test
    void resumoSemLancamentosRetornaTotaisZeradosEListaVazia() {
        java.time.YearMonth mes = java.time.YearMonth.of(2026, 12);
        when(lancamentos.findPorPeriodo(any(), any(), any()))
            .thenReturn(java.util.List.of());
        var response = service.resumirMes("dono@example.com", mes);
        assertEquals(new BigDecimal("0.00"), response.totalDespesas());
        assertEquals(new BigDecimal("0.00"), response.totalReceitas());
        assertEquals(new BigDecimal("0.00"), response.diferenca());
        assertTrue(response.lancamentos().isEmpty());
    }

    @Test
    void resumoPermiteSaldoNegativoQuandoNaoHaReceita() {
        java.time.YearMonth mes = java.time.YearMonth.of(2026, 12);
        when(lancamentos.findPorPeriodo(any(), any(), any()))
            .thenReturn(java.util.List.of(lancamentoResumo(20L, TipoLancamento.DESPESA, "10.25", mes.atDay(1))));
        var response = service.resumirMes("dono@example.com", mes);
        assertEquals(new BigDecimal("-10.25"), response.diferenca());
        assertEquals(new BigDecimal("0.00"), response.totalReceitas());
    }

    private Lancamento lancamentoResumo(Long id, TipoLancamento tipo, String valor, LocalDate vencimento) {
        Lancamento lancamento = new Lancamento();
        lancamento.setId(id);
        lancamento.setUsuario(usuario);
        lancamento.setCategoria(categoria);
        lancamento.setTipoLancamento(tipo);
        lancamento.setDescricao("Lancamento do resumo");
        lancamento.setValor(new BigDecimal(valor));
        lancamento.setDataVencimento(vencimento);
        return lancamento;
    }
}
