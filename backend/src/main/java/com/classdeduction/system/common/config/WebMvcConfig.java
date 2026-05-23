package com.classdeduction.system.common.config;

import com.classdeduction.system.modules.auth.config.AuthProperties;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared web-layer configuration.
 * This currently provides CORS support for split frontend and backend development.
 */
@Configuration
public class WebMvcConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.cors")
    public CorsProperties corsProperties() {
        return new CorsProperties();
    }

    @Bean
    public WebMvcConfigurer webMvcConfigurer(
            CorsProperties corsProperties,
            AuthInterceptor authInterceptor,
            AuthProperties authProperties
    ) {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new))
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }

            @Override
            public void addFormatters(FormatterRegistry registry) {
                registry.addConverter(new Converter<String, LocalDateTime>() {
                    @Override
                    public LocalDateTime convert(String source) {
                        if (source == null || source.isBlank()) return null;
                        // 优先尝试无 timezone 格式 (yyyy-MM-ddTHH:mm:ss)
                        try {
                            return LocalDateTime.parse(source, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        } catch (DateTimeParseException ignored) {
                            // 否则尝试 ISO 8601 带 timezone 格式 (如 ...T00:00:00.000Z)
                            return OffsetDateTime.parse(source, DateTimeFormatter.ISO_DATE_TIME)
                                    .toLocalDateTime();
                        }
                    }
                });
            }

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                if (!authProperties.isInterceptorEnabled()) {
                    return;
                }
                registry.addInterceptor(authInterceptor)
                        .addPathPatterns("/v1/**")
                        .excludePathPatterns(
                                "/v1/auth/login",
                                "/health",
                                "/actuator/health",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        );
            }
        };
    }

    public static class CorsProperties {
        private List<String> allowedOrigins = new ArrayList<>();

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
}
