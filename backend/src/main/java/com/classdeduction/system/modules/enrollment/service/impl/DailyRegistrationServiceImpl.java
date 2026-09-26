package com.classdeduction.system.modules.enrollment.service.impl;

import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.enrollment.dto.DailyRegistrationQuery;
import com.classdeduction.system.modules.enrollment.dto.DataSummaryQuery;
import com.classdeduction.system.modules.enrollment.service.DailyRegistrationService;
import com.classdeduction.system.modules.enrollment.service.DataSummaryService;
import com.classdeduction.system.modules.enrollment.vo.DailyRegistrationResponseVO;
import com.classdeduction.system.modules.enrollment.vo.DataSummaryResponseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DailyRegistrationServiceImpl implements DailyRegistrationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final DataSummaryService dataSummaryService;

    @Override
    public DailyRegistrationResponseVO query(DailyRegistrationQuery query) {
        DataSummaryQuery summaryQuery = new DataSummaryQuery();
        summaryQuery.setSavedAtFrom(startOfDay(query.getFromDate()));
        summaryQuery.setSavedAtBefore(startOfDay(query.getToDate().plusDays(1)));
        summaryQuery.setIncludeInactiveOrders(true);
        summaryQuery.setRegistrationOperatorUserId(query.getOperatorUserId());

        List<DataSummaryResponseVO.RowVO> sourceRows = dataSummaryService.getSummary(summaryQuery).rows();
        List<DailyRegistrationResponseVO.RegistrationRowVO> rows = sourceRows.stream()
                .map(this::toRow)
                .sorted(Comparator.comparing(DailyRegistrationResponseVO.RegistrationRowVO::savedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DailyRegistrationResponseVO.RegistrationRowVO::orderId,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Set<Long> orderIds = new LinkedHashSet<>();
        Set<Long> studentIds = new LinkedHashSet<>();
        BigDecimal amountDue = ZERO;
        BigDecimal amountCollected = ZERO;
        BigDecimal outstanding = ZERO;
        for (DailyRegistrationResponseVO.RegistrationRowVO row : rows) {
            if (row.orderId() != null) orderIds.add(row.orderId());
            if (row.studentId() != null) studentIds.add(row.studentId());
            amountDue = amountDue.add(value(row.amountDue()));
            amountCollected = amountCollected.add(value(row.amountCollected()));
            outstanding = outstanding.add(value(row.outstandingAmount()));
        }

        return new DailyRegistrationResponseVO(query.getFromDate(), query.getToDate(), rows.size(),
                orderIds.size(), studentIds.size(), amountDue, amountCollected, outstanding,
                summarizeOperators(rows), rows);
    }

    private LocalDateTime startOfDay(java.time.LocalDate date) {
        return date.atStartOfDay(AppTime.APP_ZONE_ID).toLocalDateTime();
    }

    private DailyRegistrationResponseVO.RegistrationRowVO toRow(DataSummaryResponseVO.RowVO row) {
        return new DailyRegistrationResponseVO.RegistrationRowVO(
                row.orderId(), row.orderItemId(), row.studentId(), row.studentName(),
                row.courseProductName(), row.courseType(), row.className(), row.grade(), row.campusName(),
                row.signupDate(), toOffsetDateTime(row.registrationSavedAt()), row.registrationOperatorUserId(),
                row.registrationOperatorName(), row.paymentMethod(), row.paymentStatus(), row.orderStatus(),
                row.amountDue(), row.paidAmount(), row.balanceAmount() == null ? null : row.balanceAmount().negate());
    }

    private List<DailyRegistrationResponseVO.OperatorSummaryVO> summarizeOperators(
            List<DailyRegistrationResponseVO.RegistrationRowVO> rows) {
        Map<Long, List<DailyRegistrationResponseVO.RegistrationRowVO>> grouped = new LinkedHashMap<>();
        for (DailyRegistrationResponseVO.RegistrationRowVO row : rows) {
            Long key = row.operatorUserId() == null ? Long.MIN_VALUE : row.operatorUserId();
            grouped.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(row);
        }
        return grouped.values().stream().map(operatorRows -> {
            DailyRegistrationResponseVO.RegistrationRowVO first = operatorRows.get(0);
            Set<Long> orderIds = new LinkedHashSet<>();
            Set<Long> studentIds = new LinkedHashSet<>();
            BigDecimal due = ZERO;
            BigDecimal collected = ZERO;
            for (DailyRegistrationResponseVO.RegistrationRowVO row : operatorRows) {
                if (row.orderId() != null) orderIds.add(row.orderId());
                if (row.studentId() != null) studentIds.add(row.studentId());
                due = due.add(value(row.amountDue()));
                collected = collected.add(value(row.amountCollected()));
            }
            return new DailyRegistrationResponseVO.OperatorSummaryVO(first.operatorUserId(),
                    first.operatorName(), operatorRows.size(), orderIds.size(), studentIds.size(), due, collected);
        }).toList();
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(AppTime.APP_ZONE_ID).toOffsetDateTime();
    }
}
