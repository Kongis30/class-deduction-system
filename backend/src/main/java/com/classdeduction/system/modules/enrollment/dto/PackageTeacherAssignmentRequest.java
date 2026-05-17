package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课时包老师分配请求。
 */
@Data
@Schema(description = "课时包老师分配请求")
public class PackageTeacherAssignmentRequest {

    @NotNull(message = "课时包ID不能为空")
    @Schema(description = "课时包ID", example = "1880000000000000001")
    private Long packageId;

    @NotNull(message = "老师ID不能为空")
    @Schema(description = "老师ID", example = "1880000000000000002")
    private Long teacherId;

    @Schema(description = "是否为主老师", example = "true")
    private Boolean isPrimary;

    @PastOrPresent(message = "生效时间不能是未来时间")
    @Schema(description = "生效时间", example = "2026-04-07T09:00:00")
    private LocalDateTime effectiveFrom;

    @Schema(description = "失效时间", example = "2026-07-07T09:00:00")
    private LocalDateTime effectiveTo;

    @Pattern(regexp = "^(INITIAL_ASSIGNMENT|REPLACEMENT|LEAVE|CHANGE|OTHER)$", message = "变更原因必须是有效值")
    @Schema(description = "变更原因: INITIAL_ASSIGNMENT/REPLACEMENT/LEAVE/CHANGE/OTHER", example = "INITIAL_ASSIGNMENT")
    private String changeReason;
}
