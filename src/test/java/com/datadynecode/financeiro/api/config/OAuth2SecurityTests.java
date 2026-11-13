package com.datadynecode.financeiro.api.config;

import com.datadynecode.financeiro.api.config.auth.PasswordGrantAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("invalid_client"));
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

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, AuthorizationServerConfig.class, ResourceServerConfig.class, JwtConfig.class})
    static class TestConfig {
        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(User.withUsername("admin@financeiro.local")
                .password(new BCryptPasswordEncoder().encode("senha-teste")).roles("ADMIN").build());
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
