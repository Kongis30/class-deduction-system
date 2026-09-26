package com.classdeduction.system.modules.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Schema(description = "每日报名流水查询条件")
public class DailyRegistrationQuery {

    @NotNull
    private LocalDate fromDate;

    @NotNull
    private LocalDate toDate;

    private Long operatorUserId;

    @AssertTrue(message = "Date range must be no longer than 366 days")
    public boolean isDateRangeValid() {
        return fromDate == null || toDate == null
                || (!toDate.isBefore(fromDate) && !toDate.isAfter(fromDate.plusDays(365)));
    }
}
