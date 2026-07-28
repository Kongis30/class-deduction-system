package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 管理员在邮件预览后确认发送的续费提醒请求。
 */
@Data
@Schema(description = "Confirmed renewal reminder send request")
public class RenewalReminderSendRequest {

    @NotEmpty(message = "Select at least one renewal reminder.")
    @Schema(description = "Selected reminder IDs returned by the renewal reminder overview")
    private List<String> reminderIds;

    @NotNull(message = "Email preview confirmation is required.")
    @Schema(description = "Must be true after the administrator reviews the prepared emails", example = "true")
    private Boolean previewConfirmed;
}
