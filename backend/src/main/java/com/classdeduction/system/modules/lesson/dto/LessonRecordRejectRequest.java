package com.classdeduction.system.modules.lesson.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员驳回老师初确认请求。
 */
@Data
@Schema(description = "Lesson record reject request")
public class LessonRecordRejectRequest {

    @NotBlank(message = "Reject reason is required")
    @Schema(description = "Reject reason", example = "Actual duration needs correction.")
    private String rejectReason;
}
