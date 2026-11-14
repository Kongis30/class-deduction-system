package com.classdeduction.system.modules.schedule.vo;

import java.util.List;
import java.time.LocalDateTime;

/** 指定日期的私教课次与去重学生数。 */
public record DailyPrivateLessonsVO(List<Lesson> lessons, int studentCount) {
    public record Lesson(Long id, LocalDateTime plannedStartAt, LocalDateTime plannedEndAt,
                         List<String> participantNames, List<Participant> participants, String subjectName, String packageName,
                         String plannedTeacherName, String plannedClassroomName) {}
    public record Participant(Long studentId, Long packageId, String studentName) {}
}
