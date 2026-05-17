package com.classdeduction.system.modules.systemconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.GuardianContactEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.GuardianContactMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.systemconfig.entity.BrandConfigEntity;
import com.classdeduction.system.modules.systemconfig.entity.NotificationLogEntity;
import com.classdeduction.system.modules.systemconfig.entity.NotificationTemplateEntity;
import com.classdeduction.system.modules.systemconfig.entity.NotificationTriggerRuleEntity;
import com.classdeduction.system.modules.systemconfig.mapper.BrandConfigMapper;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationLogMapper;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationTemplateMapper;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationTriggerRuleMapper;
import com.classdeduction.system.modules.systemconfig.service.NotificationOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 课后通知出站服务：按触发规则判断是否发邮件，写入 notification_log 供 scheduler 投递。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxServiceImpl implements NotificationOutboxService {

    private static final String TRIGGER_LESSON_CONFIRMED = "LESSON_CONFIRMED";
    private static final String TRIGGER_LOW_BALANCE = "LOW_BALANCE";

    private final NotificationTemplateMapper notificationTemplateMapper;
    private final NotificationLogMapper notificationLogMapper;
    private final BrandConfigMapper brandConfigMapper;
    private final GuardianContactMapper guardianContactMapper;
    private final NotificationTriggerRuleMapper notificationTriggerRuleMapper;
    private final JsonHelper jsonHelper;

    @Override
    public void queueLessonBalanceEmail(LessonRecordEntity lessonRecord,
                                        CoursePackageEntity coursePackage,
                                        StudentEntity student,
                                        String lessonStatusName) {
        // 按触发规则判断是否应发送：查 LESSON_CONFIRMED 规则，enabled 才发
        NotificationTriggerRuleEntity rule = notificationTriggerRuleMapper.selectOne(
                new LambdaQueryWrapper<NotificationTriggerRuleEntity>()
                        .eq(NotificationTriggerRuleEntity::getTriggerType, TRIGGER_LESSON_CONFIRMED)
                        .eq(NotificationTriggerRuleEntity::getEnabled, 1)
                        .eq(NotificationTriggerRuleEntity::getIsDeleted, 0)
                        .last("LIMIT 1"));

        if (rule == null) {
            log.debug("LESSON_CONFIRMED trigger rule disabled, skipping notification for lessonRecordId={}",
                    lessonRecord.getId());
            return;
        }

        try {
            NotificationTemplateEntity template = findTemplate(rule.getTemplateId());
            BrandConfigEntity brandConfig = findActiveBrand();
            List<String> recipients = resolveRecipients(rule, student.getId());

            if (template == null || brandConfig == null || recipients.isEmpty()) {
                log.warn("Cannot send notification: template={}, brand={}, recipients={}",
                        template != null, brandConfig != null, recipients.size());
                return;
            }

            Map<String, String> variables = buildVariables(lessonRecord, coursePackage, student, lessonStatusName, brandConfig);
            String subject = renderTemplate(template.getSubjectTemplate(), variables);
            String body = renderTemplate(template.getBodyTemplate(), variables);
            LocalDateTime scheduledAt = AppTime.nowLocalDateTime();

            for (String recipient : recipients) {
                NotificationLogEntity logEntity = new NotificationLogEntity();
                logEntity.setChannelType(template.getChannelType());
                logEntity.setTemplateId(template.getId());
                logEntity.setStudentId(student.getId());
                logEntity.setPackageId(coursePackage.getId());
                logEntity.setLessonRecordId(lessonRecord.getId());
                logEntity.setRecipient(recipient);
                logEntity.setSendStatus("PENDING");
                logEntity.setScheduledAt(scheduledAt);
                logEntity.setRetryCount(0);
                logEntity.setCreatedBy(lessonRecord.getConfirmedByUserId());
                logEntity.setUpdatedBy(lessonRecord.getConfirmedByUserId());
                logEntity.setRequestPayload(jsonHelper.toJson(Map.of(
                        "templateCode", template.getTemplateCode(),
                        "subject", subject,
                        "body", body,
                        "variables", variables
                )));
                notificationLogMapper.insert(logEntity);
            }
        } catch (Exception exception) {
            log.warn("Failed to queue lesson balance email for lessonRecordId={}", lessonRecord.getId(), exception);
        }
    }

    private NotificationTemplateEntity findTemplate(Long templateId) {
        if (templateId != null) {
            NotificationTemplateEntity t = notificationTemplateMapper.selectById(templateId);
            if (t != null && "ACTIVE".equals(t.getStatus())) {
                return t;
            }
        }
        return notificationTemplateMapper.selectOne(new LambdaQueryWrapper<NotificationTemplateEntity>()
                .eq(NotificationTemplateEntity::getStatus, "ACTIVE")
                .orderByAsc(NotificationTemplateEntity::getId)
                .last("LIMIT 1"));
    }

    private BrandConfigEntity findActiveBrand() {
        return brandConfigMapper.selectOne(new LambdaQueryWrapper<BrandConfigEntity>()
                .eq(BrandConfigEntity::getStatus, "ACTIVE")
                .orderByAsc(BrandConfigEntity::getId)
                .last("LIMIT 1"));
    }

    private List<String> resolveRecipients(NotificationTriggerRuleEntity rule, Long studentId) {
        if ("CUSTOM_EMAIL".equals(rule.getRecipientType())) {
            return parseRecipients(rule.getCustomRecipients());
        }
        // Default: GUARDIAN_EMAIL — 查家长联系邮箱
        return findRecipients(studentId);
    }

    /**
     * 先通知主联系人；若未维护主联系人邮箱，则退化为所有可用邮箱去重后发送。
     */
    private List<String> findRecipients(Long studentId) {
        List<GuardianContactEntity> contacts = guardianContactMapper.selectList(new LambdaQueryWrapper<GuardianContactEntity>()
                .eq(GuardianContactEntity::getStudentId, studentId)
                .eq(GuardianContactEntity::getStatus, "ACTIVE")
                .orderByDesc(GuardianContactEntity::getIsPrimary)
                .orderByAsc(GuardianContactEntity::getId));
        List<GuardianContactEntity> eligible = contacts.stream()
                .filter(item -> StringUtils.hasText(item.getEmail()))
                .toList();
        if (eligible.isEmpty()) {
            return Collections.emptyList();
        }
        List<GuardianContactEntity> primaryContacts = eligible.stream()
                .filter(item -> item.getIsPrimary() != null && item.getIsPrimary() == 1)
                .toList();
        List<GuardianContactEntity> selected = primaryContacts.isEmpty() ? eligible : primaryContacts;
        return selected.stream()
                .map(GuardianContactEntity::getEmail)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, String> buildVariables(LessonRecordEntity lessonRecord,
                                               CoursePackageEntity coursePackage,
                                               StudentEntity student,
                                               String lessonStatusName,
                                               BrandConfigEntity brandConfig) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("brandName", defaultString(brandConfig.getBrandName(), "Training Center"));
        variables.put("studentName", defaultString(student.getStudentName(), "Student"));
        variables.put("packageName", defaultString(coursePackage.getPackageName(), "Course Package"));
        variables.put("lessonStatus", defaultString(lessonStatusName, lessonRecord.getResultStatus()));
        variables.put("deductHours", formatHours(lessonRecord.getDeductedUnits()));
        variables.put("remainingHours", formatHours(coursePackage.getRemainingUnits()));
        variables.put("estimatedEndDate", formatDate(coursePackage.getEstimatedEndDate()));
        return variables;
    }

    private String renderTemplate(String template, Map<String, String> variables) {
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return rendered;
    }

    private String formatHours(Integer units) {
        if (units == null) {
            return "0";
        }
        BigDecimal hours = BigDecimal.valueOf(units)
                .multiply(new BigDecimal("0.5"))
                .setScale(1, RoundingMode.UNNECESSARY)
                .stripTrailingZeros();
        return hours.toPlainString();
    }

    private String formatDate(LocalDate date) {
        return date == null ? "TBD" : date.toString();
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private List<String> parseRecipients(String customRecipients) {
        if (!StringUtils.hasText(customRecipients)) {
            return Collections.emptyList();
        }
        try {
            return jsonHelper.fromJson(customRecipients, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse custom recipients JSON: {}", customRecipients);
            return Collections.emptyList();
        }
    }

    // ── LOW_BALANCE 通知 ───────────────────────────────────────────────────────

    @Override
    public void queueLowBalanceWarning(CoursePackageEntity coursePackage, StudentEntity student) {
        NotificationTriggerRuleEntity rule = notificationTriggerRuleMapper.selectOne(
                new LambdaQueryWrapper<NotificationTriggerRuleEntity>()
                        .eq(NotificationTriggerRuleEntity::getTriggerType, TRIGGER_LOW_BALANCE)
                        .eq(NotificationTriggerRuleEntity::getEnabled, 1)
                        .eq(NotificationTriggerRuleEntity::getIsDeleted, 0)
                        .last("LIMIT 1"));

        if (rule == null) {
            log.debug("LOW_BALANCE trigger rule disabled, skipping notification for packageId={}",
                    coursePackage.getId());
            return;
        }

        try {
            NotificationTemplateEntity template = findTemplate(rule.getTemplateId());
            BrandConfigEntity brandConfig = findActiveBrand();
            List<String> recipients = resolveRecipients(rule, student.getId());

            if (template == null || brandConfig == null || recipients.isEmpty()) {
                log.warn("Cannot send LOW_BALANCE notification: template={}, brand={}, recipients={}",
                        template != null, brandConfig != null, recipients.size());
                return;
            }

            Map<String, String> variables = buildLowBalanceVariables(coursePackage, student, brandConfig);
            String subject = renderTemplate(template.getSubjectTemplate(), variables);
            String body = renderTemplate(template.getBodyTemplate(), variables);
            LocalDateTime scheduledAt = AppTime.nowLocalDateTime();

            for (String recipient : recipients) {
                NotificationLogEntity logEntity = new NotificationLogEntity();
                logEntity.setChannelType(template.getChannelType());
                logEntity.setTemplateId(template.getId());
                logEntity.setStudentId(student.getId());
                logEntity.setPackageId(coursePackage.getId());
                logEntity.setRecipient(recipient);
                logEntity.setSendStatus("PENDING");
                logEntity.setScheduledAt(scheduledAt);
                logEntity.setRetryCount(0);
                logEntity.setRequestPayload(jsonHelper.toJson(Map.of(
                        "templateCode", template.getTemplateCode(),
                        "subject", subject,
                        "body", body,
                        "variables", variables
                )));
                notificationLogMapper.insert(logEntity);
            }
        } catch (Exception exception) {
            log.warn("Failed to queue LOW_BALANCE warning email for packageId={}", coursePackage.getId(), exception);
        }
    }

    private Map<String, String> buildLowBalanceVariables(CoursePackageEntity coursePackage,
                                                          StudentEntity student,
                                                          BrandConfigEntity brandConfig) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("brandName", defaultString(brandConfig.getBrandName(), "Training Center"));
        variables.put("studentName", defaultString(student.getStudentName(), "Student"));
        variables.put("packageName", defaultString(coursePackage.getPackageName(), "Course Package"));
        variables.put("remainingHours", formatHours(coursePackage.getRemainingUnits()));
        variables.put("deductHours", "0");
        variables.put("estimatedEndDate", formatDate(coursePackage.getEstimatedEndDate()));
        return variables;
    }
}
