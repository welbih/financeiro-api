package com.datadynecode.financeiro.api.controller;

import java.time.YearMonth;

import com.datadynecode.financeiro.api.dto.*;
import com.datadynecode.financeiro.api.service.PlanejamentoFinanceiroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/planejamento-financeiro")
@RequiredArgsConstructor
public class PlanejamentoFinanceiroController {
    private final PlanejamentoFinanceiroService planejamentoService;

    @PutMapping
    public PlanejamentoFinanceiroResponse configurar(@Valid @RequestBody PlanejamentoFinanceiroRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return planejamentoService.configurar(request, jwt.getSubject());
    }

    @GetMapping
    public PlanejamentoFinanceiroResponse buscar(@AuthenticationPrincipal Jwt jwt) {
        return planejamentoService.buscar(jwt.getSubject());
    }

    @GetMapping("/resumo")
    public ResumoPlanejamentoFinanceiroResponse resumir(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(pattern = "uuuu-MM") YearMonth mesFechamento) {
        return planejamentoService.resumir(jwt.getSubject(), mesFechamento);
    }
}
