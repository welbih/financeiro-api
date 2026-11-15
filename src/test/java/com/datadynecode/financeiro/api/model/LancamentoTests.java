package com.datadynecode.financeiro.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LancamentoTests {
    private static final LocalDate VENCIMENTO = LocalDate.of(2026, 11, 10);
    private static final LocalDate DEPOIS_DO_VENCIMENTO = LocalDate.of(2026, 11, 11);

    private Lancamento lancamento() {
        Lancamento lancamento = new Lancamento();
        lancamento.setTipoLancamento(TipoLancamento.DESPESA);
        lancamento.setUsuario(new Usuario());
        lancamento.setDescricao("Conta de energia");
        lancamento.setValor(new BigDecimal("150.25"));
        lancamento.setDataVencimento(VENCIMENTO);
        return lancamento;
    }

    @Test
    void consideraEmAtrasoSomenteAposVencimento() {
        Lancamento lancamento = lancamento();
        assertFalse(lancamento.estaEmAtrasoEm(VENCIMENTO));
        assertTrue(lancamento.estaEmAtrasoEm(DEPOIS_DO_VENCIMENTO));
    }

    @Test
    void pagamentoRetiraLancamentoDoAtraso() {
        Lancamento lancamento = lancamento();
        lancamento.pagarEm(DEPOIS_DO_VENCIMENTO);
        assertFalse(lancamento.estaEmAtrasoEm(DEPOIS_DO_VENCIMENTO));
    }

    @Test
    void naoPermitePagamentoDuplicado() {
        Lancamento lancamento = lancamento();
        lancamento.pagarEm(VENCIMENTO);
        assertThrows(IllegalStateException.class, () -> lancamento.pagarEm(VENCIMENTO));
    }

    @Test
    void reabrirPermiteNovoPagamento() {
        Lancamento lancamento = lancamento();
        lancamento.pagarEm(VENCIMENTO);
        lancamento.reabrirPagamento();
        assertTrue(lancamento.estaEmAtrasoEm(DEPOIS_DO_VENCIMENTO));
        assertDoesNotThrow(() -> lancamento.pagarEm(DEPOIS_DO_VENCIMENTO));
    }

    @Test
    void pagamentoSemDataNaoMudaEstado() {
        Lancamento lancamento = lancamento();
        assertThrows(NullPointerException.class, () -> lancamento.pagarEm(null));
        assertTrue(lancamento.estaEmAtrasoEm(DEPOIS_DO_VENCIMENTO));
    }

    @Test
    void previsaoDePagamentoNaoAlteraVencimento() {
        Lancamento lancamento = lancamento();
        lancamento.reagendarPagamentoPara(LocalDate.of(2026, 12, 1));
        assertTrue(lancamento.estaEmAtrasoEm(DEPOIS_DO_VENCIMENTO));
    }

}
