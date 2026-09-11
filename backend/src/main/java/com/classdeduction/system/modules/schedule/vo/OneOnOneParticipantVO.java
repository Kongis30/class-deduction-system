package com.classdeduction.system.modules.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 共享私教课中的学生成员摘要。
 */
@Builder
@Schema(description = "Private lesson group participant")
public record OneOnOneParticipantVO(
        Long studentId,
        String studentNo,
        String studentName,
        Long packageId,
        java.math.BigDecimal priceMultiplier,
        Double remainingUnits
) {
}
