package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.modules.schedule.dto.ClassroomOccupancyPageQuery;
import com.classdeduction.system.modules.schedule.dto.LessonOccurrencePageQuery;
import com.classdeduction.system.modules.schedule.dto.OneOnOneDeductionQuery;
import com.classdeduction.system.modules.schedule.dto.PendingClassroomBackfillRequest;
import com.classdeduction.system.modules.schedule.vo.ClassroomOccupancyVO;
import com.classdeduction.system.modules.schedule.vo.LessonOccurrenceVO;
import com.classdeduction.system.modules.schedule.vo.DailyPrivateLessonsVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOneTeacherVO;
import com.classdeduction.system.modules.schedule.vo.PendingClassroomAssignmentVO;
import com.classdeduction.system.modules.schedule.vo.StudentScheduleSlotVO;

import java.util.List;
import java.time.LocalDate;

public interface LessonOccurrenceService {

    DailyPrivateLessonsVO listDailyPrivateLessons(LocalDate date);

    PageResponse<LessonOccurrenceVO> pageOccurrences(LessonOccurrencePageQuery query);

    PageResponse<LessonOccurrenceVO> pageOccurrences(LessonOccurrencePageQuery query, CurrentUserResponse currentUser);

    PageResponse<ClassroomOccupancyVO> pageClassroomOccupancy(ClassroomOccupancyPageQuery query);

    List<PendingClassroomAssignmentVO> listPendingClassroomAssignments();

    PendingClassroomAssignmentVO backfillPendingClassroom(PendingClassroomBackfillRequest request);

    List<StudentScheduleSlotVO> getActiveStudentSchedule(Long studentId);

    List<OneOnOneTeacherVO> listOneOnOneDeductions(OneOnOneDeductionQuery query, CurrentUserResponse currentUser);

    LessonOccurrenceVO getOccurrence(Long id);

    LessonOccurrenceVO getOccurrence(Long id, CurrentUserResponse currentUser);
}
