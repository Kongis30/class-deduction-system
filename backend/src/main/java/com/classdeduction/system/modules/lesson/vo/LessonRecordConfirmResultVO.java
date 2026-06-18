package com.classdeduction.system.modules.lesson.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 课次确认结果。
 */
@Builder
@Schema(description = "Lesson confirmation result")
public record LessonRecordConfirmResultVO(
        Long lessonRecordId,
        Long occurrenceId,
        Long ledgerId,
        Double deductedUnits,
        Double remainingUnits,
        Boolean packageCompleted
) {
}
