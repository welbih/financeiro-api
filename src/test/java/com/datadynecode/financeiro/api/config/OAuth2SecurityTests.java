package com.datadynecode.financeiro.api.config;

import com.datadynecode.financeiro.api.config.auth.PasswordGrantAuthenticationToken;
import com.datadynecode.financeiro.api.controller.UsuarioController;
import com.datadynecode.financeiro.api.controller.LancamentoController;
import com.datadynecode.financeiro.api.controller.CategoriaController;
import com.datadynecode.financeiro.api.controller.AutenticacaoController;
import com.datadynecode.financeiro.api.service.LogoutService;
import com.datadynecode.financeiro.api.service.CategoriaService;
import com.datadynecode.financeiro.api.dto.CategoriaRequest;
import com.datadynecode.financeiro.api.dto.CategoriaResponse;
import com.datadynecode.financeiro.api.service.LancamentoService;
import com.datadynecode.financeiro.api.dto.LancamentoResponse;
import com.datadynecode.financeiro.api.dto.UsuarioResponse;
import com.datadynecode.financeiro.api.exception.ApiExceptionHandler;
import com.datadynecode.financeiro.api.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = OAuth2SecurityTests.TestConfig.class)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "security.client-id=test-client", "security.client-secret=test-secret", "jwt.duration=600",
    "jwt.issuer=http://localhost", "cors.origins=http://localhost:5173"
})
class OAuth2SecurityTests {

    @Autowired private WebApplicationContext context;
    @Autowired private JwtDecoder decoder;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void emiteJwtComRolesEPermiteAcessoAutenticado() throws Exception {
        String body = mvc.perform(post("/oauth2/token").with(httpBasic("test-client", "test-secret"))
                .param("grant_type", PasswordGrantAuthenticationToken.GRANT_TYPE.getValue())
                .param("username", "admin@financeiro.local").param("password", "senha-teste"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.expires_in").isNumber()).andReturn().getResponse().getContentAsString();
        String token = JsonMapper.builder().build().readTree(body).get("access_token").asString();
        var jwt = decoder.decode(token);
        assertEquals("admin@financeiro.local", jwt.getSubject());
        assertEquals("Maria", jwt.getClaimAsString("nome"));
        assertEquals("Silva", jwt.getClaimAsString("sobrenome"));
        assertEquals(java.util.List.of("ROLE_ADMIN"), jwt.getClaimAsStringList("authorities"));
        assertEquals(600, java.time.Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toSeconds());
        mvc.perform(get("/api/test").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(content().string("admin@financeiro.local:true"));
        mvc.perform(get("/api/test").header("Authorization", "Bearer " + token + "alterado"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejeitaSenhaIncorreta() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("test-client", "test-secret"))
                .param("grant_type", PasswordGrantAuthenticationToken.GRANT_TYPE.getValue())
                .param("username", "admin@financeiro.local").param("password", "errada"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void rejeitaClienteIncorreto() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("test-client", "errado"))
                .param("grant_type", PasswordGrantAuthenticationToken.GRANT_TYPE.getValue())
                .param("username", "admin@financeiro.local").param("password", "senha-teste"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_client"))
            .andExpect(header().exists("X-Auth-Error-Id"))
            .andExpect(jsonPath("$.error_description").doesNotExist());
    }

    @Test
    void diagnosticaClienteDesconhecido() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("cliente-ausente", "segredo"))
                .param("grant_type", PasswordGrantAuthenticationToken.GRANT_TYPE.getValue())
                .param("username", "admin@financeiro.local").param("password", "senha-teste"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_client"))
            .andExpect(header().exists("X-Auth-Error-Id"));
    }

    @Test
    void diagnosticaBasicAusente() throws Exception {
        mvc.perform(post("/oauth2/token")
                .param("grant_type", PasswordGrantAuthenticationToken.GRANT_TYPE.getValue())
                .param("username", "admin@financeiro.local").param("password", "senha-teste"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_client"))
            .andExpect(header().exists("X-Auth-Error-Id"));
    }

    @Test
    void segredoComMaisExigeCodificacaoFormAntesDoBasic() throws Exception {
        var clients = context.getBean(org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository.class);
        var encoder = context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class);
        String secret = "segredo+com/barra=padding";
        clients.save(org.springframework.security.oauth2.server.authorization.client.RegisteredClient
            .withId("special-client").clientId("special-client").clientSecret(encoder.encode(secret))
            .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(PasswordGrantAuthenticationToken.GRANT_TYPE).scope("api.read").build());

        mvc.perform(post("/oauth2/token").with(httpBasic("special-client", secret))
                .param("grant_type", PasswordGrantAuthenticationToken.GRANT_TYPE.getValue())
                .param("username", "admin@financeiro.local").param("password", "senha-teste"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_client"));

        String encoded = java.net.URLEncoder.encode(secret, java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(post("/oauth2/token").with(httpBasic("special-client", encoded))
                .param("grant_type", PasswordGrantAuthenticationToken.GRANT_TYPE.getValue())
                .param("username", "admin@financeiro.local").param("password", "senha-teste"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").isString());
    }

    @Test
    void rejeitaScopeNaoRegistrado() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("test-client", "test-secret"))
                .param("grant_type", PasswordGrantAuthenticationToken.GRANT_TYPE.getValue())
                .param("username", "admin@financeiro.local").param("password", "senha-teste")
                .param("scope", "admin"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_scope"));
    }

    @Test
    void rejeitaParametrosDuplicados() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("test-client", "test-secret"))
                .param("grant_type", PasswordGrantAuthenticationToken.GRANT_TYPE.getValue())
                .param("username", "admin@financeiro.local", "outro").param("password", "senha-teste"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void bloqueiaRequisicaoSemToken() throws Exception {
        mvc.perform(get("/api/test")).andExpect(status().isUnauthorized());
    }

    @Test
    void permitePreflightDaOrigemConfigurada() throws Exception {
        mvc.perform(options("/oauth2/token").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    private static final String CADASTRO = """
        {"nome":"Maria","sobrenome":"Silva","email":"maria@example.com",
         "senha":"SenhaForte123!","roles":["ROLE_ADMIN"]}
        """;

    private static final String CADASTRO_LANCAMENTO = """
        {"tipoLancamento":"DESPESA","descricao":"Energia","valor":150.25,
         "pago":false,"dataVencimento":"2026-12-10","categoriaId":30}
        """;

    @Test
    void cadastroLancamentoExigeToken() throws Exception {
        mvc.perform(post("/lancamentos").contentType("application/json").content(CADASTRO_LANCAMENTO))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void cadastroLancamentoUsaSubjectDoTokenEIgnoraUsuarioDoBody() throws Exception {
        var service = context.getBean(LancamentoService.class);
        org.mockito.Mockito.clearInvocations(service);
        org.mockito.Mockito.when(service.cadastrar(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("dono@example.com")))
            .thenReturn(new LancamentoResponse(20L, com.datadynecode.financeiro.api.model.TipoLancamento.DESPESA,
                10L, "Energia", new java.math.BigDecimal("150.25"), false,
                java.time.LocalDate.of(2026, 12, 10), null, null, 30L));
        String json = CADASTRO_LANCAMENTO.replace("{", "{\"usuarioId\":999,\"usuario\":{\"id\":999},");
        mvc.perform(post("/lancamentos").with(jwt().jwt(token -> token.subject("dono@example.com")))
                .contentType("application/json").content(json))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.usuarioId").value(10))
            .andExpect(jsonPath("$.usuario").doesNotExist());
        org.mockito.Mockito.verify(service).cadastrar(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.eq("dono@example.com"));
    }

    @Test
    void cadastroLancamentoValidaValorEDatas() throws Exception {
        mvc.perform(post("/lancamentos").with(jwt())
                .contentType("application/json").content(CADASTRO_LANCAMENTO.replace("150.25", "-10")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/lancamentos").with(jwt())
                .contentType("application/json").content(CADASTRO_LANCAMENTO.replace("2026-12-10", "data-invalida")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void operacoesLancamentoExigemToken() throws Exception {
        mvc.perform(get("/lancamentos")).andExpect(status().isUnauthorized());
        mvc.perform(put("/lancamentos/20").contentType("application/json").content(CADASTRO_LANCAMENTO))
            .andExpect(status().isUnauthorized());
        mvc.perform(delete("/lancamentos/20")).andExpect(status().isUnauthorized());
    }

    @Test
    void listagemUsaUsuarioDoJwtEValidaPaginacao() throws Exception {
        var service = context.getBean(LancamentoService.class);
        org.mockito.Mockito.when(service.listar("dono@example.com", 0, 20)).thenReturn(
            new com.datadynecode.financeiro.api.dto.LancamentoPageResponse(java.util.List.of(), 0, 20, 0, 0));
        mvc.perform(get("/lancamentos").with(jwt().jwt(token -> token.subject("dono@example.com"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
        mvc.perform(get("/lancamentos?page=-1").with(jwt())).andExpect(status().isBadRequest());
        mvc.perform(get("/lancamentos?size=101").with(jwt())).andExpect(status().isBadRequest());
    }

    @Test
    void atualizacaoERemocaoUsamIdEUsuarioAutenticado() throws Exception {
        var service = context.getBean(LancamentoService.class);
        org.mockito.Mockito.clearInvocations(service);
        mvc.perform(put("/lancamentos/20").with(jwt().jwt(token -> token.subject("dono@example.com")))
                .contentType("application/json").content(CADASTRO_LANCAMENTO))
            .andExpect(status().isOk());
        org.mockito.Mockito.verify(service).atualizar(org.mockito.ArgumentMatchers.eq(20L),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("dono@example.com"));
        mvc.perform(delete("/lancamentos/20").with(jwt().jwt(token -> token.subject("dono@example.com"))))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        org.mockito.Mockito.verify(service).remover(20L, "dono@example.com");
    }

    @Test
    void retorna404SemRevelarLancamentoDeOutroUsuario() throws Exception {
        var service = context.getBean(LancamentoService.class);
        org.mockito.Mockito.doThrow(new com.datadynecode.financeiro.api.exception.LancamentoNaoEncontradoException())
            .when(service).remover(999L, "dono@example.com");
        mvc.perform(delete("/lancamentos/999").with(jwt().jwt(token -> token.subject("dono@example.com"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void resumoMensalExigeToken() throws Exception {
        mvc.perform(get("/lancamentos/resumo-mensal?mes=2026-12")).andExpect(status().isUnauthorized());
    }

    @Test
    void resumoMensalUsaMesEUsuarioAutenticado() throws Exception {
        var service = context.getBean(LancamentoService.class);
        org.mockito.Mockito.when(service.resumirMes("dono@example.com", java.time.YearMonth.of(2026, 12)))
            .thenReturn(new com.datadynecode.financeiro.api.dto.ResumoMensalLancamentosResponse(
                "2026-12", new java.math.BigDecimal("0.00"), new java.math.BigDecimal("0.00"),
                new java.math.BigDecimal("0.00"), java.util.List.of()));
        mvc.perform(get("/lancamentos/resumo-mensal?mes=2026-12")
                .with(jwt().jwt(token -> token.subject("dono@example.com"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mes").value("2026-12"))
            .andExpect(jsonPath("$.totalDespesas").value(0))
            .andExpect(jsonPath("$.totalReceitas").value(0))
            .andExpect(jsonPath("$.diferenca").value(0))
            .andExpect(jsonPath("$.lancamentos").isArray());
        org.mockito.Mockito.verify(service).resumirMes("dono@example.com", java.time.YearMonth.of(2026, 12));
    }

    @Test
    void resumoMensalExigeMesComAnoValido() throws Exception {
        mvc.perform(get("/lancamentos/resumo-mensal").with(jwt())).andExpect(status().isBadRequest());
        mvc.perform(get("/lancamentos/resumo-mensal?mes=12").with(jwt())).andExpect(status().isBadRequest());
        mvc.perform(get("/lancamentos/resumo-mensal?mes=2026-13").with(jwt())).andExpect(status().isBadRequest());
    }

    private static final String CADASTRO_CATEGORIA = """
        {"nome":"Alimentacao","tipoLancamento":"DESPESA","usuarioId":999}
        """;

    @Test
    void crudCategoriasExigeToken() throws Exception {
        mvc.perform(post("/categorias").contentType("application/json").content(CADASTRO_CATEGORIA))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/categorias")).andExpect(status().isUnauthorized());
        mvc.perform(get("/categorias/30")).andExpect(status().isUnauthorized());
        mvc.perform(put("/categorias/30").contentType("application/json").content(CADASTRO_CATEGORIA))
            .andExpect(status().isUnauthorized());
        mvc.perform(delete("/categorias/30")).andExpect(status().isUnauthorized());
    }

    @Test
    void cadastraCategoriaParaUsuarioDoJwt() throws Exception {
        var service = context.getBean(CategoriaService.class);
        org.mockito.Mockito.when(service.cadastrar(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("dono@example.com")))
            .thenReturn(new CategoriaResponse(30L, "Alimentacao",
                com.datadynecode.financeiro.api.model.TipoLancamento.DESPESA, 10L));
        mvc.perform(post("/categorias").with(jwt().jwt(token -> token.subject("dono@example.com")))
                .contentType("application/json").content(CADASTRO_CATEGORIA))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.usuarioId").value(10))
            .andExpect(jsonPath("$.usuario").doesNotExist());
    }

    @Test
    void listaCategoriasComFiltroDeTipo() throws Exception {
        var service = context.getBean(CategoriaService.class);
        org.mockito.Mockito.when(service.listar("dono@example.com",
                com.datadynecode.financeiro.api.model.TipoLancamento.DESPESA, 0, 20))
            .thenReturn(new com.datadynecode.financeiro.api.dto.CategoriaPageResponse(java.util.List.of(), 0, 20, 0, 0));
        mvc.perform(get("/categorias?tipoLancamento=DESPESA")
                .with(jwt().jwt(token -> token.subject("dono@example.com"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
        mvc.perform(get("/categorias?size=101").with(jwt())).andExpect(status().isBadRequest());
    }

    @Test
    void categoriasValidamNomeETipoECategoriaOpcionalNoLancamento() throws Exception {
        mvc.perform(post("/categorias").with(jwt()).contentType("application/json")
                .content("{\"nome\":\" \",\"tipoLancamento\":\"DESPESA\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/categorias").with(jwt()).contentType("application/json")
                .content("{\"nome\":\"Casa\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/lancamentos").with(jwt()).contentType("application/json")
                .content(CADASTRO_LANCAMENTO.replace(",\"categoriaId\":30", "")))
            .andExpect(status().isCreated());
        mvc.perform(post("/lancamentos").with(jwt()).contentType("application/json")
                .content(CADASTRO_LANCAMENTO.replace("\"categoriaId\":30", "\"categoriaId\":null")))
            .andExpect(status().isCreated());
        mvc.perform(post("/lancamentos").with(jwt()).contentType("application/json")
                .content(CADASTRO_LANCAMENTO.replace("\"categoriaId\":30", "\"categoriaId\":0")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void logoutExigeBearerToken() throws Exception {
        mvc.perform(post("/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer token-invalido"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevogaJwtEPermiteNovoLogin() throws Exception {
        String token = emitirToken();
        String outraSessao = emitirToken();
        assertNotEquals(token, outraSessao);
        mvc.perform(get("/api/test").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"token\":\"ignorado\"}"))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/test").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/test").header("Authorization", "Bearer " + outraSessao)).andExpect(status().isOk());
        mvc.perform(get("/api/test").header("Authorization", "Bearer " + emitirToken())).andExpect(status().isOk());
    }

    @Test
    void revogacaoOAuthTambemInvalidaJwtNosRecursos() throws Exception {
        String token = emitirToken();
        mvc.perform(post("/oauth2/revoke").with(httpBasic("test-client", "test-secret"))
                .param("token", token).param("token_type_hint", "access_token"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/test").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    private String emitirToken() throws Exception {
        String body = mvc.perform(post("/oauth2/token").with(httpBasic("test-client", "test-secret"))
                .param("grant_type", PasswordGrantAuthenticationToken.GRANT_TYPE.getValue())
                .param("username", "admin@financeiro.local").param("password", "senha-teste"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(body).get("access_token").asString();
    }

    @Test
    void categoriaEmUsoRetornaConflitoECategoriaAlheiaRetorna404() throws Exception {
        var service = context.getBean(CategoriaService.class);
        org.mockito.Mockito.doThrow(new com.datadynecode.financeiro.api.exception.CategoriaEmConflitoException("Categoria em uso"))
            .when(service).remover(31L, "dono@example.com");
        org.mockito.Mockito.when(service.buscar(999L, "dono@example.com"))
            .thenThrow(new com.datadynecode.financeiro.api.exception.CategoriaNaoEncontradaException());
        mvc.perform(delete("/categorias/31").with(jwt().jwt(token -> token.subject("dono@example.com"))))
            .andExpect(status().isConflict());
        mvc.perform(get("/categorias/999").with(jwt().jwt(token -> token.subject("dono@example.com"))))
            .andExpect(status().isNotFound());
    }

    @Test
    void cadastroPermiteAcessoSemToken() throws Exception {
        mvc.perform(post("/usuarios").contentType("application/json").content(CADASTRO))
            .andExpect(status().isCreated());
    }

    @Test
    void cadastroPermiteUsuarioSemRoleAdmin() throws Exception {
        mvc.perform(post("/usuarios").with(jwt().authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))
                .contentType("application/json").content(CADASTRO))
            .andExpect(status().isCreated());
    }

    @Test
    void administradorCadastraSemExporSenha() throws Exception {
        var service = context.getBean(UsuarioService.class);
        org.mockito.Mockito.when(service.cadastrar(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new UsuarioResponse(10L, "Maria", "Silva", "maria@example.com", java.util.Set.of("ROLE_ADMIN")));
        mvc.perform(post("/usuarios").with(jwt().authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json").content(CADASTRO))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"))
            .andExpect(jsonPath("$.senha").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void cadastroValidaEntradaSemExporSenhaNoErro() throws Exception {
        mvc.perform(post("/usuarios")
                .contentType("application/json").content(CADASTRO.replace("SenhaForte123!", "curta")))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("curta"))));
    }

    @TestConfiguration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, AuthorizationServerConfig.class, ResourceServerConfig.class, JwtConfig.class,
        UsuarioController.class, LancamentoController.class, CategoriaController.class, AutenticacaoController.class,
        LogoutService.class, ApiExceptionHandler.class})
    static class TestConfig {
        @Bean
        UsuarioService usuarioService() { return org.mockito.Mockito.mock(UsuarioService.class); }

        @Bean
        LancamentoService lancamentoService() { return org.mockito.Mockito.mock(LancamentoService.class); }

        @Bean
        CategoriaService categoriaService() { return org.mockito.Mockito.mock(CategoriaService.class); }

        @Bean
        UserDetailsService userDetailsService() {
            var usuario = new com.datadynecode.financeiro.api.model.Usuario();
            usuario.setNome("Maria");
            usuario.setSobrenome("Silva");
            usuario.setEmail("admin@financeiro.local");
            usuario.setSenha(new BCryptPasswordEncoder().encode("senha-teste"));
            var role = new com.datadynecode.financeiro.api.model.Role();
            role.setAuthority("ROLE_ADMIN");
            usuario.getRoles().adicionar(role);
            return username -> {
                if (!usuario.getUsername().equals(username)) {
                    throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Usuario nao encontrado");
                }
                return usuario;
            };
        }

        @Bean
        TestController testController() { return new TestController(); }
    }

    @RestController
    static class TestController {
        @GetMapping("/api/test")
        String authenticated(Authentication authentication) {
            return authentication.getName() + ":" + authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        }
    }
}
