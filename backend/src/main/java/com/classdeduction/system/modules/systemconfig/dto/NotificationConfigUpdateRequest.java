package com.classdeduction.system.modules.systemconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 邮件发送方配置更新请求。
 */
@Data
@Schema(description = "Notification config update request")
public class NotificationConfigUpdateRequest {

    @Schema(description = "Enable email sending")
    private Boolean enabled;

    @NotBlank(message = "From address is required")
    @Schema(description = "SMTP sender email address")
    private String fromAddress;

    @NotBlank(message = "From name is required")
    @Schema(description = "SMTP sender display name")
    private String fromName;

    @NotBlank(message = "SMTP authorization code is required")
    @Schema(description = "SMTP authorization code")
    private String authCode;

    @Schema(description = "SMTP server hostname (e.g. smtp.qq.com)")
    private String smtpHost;

    @Schema(description = "SMTP server port (e.g. 587)")
    private Integer smtpPort;

    @Schema(description = "SMTP login username/account")
    private String smtpUsername;

    @Schema(description = "SMTP login password or authorization code")
    private String smtpPassword;

    @Schema(description = "Strict allowlist mode: true=only allowed recipients, false=allow all")
    private Boolean strictAllowlist;

    @Schema(description = "Allowed recipients JSON array string")
    private String allowedRecipients;

    @Min(value = 1, message = "Threshold must be at least 1 unit (0.5h)")
    @Schema(description = "Low balance threshold in 0.5h units (e.g. 2=1h)")
    private Integer lowBalanceThreshold;

    @Schema(description = "Low balance notification recipients JSON array string")
    private String lowBalanceRecipients;
}