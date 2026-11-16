package com.datadynecode.financeiro.api.dto;

import java.util.List;

public record CategoriaPageResponse(List<CategoriaResponse> content, int page, int size,
    long totalElements, int totalPages) {}
