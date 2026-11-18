package com.datadynecode.financeiro.api.exception;

public class UsuarioNaoEncontradoException extends RuntimeException {
    public UsuarioNaoEncontradoException() {
        super("Usuario nao encontrado");
    }
}
