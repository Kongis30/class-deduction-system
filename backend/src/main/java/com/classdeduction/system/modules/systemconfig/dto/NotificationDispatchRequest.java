package com.classdeduction.system.modules.systemconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 通知分发请求：由管理员手动触发一批 pending 邮件日志。
 */
@Data
@Schema(description = "Notification dispatch request")
public class NotificationDispatchRequest {

    @Min(value = 1, message = "Batch size must be at least 1")
    @Max(value = 100, message = "Batch size must not exceed 100")
    @Schema(description = "Requested batch size", example = "20")
    private Integer batchSize = 20;
}
