package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.schedule.dto.ClassroomOccupancyPageQuery;
import com.classdeduction.system.modules.schedule.dto.LessonOccurrencePageQuery;
import com.classdeduction.system.modules.schedule.vo.ClassroomOccupancyVO;
import com.classdeduction.system.modules.schedule.vo.LessonOccurrenceVO;
import com.classdeduction.system.modules.schedule.vo.StudentScheduleSlotVO;

import java.util.List;

public interface LessonOccurrenceService {

    PageResponse<LessonOccurrenceVO> pageOccurrences(LessonOccurrencePageQuery query);

    PageResponse<ClassroomOccupancyVO> pageClassroomOccupancy(ClassroomOccupancyPageQuery query);

    List<StudentScheduleSlotVO> getActiveStudentSchedule(Long studentId);

    LessonOccurrenceVO getOccurrence(Long id);
}
