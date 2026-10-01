package com.classdeduction.system.common.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseTargetGuardTest {

    private Environment environment;
    private ConfigurableListableBeanFactory beanFactory;
    private DatabaseTargetGuard guard;

    @BeforeEach
    void setUp() {
        environment = mock(Environment.class);
        beanFactory = mock(ConfigurableListableBeanFactory.class);
        guard = new DatabaseTargetGuard();
        guard.setEnvironment(environment);
        when(environment.matchesProfiles("group-course-v2")).thenReturn(true);
        when(environment.getProperty("app.database.required-name", "class_deduction_system_v2"))
                .thenReturn("class_deduction_system_v2");
    }

    @Test
    void allowsTheLocalV2DatabaseWithJdbcOptions() {
        when(environment.getProperty("spring.datasource.url", ""))
                .thenReturn("jdbc:mysql://127.0.0.1:3306/class_deduction_system_v2?useSSL=false");

        guard.postProcessBeanFactory(beanFactory);
    }

    @Test
    void rejectsRemoteHostEvenWhenDatabaseNameMatches() {
        when(environment.getProperty("spring.datasource.url", ""))
                .thenReturn("jdbc:mysql://example.com:3306/class_deduction_system_v2");

        assertThatThrownBy(() -> guard.postProcessBeanFactory(beanFactory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("local class_deduction_system_v2");
    }

    @Test
    void rejectsUnexpectedLocalDatabaseName() {
        when(environment.getProperty("spring.datasource.url", ""))
                .thenReturn("jdbc:mysql://localhost:3306/class_deduction_system");

        assertThatThrownBy(() -> guard.postProcessBeanFactory(beanFactory))
                .isInstanceOf(IllegalStateException.class);
    }
}
