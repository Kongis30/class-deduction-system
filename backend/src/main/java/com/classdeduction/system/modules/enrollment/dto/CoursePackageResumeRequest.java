package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "Resume paused 1-on-1 package request")
public class CoursePackageResumeRequest {

    @NotNull(message = "Start date is required")
    @Schema(description = "New date to resume scheduled lessons", example = "2026-09-01")
    private LocalDate startDate;
}
