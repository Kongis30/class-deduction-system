package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 报名单中的参与学生详情。订单详情和收据编辑共用这份快照，避免前端从展示用 JSON 反推业务数据。
 */
@Schema(description = "Enrollment order participant detail")
public record EnrollmentOrderParticipantVO(
        Long id,
        Long studentId,
        Integer participantIndex,
        String studentName,
        String studentNo,
        String grade,
        String school,
        String studentClass,
        String guardianName,
        String guardianPhone,
        String guardianPhone2,
        String guardianEmail,
        Boolean pickupRequired,
        String pickupAddress,
        Boolean dropoffRequired,
        String dropoffAddress,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        String discountDetail,
        BigDecimal amountDue,
        BigDecimal paidAmount,
        BigDecimal balanceAmount
) {
}
