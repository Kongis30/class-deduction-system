package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.modules.schedule.dto.ClassHolidaySaveRequest;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayVO;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayPreviewVO;
import java.time.LocalDate;
import java.util.List;

public interface ClassHolidayService {

    List<ClassHolidayVO> listHolidays(Long groupProjectId);

    List<ClassHolidayVO> listHolidays(Long groupProjectId, LocalDate startDate, LocalDate endDate);

    boolean isHoliday(Long groupProjectId, LocalDate date);

    default boolean isHoliday(LocalDate date, List<ClassHolidayVO> holidays) {
        return holidays != null && holidays.stream().anyMatch(holiday ->
                !date.isBefore(holiday.startDate()) && !date.isAfter(holiday.endDate()));
    }

    ClassHolidayPreviewVO previewHoliday(Long groupProjectId, Long holidayId, ClassHolidaySaveRequest request);

    ClassHolidayVO createHoliday(Long groupProjectId, ClassHolidaySaveRequest request);

    ClassHolidayVO updateHoliday(Long groupProjectId, Long id, ClassHolidaySaveRequest request);

    void deleteHoliday(Long groupProjectId, Long id);
}
