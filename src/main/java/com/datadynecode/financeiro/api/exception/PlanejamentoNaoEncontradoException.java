package com.datadynecode.financeiro.api.exception;

public class PlanejamentoNaoEncontradoException extends RuntimeException {
    public PlanejamentoNaoEncontradoException() { super("Planejamento financeiro ainda nao configurado"); }
}
