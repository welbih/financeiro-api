package com.datadynecode.financeiro.api.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(PlanejamentoNaoEncontradoException.class)
    public ProblemDetail planejamentoNaoEncontrado(PlanejamentoNaoEncontradoException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(TokenInativoException.class)
    public ProblemDetail tokenInativo(TokenInativoException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(CategoriaNaoEncontradaException.class)
    public ProblemDetail categoriaNaoEncontrada(CategoriaNaoEncontradaException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(CategoriaEmConflitoException.class)
    public ProblemDetail categoriaEmConflito(CategoriaEmConflitoException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
    @ExceptionHandler(LancamentoNaoEncontradoException.class)
    public ProblemDetail lancamentoNaoEncontrado(LancamentoNaoEncontradoException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(UsuarioAutenticadoNaoEncontradoException.class)
    public ProblemDetail usuarioAutenticadoNaoEncontrado(UsuarioAutenticadoNaoEncontradoException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ProblemDetail emailJaCadastrado(EmailJaCadastradoException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(CadastroInvalidoException.class)
    public ProblemDetail cadastroInvalido(CadastroInvalidoException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException exception) {
        // Nao inclui valores rejeitados: podem conter a senha do usuario.
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "Dados invalidos: verifique os campos obrigatorios e os formatos informados");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflitoDeDados(DataIntegrityViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Conflito com dados ja cadastrados");
    }
}
