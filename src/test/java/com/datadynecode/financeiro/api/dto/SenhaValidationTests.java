package com.datadynecode.financeiro.api.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SenhaValidationTests {
    @Test
    void cadastroETrocaExigemComprimentoEComplexidade() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String senha : new String[]{"Abcdef1!", "Ábcdef1#", "UmaSenhaForte123!"}) {
                assertTrue(validator.validate(new UsuarioRequest("Maria", "Silva", "maria@example.com", senha)).isEmpty());
                assertTrue(validator.validate(new AlterarSenhaRequest("antiga", senha)).isEmpty());
            }
            for (String senha : new String[]{"Abcd1!x", "abcdef1!", "ABCDEF1!", "Abcdefg!", "Abcdef12", "Abcdef1 ", "", "        "}) {
                assertFalse(validator.validate(new UsuarioRequest("Maria", "Silva", "maria@example.com", senha)).isEmpty(), senha);
                assertFalse(validator.validate(new AlterarSenhaRequest("antiga", senha)).isEmpty(), senha);
            }
            assertFalse(validator.validate(new AlterarSenhaRequest("antiga", null)).isEmpty());
            assertFalse(validator.validate(new AlterarSenhaRequest("antiga", "Aa1!".repeat(19))).isEmpty());
        }
    }
}
