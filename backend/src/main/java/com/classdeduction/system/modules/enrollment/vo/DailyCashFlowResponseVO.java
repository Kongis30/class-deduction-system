package com.classdeduction.system.modules.enrollment.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyCashFlowResponseVO(
        LocalDate fromDate,
        LocalDate toDate,
        long transactionCount,
        BigDecimal incomingAmount,
        BigDecimal outgoingAmount,
        BigDecimal netAmount,
        BigDecimal internalTransferAmount,
        List<DailyCashFlowRow> rows
) {
}
