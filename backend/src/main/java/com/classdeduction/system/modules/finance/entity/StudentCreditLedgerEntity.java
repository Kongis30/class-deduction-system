package com.classdeduction.system.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生 Credit 不可变流水。正数增加余额，负数表示报名抵扣或人工扣减。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("student_credit_ledger")
public class StudentCreditLedgerEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long studentId;

    private String entryType;

    private BigDecimal amount;

    private Long sourceEnrollmentOrderId;

    private Long sourcePaymentRecordId;

    private String referenceNo;

    private String handledByName;

    private Long handledByUserId;

    private LocalDateTime occurredAt;

    private String remark;
}
