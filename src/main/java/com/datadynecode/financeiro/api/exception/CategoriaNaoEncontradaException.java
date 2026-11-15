package com.datadynecode.financeiro.api.exception;

public class CategoriaNaoEncontradaException extends RuntimeException {
    public CategoriaNaoEncontradaException() { super("Categoria nao encontrada"); }
}
