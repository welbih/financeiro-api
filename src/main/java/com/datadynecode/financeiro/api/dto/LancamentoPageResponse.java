package com.datadynecode.financeiro.api.dto;

import java.util.List;

public record LancamentoPageResponse(
    List<LancamentoResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {}
