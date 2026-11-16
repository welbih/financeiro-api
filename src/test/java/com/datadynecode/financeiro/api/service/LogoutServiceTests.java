package com.datadynecode.financeiro.api.service;

import java.time.Instant;

import com.datadynecode.financeiro.api.config.auth.TokenAtivoValidator;
import com.datadynecode.financeiro.api.exception.TokenInativoException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import static org.junit.jupiter.api.Assertions.*;

class LogoutServiceTests {
    private final OAuth2AuthorizationService authorizations = new InMemoryOAuth2AuthorizationService();
    private final LogoutService service = new LogoutService(authorizations);
    private final TokenAtivoValidator validator = new TokenAtivoValidator(authorizations,
        email -> org.springframework.security.core.userdetails.User.withUsername(email)
            .password("hash").authorities("ROLE_USER").build());

    private void registrar(String token, String subject, Instant expiresAt) {
        RegisteredClient client = RegisteredClient.withId("logout-client").clientId("logout-client")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
            token, Instant.now().minusSeconds(120), expiresAt);
        authorizations.save(OAuth2Authorization.withRegisteredClient(client)
            .principalName(subject).authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .accessToken(accessToken).build());
    }

    private Jwt jwt(String token, String subject) {
        return Jwt.withTokenValue(token).header("alg", "RS256").subject(subject)
            .issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(600)).build();
    }

    @Test
    void logoutInvalidaApenasTokenAtualEPreservaRegistroDeAutorizacao() {
        registrar("token-atual", "usuario@example.com", Instant.now().plusSeconds(600));
        registrar("outra-sessao", "usuario@example.com", Instant.now().plusSeconds(600));
        assertFalse(validator.validate(jwt("token-atual", "usuario@example.com")).hasErrors());

        service.deslogar("token-atual", "usuario@example.com");

        OAuth2Authorization authorization = authorizations.findByToken("token-atual", OAuth2TokenType.ACCESS_TOKEN);
        assertNotNull(authorization);
        assertTrue(authorization.getAccessToken().isInvalidated());
        assertTrue(validator.validate(jwt("token-atual", "usuario@example.com")).hasErrors());
        assertFalse(validator.validate(jwt("outra-sessao", "usuario@example.com")).hasErrors());
        assertThrows(TokenInativoException.class, () -> service.deslogar("token-atual", "usuario@example.com"));
    }

    @Test
    void naoRevogaTokenDeOutroUsuario() {
        registrar("token-outro", "outro@example.com", Instant.now().plusSeconds(600));
        assertThrows(TokenInativoException.class, () -> service.deslogar("token-outro", "usuario@example.com"));
        assertFalse(validator.validate(jwt("token-outro", "outro@example.com")).hasErrors());
        assertTrue(validator.validate(jwt("token-outro", "usuario@example.com")).hasErrors());
    }

    @Test
    void rejeitaTokenNaoRegistradoOuExpirado() {
        assertTrue(validator.validate(jwt("desconhecido", "usuario@example.com")).hasErrors());
        assertThrows(TokenInativoException.class, () -> service.deslogar("desconhecido", "usuario@example.com"));
        registrar("expirado", "usuario@example.com", Instant.now().minusSeconds(30));
        assertTrue(validator.validate(jwt("expirado", "usuario@example.com")).hasErrors());
        assertThrows(TokenInativoException.class, () -> service.deslogar("expirado", "usuario@example.com"));
    }
}
