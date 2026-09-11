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
 * Immutable order-level payment ledger entry.
 *
 * <p>Entries record a payment or refund for the whole enrollment order; they do not allocate
 * money to an order item or course package.</p>
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

    /** 1 对 N 收据中的学生序号；普通订单为空。 */
    private Integer participantIndex;

    private String paymentNo;

    private String paymentMethod;

    /** 记录类型：PAYMENT（支付）/ REFUND（退款）。 */
    private String paymentType;

    private String paymentStatus;

    private BigDecimal amount;

    private LocalDateTime paidAt;

    private Long handledByUserId;

    private String handledByName;

    private String remark;
}
