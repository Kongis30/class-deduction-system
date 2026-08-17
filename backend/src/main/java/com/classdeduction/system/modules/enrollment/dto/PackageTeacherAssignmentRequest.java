package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课时包老师分配请求。
 */
@Data
@Schema(description = "课时包老师分配请求")
public class PackageTeacherAssignmentRequest {

    @NotNull(message = "Course package is required")
    @Schema(description = "课时包ID", example = "1880000000000000001")
    private Long packageId;

    @NotNull(message = "Teacher is required")
    @Schema(description = "老师ID", example = "1880000000000000002")
    private Long teacherId;

    @Schema(description = "是否为主老师", example = "true")
    private Boolean isPrimary;

    @Positive(message = "Allocated hours must be greater than 0")
    @Schema(description = "Allocated teaching hours", example = "30.0")
    private BigDecimal allocatedHours;

    @PastOrPresent(message = "Effective time cannot be in the future")
    @Schema(description = "生效时间", example = "2026-04-07T09:00:00")
    private LocalDateTime effectiveFrom;

    @Schema(description = "失效时间", example = "2026-07-07T09:00:00")
    private LocalDateTime effectiveTo;

    @Pattern(regexp = "^(INITIAL_ASSIGNMENT|REPLACEMENT|LEAVE|CHANGE|OTHER)$", message = "Change reason is invalid")
    @Schema(description = "变更原因: INITIAL_ASSIGNMENT/REPLACEMENT/LEAVE/CHANGE/OTHER", example = "INITIAL_ASSIGNMENT")
    private String changeReason;
}
