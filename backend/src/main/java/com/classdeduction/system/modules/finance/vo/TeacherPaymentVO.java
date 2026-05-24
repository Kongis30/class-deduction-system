package com.classdeduction.system.modules.finance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 教师结算批次响应。
 */
@Builder
@Schema(description = "Teacher payment VO")
public record TeacherPaymentVO(
        Long id,
        String paymentNo,
        Long teacherId,
        String teacherNo,
        String teacherName,
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer totalUnits,
        BigDecimal totalHours,
        BigDecimal totalAmount,
        String status,
        LocalDateTime paidAt,
        String paidByName,
        String notes,
        LocalDateTime createdAt,
        List<TeacherPaymentItemVO> items
) {}
