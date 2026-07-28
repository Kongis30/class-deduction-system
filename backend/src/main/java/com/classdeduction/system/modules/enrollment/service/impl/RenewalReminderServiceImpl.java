package com.classdeduction.system.modules.enrollment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.enrollment.dto.RenewalReminderSendRequest;
import com.classdeduction.system.modules.enrollment.dto.RenewalReminderSettingsUpdateRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.service.RenewalReminderService;
import com.classdeduction.system.modules.enrollment.vo.RenewalReminderOverviewVO;
import com.classdeduction.system.modules.enrollment.vo.RenewalReminderSendResultVO;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.systemconfig.dto.ApprovedEmailRequest;
import com.classdeduction.system.modules.systemconfig.entity.NotificationTriggerRuleEntity;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationTriggerRuleMapper;
import com.classdeduction.system.modules.systemconfig.service.SystemNotificationService;
import com.classdeduction.system.modules.systemconfig.vo.ApprovedEmailResultVO;
import com.classdeduction.system.modules.systemconfig.vo.BrandConfigVO;
import com.classdeduction.system.modules.systemconfig.vo.NotificationConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 续费提醒工作台的服务实现。
 *
 * <p>只生成需要管理员跟进的名单与逐封已渲染的邮件；没有任何自动发送路径。</p>
 */
@Service
@RequiredArgsConstructor
public class RenewalReminderServiceImpl implements RenewalReminderService {

    private static final String RULE_EXPIRY_DAYS = "RENEWAL_EXPIRY_DAYS";
    private static final String RULE_REMAINING_LESSONS = "RENEWAL_ONE_ON_ONE_LESSONS";
    private static final int DEFAULT_EXPIRY_DAYS = 30;
    private static final int DEFAULT_REMAINING_LESSONS = 2;

    private final CoursePackageMapper coursePackageMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final StudentMapper studentMapper;
    private final NotificationTriggerRuleMapper notificationTriggerRuleMapper;
    private final SystemNotificationService systemNotificationService;

    @Override
    public RenewalReminderOverviewVO getOverview() {
        RenewalReminderOverviewVO.SettingsVO settings = loadSettings();
        LocalDate today = AppTime.nowLocalDateTime().toLocalDate();
        String brandName = resolveBrandName();
        List<RenewalReminderOverviewVO.ReminderVO> reminders = new ArrayList<>();
        reminders.addAll(listOneOnOneReminders(settings, today, brandName));
        reminders.addAll(listGroupClassReminders(settings, today, brandName));
        reminders.sort(Comparator
                .comparingInt(this::urgencyRank)
                .thenComparing(RenewalReminderOverviewVO.ReminderVO::expectedEndDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(RenewalReminderOverviewVO.ReminderVO::studentName,
                        Comparator.nullsLast(String::compareToIgnoreCase)));

        NotificationConfigVO emailConfig = systemNotificationService.getNotificationConfig();
        boolean emailReady = emailConfig != null
                && Boolean.TRUE.equals(emailConfig.enabled())
                && StringUtils.hasText(emailConfig.fromAddress());
        return new RenewalReminderOverviewVO(settings, emailReady,
                emailConfig == null ? null : emailConfig.fromAddress(), reminders);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RenewalReminderOverviewVO updateSettings(RenewalReminderSettingsUpdateRequest request, Long operatorId) {
        updateSettingRule(RULE_EXPIRY_DAYS, request.getExpiryReminderDays(), "Days before expected end date",
                operatorId);
        updateSettingRule(RULE_REMAINING_LESSONS, request.getOneOnOneRemainingLessons(),
                "One-on-one lessons remaining", operatorId);
        return getOverview();
    }

    @Override
    public RenewalReminderSendResultVO sendConfirmedReminders(RenewalReminderSendRequest request, Long operatorId) {
        if (!Boolean.TRUE.equals(request.getPreviewConfirmed())) {
            throw new BusinessException("EMAIL_PREVIEW_CONFIRMATION_REQUIRED",
                    "Review the prepared email content before sending reminders.");
        }

        Map<String, RenewalReminderOverviewVO.ReminderVO> remindersById = getOverview().reminders().stream()
                .collect(Collectors.toMap(RenewalReminderOverviewVO.ReminderVO::reminderId,
                        item -> item, (left, right) -> left, LinkedHashMap::new));
        List<RenewalReminderSendResultVO.SendItemVO> results = new ArrayList<>();
        int sentCount = 0;

        for (String reminderId : request.getReminderIds()) {
            RenewalReminderOverviewVO.ReminderVO reminder = remindersById.get(reminderId);
            if (reminder == null) {
                results.add(new RenewalReminderSendResultVO.SendItemVO(reminderId, null, null, false,
                        "This student no longer needs a reminder. Refresh the list and review again."));
                continue;
            }
            if (!Boolean.TRUE.equals(reminder.emailAvailable())) {
                results.add(new RenewalReminderSendResultVO.SendItemVO(reminderId, reminder.studentName(), null, false,
                        "No guardian email is saved for this student."));
                continue;
            }

            ApprovedEmailResultVO result = systemNotificationService.sendApprovedEmail(
                    ApprovedEmailRequest.builder()
                            .recipient(reminder.guardianEmail())
                            .subject(reminder.emailSubject())
                            .body(reminder.emailBody())
                            .studentId(reminder.studentId())
                            .packageId(reminder.packageId())
                            .source("RENEWAL_REMINDER")
                            .build(),
                    operatorId);
            if (Boolean.TRUE.equals(result.sent())) {
                sentCount++;
            }
            results.add(new RenewalReminderSendResultVO.SendItemVO(reminderId, reminder.studentName(),
                    reminder.guardianEmail(), result.sent(), result.message()));
        }

        return new RenewalReminderSendResultVO(request.getReminderIds().size(), sentCount,
                request.getReminderIds().size() - sentCount, results);
    }

    private List<RenewalReminderOverviewVO.ReminderVO> listOneOnOneReminders(
            RenewalReminderOverviewVO.SettingsVO settings, LocalDate today, String brandName) {
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(new LambdaQueryWrapper<CoursePackageEntity>()
                .eq(CoursePackageEntity::getPackageStatus, "ACTIVE")
                .and(wrapper -> wrapper.eq(CoursePackageEntity::getTeachingMode, "ONE_ON_ONE")
                        .or()
                        .isNull(CoursePackageEntity::getTeachingMode)));
        Map<Long, StudentEntity> students = loadStudents(packages.stream()
                .map(CoursePackageEntity::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        List<RenewalReminderOverviewVO.ReminderVO> reminders = new ArrayList<>();
        for (CoursePackageEntity coursePackage : packages) {
            StudentEntity student = students.get(coursePackage.getStudentId());
            if (student == null) {
                continue;
            }

            LocalDate endDate = coursePackage.getEstimatedEndDate();
            Integer daysUntilEnd = endDate == null ? null : (int) ChronoUnit.DAYS.between(today, endDate);
            int lessonUnits = positive(coursePackage.getDefaultLessonUnits()) ? coursePackage.getDefaultLessonUnits() : 4;
            int remainingUnits = safeUnits(coursePackage.getRemainingUnits());
            int remainingLessons = (int) Math.ceil((double) remainingUnits / lessonUnits);
            boolean expiring = daysUntilEnd != null && daysUntilEnd <= settings.expiryReminderDays();
            boolean lowLessons = remainingLessons <= settings.oneOnOneRemainingLessons();
            if (!expiring && !lowLessons) {
                continue;
            }

            String status = daysUntilEnd != null && daysUntilEnd < 0 ? "EXPIRED" : "NEEDS_RENEWAL";
            String reason = buildOneOnOneReason(daysUntilEnd, remainingLessons);
            reminders.add(toReminder("PACKAGE:" + coursePackage.getId(), "ONE_ON_ONE", student,
                    coursePackage.getId(), null, null, defaultText(coursePackage.getPackageName(), "1-on-1 course"),
                    "ONE_ON_ONE", endDate, daysUntilEnd, toHours(remainingUnits), remainingLessons, status, reason, brandName));
        }
        return reminders;
    }

    private List<RenewalReminderOverviewVO.ReminderVO> listGroupClassReminders(
            RenewalReminderOverviewVO.SettingsVO settings, LocalDate today, String brandName) {
        List<ClassEnrollmentEntity> enrollments = classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                .isNotNull(ClassEnrollmentEntity::getExpiryDate));
        Map<Long, StudentEntity> students = loadStudents(enrollments.stream()
                .map(ClassEnrollmentEntity::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, ClassGroupEntity> classes = loadClasses(enrollments.stream()
                .map(ClassEnrollmentEntity::getClassId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        List<RenewalReminderOverviewVO.ReminderVO> reminders = new ArrayList<>();
        for (ClassEnrollmentEntity enrollment : enrollments) {
            StudentEntity student = students.get(enrollment.getStudentId());
            ClassGroupEntity classGroup = classes.get(enrollment.getClassId());
            if (student == null || classGroup == null) {
                continue;
            }
            int daysUntilEnd = (int) ChronoUnit.DAYS.between(today, enrollment.getExpiryDate());
            if (daysUntilEnd > settings.expiryReminderDays()) {
                continue;
            }
            String status = daysUntilEnd < 0 ? "EXPIRED" : "EXPIRING_SOON";
            String reason = daysUntilEnd < 0
                    ? "Expired " + Math.abs(daysUntilEnd) + " day(s) ago"
                    : "Expires in " + daysUntilEnd + " day(s)";
            reminders.add(toReminder("CLASS_ENROLLMENT:" + enrollment.getId(), "GROUP", student,
                    null, enrollment.getId(), enrollment.getClassId(), classGroup.getClassName(), "GROUP",
                    enrollment.getExpiryDate(), daysUntilEnd, null, null, status, reason, brandName));
        }
        return reminders;
    }

    private RenewalReminderOverviewVO.ReminderVO toReminder(String reminderId, String reminderType,
                                                             StudentEntity student, Long packageId,
                                                             Long classEnrollmentId, Long classId,
                                                             String courseName, String courseType,
                                                             LocalDate endDate, Integer daysUntilEnd,
                                                             Double remainingHours, Integer remainingLessons,
                                                             String status, String reason, String brandName) {
        String recipient = StringUtils.hasText(student.getGuardianEmail())
                ? student.getGuardianEmail().trim().toLowerCase()
                : null;
        String subject = brandName + " - Renewal reminder for " + defaultText(student.getStudentName(), "your student");
        String body = buildEmailBody(brandName, student.getGuardianName(), student.getStudentName(), courseName,
                endDate, daysUntilEnd, remainingHours, remainingLessons);
        return new RenewalReminderOverviewVO.ReminderVO(
                reminderId, reminderType, student.getId(), student.getStudentName(), student.getGuardianName(),
                recipient, packageId, classEnrollmentId, classId, courseName, courseType, endDate, daysUntilEnd,
                remainingHours, remainingLessons, status, reason, StringUtils.hasText(recipient), subject, body);
    }

    private String buildEmailBody(String brandName, String guardianName, String studentName, String courseName,
                                  LocalDate endDate, Integer daysUntilEnd, Double remainingHours,
                                  Integer remainingLessons) {
        String greeting = StringUtils.hasText(guardianName) ? "Dear " + guardianName + "," : "Dear parent or guardian,";
        List<String> details = new ArrayList<>();
        if (endDate != null) {
            if (daysUntilEnd != null && daysUntilEnd < 0) {
                details.add("The enrollment ended on " + endDate + ".");
            } else {
                details.add("The current enrollment is expected to end on " + endDate + ".");
            }
        }
        if (remainingLessons != null) {
            details.add("There are " + remainingLessons + " lesson(s) remaining"
                    + (remainingHours == null ? "." : " (" + formatHours(remainingHours) + " hours)."));
        }
        return String.join("\n\n",
                greeting,
                "This is a friendly renewal reminder for " + defaultText(studentName, "your student")
                        + "'s " + defaultText(courseName, "course") + ".",
                String.join(" ", details),
                "Please contact us to arrange the next enrollment so lessons can continue without interruption.",
                "Best regards,\n" + brandName);
    }

    private String buildOneOnOneReason(Integer daysUntilEnd, Integer remainingLessons) {
        List<String> reasons = new ArrayList<>();
        if (daysUntilEnd != null) {
            reasons.add(daysUntilEnd < 0
                    ? "Expected end passed " + Math.abs(daysUntilEnd) + " day(s) ago"
                    : "Expected end in " + daysUntilEnd + " day(s)");
        }
        if (remainingLessons != null) {
            reasons.add(remainingLessons + " lesson(s) remaining");
        }
        return String.join(" · ", reasons);
    }

    private RenewalReminderOverviewVO.SettingsVO loadSettings() {
        return new RenewalReminderOverviewVO.SettingsVO(
                settingValue(RULE_EXPIRY_DAYS, DEFAULT_EXPIRY_DAYS),
                settingValue(RULE_REMAINING_LESSONS, DEFAULT_REMAINING_LESSONS));
    }

    private int settingValue(String triggerType, int fallback) {
        NotificationTriggerRuleEntity rule = findSettingRule(triggerType);
        return rule != null && rule.getThresholdValue() != null ? rule.getThresholdValue() : fallback;
    }

    private void updateSettingRule(String triggerType, Integer threshold, String remark, Long operatorId) {
        NotificationTriggerRuleEntity rule = findSettingRule(triggerType);
        if (rule == null) {
            rule = new NotificationTriggerRuleEntity();
            rule.setTriggerType(triggerType);
            rule.setEnabled(0);
            rule.setRecipientType("GUARDIAN_EMAIL");
            rule.setRemark(remark);
            rule.setCreatedBy(operatorId);
            rule.setIsDeleted(0);
            rule.setThresholdValue(threshold);
            rule.setUpdatedBy(operatorId);
            notificationTriggerRuleMapper.insert(rule);
            return;
        }
        rule.setThresholdValue(threshold);
        rule.setRemark(remark);
        rule.setUpdatedBy(operatorId);
        notificationTriggerRuleMapper.updateById(rule);
    }

    private NotificationTriggerRuleEntity findSettingRule(String triggerType) {
        return notificationTriggerRuleMapper.selectOne(new LambdaQueryWrapper<NotificationTriggerRuleEntity>()
                .eq(NotificationTriggerRuleEntity::getTriggerType, triggerType)
                .eq(NotificationTriggerRuleEntity::getIsDeleted, 0)
                .last("LIMIT 1"));
    }

    private Map<Long, StudentEntity> loadStudents(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return studentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(StudentEntity::getId, item -> item));
    }

    private Map<Long, ClassGroupEntity> loadClasses(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return classGroupMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ClassGroupEntity::getId, item -> item));
    }

    private String resolveBrandName() {
        BrandConfigVO brandConfig = systemNotificationService.getCurrentBrandConfig();
        return brandConfig != null && StringUtils.hasText(brandConfig.brandName())
                ? brandConfig.brandName()
                : "JIN BEST TUTORING";
    }

    private int urgencyRank(RenewalReminderOverviewVO.ReminderVO reminder) {
        if ("EXPIRED".equals(reminder.status())) {
            return 0;
        }
        if (reminder.daysUntilEnd() != null) {
            return 1;
        }
        return 2;
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private int safeUnits(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private Double toHours(int units) {
        return BigDecimal.valueOf(units)
                .multiply(BigDecimal.valueOf(0.5))
                .setScale(1, RoundingMode.UNNECESSARY)
                .doubleValue();
    }

    private String formatHours(Double hours) {
        return BigDecimal.valueOf(hours).stripTrailingZeros().toPlainString();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
