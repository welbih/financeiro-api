package com.datadynecode.financeiro.api.exception;

public class TokenInativoException extends RuntimeException {
    public TokenInativoException() { super("Token invalido ou inativo"); }
}
