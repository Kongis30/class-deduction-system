package com.classdeduction.system.modules.systemconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 通知模板更新请求：不允许修改 templateCode，避免前后端约定失效。
 */
@Data
@Schema(description = "Notification template update request")
public class NotificationTemplateUpdateRequest {

    @NotBlank(message = "Template name is required")
    @Size(max = 128, message = "Template name must not exceed 128 characters")
    @Schema(description = "Template name")
    private String templateName;

    @NotBlank(message = "Subject template is required")
    @Size(max = 255, message = "Subject template must not exceed 255 characters")
    @Schema(description = "Subject template")
    private String subjectTemplate;

    @NotBlank(message = "Body template is required")
    @Schema(description = "Body template")
    private String bodyTemplate;

    @Schema(description = "Template status", example = "ACTIVE")
    private String status;

    @Schema(description = "Remark")
    private String remark;
}
