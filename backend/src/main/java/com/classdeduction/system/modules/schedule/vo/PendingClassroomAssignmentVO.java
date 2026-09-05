package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Pending classroom assignment item shown in the classroom backfill queue.
 */
@Builder
@Schema(description = "Pending classroom assignment")
public record PendingClassroomAssignmentVO(
        String targetType,
        Long targetId,
        Long occurrenceId,
        String lessonNo,
        Long classGroupId,
        Long productId,
        String productName,
        String className,
        String gradeBand,
        String actualClass,
        Long packageId,
        String packageName,
        Long studentId,
        String studentName,
        Long teacherId,
        String teacherName,
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,
        Integer plannedUnits,
        String scheduleDesc
) {
}
