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
import java.time.LocalDateTime;

/**
 * 支付记录实体：第一阶段固定为现金支付。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("payment_record")
@Schema(description = "支付记录实体")
public class PaymentRecordEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long enrollmentOrderId;

    private String paymentNo;

    private String paymentMethod;

    private String paymentStatus;

    private BigDecimal amount;

    private LocalDateTime paidAt;

    private Long handledByUserId;

    private String handledByName;

    private String remark;
}
