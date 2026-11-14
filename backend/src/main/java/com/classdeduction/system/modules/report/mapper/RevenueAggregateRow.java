package com.classdeduction.system.modules.report.mapper;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 月度收入聚合查询的原始行；字段与 SQL 别名一一对应。 */
@Getter
@Setter
public class RevenueAggregateRow {

    private String monthKey;
    private Long campusId;
    private String campusNameSnapshot;
    private Long courseProductId;
    private BigDecimal amount;
}
