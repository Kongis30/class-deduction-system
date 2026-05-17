package com.classdeduction.system.modules.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Authentication module properties.
 */
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private long sessionTtlHours = 12;

    private boolean interceptorEnabled = true;

    public long getSessionTtlHours() {
        return sessionTtlHours;
    }

    public void setSessionTtlHours(long sessionTtlHours) {
        this.sessionTtlHours = sessionTtlHours;
    }

    public boolean isInterceptorEnabled() {
        return interceptorEnabled;
    }

    public void setInterceptorEnabled(boolean interceptorEnabled) {
        this.interceptorEnabled = interceptorEnabled;
    }
}
