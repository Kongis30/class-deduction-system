package com.classdeduction.system.modules.systemconfig.service;

import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;

/**
 * 通知出站服务：负责把待发送通知写入通知日志表，真实发送由后续异步任务接管。
 */
public interface NotificationOutboxService {

    void queueLessonBalanceEmail(LessonRecordEntity lessonRecord,
                                 CoursePackageEntity coursePackage,
                                 StudentEntity student,
                                 String lessonStatusName);

    /**
     * 余额不足通知（1v1 按课时）：当课包剩余课时低于阈值时，向对应学生家长发送余额预警邮件。
     * 仅当 LOW_BALANCE_HOUR 触发规则 enabled=1 时才发送。
     */
    void queueLowBalanceWarning(CoursePackageEntity coursePackage, StudentEntity student);

    /**
     * 余额不足通知（班课 PER_SESSION）：当班课报名剩余课次低于阈值时，向学生家长发送预警邮件。
     * 仅当 LOW_BALANCE_SESSION 触发规则 enabled=1 时才发送。
     */
    void queueLowBalanceSessionWarning(Long studentId, Long classId, String className, int remainingSessions);

    /**
     * 余额不足通知（班课 MONTHLY）：当班课报名剩余月份低于阈值时，向学生家长发送预警邮件。
     * 仅当 LOW_BALANCE_MONTH 触发规则 enabled=1 时才发送。
     */
    void queueLowBalanceMonthWarning(Long studentId, Long classId, String className,
                                     int remainingMonths, java.time.LocalDate expiryDate);
}
