package com.classdeduction.system.modules.lesson.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 管理员撤销最终课次处理的原因和乐观锁版本。 */
@Data
@Schema(description = "Revoke a finalized lesson result")
public class LessonRecordRevokeRequest {

    @Size(max = 1000, message = "Revoke reason must not exceed 1000 characters")
    private String reason;

    @NotNull(message = "Lesson record version is required")
    private Integer expectedVersion;
}
