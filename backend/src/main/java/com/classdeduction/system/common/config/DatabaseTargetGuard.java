package com.classdeduction.system.common.config;

import java.net.URI;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * V2 profile 的数据库目标保护：在连接池和启动兼容迁移器初始化前检查目标库名。
 */
@Component
public class DatabaseTargetGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(
            org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory
    ) throws BeansException {
        if (!environment.matchesProfiles("group-course-v2")) {
            return;
        }
        String configuredUrl = environment.getProperty("spring.datasource.url", "");
        String url = environment.resolveRequiredPlaceholders(configuredUrl);
        String requiredName = environment.getProperty("app.database.required-name", "class_deduction_system_v2");
        if (!isLocalV2Database(url, requiredName)) {
            throw new IllegalStateException(
                    "The group-course-v2 profile can only start against the local " + requiredName
                            + " database; resolved datasource target is " + describeTarget(url) + "."
            );
        }
    }

    /** 输出不含账号、密码和连接参数的目标摘要，便于定位启动配置问题。 */
    private String describeTarget(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) {
            return "unrecognized JDBC URL";
        }
        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String host = uri.getHost();
            String database = uri.getPath();
            return "host=" + (host == null ? "unknown" : host)
                    + ", database=" + (database == null ? "unknown" : database);
        } catch (IllegalArgumentException ex) {
            return "invalid MySQL URL";
        }
    }

    private boolean isLocalV2Database(String jdbcUrl, String requiredName) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:mysql://")) return false;
        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String host = uri.getHost();
            String databasePath = uri.getPath();
            boolean loopback = "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host);
            return loopback && ("/" + requiredName).equals(databasePath);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
