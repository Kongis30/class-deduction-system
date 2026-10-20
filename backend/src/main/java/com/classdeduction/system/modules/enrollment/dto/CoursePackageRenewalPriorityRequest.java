package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 课包手动置顶操作；true 为置顶，false 为取消置顶。 */
@Schema(description = "Manual renewal priority setting for a course package")
public record CoursePackageRenewalPriorityRequest(
        @NotNull(message = "Pinned state is required.")
        @Schema(description = "Whether this package should remain pinned", example = "true")
        Boolean pinned
) {
}
