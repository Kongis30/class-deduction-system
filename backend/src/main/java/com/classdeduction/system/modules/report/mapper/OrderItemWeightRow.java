package com.classdeduction.system.modules.report.mapper;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 订单课程项的价格权重；用于历史未分摊流水的回退分摊。 */
@Getter
@Setter
public class OrderItemWeightRow {

    private Long enrollmentOrderId;
    private Long enrollmentOrderItemId;
    private Long courseProductId;
    private BigDecimal weightAmount;
}
