package com.datadynecode.financeiro.api.exception;

public class LancamentoNaoEncontradoException extends RuntimeException {
    public LancamentoNaoEncontradoException() {
        super("Lancamento nao encontrado");
    }
}
