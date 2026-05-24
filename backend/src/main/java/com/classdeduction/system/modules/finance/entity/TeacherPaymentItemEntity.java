package com.classdeduction.system.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 结算明细 — 一笔结算中包含的具体课次记录。
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@TableName("teacher_payment_item")
@Schema(description = "Teacher payment item entity")
public class TeacherPaymentItemEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long paymentId;

    private Long lessonRecordId;

    private Long packageId;

    private Long courseProductId;

    private Integer actualUnits;

    private BigDecimal hourlyRate;

    private BigDecimal amount;

    private LocalDateTime createdAt;
}
