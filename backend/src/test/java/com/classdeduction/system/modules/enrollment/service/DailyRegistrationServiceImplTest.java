package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.enrollment.dto.DataSummaryQuery;
import com.classdeduction.system.modules.enrollment.dto.DailyRegistrationQuery;
import com.classdeduction.system.modules.enrollment.service.impl.DailyRegistrationServiceImpl;
import com.classdeduction.system.modules.enrollment.mapper.DailyCashFlowMapper;
import com.classdeduction.system.modules.enrollment.vo.DailyCashFlowRow;
import com.classdeduction.system.modules.enrollment.vo.DataSummaryResponseVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyRegistrationServiceImplTest {

    @Mock
    private DataSummaryService dataSummaryService;

    @Mock
    private DailyCashFlowMapper dailyCashFlowMapper;

    @InjectMocks
    private DailyRegistrationServiceImpl dailyRegistrationService;

    @Test
    void shouldFilterBySavedTimeInNewYorkAndIncludeClosedOrdersForHistory() {
        DailyRegistrationQuery query = new DailyRegistrationQuery();
        query.setFromDate(LocalDate.of(2026, 9, 25));
        query.setToDate(LocalDate.of(2026, 9, 26));
        query.setOperatorUserId(42L);
        when(dataSummaryService.getSummary(any())).thenReturn(new DataSummaryResponseVO(
                0L, null, null, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));

        dailyRegistrationService.query(query);

        ArgumentCaptor<DataSummaryQuery> summaryQuery = ArgumentCaptor.forClass(DataSummaryQuery.class);
        verify(dataSummaryService).getSummary(summaryQuery.capture());
        assertThat(summaryQuery.getValue().getSavedAtFrom())
                .isEqualTo(query.getFromDate().atStartOfDay(AppTime.APP_ZONE_ID).toLocalDateTime());
        assertThat(summaryQuery.getValue().getSavedAtBefore())
                .isEqualTo(query.getToDate().plusDays(1).atStartOfDay(AppTime.APP_ZONE_ID).toLocalDateTime());
        assertThat(summaryQuery.getValue().getIncludeInactiveOrders()).isTrue();
        assertThat(summaryQuery.getValue().getRegistrationOperatorUserId()).isEqualTo(42L);
    }

    @Test
    void shouldCalculateCashFlowWithoutCountingVoidsOrInternalCreditAsCash() {
        DailyRegistrationQuery query = new DailyRegistrationQuery();
        query.setFromDate(LocalDate.of(2026, 10, 25));
        query.setToDate(LocalDate.of(2026, 10, 25));
        query.setOperatorUserId(42L);
        DailyCashFlowRow receipt = row("IN", "1600.00", false, false);
        DailyCashFlowRow refund = row("OUT", "-1000.00", false, false);
        DailyCashFlowRow payroll = row("OUT", "200.00", false, false);
        DailyCashFlowRow credit = row("OUT", "-100.00", true, false);
        DailyCashFlowRow voided = row("IN", "50.00", false, true);
        when(dailyCashFlowMapper.selectByPeriod(any(), any(), any()))
                .thenReturn(List.of(receipt, refund, payroll, credit, voided));

        var result = dailyRegistrationService.queryCashFlow(query);

        assertThat(result.transactionCount()).isEqualTo(3);
        assertThat(result.incomingAmount()).isEqualByComparingTo("1600.00");
        assertThat(result.outgoingAmount()).isEqualByComparingTo("1200.00");
        assertThat(result.netAmount()).isEqualByComparingTo("400.00");
        assertThat(result.internalTransferAmount()).isEqualByComparingTo("100.00");
        ArgumentCaptor<java.time.LocalDateTime> fromTime = ArgumentCaptor.forClass(java.time.LocalDateTime.class);
        ArgumentCaptor<java.time.LocalDateTime> toTime = ArgumentCaptor.forClass(java.time.LocalDateTime.class);
        verify(dailyCashFlowMapper).selectByPeriod(fromTime.capture(), toTime.capture(), org.mockito.ArgumentMatchers.eq(42L));
        assertThat(fromTime.getValue()).isEqualTo(query.getFromDate().atStartOfDay(AppTime.APP_ZONE_ID).toLocalDateTime());
        assertThat(toTime.getValue()).isEqualTo(query.getToDate().plusDays(1).atStartOfDay(AppTime.APP_ZONE_ID).toLocalDateTime());
    }

    private DailyCashFlowRow row(String direction, String amount, boolean internal, boolean voided) {
        DailyCashFlowRow row = new DailyCashFlowRow();
        row.setDirection(direction);
        row.setAmount(new BigDecimal(amount));
        row.setInternalTransfer(internal);
        row.setVoided(voided);
        return row;
    }
}
