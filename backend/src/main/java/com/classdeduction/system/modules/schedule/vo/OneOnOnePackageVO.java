package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

@Builder
@Schema(description = "One-on-one deduction package")
public record OneOnOnePackageVO(
        Long packageId,
        String packageNo,
        Long courseProductId,
        String courseProductName,
        String gradeBand,
        String packageName,
        Double totalUnits,
        Double purchasedUnits,
        Double bonusUnits,
        Double consumedUnits,
        Double remainingUnits,
        Boolean renewalReminder,
        String packageStatus,
        LocalDate startDate,
        LocalDate estimatedEndDate,
        Long privateLessonGroupId,
        Integer participantCount,
        Boolean privateLessonGroupPrimary,
        List<OneOnOneParticipantVO> participants,
        List<OneOnOneLessonVO> lessons
) {
}
