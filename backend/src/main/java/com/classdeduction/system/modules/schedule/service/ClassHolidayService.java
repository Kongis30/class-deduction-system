package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.modules.schedule.dto.ClassHolidaySaveRequest;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayVO;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayPreviewVO;

import java.time.LocalDate;
import java.util.List;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayVO;

public interface ClassHolidayService {

    List<ClassHolidayVO> listHolidays(Long courseProductId, String gradeBand);

    List<ClassHolidayVO> listHolidays(Long courseProductId, String gradeBand, LocalDate startDate, LocalDate endDate);

    boolean isHoliday(Long courseProductId, String gradeBand, LocalDate date);

    default boolean isHoliday(LocalDate date, List<ClassHolidayVO> holidays) {
        return holidays != null && holidays.stream().anyMatch(holiday ->
                !date.isBefore(holiday.startDate()) && !date.isAfter(holiday.endDate()));
    }

    ClassHolidayPreviewVO previewHoliday(Long courseProductId, String gradeBand, Long holidayId, ClassHolidaySaveRequest request);

    ClassHolidayVO createHoliday(Long courseProductId, String gradeBand, ClassHolidaySaveRequest request);

    ClassHolidayVO updateHoliday(Long courseProductId, String gradeBand, Long id, ClassHolidaySaveRequest request);

    void deleteHoliday(Long courseProductId, String gradeBand, Long id);
}
