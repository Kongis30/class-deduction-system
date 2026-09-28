package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
@Schema(description = "One-on-one deduction lesson row")
public record OneOnOneLessonVO(
        Long occurrenceId,
        Long lessonRecordId,
        String lessonNo,
        Long packageId,
        Long plannedTeacherId,
        String plannedTeacherName,
        String subjectName,
        String deliveryMode,
        Long plannedClassroomId,
        String plannedClassroomName,
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,
        Double plannedUnits,
        String planningStatus,
        String resultStatus,
        String resultStatusName,
        String confirmStage,
        Long actualTeacherId,
        String actualTeacherName,
        Long actualClassroomId,
        String actualClassroomName,
        LocalDateTime actualStartAt,
        LocalDateTime actualEndAt,
        Double actualUnits,
        Double deductedUnits,
        LocalDateTime teacherConfirmedAt,
        LocalDateTime adminConfirmedAt,
        String rejectReason,
        String notes
) {
}
