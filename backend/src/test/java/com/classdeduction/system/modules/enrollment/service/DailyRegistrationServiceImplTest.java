package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.enrollment.dto.DataSummaryQuery;
import com.classdeduction.system.modules.enrollment.dto.DailyRegistrationQuery;
import com.classdeduction.system.modules.enrollment.service.impl.DailyRegistrationServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.DataSummaryResponseVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyRegistrationServiceImplTest {

    @Mock
    private DataSummaryService dataSummaryService;

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
}
