package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 教室占用响应。
 */
@Builder
@Schema(description = "Classroom occupancy response")
public record ClassroomOccupancyVO(
        Long occurrenceId,
        String lessonNo,
        Long classroomId,
        String classroomName,
        Long teacherId,
        String teacherName,
        Long packageId,
        String packageNo,
        String packageName,
        Long studentId,
        String studentName,
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,
        Integer plannedUnits,
        String planningStatus
) {
}
