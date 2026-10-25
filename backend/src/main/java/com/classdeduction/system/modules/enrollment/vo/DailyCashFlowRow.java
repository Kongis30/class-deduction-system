package com.classdeduction.system.modules.enrollment.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 每一行对应一笔实际收款、退款或已支付教师工资。 */
@Getter
@Setter
@NoArgsConstructor
public class DailyCashFlowRow {
    private String transactionId;
    private String transactionType;
    private String direction;
    private LocalDateTime happenedAt;
    private Long handledByUserId;
    private String handledByName;
    private String counterpartyName;
    private String paymentMethod;
    private BigDecimal amount;
    private String status;
    private String referenceNo;
    private String note;
    private boolean internalTransfer;
    private boolean voided;
}
