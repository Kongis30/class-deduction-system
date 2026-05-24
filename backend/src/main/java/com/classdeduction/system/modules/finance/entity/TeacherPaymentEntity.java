package com.classdeduction.system.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 教师结算批次 — 一次工资结算。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("teacher_payment")
@Schema(description = "Teacher payment entity")
public class TeacherPaymentEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String paymentNo;

    private Long teacherId;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private Integer totalUnits;

    private BigDecimal totalHours;

    private BigDecimal totalAmount;

    private String status;

    private LocalDateTime paidAt;

    private Long paidBy;

    private String notes;
}
