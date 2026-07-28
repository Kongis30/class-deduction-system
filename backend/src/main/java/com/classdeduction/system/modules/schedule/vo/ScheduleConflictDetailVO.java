package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Structured conflict detail for schedule conflict errors.
 */
@Builder
@Schema(description = "Schedule conflict detail")
public record ScheduleConflictDetailVO(
        @Schema(description = "Resource type: TEACHER/STUDENT/CLASSROOM")
        String resourceType,
        Long resourceId,
        String resourceName,
        LocalDateTime conflictStartAt,
        LocalDateTime conflictEndAt,
        Long existingOccurrenceId,
        String existingLessonNo
) {
}
