package com.classdeduction.system.modules.enrollment.entity;

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

/**
 * 课程级付款分摊明细：一笔付款流水按课程项拆分的金额记录。
 * 付款（PAYMENT）为正数，退款（REFUND）为负数。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("payment_allocation")
@Schema(description = "Payment allocation per course item")
public class PaymentAllocationEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long paymentRecordId;

    private Long enrollmentOrderId;

    private Long enrollmentOrderItemId;

    private BigDecimal amount;
}
