package com.classdeduction.system.modules.lesson.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课次确认请求：一次确认实际上课结果，并驱动后续消课台账。
 */
@Data
@Schema(description = "Lesson record confirmation request")
public class LessonRecordConfirmRequest {

    @NotNull(message = "Occurrence ID is required")
    @Schema(description = "Planned occurrence ID", example = "1880000000000000001")
    private Long occurrenceId;

    @NotBlank(message = "Result status is required")
    @Schema(description = "Lesson result status code", example = "COMPLETED")
    private String resultStatus;

    @Schema(description = "Actual teacher ID, defaulting to the planned teacher", example = "1880000000000000002")
    private Long actualTeacherId;

    @Schema(description = "Actual classroom ID, defaulting to the planned classroom", example = "1880000000000000003")
    private Long actualClassroomId;

    @Schema(description = "Actual start time", example = "2026-04-12T18:03:00")
    private LocalDateTime actualStartAt;

    @Schema(description = "Actual end time", example = "2026-04-12T20:00:00")
    private LocalDateTime actualEndAt;

    @Min(value = 0, message = "Actual units must be at least 0")
    @Schema(description = "Actual units where 1 unit = 0.5 hour", example = "4")
    private Integer actualUnits;

    @Schema(description = "Override whether units should be deducted")
    private Boolean overrideShouldDeduct;

    @Min(value = 0, message = "Override deducted units must be at least 0")
    @Schema(description = "Override deducted units")
    private Integer overrideDeductedUnits;

    @Schema(description = "Override whether the package end date should be extended")
    private Boolean overrideShouldExtendEndDate;

    @Schema(description = "Lesson notes")
    private String notes;
}
