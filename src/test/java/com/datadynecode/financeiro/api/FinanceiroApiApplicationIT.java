package com.datadynecode.financeiro.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.math.BigDecimal;
import java.time.LocalDate;

import com.datadynecode.financeiro.api.model.Lancamento;
import com.datadynecode.financeiro.api.model.Categoria;
import com.datadynecode.financeiro.api.repository.CategoriaRepository;
import com.datadynecode.financeiro.api.model.TipoLancamento;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired CategoriaRepository categorias;

    @Test
    void validaMigrationsLoginPersistenciaEHealthSemSeedDev() throws Exception {
        assertEquals(11, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class));
        assertEquals(0, usuarios.count());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM roles", Integer.class));

        jdbc.update("INSERT INTO roles(authority) VALUES ('ROLE_USER')");
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

        HttpRequest cadastro = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/usuarios"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""
                {"nome":"Maria","sobrenome":"Silva","email":"maria@example.com",
                 "senha":"SenhaForte123!"}
                """)).build();
        var created = client.send(cadastro, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, created.statusCode(), created.body());
        var json = JsonMapper.builder().build().readTree(created.body());
        assertFalse(json.has("senha"));
        assertFalse(json.has("password"));
        var maria = usuarios.findByEmail("maria@example.com").orElseThrow();
        assertTrue(encoder.matches("SenhaForte123!", maria.getSenha()));
        assertTrue(maria.getRoles().contemPorNome("ROLE_USER"));
        assertFalse(maria.getRoles().contemPorNome("ROLE_ADMIN"));
        assertEquals(409, client.send(cadastro, HttpResponse.BodyHandlers.ofString()).statusCode());

        validarPersistenciaLancamento(usuario);
        validarCadastroLancamentoAutenticado(client, token, usuario, maria);
        validarCrudCategorias(client, token, usuario, maria);
        validarCategoriaOpcional(client, token);
        validarPlanejamento(client, token, usuario, maria);
        validarRecorrencias(client, token, usuario);
        validarPagamento(client, token, usuario, maria);

        var health = client.send(HttpRequest.newBuilder(URI.create(
            "http://localhost:" + port + "/actuator/health/readiness")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        assertEquals("UP", JsonMapper.builder().build().readTree(health.body()).get("status").asString());
        assertFalse(health.body().contains("components"));
        validarLogout(client, token, login);
        validarAdministracaoUsuarios(client, login, usuario, maria);
        validarAlteracaoSenha(client, login, usuario);
    }

    private void validarAdministracaoUsuarios(HttpClient client, HttpRequest login, Usuario admin, Usuario maria) throws Exception {
        var mapper = JsonMapper.builder().build();
        String token = mapper.readTree(client.send(login, HttpResponse.BodyHandlers.ofString()).body()).get("access_token").asString();
        HttpRequest loginMaria = HttpRequest.newBuilder(login.uri())
            .header("Authorization", login.headers().firstValue("Authorization").orElseThrow())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=urn:financeiro:params:oauth:grant-type:password&username=maria@example.com&password=SenhaForte123!"))
            .build();
        String tokenMaria = mapper.readTree(client.send(loginMaria, HttpResponse.BodyHandlers.ofString()).body()).get("access_token").asString();
        String base = "http://localhost:" + port + "/usuarios";
        assertEquals(403, enviarJson(client, tokenMaria, "GET", base, null).statusCode());
        assertEquals(403, enviarJson(client, tokenMaria, "PATCH", base + "/" + admin.getId() + "/inativar", null).statusCode());
        var lista = enviarJson(client, token, "GET", base + "?page=0&size=1", null);
        assertEquals(200, lista.statusCode(), lista.body());
        assertEquals(1, mapper.readTree(lista.body()).get("content").size());
        assertEquals(2, mapper.readTree(lista.body()).get("totalElements").asInt());
        assertFalse(lista.body().contains("senha"));
        String inativar = base + "/" + maria.getId() + "/inativar";
        assertEquals(204, enviarJson(client, token, "PATCH", inativar, null).statusCode());
        assertEquals(204, enviarJson(client, token, "PATCH", inativar, null).statusCode());
        assertFalse(usuarios.findById(maria.getId()).orElseThrow().isAtivo());
        assertEquals(401, enviarJson(client, tokenMaria, "GET", "http://localhost:" + port + "/lancamentos", null).statusCode());
        assertEquals(400, client.send(loginMaria, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(404, enviarJson(client, token, "PATCH", base + "/999999999/inativar", null).statusCode());
        assertEquals(200, enviarJson(client, token, "GET", base, null).statusCode());
    }

    private void validarAlteracaoSenha(HttpClient client, HttpRequest login, Usuario usuario) throws Exception {
        var autenticacao = client.send(login, HttpResponse.BodyHandlers.ofString());
        String token = JsonMapper.builder().build().readTree(autenticacao.body()).get("access_token").asString();
        String url = "http://localhost:" + port + "/usuarios/senha";
        String hashAnterior = usuarios.findById(usuario.getId()).orElseThrow().getSenha();
        assertEquals(400, enviarJson(client, token, "PUT", url,
            "{\"senhaAtual\":\"errada\",\"novaSenha\":\"NovaSenhaForte123!\"}").statusCode());
        assertEquals(hashAnterior, usuarios.findById(usuario.getId()).orElseThrow().getSenha());
        var resposta = enviarJson(client, token, "PUT", url,
            "{\"senhaAtual\":\"test-password\",\"novaSenha\":\"NovaSenhaForte123!\"}");
        assertEquals(204, resposta.statusCode(), resposta.body());
        assertEquals("", resposta.body());
        String hashNovo = usuarios.findById(usuario.getId()).orElseThrow().getSenha();
        assertTrue(encoder.matches("NovaSenhaForte123!", hashNovo));
        assertFalse(encoder.matches("test-password", hashNovo));
        assertNotEquals("NovaSenhaForte123!", hashNovo);
        assertEquals(400, client.send(login, HttpResponse.BodyHandlers.ofString()).statusCode());
        HttpRequest novoLogin = HttpRequest.newBuilder(login.uri())
            .header("Authorization", login.headers().firstValue("Authorization").orElseThrow())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=urn:financeiro:params:oauth:grant-type:password&username=integration@example.com&password=NovaSenhaForte123!"))
            .build();
        assertEquals(200, client.send(novoLogin, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private void validarPlanejamento(HttpClient client, String token, Usuario usuario, Usuario outro) throws Exception {
        String base = "http://localhost:" + port + "/planejamento-financeiro";
        assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base)).GET().build(),
            HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(404, enviarJson(client, token, "GET", base, null).statusCode());
        String config = """
            {"diaFechamento":9,"dataInicioControle":"2090-11-10","saldoInicial":300,"usuarioId":%d}
            """.formatted(outro.getId());
        var salvo = enviarJson(client, token, "PUT", base, config);
        assertEquals(200, salvo.statusCode(), salvo.body());
        var mapper = JsonMapper.builder().build();
        assertEquals(usuario.getId().longValue(), mapper.readTree(salvo.body()).get("usuarioId").asLong());
        assertEquals(200, enviarJson(client, token, "PUT", base, config).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM planejamentos_financeiros", Integer.class));
        assertEquals(200, enviarJson(client, token, "GET", base, null).statusCode());
        assertEquals(400, enviarJson(client, token, "PUT", base, config.replace(":9,", ":32,")).statusCode());
        assertEquals(400, enviarJson(client, token, "PUT", base, config.replace("2090-11-10", "2090-11-11")).statusCode());
        inserirParaResumo(usuario, "DESPESA", "999", "2090-11-09");
        Long receita = inserirParaResumo(usuario, "RECEITA", "4000", "2090-12-09");
        Long despesa = inserirParaResumo(usuario, "DESPESA", "2500", "2090-11-10");
        jdbc.update("UPDATE lancamentos SET pago = true, data_pagamento = data_vencimento WHERE id IN (?, ?)", receita, despesa);
        Long previsto = inserirParaResumo(usuario, "DESPESA", "1000", "2090-12-15");
        jdbc.update("UPDATE lancamentos SET data_previsao_pagamento = DATE '2090-12-09' WHERE id = ?", previsto);
        inserirParaResumo(usuario, "RECEITA", "4000", "2091-01-09");
        inserirParaResumo(usuario, "DESPESA", "4200", "2090-12-10");
        inserirParaResumo(outro, "RECEITA", "99999", "2090-12-09");
        var janeiro = enviarJson(client, token, "GET", base + "/resumo?mesFechamento=2091-01", null);
        assertEquals(200, janeiro.statusCode(), janeiro.body());
        var jan = mapper.readTree(janeiro.body());
        assertEquals(800, jan.get("saldoInicialProjetado").asInt());
        assertEquals(600, jan.get("saldoFinalProjetado").asInt());
        assertEquals(2, jan.get("lancamentos").size());
        var dezembro = enviarJson(client, token, "GET", base + "/resumo?mesFechamento=2090-12", null);
        var dez = mapper.readTree(dezembro.body());
        assertEquals("2090-11-10", dez.get("dataInicial").asString());
        assertEquals("2090-12-09", dez.get("dataFinal").asString());
        assertEquals(800, dez.get("saldoFinalProjetado").asInt());
        assertEquals(4000, dez.get("receitasRecebidas").asInt());
        assertEquals(2500, dez.get("despesasPagas").asInt());
        assertEquals(1000, dez.get("despesasPendentes").asInt());
        assertEquals(3, dez.get("lancamentos").size());
        var fevereiro = enviarJson(client, token, "GET", base + "/resumo?mesFechamento=2091-02", null);
        assertEquals(600, mapper.readTree(fevereiro.body()).get("saldoFinalProjetado").asInt());
        jdbc.update("UPDATE lancamentos SET valor = 2600 WHERE id = ?", despesa);
        var recalculado = enviarJson(client, token, "GET", base + "/resumo?mesFechamento=2091-01", null);
        assertEquals(500, mapper.readTree(recalculado.body()).get("saldoFinalProjetado").asInt());
        assertEquals(400, enviarJson(client, token, "GET", base + "/resumo?mesFechamento=2090-11", null).statusCode());
        assertEquals(400, enviarJson(client, token, "GET", base + "/resumo?mesFechamento=invalido", null).statusCode());
    }

    private void validarPagamento(HttpClient client, String token, Usuario usuario, Usuario outro) throws Exception {
        Long id = inserirParaResumo(usuario, "DESPESA", "50", "2095-01-01");
        String url = "http://localhost:" + port + "/lancamentos/" + id + "/pagar";
        var mapper = JsonMapper.builder().build();
        assertEquals(400, enviarJson(client, token, "PATCH", url, "{\"data\":\"invalida\"}").statusCode());
        var pago = enviarJson(client, token, "PATCH", url, "{\"data\":\"2026-11-16\"}");
        assertEquals(200, pago.statusCode(), pago.body());
        assertTrue(mapper.readTree(pago.body()).get("pago").asBoolean());
        assertEquals("2026-11-16", mapper.readTree(pago.body()).get("dataPagamento").asString());
        assertEquals(LocalDate.of(2026, 11, 16), jdbc.queryForObject("SELECT data_pagamento FROM lancamentos WHERE id = ?", LocalDate.class, id));
        assertEquals(400, enviarJson(client, token, "PATCH", url, "{}").statusCode());
        for (String body : new String[]{"{}", null, "{\"data\":null}"}) {
            Long pendente = inserirParaResumo(usuario, "RECEITA", "50", "2095-01-01");
            LocalDate antes = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
            var atual = enviarJson(client, token, "PATCH", url.replace("/" + id + "/", "/" + pendente + "/"), body);
            assertEquals(200, atual.statusCode(), atual.body());
            LocalDate data = LocalDate.parse(mapper.readTree(atual.body()).get("dataPagamento").asString());
            assertFalse(data.isBefore(antes));
            assertFalse(data.isAfter(LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"))));
        }
        Long alheio = inserirParaResumo(outro, "DESPESA", "50", "2095-01-01");
        assertEquals(404, enviarJson(client, token, "PATCH", url.replace("/" + id + "/", "/" + alheio + "/"), "{}").statusCode());
        assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(url)).method("PATCH", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private void validarRecorrencias(HttpClient client, String token, Usuario usuario) throws Exception {
        String url = "http://localhost:" + port + "/lancamentos";
        String body = """
            {"tipoLancamento":"DESPESA","descricao":"Recorrencia integracao","valor":85.50,
             "dataVencimento":"2096-01-31","dataPrevisaoPagamento":"2096-02-10","quantidadeRecorrencias":2}
            """;
        var resposta = enviarJson(client, token, "POST", url, body);
        assertEquals(201, resposta.statusCode(), resposta.body());
        var registros = jdbc.queryForList("SELECT * FROM lancamentos WHERE descricao = 'Recorrencia integracao' ORDER BY data_vencimento");
        assertEquals(3, registros.size());
        assertEquals(java.sql.Date.valueOf("2096-02-29"), registros.get(1).get("data_vencimento"));
        assertEquals(java.sql.Date.valueOf("2096-03-31"), registros.get(2).get("data_vencimento"));
        assertEquals(java.sql.Date.valueOf("2096-04-10"), registros.get(2).get("data_previsao_pagamento"));
        for (var registro : registros) {
            assertEquals(usuario.getId(), registro.get("usuario_id"));
            assertEquals(new BigDecimal("85.50"), registro.get("valor"));
            assertEquals(false, registro.get("pago"));
        }
        for (int quantidade : new int[]{-1, 13}) {
            assertEquals(400, enviarJson(client, token, "POST", url,
                body.replace("\"quantidadeRecorrencias\":2", "\"quantidadeRecorrencias\":" + quantidade)).statusCode());
        }
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM lancamentos WHERE descricao = 'Recorrencia integracao'", Integer.class));
    }

    private void validarPersistenciaLancamento(Usuario usuario) {
        LocalDate vencimento = LocalDate.of(2026, 11, 10);
        LocalDate pagamento = LocalDate.of(2026, 11, 12);
        Lancamento lancamento = new Lancamento();
        lancamento.setTipoLancamento(TipoLancamento.DESPESA);
        lancamento.setUsuario(usuario);
        lancamento.setCategoria(categoriaPara(usuario, TipoLancamento.DESPESA));
        lancamento.setDescricao("Energia");
        lancamento.setValor(new BigDecimal("150.25"));
        lancamento.setDataVencimento(vencimento);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> entityManager.persist(lancamento));
        Long identificador = jdbc.queryForObject("SELECT id FROM lancamentos WHERE usuario_id = ?", Long.class, usuario.getId());
        assertEquals(new BigDecimal("150.25"), jdbc.queryForObject("SELECT valor FROM lancamentos WHERE id = ?", BigDecimal.class, identificador));
        assertEquals("Energia", jdbc.queryForObject("SELECT descricao FROM lancamentos WHERE id = ?", String.class, identificador));
        assertEquals("DESPESA", jdbc.queryForObject("SELECT tipo_lancamento FROM lancamentos WHERE id = ?", String.class, identificador));
        assertFalse(jdbc.queryForObject("SELECT pago FROM lancamentos WHERE id = ?", Boolean.class, identificador));
        assertNull(jdbc.queryForObject("SELECT data_previsao_pagamento FROM lancamentos WHERE id = ?", LocalDate.class, identificador));
        transaction.executeWithoutResult(status -> {
            Lancamento salvo = entityManager.find(Lancamento.class, identificador);
            assertTrue(salvo.estaEmAtrasoEm(pagamento));
            salvo.reagendarPagamentoPara(pagamento);
            salvo.pagarEm(pagamento);
        });
        assertTrue(jdbc.queryForObject("SELECT pago FROM lancamentos WHERE id = ?", Boolean.class, identificador));
        assertEquals(pagamento, jdbc.queryForObject("SELECT data_pagamento FROM lancamentos WHERE id = ?", LocalDate.class, identificador));
        assertEquals(pagamento, jdbc.queryForObject("SELECT data_previsao_pagamento FROM lancamentos WHERE id = ?", LocalDate.class, identificador));
        assertEquals(vencimento, jdbc.queryForObject("SELECT data_vencimento FROM lancamentos WHERE id = ?", LocalDate.class, identificador));
        transaction.executeWithoutResult(status -> {
            Lancamento salvo = entityManager.find(Lancamento.class, identificador);
            assertFalse(salvo.estaEmAtrasoEm(pagamento));
            salvo.reabrirPagamento();
        });
        assertFalse(jdbc.queryForObject("SELECT pago FROM lancamentos WHERE id = ?", Boolean.class, identificador));
        assertNull(jdbc.queryForObject("SELECT data_pagamento FROM lancamentos WHERE id = ?", LocalDate.class, identificador));
        Lancamento receita = new Lancamento();
        receita.setTipoLancamento(TipoLancamento.RECEITA);
        receita.setUsuario(usuario);
        receita.setCategoria(categoriaPara(usuario, TipoLancamento.RECEITA));
        receita.setDescricao("Salario");
        receita.setValor(new BigDecimal("5000.00"));
        receita.setDataVencimento(vencimento);
        transaction.executeWithoutResult(status -> entityManager.persist(receita));
        assertNull(jdbc.queryForObject("SELECT data_previsao_pagamento FROM lancamentos WHERE tipo_lancamento = 'RECEITA'", LocalDate.class));
    }

    private void validarCadastroLancamentoAutenticado(HttpClient client, String token,
            Usuario autenticado, Usuario outroUsuario) throws Exception {
        String body = """
            {"tipoLancamento":"DESPESA","descricao":"Internet","valor":99.90,"pago":false,
             "dataVencimento":"2026-12-10","dataPrevisaoPagamento":"2026-12-09","dataPagamento":null,
             "usuarioId":%d,"usuario":{"id":%d},"categoriaId":%d}
            """.formatted(outroUsuario.getId(), outroUsuario.getId(), categoriaPara(autenticado, TipoLancamento.DESPESA).getId());
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/lancamentos"))
            .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode(), response.body());
        var json = JsonMapper.builder().build().readTree(response.body());
        long identificador = json.get("id").asLong();
        assertEquals(autenticado.getId().longValue(), json.get("usuarioId").asLong());
        assertFalse(json.has("usuario"));
        assertEquals(autenticado.getId(), jdbc.queryForObject(
            "SELECT usuario_id FROM lancamentos WHERE id = ?", Long.class, identificador));
        assertFalse(jdbc.queryForObject("SELECT pago FROM lancamentos WHERE id = ?", Boolean.class, identificador));

        String pago = body.replace("\"pago\":false", "\"pago\":true")
            .replace("\"dataPagamento\":null", "\"dataPagamento\":\"2026-11-09\"");
        HttpRequest requestPago = HttpRequest.newBuilder(request.uri())
            .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(pago)).build();
        var responsePago = client.send(requestPago, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, responsePago.statusCode(), responsePago.body());
        var jsonPago = JsonMapper.builder().build().readTree(responsePago.body());
        assertTrue(jsonPago.get("pago").asBoolean());
        assertEquals("2026-11-09", jsonPago.get("dataPagamento").asString());

        HttpRequest invalido = HttpRequest.newBuilder(request.uri())
            .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.replace("\"pago\":false", "\"pago\":true"))).build();
        assertEquals(400, client.send(invalido, HttpResponse.BodyHandlers.ofString()).statusCode());
        HttpRequest anonimo = HttpRequest.newBuilder(request.uri()).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        assertEquals(401, client.send(anonimo, HttpResponse.BodyHandlers.ofString()).statusCode());
        validarListagemAtualizacaoRemocao(client, token, autenticado, outroUsuario, identificador, body);
        validarResumoMensal(client, token, autenticado, outroUsuario);
    }

    private void validarListagemAtualizacaoRemocao(HttpClient client, String token, Usuario autenticado,
            Usuario outroUsuario, long identificador, String body) throws Exception {
        Long identificadorAlheio = jdbc.queryForObject("""
            INSERT INTO lancamentos(tipo_lancamento, usuario_id, descricao, valor, data_vencimento, categoria_id)
            VALUES ('DESPESA', ?, 'Lancamento de outro usuario', 10, '2026-01-01', ?) RETURNING id
            """, Long.class, outroUsuario.getId(), categoriaPara(outroUsuario, TipoLancamento.DESPESA).getId());
        String base = "http://localhost:" + port + "/lancamentos";
        HttpRequest listagem = HttpRequest.newBuilder(URI.create(base + "?page=0&size=2"))
            .header("Authorization", "Bearer " + token).GET().build();
        var listagemResponse = client.send(listagem, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, listagemResponse.statusCode(), listagemResponse.body());
        var pagina = JsonMapper.builder().build().readTree(listagemResponse.body());
        assertEquals(2, pagina.get("content").size());
        long totalDoUsuario = jdbc.queryForObject("SELECT COUNT(*) FROM lancamentos WHERE usuario_id = ?",
            Long.class, autenticado.getId());
        assertEquals(totalDoUsuario, pagina.get("totalElements").asLong());
        for (var item : pagina.get("content")) {
            assertEquals(autenticado.getId().longValue(), item.get("usuarioId").asLong());
            assertNotEquals(identificadorAlheio.longValue(), item.get("id").asLong());
        }

        String atualizado = body.replace("Internet", "Internet atualizada")
            .replace("\"pago\":false", "\"pago\":true")
            .replace("\"dataPagamento\":null", "\"dataPagamento\":\"2026-11-09\"");
        HttpRequest atualizacao = HttpRequest.newBuilder(URI.create(base + "/" + identificador))
            .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(atualizado)).build();
        var atualizadoResponse = client.send(atualizacao, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, atualizadoResponse.statusCode(), atualizadoResponse.body());
        assertTrue(jdbc.queryForObject("SELECT pago FROM lancamentos WHERE id = ?", Boolean.class, identificador));
        assertEquals("Internet atualizada", jdbc.queryForObject("SELECT descricao FROM lancamentos WHERE id = ?", String.class, identificador));
        assertEquals(autenticado.getId(), jdbc.queryForObject("SELECT usuario_id FROM lancamentos WHERE id = ?", Long.class, identificador));

        HttpRequest reabertura = HttpRequest.newBuilder(atualizacao.uri())
            .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        assertEquals(200, client.send(reabertura, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertFalse(jdbc.queryForObject("SELECT pago FROM lancamentos WHERE id = ?", Boolean.class, identificador));
        assertNull(jdbc.queryForObject("SELECT data_pagamento FROM lancamentos WHERE id = ?", LocalDate.class, identificador));

        HttpRequest atualizacaoAlheia = HttpRequest.newBuilder(URI.create(base + "/" + identificadorAlheio))
            .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body)).build();
        assertEquals(404, client.send(atualizacaoAlheia, HttpResponse.BodyHandlers.ofString()).statusCode());
        HttpRequest remocaoAlheia = HttpRequest.newBuilder(atualizacaoAlheia.uri())
            .header("Authorization", "Bearer " + token).DELETE().build();
        assertEquals(404, client.send(remocaoAlheia, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals("Lancamento de outro usuario", jdbc.queryForObject(
            "SELECT descricao FROM lancamentos WHERE id = ?", String.class, identificadorAlheio));

        HttpRequest remocao = HttpRequest.newBuilder(atualizacao.uri())
            .header("Authorization", "Bearer " + token).DELETE().build();
        var removidoResponse = client.send(remocao, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, removidoResponse.statusCode());
        assertEquals("", removidoResponse.body());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM lancamentos WHERE id = ?", Integer.class, identificador));
        assertEquals(404, client.send(remocao, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private void validarResumoMensal(HttpClient client, String token, Usuario autenticado, Usuario outroUsuario) throws Exception {
        Long despesaPaga = inserirParaResumo(autenticado, "DESPESA", "10.10", "2040-02-01");
        jdbc.update("UPDATE lancamentos SET pago = true, data_pagamento = DATE '2040-03-01' WHERE id = ?", despesaPaga);
        inserirParaResumo(autenticado, "DESPESA", "5.05", "2040-02-15");
        Long receita = inserirParaResumo(autenticado, "RECEITA", "30.30", "2040-02-29");
        jdbc.update("UPDATE lancamentos SET data_previsao_pagamento = DATE '2040-01-20' WHERE id = ?", receita);
        Long receitaComPrevisaoNoMes = inserirParaResumo(autenticado, "RECEITA", "30.30", "2040-03-01");
        jdbc.update("UPDATE lancamentos SET data_previsao_pagamento = DATE '2040-02-29' WHERE id = ?", receitaComPrevisaoNoMes);
        inserirParaResumo(autenticado, "DESPESA", "1000.00", "2040-01-31");
        inserirParaResumo(autenticado, "DESPESA", "2000.00", "2040-03-01");
        inserirParaResumo(autenticado, "DESPESA", "3000.00", "2039-02-01");
        inserirParaResumo(autenticado, "RECEITA", "4000.00", "2041-02-01");
        Long lancamentoAlheio = inserirParaResumo(outroUsuario, "DESPESA", "9999.00", "2040-03-01");
        jdbc.update("UPDATE lancamentos SET data_previsao_pagamento = DATE '2040-02-01' WHERE id = ?", lancamentoAlheio);

        String base = "http://localhost:" + port + "/lancamentos/resumo-mensal?mes=";
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "2040-02"))
            .header("Authorization", "Bearer " + token).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        var json = JsonMapper.builder().build().readTree(response.body());
        assertEquals("2040-02", json.get("mes").asString());
        assertEquals(0, new BigDecimal("15.15").compareTo(json.get("totalDespesas").decimalValue()));
        assertEquals(0, new BigDecimal("30.30").compareTo(json.get("totalReceitas").decimalValue()));
        assertEquals(0, new BigDecimal("15.15").compareTo(json.get("diferenca").decimalValue()));
        assertEquals(3, json.get("lancamentos").size());
        assertEquals("2040-02-01", json.get("lancamentos").get(0).get("dataVencimento").asString());
        assertEquals("2040-03-01", json.get("lancamentos").get(2).get("dataVencimento").asString());
        assertEquals("2040-02-29", json.get("lancamentos").get(2).get("dataPrevisaoPagamento").asString());
        for (var lancamento : json.get("lancamentos")) {
            assertEquals(autenticado.getId().longValue(), lancamento.get("usuarioId").asLong());
            assertNotEquals(receita.longValue(), lancamento.get("id").asLong());
            assertFalse(lancamento.has("usuario"));
        }

        HttpRequest janeiro = HttpRequest.newBuilder(URI.create(base + "2040-01"))
            .header("Authorization", "Bearer " + token).GET().build();
        var janeiroResponse = client.send(janeiro, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, janeiroResponse.statusCode());
        var jsonJaneiro = JsonMapper.builder().build().readTree(janeiroResponse.body());
        assertEquals(2, jsonJaneiro.get("lancamentos").size());
        assertEquals(receita.longValue(), jsonJaneiro.get("lancamentos").get(0).get("id").asLong());
        assertEquals(0, new BigDecimal("30.30").compareTo(jsonJaneiro.get("totalReceitas").decimalValue()));

        HttpRequest vazio = HttpRequest.newBuilder(URI.create(base + "2042-02"))
            .header("Authorization", "Bearer " + token).GET().build();
        var vazioResponse = client.send(vazio, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, vazioResponse.statusCode());
        var jsonVazio = JsonMapper.builder().build().readTree(vazioResponse.body());
        assertEquals(0, jsonVazio.get("lancamentos").size());
        assertEquals(0, jsonVazio.get("totalDespesas").decimalValue().signum());
        assertEquals(0, jsonVazio.get("totalReceitas").decimalValue().signum());
        assertEquals(0, jsonVazio.get("diferenca").decimalValue().signum());
    }

    private Long inserirParaResumo(Usuario usuario, String tipo, String valor, String vencimento) {
        return jdbc.queryForObject("""
            INSERT INTO lancamentos(tipo_lancamento, usuario_id, descricao, valor, data_vencimento, categoria_id)
            VALUES (?, ?, 'Fixture resumo mensal', ?, ?, ?) RETURNING id
            """, Long.class, tipo, usuario.getId(), new BigDecimal(valor), LocalDate.parse(vencimento),
            categoriaPara(usuario, TipoLancamento.valueOf(tipo)).getId());
    }

    private Categoria categoriaPara(Usuario usuario, TipoLancamento tipo) {
        String nome = "Categoria fixture " + tipo;
        jdbc.update("INSERT INTO categorias(nome, tipo_lancamento, usuario_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
            nome, tipo.name(), usuario.getId());
        Long id = jdbc.queryForObject("SELECT id FROM categorias WHERE usuario_id = ? AND nome = ?",
            Long.class, usuario.getId(), nome);
        return categorias.findById(id).orElseThrow();
    }

    private void validarCrudCategorias(HttpClient client, String token, Usuario usuario, Usuario outroUsuario) throws Exception {
        String base = "http://localhost:" + port;
        String body = "{\"nome\":\" Transporte \",\"tipoLancamento\":\"DESPESA\",\"usuarioId\":" + outroUsuario.getId() + "}";
        var created = enviarJson(client, token, "POST", base + "/categorias", body);
        assertEquals(201, created.statusCode(), created.body());
        var categoriaJson = JsonMapper.builder().build().readTree(created.body());
        long id = categoriaJson.get("id").asLong();
        assertEquals(usuario.getId().longValue(), categoriaJson.get("usuarioId").asLong());
        assertEquals("Transporte", categoriaJson.get("nome").asString());
        assertFalse(categoriaJson.has("usuario"));
        assertEquals(409, enviarJson(client, token, "POST", base + "/categorias",
            "{\"nome\":\" transporte \",\"tipoLancamento\":\"RECEITA\"}").statusCode());

        String basic = Base64.getEncoder().encodeToString(
            "integration-client:integration-secret-with-at-least-32-characters".getBytes(StandardCharsets.UTF_8));
        HttpRequest loginOutro = HttpRequest.newBuilder(URI.create(base + "/oauth2/token"))
            .header("Authorization", "Basic " + basic).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=urn:financeiro:params:oauth:grant-type:password&username=maria@example.com&password=SenhaForte123!"))
            .build();
        var loginResponse = client.send(loginOutro, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginResponse.statusCode(), loginResponse.body());
        String tokenOutro = JsonMapper.builder().build().readTree(loginResponse.body()).get("access_token").asString();
        var mesmaCategoriaOutroUsuario = enviarJson(client, tokenOutro, "POST", base + "/categorias", body);
        assertEquals(201, mesmaCategoriaOutroUsuario.statusCode(), mesmaCategoriaOutroUsuario.body());
        long idAlheio = JsonMapper.builder().build().readTree(mesmaCategoriaOutroUsuario.body()).get("id").asLong();
        assertEquals(404, enviarJson(client, token, "GET", base + "/categorias/" + idAlheio, null).statusCode());
        assertEquals(404, enviarJson(client, token, "PUT", base + "/categorias/" + idAlheio, body).statusCode());
        assertEquals(404, enviarJson(client, token, "DELETE", base + "/categorias/" + idAlheio, null).statusCode());
        var list = enviarJson(client, token, "GET", base + "/categorias?tipoLancamento=DESPESA&size=100", null);
        assertEquals(200, list.statusCode());
        for (var categoria : JsonMapper.builder().build().readTree(list.body()).get("content")) {
            assertEquals(usuario.getId().longValue(), categoria.get("usuarioId").asLong());
            assertEquals("DESPESA", categoria.get("tipoLancamento").asString());
        }
        assertEquals(200, enviarJson(client, token, "GET", base + "/categorias/" + id, null).statusCode());
        assertEquals(409, enviarJson(client, token, "PUT", base + "/categorias/" + id,
            "{\"nome\":\"Categoria fixture DESPESA\",\"tipoLancamento\":\"DESPESA\"}").statusCode());
        assertEquals(200, enviarJson(client, token, "PUT", base + "/categorias/" + id,
            "{\"nome\":\"Renda extra\",\"tipoLancamento\":\"RECEITA\"}").statusCode());
        String lancamento = "{\"tipoLancamento\":\"DESPESA\",\"descricao\":\"Teste categoria\",\"valor\":75.50,\"dataVencimento\":\"2026-12-10\",\"categoriaId\":" + id + "}";
        assertEquals(400, enviarJson(client, token, "POST", base + "/lancamentos", lancamento).statusCode());
        assertEquals(404, enviarJson(client, token, "POST", base + "/lancamentos",
            lancamento.replace("\"categoriaId\":" + id, "\"categoriaId\":" + idAlheio)).statusCode());
        var registro = enviarJson(client, token, "POST", base + "/lancamentos", lancamento.replace("DESPESA", "RECEITA"));
        assertEquals(201, registro.statusCode(), registro.body());
        var registroJson = JsonMapper.builder().build().readTree(registro.body());
        long registroId = registroJson.get("id").asLong();
        assertEquals(id, registroJson.get("categoriaId").asLong());
        assertEquals(409, enviarJson(client, token, "DELETE", base + "/categorias/" + id, null).statusCode());
        assertEquals(409, enviarJson(client, token, "PUT", base + "/categorias/" + id,
            "{\"nome\":\"Renda extra\",\"tipoLancamento\":\"DESPESA\"}").statusCode());
        assertEquals(200, enviarJson(client, token, "PUT", base + "/categorias/" + id,
            "{\"nome\":\"Renda complementar\",\"tipoLancamento\":\"RECEITA\"}").statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lancamentos WHERE id=?", Integer.class, registroId));
        assertEquals(204, enviarJson(client, token, "DELETE", base + "/lancamentos/" + registroId, null).statusCode());
        assertEquals(200, enviarJson(client, token, "GET", base + "/categorias/" + id, null).statusCode());
        assertEquals(204, enviarJson(client, token, "DELETE", base + "/categorias/" + id, null).statusCode());
        assertEquals(404, enviarJson(client, token, "GET", base + "/categorias/" + id, null).statusCode());
        assertEquals(200, enviarJson(client, tokenOutro, "GET", base + "/categorias/" + idAlheio, null).statusCode());
    }

    private HttpResponse<String> enviarJson(HttpClient client, String token, String metodo, String url, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + token);
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        request.method(metodo, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void validarLogout(HttpClient client, String token, HttpRequest login) throws Exception {
        String base = "http://localhost:" + port;
        var novaSessao = client.send(login, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, novaSessao.statusCode());
        String outroToken = JsonMapper.builder().build().readTree(novaSessao.body()).get("access_token").asString();
        assertNotEquals(token, outroToken);
        var response = enviarJson(client, token, "POST", base + "/auth/logout", null);
        assertEquals(204, response.statusCode(), response.body());
        assertEquals("", response.body());
        var authorization = authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        assertNotNull(authorization);
        assertTrue(authorization.getAccessToken().isInvalidated());
        // Uma nova instancia do servico JDBC tambem observa a revogacao persistida.
        var clients = new org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository(
            org.springframework.security.oauth2.server.authorization.client.RegisteredClient.withId("integration-client")
                .clientId("integration-client")
                .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(com.datadynecode.financeiro.api.config.auth.PasswordGrantAuthenticationToken.GRANT_TYPE).build());
        var outroServicoJdbc = new org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService(jdbc, clients);
        assertTrue(outroServicoJdbc.findByToken(token, OAuth2TokenType.ACCESS_TOKEN).getAccessToken().isInvalidated());
        assertEquals(401, enviarJson(client, token, "GET", base + "/lancamentos", null).statusCode());
        assertEquals(401, enviarJson(client, token, "POST", base + "/auth/logout", null).statusCode());
        assertEquals(200, enviarJson(client, outroToken, "GET", base + "/lancamentos", null).statusCode());
        var novoLogin = client.send(login, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, novoLogin.statusCode());
        String tokenNovoLogin = JsonMapper.builder().build().readTree(novoLogin.body()).get("access_token").asString();
        assertEquals(200, enviarJson(client, tokenNovoLogin, "GET", base + "/lancamentos", null).statusCode());
    }

    private void validarCategoriaOpcional(HttpClient client, String token) throws Exception {
        String base = "http://localhost:" + port;
        String body = """
            {"tipoLancamento":"DESPESA","descricao":"Sem categoria","valor":12.34,
             "pago":false,"dataVencimento":"2080-01-15"}
            """;
        var criado = enviarJson(client, token, "POST", base + "/lancamentos", body);
        assertEquals(201, criado.statusCode(), criado.body());
        var json = JsonMapper.builder().build().readTree(criado.body());
        long id = json.get("id").asLong();
        assertTrue(json.get("categoriaId").isNull());
        assertNull(jdbc.queryForObject("SELECT categoria_id FROM lancamentos WHERE id=?", Long.class, id));
        var categoria = enviarJson(client, token, "POST", base + "/categorias",
            "{\"nome\":\"Categoria temporaria opcional\",\"tipoLancamento\":\"DESPESA\"}");
        assertEquals(201, categoria.statusCode(), categoria.body());
        long categoriaId = JsonMapper.builder().build().readTree(categoria.body()).get("id").asLong();
        String comCategoria = body.replace("{", "{\"categoriaId\":" + categoriaId + ",");
        assertEquals(200, enviarJson(client, token, "PUT", base + "/lancamentos/" + id, comCategoria).statusCode());
        assertEquals(categoriaId, jdbc.queryForObject("SELECT categoria_id FROM lancamentos WHERE id=?", Long.class, id));
        String semCategoria = body.replace("{", "{\"categoriaId\":null,");
        var atualizado = enviarJson(client, token, "PUT", base + "/lancamentos/" + id, semCategoria);
        assertEquals(200, atualizado.statusCode(), atualizado.body());
        assertTrue(JsonMapper.builder().build().readTree(atualizado.body()).get("categoriaId").isNull());
        assertNull(jdbc.queryForObject("SELECT categoria_id FROM lancamentos WHERE id=?", Long.class, id));
        assertEquals(204, enviarJson(client, token, "DELETE", base + "/categorias/" + categoriaId, null).statusCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM lancamentos WHERE id=?", Integer.class, id));
        var resumo = enviarJson(client, token, "GET", base + "/lancamentos/resumo-mensal?mes=2080-01", null);
        assertEquals(200, resumo.statusCode(), resumo.body());
        var jsonResumo = JsonMapper.builder().build().readTree(resumo.body());
        assertEquals(1, jsonResumo.get("lancamentos").size());
        assertTrue(jsonResumo.get("lancamentos").get(0).get("categoriaId").isNull());
        assertEquals(0, new BigDecimal("12.34").compareTo(jsonResumo.get("totalDespesas").decimalValue()));
        assertEquals(200, enviarJson(client, token, "GET", base + "/lancamentos?size=100", null).statusCode());
    }
}
