package com.datadynecode.financeiro.api.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.datadynecode.financeiro.api.config.auth.OAuth2AuthenticationFailureHandler;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.junit.jupiter.api.Assertions.*;

class OAuth2AuthenticationFailureHandlerTests {
    @Test
    void identificaFalhaDoSegredoSemExporCredenciais() throws Exception {
        verifyReason("Client authentication failed: client_secret", "CLIENT_SECRET_NAO_CONFERE", true);
    }

    @Test
    void identificaClienteDesconhecido() throws Exception {
        verifyReason("Client authentication failed: client_id", "CLIENT_ID_NAO_ENCONTRADO", true);
    }

    @Test
    void identificaBasicAusente() throws Exception {
        verifyReason(null, "BASIC_AUTH_AUSENTE_OU_INCORRETO", false);
    }

    @Test
    void naoRegistraDescricaoLivreDaExcecao() throws Exception {
        verifyReason("credencial-super-secreta", "AUTENTICACAO_REJEITADA", true);
    }

    private void verifyReason(String description, String reason, boolean basic) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            if (basic) {
                request.addHeader("Authorization", "Basic credencial-super-secreta");
            }
            request.setParameter("password", "senha-do-usuario");
            MockHttpServletResponse response = new MockHttpServletResponse();
            new OAuth2AuthenticationFailureHandler().onAuthenticationFailure(request, response,
                new OAuth2AuthenticationException(new OAuth2Error("invalid_client", description, null)));

            assertEquals(401, response.getStatus());
            String reference = response.getHeader("X-Auth-Error-Id");
            assertNotNull(reference);
            assertEquals("{\"error\":\"invalid_client\"}", response.getContentAsString());
            String log = appender.list.getFirst().getFormattedMessage();
            assertTrue(log.contains(reason));
            assertTrue(log.contains(reference));
            assertFalse(log.contains("credencial-super-secreta"));
            assertFalse(log.contains("senha-do-usuario"));
        }
        finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
