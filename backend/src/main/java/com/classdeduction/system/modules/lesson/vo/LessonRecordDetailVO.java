package com.classdeduction.system.modules.lesson.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 实际课次记录详情。
 */
@Builder
@Schema(description = "Lesson record detail")
public record LessonRecordDetailVO(
        Long id,
        Long occurrenceId,
        String lessonNo,
        Long packageId,
        String packageNo,
        Long studentId,
        String studentName,
        Long plannedTeacherId,
        String plannedTeacherName,
        Long actualTeacherId,
        String actualTeacherName,
        Long plannedClassroomId,
        String plannedClassroomName,
        Long actualClassroomId,
        String actualClassroomName,
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,
        Double plannedUnits,
        String resultStatus,
        LocalDateTime actualStartAt,
        LocalDateTime actualEndAt,
        Double actualUnits,
        Boolean shouldDeduct,
        Double deductedUnits,
        Boolean shouldExtendEndDate,
        String confirmStage,
        Long confirmedByUserId,
        LocalDateTime confirmedAt,
        LocalDateTime teacherConfirmedAt,
        Long teacherConfirmedByUserId,
        LocalDateTime adminConfirmedAt,
        Long adminConfirmedByUserId,
        String rejectReason,
        LocalDateTime rejectedAt,
        Long rejectedByUserId,
        String recordSource,
        String notes
) {
}
