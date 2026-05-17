package com.classdeduction.system.modules.systemconfig.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件分发配置：默认关闭发送，并通过白名单和批量上限保护误发风险。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.notification.email")
public class NotificationEmailProperties {

    private boolean enabled = false;

    private String fromAddress;

    private String fromName;

    private boolean strictAllowlist = true;

    private List<String> allowedRecipients = new ArrayList<>();

    @Min(value = 1, message = "Max batch size must be at least 1")
    @Max(value = 200, message = "Max batch size must be at most 200")
    private int maxBatchSize = 20;

    @Min(value = 0, message = "Max retries must not be negative")
    @Max(value = 20, message = "Max retries must be at most 20")
    private int maxRetries = 5;
}
