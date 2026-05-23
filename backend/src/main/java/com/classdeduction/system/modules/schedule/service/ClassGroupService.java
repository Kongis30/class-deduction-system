package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ClassGroupService {

    /** 获取所有班课列表 */
    List<Map<String, Object>> listClasses();

    /** 获取班课详情 */
    Map<String, Object> getClassDetail(Long classId);

    /** 获取班课在读学生 */
    List<Map<String, Object>> listEnrolledStudents(Long classId);

    /** 获取班课排课记录 */
    List<Map<String, Object>> listClassOccurrences(Long classId);

    /** 统一核销：为班课所有在册学生确认某次课 */
    Map<String, Object> confirmLesson(Long classId, Long occurrenceId, Map<String, Object> body);

    /** 发送到期提醒邮件 */
    Map<String, Object> sendExpiryReminders(Long classId);

    /** 学生报名班课 */
    Map<String, Object> enrollStudent(Long classId, Long studentId, LocalDate enrollmentDate, LocalDate expiryDate);

    /** 获取学生已报名的活跃班课列表（含解析后的时段） */
    List<Map<String, Object>> listEnrolledByStudent(Long studentId);

    /** 获取教师授课的活跃班课列表（含解析后的时段） */
    List<Map<String, Object>> listByTeacher(Long teacherId);

    /** 获取教室占用的活跃班课列表（含解析后的时段） */
    List<Map<String, Object>> listByClassroom(Long classroomId);
}
