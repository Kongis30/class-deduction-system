package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.modules.schedule.dto.ClassHolidaySaveRequest;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayVO;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayPreviewVO;

import java.time.LocalDate;
import java.util.List;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayVO;

public interface ClassHolidayService {

    List<ClassHolidayVO> listHolidays();

    List<ClassHolidayVO> listHolidays(LocalDate startDate, LocalDate endDate);

    boolean isHoliday(LocalDate date);

    default boolean isHoliday(LocalDate date, List<ClassHolidayVO> holidays) {
        return holidays != null && holidays.stream().anyMatch(holiday ->
                !date.isBefore(holiday.startDate()) && !date.isAfter(holiday.endDate()));
    }

    ClassHolidayPreviewVO previewHoliday(Long holidayId, ClassHolidaySaveRequest request);

    ClassHolidayVO createHoliday(ClassHolidaySaveRequest request);

    ClassHolidayVO updateHoliday(Long id, ClassHolidaySaveRequest request);

    void deleteHoliday(Long id);
}
