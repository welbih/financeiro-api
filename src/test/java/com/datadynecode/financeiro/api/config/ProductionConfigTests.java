package com.datadynecode.financeiro.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

class ProductionConfigTests {
    private static final String SECRET = "production-secret-with-more-than-32-characters";

    @Test
    void aceitaConfiguracaoHttpsComSegredoForte() {
        assertDoesNotThrow(() -> new ProductionConfig(new MockEnvironment(),
            "https://api.example.com", "https://app.example.com,https://admin.example.com", SECRET));
    }

    @Test
    void rejeitaDevEmProducao() {
        assertThrows(IllegalStateException.class, () -> new ProductionConfig(
            new MockEnvironment().withProperty("spring.profiles.active", "prod,dev"),
            "https://api.example.com", "https://app.example.com", SECRET));
    }

    @Test
    void rejeitaSegredoFraco() {
        assertThrows(IllegalArgumentException.class, () -> new ProductionConfig(new MockEnvironment(),
            "https://api.example.com", "https://app.example.com", "curto"));
    }

    @Test
    void rejeitaIssuerHttpEOrigemCuringa() {
        assertThrows(IllegalArgumentException.class, () -> new ProductionConfig(new MockEnvironment(),
            "http://api.example.com", "https://app.example.com", SECRET));
        assertThrows(IllegalArgumentException.class, () -> new ProductionConfig(new MockEnvironment(),
            "https://api.example.com", "*", SECRET));
    }
}
