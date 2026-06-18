package com.classdeduction.system.modules.systemconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.service.CoursePackageService;
import com.classdeduction.system.modules.enrollment.vo.GroupClassStudentProgressVO;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.systemconfig.entity.NotificationTriggerRuleEntity;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationTriggerRuleMapper;
import com.classdeduction.system.modules.systemconfig.service.NotificationOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 低额度扫描定时任务：周期性扫描班课报名与 1v1 课包，按各计费口径阈值入队低额度提醒。
 *
 * 即时核销触发只覆盖 1v1 课时场景，班课课次/月份口径无即时扣减点，故由本任务兜底；
 * 1v1 也一并扫描，与即时触发互为兜底（出站服务内有去重，避免重复发送）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LowBalanceScanScheduler {

    private static final String TRIGGER_LOW_BALANCE_HOUR = "LOW_BALANCE_HOUR";
    private static final String TRIGGER_LOW_BALANCE_SESSION = "LOW_BALANCE_SESSION";
    private static final String TRIGGER_LOW_BALANCE_MONTH = "LOW_BALANCE_MONTH";

    private final NotificationOutboxService notificationOutboxService;
    private final NotificationTriggerRuleMapper notificationTriggerRuleMapper;
    private final CoursePackageService coursePackageService;
    private final CoursePackageMapper coursePackageMapper;
    private final ClassGroupMapper classGroupMapper;
    private final StudentMapper studentMapper;

    /**
     * 默认每小时扫描一次，初始延迟兜底避免应用启动瞬间执行。
     */
    @Scheduled(fixedDelayString = "${app.notification.low-balance-scan-interval-ms:3600000}",
            initialDelayString = "${app.notification.low-balance-scan-initial-delay-ms:60000}")
    public void scanLowBalance() {
        log.info("Low balance scan job started.");
        int hourQueued = scanOneOnOnePackages();
        int classQueued = scanClassEnrollments();
        log.info("Low balance scan job finished. hourQueued={}, classQueued={}", hourQueued, classQueued);
    }

    /**
     * 扫描 1v1 课包（ACTIVE），剩余课时低于 LOW_BALANCE_HOUR 阈值则入队提醒。
     */
    private int scanOneOnOnePackages() {
        Integer threshold = findThreshold(TRIGGER_LOW_BALANCE_HOUR);
        if (threshold == null) {
            // 规则未启用，跳过 1v1 扫描
            return 0;
        }
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(
                new LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getPackageStatus, "ACTIVE")
                        .le(CoursePackageEntity::getRemainingUnits, threshold)
                        .eq(CoursePackageEntity::getIsDeleted, 0));
        int queued = 0;
        for (CoursePackageEntity pkg : packages) {
            try {
                StudentEntity student = studentMapper.selectById(pkg.getStudentId());
                if (student == null) {
                    continue;
                }
                notificationOutboxService.queueLowBalanceWarning(pkg, student);
                queued++;
            } catch (Exception e) {
                log.warn("Failed to scan low balance for packageId={}", pkg.getId(), e);
            }
        }
        return queued;
    }

    /**
     * 扫描班课报名（ACTIVE 班课下的活跃报名），按 PER_SESSION / MONTHLY 口径入队提醒。
     */
    private int scanClassEnrollments() {
        Integer sessionThreshold = findThreshold(TRIGGER_LOW_BALANCE_SESSION);
        Integer monthThreshold = findThreshold(TRIGGER_LOW_BALANCE_MONTH);
        if (sessionThreshold == null && monthThreshold == null) {
            // 两个班课规则都未启用，跳过班课扫描
            return 0;
        }

        List<ClassGroupEntity> classes = classGroupMapper.selectList(
                new LambdaQueryWrapper<ClassGroupEntity>()
                        .eq(ClassGroupEntity::getStatus, "ACTIVE")
                        .isNotNull(ClassGroupEntity::getProductId)
                        .eq(ClassGroupEntity::getIsDeleted, 0));

        int queued = 0;
        LocalDate today = AppTime.nowLocalDateTime().toLocalDate();
        for (ClassGroupEntity classGroup : classes) {
            try {
                List<GroupClassStudentProgressVO> progresses =
                        coursePackageService.listGroupClassStudentProgress(classGroup.getId());
                for (GroupClassStudentProgressVO progress : progresses) {
                    queued += scanOneEnrollment(classGroup, progress, sessionThreshold, monthThreshold, today);
                }
            } catch (Exception e) {
                log.warn("Failed to scan low balance for classId={}", classGroup.getId(), e);
            }
        }
        return queued;
    }

    /**
     * 处理单条班课报名进度：按计费口径判定剩余量是否低于阈值并入队。
     */
    private int scanOneEnrollment(ClassGroupEntity classGroup,
                                  GroupClassStudentProgressVO progress,
                                  Integer sessionThreshold,
                                  Integer monthThreshold,
                                  LocalDate today) {
        if (!"ACTIVE".equals(progress.enrollmentStatus())) {
            return 0;
        }
        try {
            if ("PER_SESSION".equals(progress.billingType()) && sessionThreshold != null) {
                int remainingSessions = progress.remainingUnits() == null ? 0 : progress.remainingUnits();
                if (remainingSessions <= sessionThreshold) {
                    notificationOutboxService.queueLowBalanceSessionWarning(
                            progress.studentId(), classGroup.getId(), classGroup.getClassName(), remainingSessions);
                    return 1;
                }
            } else if ("MONTHLY".equals(progress.billingType()) && monthThreshold != null) {
                int remainingMonths = calculateRemainingMonths(today, progress.expiryDate());
                if (remainingMonths <= monthThreshold) {
                    notificationOutboxService.queueLowBalanceMonthWarning(
                            progress.studentId(), classGroup.getId(), classGroup.getClassName(),
                            remainingMonths, progress.expiryDate());
                    return 1;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scan low balance for enrollmentId={}", progress.enrollmentId(), e);
        }
        return 0;
    }

    /**
     * 剩余月份 = 当前月份到到期月份的整月数；已过期或缺失到期日按 0 处理。
     */
    private int calculateRemainingMonths(LocalDate today, LocalDate expiryDate) {
        if (expiryDate == null) {
            return 0;
        }
        long months = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), expiryDate.withDayOfMonth(1));
        return (int) Math.max(months, 0);
    }

    /**
     * 查询指定低额度规则的阈值；规则未启用时返回 null（表示该口径不扫描）。
     */
    private Integer findThreshold(String triggerType) {
        NotificationTriggerRuleEntity rule = notificationTriggerRuleMapper.selectOne(
                new LambdaQueryWrapper<NotificationTriggerRuleEntity>()
                        .eq(NotificationTriggerRuleEntity::getTriggerType, triggerType)
                        .eq(NotificationTriggerRuleEntity::getEnabled, 1)
                        .eq(NotificationTriggerRuleEntity::getIsDeleted, 0)
                        .last("LIMIT 1"));
        return (rule != null && rule.getThresholdValue() != null) ? rule.getThresholdValue() : null;
    }
}
