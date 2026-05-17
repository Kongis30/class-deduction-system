package com.classdeduction.system.modules.lesson.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 老师自助确认请求：限制为老师端允许的状态集合，不暴露后台覆盖字段。
 */
@Data
@Schema(description = "Teacher self lesson confirm request")
public class TeacherLessonConfirmRequest {

    @NotNull(message = "Occurrence ID is required")
    @Schema(description = "Planned occurrence ID", example = "1880000000000000001")
    private Long occurrenceId;

    @NotBlank(message = "Result status is required")
    @Schema(description = "Allowed values: COMPLETED, NO_SHOW, FORMAL_LEAVE", example = "COMPLETED")
    private String resultStatus;

    @Schema(description = "Actual start time", example = "2026-04-12T18:00:00")
    private LocalDateTime actualStartAt;

    @Schema(description = "Actual end time", example = "2026-04-12T20:00:00")
    private LocalDateTime actualEndAt;

    @Schema(description = "Actual units", example = "4")
    private Integer actualUnits;

    @Schema(description = "Notes")
    private String notes;
}
