package com.classdeduction.system.modules.schedule.vo;

import java.time.LocalDate;

public record ClassHolidayVO(Long id, String holidayName, LocalDate startDate, LocalDate endDate) {
}
