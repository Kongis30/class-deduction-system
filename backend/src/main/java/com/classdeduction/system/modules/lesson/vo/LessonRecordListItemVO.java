package com.classdeduction.system.modules.lesson.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 实际课次记录列表项。
 */
@Builder
@Schema(description = "Lesson record list item")
public record LessonRecordListItemVO(
        Long id,
        Long occurrenceId,
        String lessonNo,
        Long packageId,
        String packageNo,
        String packageName,
        Long studentId,
        String studentName,
        String resultStatus,
        Long actualTeacherId,
        String actualTeacherName,
        Long actualClassroomId,
        String actualClassroomName,
        LocalDateTime actualStartAt,
        LocalDateTime actualEndAt,
        Double actualUnits,
        Boolean shouldDeduct,
        Double deductedUnits,
        Boolean shouldExtendEndDate,
        String confirmStage,
        LocalDateTime teacherConfirmedAt,
        Long teacherConfirmedByUserId,
        LocalDateTime adminConfirmedAt,
        Long adminConfirmedByUserId,
        String rejectReason,
        LocalDateTime rejectedAt,
        Long rejectedByUserId,
        String recordSource,
        LocalDateTime confirmedAt,
        String notes
) {
}
