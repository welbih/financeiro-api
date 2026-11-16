package com.datadynecode.financeiro.api.controller;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import com.datadynecode.financeiro.api.dto.UsuarioPageResponse;

import com.datadynecode.financeiro.api.dto.UsuarioRequest;
import com.datadynecode.financeiro.api.dto.AlterarSenhaRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.datadynecode.financeiro.api.dto.UsuarioResponse;
import com.datadynecode.financeiro.api.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {
    private final UsuarioService usuarioService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioPageResponse listar(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return usuarioService.listar(page, size);
    }

    @PatchMapping("/{id}/inativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void inativar(@PathVariable @Positive Long id) {
        usuarioService.inativar(id);
    }

    @PutMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterarSenha(@Valid @RequestBody AlterarSenhaRequest request, @AuthenticationPrincipal Jwt jwt) {
        usuarioService.alterarSenha(request, jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse cadastrar(@Valid @RequestBody UsuarioRequest request) {
        return usuarioService.cadastrar(request);
    }
}
