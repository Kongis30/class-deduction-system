package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 计划课次响应。
 */
@Builder
@Schema(description = "Lesson occurrence response")
public record LessonOccurrenceVO(
        Long id,
        String lessonNo,
        Long packageId,
        String packageNo,
        String packageName,
        String productName,
        Long classGroupId,
        String className,
        String subjectName,
        Long studentId,
        String studentName,
        Long scheduleRuleId,
        Long scheduleSlotId,
        String occurrenceType,
        Long plannedTeacherId,
        String plannedTeacherName,
        Long plannedClassroomId,
        String plannedClassroomName,
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,
        Integer plannedUnits,
        String planningStatus,
        Long sourceOccurrenceId,
        Boolean conflictChecked,
        String notes,
        String resultStatus,
        String resultStatusName,
        Long lessonRecordId,
        Long recordId,
        String confirmStage,
        LocalDateTime confirmedAt,
        Long confirmedByUserId,
        LocalDateTime teacherConfirmedAt,
        Long teacherConfirmedByUserId,
        LocalDateTime adminConfirmedAt,
        Long adminConfirmedByUserId,
        String rejectReason,
        LocalDateTime rejectedAt,
        Long rejectedByUserId,
        Long actualTeacherId,
        Long actualClassroomId,
        LocalDateTime actualStartAt,
        LocalDateTime actualEndAt,
        Integer actualUnits
) {
}
