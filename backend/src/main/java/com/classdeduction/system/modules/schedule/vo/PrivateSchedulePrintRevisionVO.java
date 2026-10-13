package com.classdeduction.system.modules.schedule.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** 签字表中的排课版本快照；每次周排课变更保留独立规则和时段。 */
public record PrivateSchedulePrintRevisionVO(
        Long ruleId,
        Long supersedesRuleId,
        int revisionNumber,
        LocalDateTime changedAt,
        LocalDate startDate,
        LocalDate endDate,
        Double startingHours,
        Double allocatedHours,
        List<Slot> slots
) {
    public record Slot(
            Integer weekday,
            LocalTime startTime,
            LocalTime endTime,
            String subjectName,
            String teacherName,
            String classroomName,
            String deliveryMode,
            Double allocatedHours
    ) {
    }
}
