package com.classdeduction.system.common.config;

import com.classdeduction.system.modules.auth.config.AuthProperties;
import com.classdeduction.system.modules.auth.interceptor.AuthInterceptor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
