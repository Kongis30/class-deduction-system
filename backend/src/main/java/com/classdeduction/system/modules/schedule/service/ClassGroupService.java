package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ClassGroupService {

    /** 获取当前用户有权查看的班课列表 */
    List<Map<String, Object>> listClasses(CurrentUserResponse currentUser);

    /** 管理课程模板时可包含停用 Product 下的配置班级；运营排课列表仍使用默认过滤。 */
    List<Map<String, Object>> listClasses(CurrentUserResponse currentUser, boolean includeInactiveProducts);

    /** 获取班课详情 */
    Map<String, Object> getClassDetail(Long classId, CurrentUserResponse currentUser);

    /** 获取班课在读学生 */
    List<Map<String, Object>> listEnrolledStudents(Long classId, CurrentUserResponse currentUser);

    /** 获取班课排课记录 */
    List<Map<String, Object>> listClassOccurrences(Long classId, CurrentUserResponse currentUser);

    /** 统一核销：为班课所有在册学生确认某次课 */
    Map<String, Object> confirmLesson(Long classId, Long occurrenceId, Map<String, Object> body, CurrentUserResponse currentUser);

    /** 管理员驳回班课某次课的老师初确认 */
    void rejectLesson(Long classId, Long occurrenceId, String rejectReason, CurrentUserResponse currentUser);

    /** 更新班课固定周课表，并重建未来未确认课次 */
    Map<String, Object> updateClassSchedule(Long classId, Map<String, Object> body);

    /** 新增某个收费 grade 下的实际班级 */
    Map<String, Object> createActualClass(Map<String, Object> body);

    /** 更新实际班级的名称信息，不影响收费选项和排课 */
    Map<String, Object> updateClassInfo(Long classId, Map<String, Object> body);

    /** 发送到期提醒邮件 */
    Map<String, Object> sendExpiryReminders(Long classId);

    /** 学生报名班课 */
    Map<String, Object> enrollStudent(Long classId, Long studentId, LocalDate enrollmentDate, LocalDate expiryDate);

    /** 获取学生已报名的活跃班课列表（含解析后的时段） */
    List<Map<String, Object>> listEnrolledByStudent(Long studentId);

    /** 获取教师授课的活跃班课列表（含解析后的时段） */
    List<Map<String, Object>> listByTeacher(Long teacherId);

    /** 删除班课：取消未来课次、删除报名记录、软删除班课本身 */
    void deleteClassGroup(Long classId);

    /** 归档 Product 时终止班级及在册报名，保留班级、报名和课表历史行。 */
    void endClassForProductArchive(Long classId, Long operatorId);

    /** 获取教室占用的活跃班课列表（含解析后的时段） */
    List<Map<String, Object>> listByClassroom(Long classroomId);
}
