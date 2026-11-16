package com.datadynecode.financeiro.api.controller;

import java.time.YearMonth;

import com.datadynecode.financeiro.api.dto.LancamentoRequest;
import com.datadynecode.financeiro.api.dto.PagamentoLancamentoRequest;
import com.datadynecode.financeiro.api.dto.LancamentoResponse;
import com.datadynecode.financeiro.api.dto.LancamentoPageResponse;
import com.datadynecode.financeiro.api.dto.ResumoMensalLancamentosResponse;
import com.datadynecode.financeiro.api.service.LancamentoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/lancamentos")
@RequiredArgsConstructor
public class LancamentoController {
    private final LancamentoService lancamentoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LancamentoResponse cadastrar(@Valid @RequestBody LancamentoRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return lancamentoService.cadastrar(request, jwt.getSubject());
    }

    @GetMapping
    public LancamentoPageResponse listar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return lancamentoService.listar(jwt.getSubject(), page, size);
    }

    @PutMapping("/{id}")
    public LancamentoResponse atualizar(@PathVariable @Positive Long id,
            @Valid @RequestBody LancamentoRequest request, @AuthenticationPrincipal Jwt jwt) {
        return lancamentoService.atualizar(id, request, jwt.getSubject());
    }

    @GetMapping("/resumo-mensal")
    public ResumoMensalLancamentosResponse resumirMes(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(pattern = "uuuu-MM") YearMonth mes) {
        return lancamentoService.resumirMes(jwt.getSubject(), mes);
    }

    @PatchMapping("/{id}/pagar")
    public LancamentoResponse pagar(@PathVariable @Positive Long id,
            @RequestBody(required = false) PagamentoLancamentoRequest request, @AuthenticationPrincipal Jwt jwt) {
        return lancamentoService.pagar(id, request == null ? null : request.data(), jwt.getSubject());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable @Positive Long id, @AuthenticationPrincipal Jwt jwt) {
        lancamentoService.remover(id, jwt.getSubject());
    }
}
