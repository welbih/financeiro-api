package com.datadynecode.financeiro.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.datadynecode.financeiro.api.model.Usuario;
import com.datadynecode.financeiro.api.repository.UsuarioRepository;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = FinanceiroApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Testcontainers
class FinanceiroApiApplicationIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        RSAKey key = new RSAKeyGenerator(2048).keyID("integration-test").generate();
        registry.add("DB_URL", postgres::getJdbcUrl);
        registry.add("DB_USERNAME", postgres::getUsername);
        registry.add("DB_PASSWORD", postgres::getPassword);
        registry.add("CLIENT_ID", () -> "integration-client");
        registry.add("CLIENT_SECRET", () -> "integration-secret-with-at-least-32-characters");
        registry.add("JWT_DURATION", () -> "900");
        registry.add("JWT_ISSUER", () -> "https://api.example.com");
        registry.add("JWT_JWK", key::toJSONString);
        registry.add("CORS_ORIGINS", () -> "https://app.example.com");
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired OAuth2AuthorizationService authorizations;

    @Test
    void validaMigrationsLoginPersistenciaEHealthSemSeedDev() throws Exception {
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class));
        assertEquals(0, usuarios.count());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM roles", Integer.class));

        Long roleId = jdbc.queryForObject("INSERT INTO roles(authority) VALUES ('ROLE_ADMIN') RETURNING id", Long.class);
        Usuario usuario = new Usuario();
        usuario.setNome("Integration");
        usuario.setSobrenome("Admin");
        usuario.setEmail("integration@example.com");
        usuario.setSenha(encoder.encode("test-password"));
        usuarios.save(usuario);
        jdbc.update("INSERT INTO usuarios_roles(usuario_id, role_id) VALUES (?, ?)", usuario.getId(), roleId);

        HttpClient client = HttpClient.newHttpClient();
        String basic = Base64.getEncoder().encodeToString(
            "integration-client:integration-secret-with-at-least-32-characters".getBytes(StandardCharsets.UTF_8));
        HttpRequest login = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/token"))
            .header("Authorization", "Basic " + basic)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(
                "grant_type=urn:financeiro:params:oauth:grant-type:password&username=integration@example.com&password=test-password"))
            .build();
        var response = client.send(login, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        String token = JsonMapper.builder().build().readTree(response.body()).get("access_token").asString();
        assertEquals(java.util.List.of("ROLE_ADMIN"), decoder.decode(token).getClaimAsStringList("authorities"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM oauth2_authorization", Integer.class));
        assertNotNull(authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN));

        var health = client.send(HttpRequest.newBuilder(URI.create(
            "http://localhost:" + port + "/actuator/health/readiness")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        assertEquals("UP", JsonMapper.builder().build().readTree(health.body()).get("status").asString());
        assertFalse(health.body().contains("components"));
    }
}
