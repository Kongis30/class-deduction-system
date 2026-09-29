package com.classdeduction.system.modules.base.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Course price option response payload.
 */
@Builder
@Schema(description = "Course price option response payload")
public record CoursePriceOptionVO(
        Long id,
        String optionCode,
        String courseType,
        String registerClass,
        String gradeBand,
        String periodLabel,
        String durationLabel,
        String timeSlotLabel,
        String detailText,
        String packageUnit,
        BigDecimal packageQuantity,
        BigDecimal totalPrice,
        BigDecimal derivedUnitPrice,
        BigDecimal fullDayPrice,
        BigDecimal halfDayPrice,
        BigDecimal oneOnOneHours,
        BigDecimal priceAmount,
        BigDecimal groupTotalPrice,
        BigDecimal groupSessionPrice,
        String currencyCode,
        Long courseProductId,
        Long classId,
        List<ActualClassVO> actualClasses,
        Long groupTeacherId,
        Long groupClassroomId,
        List<GroupScheduleSlotVO> groupScheduleSlots,
        String groupBillingType,
        String groupStartDate,
        String groupEndDate,
        Integer groupTotalSessions,
        String groupStartMonth,
        String groupEndMonth,
        Integer sortOrder,
        String status,
        String notes,
        Boolean referenced,
        Long referenceCount,
        List<String> referenceSources,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record GroupScheduleSlotVO(
            Long teacherId,
            String subjectName,
            Long alternateTeacherId,
            String alternateSubjectName,
            Long alternateClassroomId,
            String alternateDeliveryMode,
            LocalDate rotationAnchorDate,
            Integer weekday,
            String startTime,
            String endTime,
            String morningEndTime,
            String afternoonStartTime,
            String attendanceOptions
    ) {
    }

    public record ActualClassVO(
            Long id,
            String className,
            String actualClass,
            Long teacherId,
            String teacherName,
            Long classroomId,
            String classroomName,
            String scheduleDesc,
            Long campusId,
            String campusName
    ) {
    }
}
