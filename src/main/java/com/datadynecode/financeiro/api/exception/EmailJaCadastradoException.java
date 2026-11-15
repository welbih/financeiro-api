package com.datadynecode.financeiro.api.exception;

public class EmailJaCadastradoException extends RuntimeException {
    public EmailJaCadastradoException() {
        super("Email ja cadastrado");
    }
}
