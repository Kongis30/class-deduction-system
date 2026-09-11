package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 课包详情中的共享私教成员摘要。
 * 续费页面需要用它恢复整组学生，但不把课时余额从成员课包里复制到课组。
 */
@Schema(description = "Shared private lesson participant summary")
public record PrivateLessonGroupParticipantVO(
        Long studentId,
        String studentName,
        String studentNo,
        String grade,
        String school,
        Long packageId,
        Integer participantIndex,
        BigDecimal priceMultiplier,
        String memberStatus
) {
}
