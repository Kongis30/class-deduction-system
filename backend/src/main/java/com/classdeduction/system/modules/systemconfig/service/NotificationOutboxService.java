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
     * 余额不足通知：当套餐剩余课时低于阈值时，向对应学生家长发送余额预警邮件。
     * 仅当对应的 LOW_BALANCE 触发规则 enabled=1 时才发送。
     */
    void queueLowBalanceWarning(CoursePackageEntity coursePackage, StudentEntity student);
}
