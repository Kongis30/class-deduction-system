package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 续费提醒工作台的简单阈值设置。
 *
 * <p>业务人员只需理解“提前多少天”和“还剩几节课”，不需要进入邮件触发规则配置。</p>
 */
@Data
@Schema(description = "Renewal reminder settings")
public class RenewalReminderSettingsUpdateRequest {

    @NotNull(message = "Expiry reminder days are required.")
    @Min(value = 1, message = "Expiry reminder days must be at least 1.")
    @Max(value = 180, message = "Expiry reminder days must not exceed 180.")
    @Schema(description = "Show expiry reminders this many days before the expected end date", example = "30")
    private Integer expiryReminderDays;

    @NotNull(message = "Remaining lessons threshold is required.")
    @Min(value = 1, message = "Remaining lessons threshold must be at least 1.")
    @Max(value = 20, message = "Remaining lessons threshold must not exceed 20.")
    @Schema(description = "Show one-on-one packages with this many lessons or fewer remaining", example = "2")
    private Integer oneOnOneRemainingLessons;
}
