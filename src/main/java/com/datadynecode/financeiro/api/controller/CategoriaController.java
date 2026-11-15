package com.datadynecode.financeiro.api.controller;

import com.datadynecode.financeiro.api.dto.*;
import com.datadynecode.financeiro.api.model.TipoLancamento;
import com.datadynecode.financeiro.api.service.CategoriaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/categorias")
@RequiredArgsConstructor
public class CategoriaController {
    private final CategoriaService categoriaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse cadastrar(@Valid @RequestBody CategoriaRequest request, @AuthenticationPrincipal Jwt jwt) {
        return categoriaService.cadastrar(request, jwt.getSubject());
    }

    @GetMapping
    public CategoriaPageResponse listar(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) TipoLancamento tipoLancamento,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return categoriaService.listar(jwt.getSubject(), tipoLancamento, page, size);
    }

    @GetMapping("/{id}")
    public CategoriaResponse buscar(@PathVariable @Positive Long id, @AuthenticationPrincipal Jwt jwt) {
        return categoriaService.buscar(id, jwt.getSubject());
    }

    @PutMapping("/{id}")
    public CategoriaResponse atualizar(@PathVariable @Positive Long id, @Valid @RequestBody CategoriaRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return categoriaService.atualizar(id, request, jwt.getSubject());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable @Positive Long id, @AuthenticationPrincipal Jwt jwt) {
        categoriaService.remover(id, jwt.getSubject());
    }
}
