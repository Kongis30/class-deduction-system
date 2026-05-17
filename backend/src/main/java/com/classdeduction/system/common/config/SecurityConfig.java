package com.classdeduction.system.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security stays lightweight for now.
 * The first phase keeps core endpoints open, and later phases can centralize auth here.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.security")
    public SecurityProperties securityProperties() {
        return new SecurityProperties();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties securityProperties) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults());

        if (!securityProperties.isEnabled()) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            // 全部放通，由 AuthInterceptor 在应用层做 token 校验
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    public static class SecurityProperties {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
