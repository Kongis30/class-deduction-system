package com.classdeduction.system.modules.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Data Summary 行归类请求；课程购买行仅用于定位，不修改订单或课包数据。 */
public record DataSummaryClassificationRequest(
        @NotNull Long orderId,
        @NotNull Long studentId,
        Integer participantIndex,
        @Size(max = 100) List<Long> orderItemIds,
        @NotNull Boolean discontinued
) {}
