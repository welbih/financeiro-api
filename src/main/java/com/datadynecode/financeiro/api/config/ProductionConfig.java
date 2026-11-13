package com.datadynecode.financeiro.api.config;

import java.net.URI;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
@Profile("prod")
public class ProductionConfig {

    public ProductionConfig(Environment environment, @Value("${jwt.issuer}") String issuer,
            @Value("${cors.origins}") String origins, @Value("${security.client-secret}") String secret) {
        validate(environment, issuer, origins, secret);
    }

    @Bean
    public static BeanFactoryPostProcessor validateBeforeDatabaseInitialization(Environment environment) {
        return beanFactory -> validate(environment, environment.getRequiredProperty("jwt.issuer"),
            environment.getRequiredProperty("cors.origins"), environment.getRequiredProperty("security.client-secret"));
    }

    private static void validate(Environment environment, String issuer, String origins, String secret) {
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new IllegalStateException("Os profiles prod e dev nao podem ser ativados juntos");
        }
        requireHttps(issuer);
        if (secret.length() < 32) {
            throw new IllegalArgumentException("CLIENT_SECRET deve ter ao menos 32 caracteres em producao");
        }
        if (origins.isBlank()) {
            throw new IllegalArgumentException("CORS_ORIGINS deve conter origens HTTPS explicitas");
        }
        Arrays.stream(origins.split(",", -1)).map(String::trim).forEach(origin -> {
            URI uri = requireHttps(origin);
            if (uri.getRawPath() != null && !uri.getRawPath().isEmpty()) {
                throw new IllegalArgumentException("Origens CORS nao devem conter caminhos ou barra final");
            }
        });
    }

    private static URI requireHttps(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("JWT_ISSUER e CORS_ORIGINS devem usar URLs HTTPS validas");
        }
        return uri;
    }
}
