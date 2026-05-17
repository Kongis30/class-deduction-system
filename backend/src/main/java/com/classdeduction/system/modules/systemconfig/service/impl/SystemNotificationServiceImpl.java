package com.classdeduction.system.modules.systemconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.systemconfig.config.NotificationEmailProperties;
import com.classdeduction.system.modules.systemconfig.dto.BrandConfigUpdateRequest;
import com.classdeduction.system.modules.systemconfig.vo.BalanceConsistencyVO;
import com.classdeduction.system.modules.systemconfig.dto.NotificationBroadcastRequest;
import com.classdeduction.system.modules.systemconfig.dto.NotificationConfigUpdateRequest;
import com.classdeduction.system.modules.systemconfig.dto.NotificationDispatchRequest;
import com.classdeduction.system.modules.systemconfig.dto.NotificationLogPageQuery;
import com.classdeduction.system.modules.systemconfig.dto.NotificationTemplatePageQuery;
import com.classdeduction.system.modules.systemconfig.dto.NotificationTemplateUpdateRequest;
import com.classdeduction.system.modules.systemconfig.dto.NotificationTriggerRuleUpdateRequest;
import com.classdeduction.system.modules.systemconfig.entity.BrandConfigEntity;
import com.classdeduction.system.modules.systemconfig.entity.NotificationConfigEntity;
import com.classdeduction.system.modules.systemconfig.entity.NotificationLogEntity;
import com.classdeduction.system.modules.systemconfig.entity.NotificationTemplateEntity;
import com.classdeduction.system.modules.systemconfig.entity.NotificationTriggerRuleEntity;
import com.classdeduction.system.modules.systemconfig.mapper.BrandConfigMapper;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationConfigMapper;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationLogMapper;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationTemplateMapper;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationTriggerRuleMapper;
import com.classdeduction.system.modules.systemconfig.service.SystemNotificationService;
import com.classdeduction.system.modules.systemconfig.vo.BroadcastResultVO;
import com.classdeduction.system.modules.systemconfig.vo.BrandConfigVO;
import com.classdeduction.system.modules.systemconfig.vo.NotificationConfigVO;
import com.classdeduction.system.modules.systemconfig.vo.NotificationDispatchResultVO;
import com.classdeduction.system.modules.systemconfig.vo.NotificationLogVO;
import com.classdeduction.system.modules.systemconfig.vo.NotificationTemplateVO;
import com.classdeduction.system.modules.systemconfig.vo.NotificationTriggerRuleVO;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.GuardianContactEntity;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.GuardianContactMapper;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 通知系统服务：管理模板、品牌配置、日志查询与管理员手动安全分发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemNotificationServiceImpl implements SystemNotificationService {

    private static final int MAX_QUERY_WINDOW_DAYS = 90;

    private final NotificationTemplateMapper notificationTemplateMapper;
    private final NotificationLogMapper notificationLogMapper;
    private final BrandConfigMapper brandConfigMapper;
    private final NotificationConfigMapper notificationConfigMapper;
    private final NotificationTriggerRuleMapper notificationTriggerRuleMapper;
    private final JavaMailSender javaMailSender;
    private final ObjectMapper objectMapper;
    private final NotificationEmailProperties notificationEmailProperties;
    private final CoursePackageMapper coursePackageMapper;
    private final HourLedgerMapper hourLedgerMapper;
    private final StudentMapper studentMapper;
    private final GuardianContactMapper guardianContactMapper;
    private final UserAccountMapper userAccountMapper;

    @Override
    @Cacheable(value = "systemConfig:brandConfig", key = "'current'")
    public BrandConfigVO getCurrentBrandConfig() {
        return toBrandConfigVO(findCurrentBrandConfig());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "systemConfig:brandConfig", allEntries = true)
    public BrandConfigVO upsertCurrentBrandConfig(BrandConfigUpdateRequest request, Long operatorId) {
        BrandConfigEntity entity = findCurrentBrandConfig();
        if (entity == null) {
            entity = new BrandConfigEntity();
        }
        entity.setBrandName(request.getBrandName());
        entity.setLogoUrl(request.getLogoUrl());
        entity.setThemeColor(request.getThemeColor());
        entity.setSupportEmail(request.getSupportEmail());
        entity.setReceiptFooterText(request.getReceiptFooterText());
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setUpdatedBy(operatorId);
        if (entity.getId() == null) {
            entity.setCreatedBy(operatorId);
            brandConfigMapper.insert(entity);
        } else {
            brandConfigMapper.updateById(entity);
        }
        return toBrandConfigVO(entity);
    }

    @Override
    public PageResponse<NotificationTemplateVO> pageTemplates(NotificationTemplatePageQuery query) {
        IPage<NotificationTemplateEntity> page = notificationTemplateMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<NotificationTemplateEntity>()
                        .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                                .like(NotificationTemplateEntity::getTemplateCode, query.getKeyword())
                                .or()
                                .like(NotificationTemplateEntity::getTemplateName, query.getKeyword()))
                        .eq(StringUtils.hasText(query.getChannelType()), NotificationTemplateEntity::getChannelType, query.getChannelType())
                        .eq(StringUtils.hasText(query.getStatus()), NotificationTemplateEntity::getStatus, query.getStatus())
                        .orderByAsc(NotificationTemplateEntity::getTemplateCode)
        );
        if (page.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        return PageResponse.of(page.getRecords().stream().map(this::toTemplateVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Cacheable(value = "systemConfig:notificationTemplate", key = "#id")
    public NotificationTemplateVO getTemplate(Long id) {
        return toTemplateVO(requireTemplate(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "systemConfig:notificationTemplate", allEntries = true)
    public NotificationTemplateVO updateTemplate(Long id, NotificationTemplateUpdateRequest request, Long operatorId) {
        NotificationTemplateEntity entity = requireTemplate(id);
        entity.setTemplateName(request.getTemplateName());
        entity.setSubjectTemplate(request.getSubjectTemplate());
        entity.setBodyTemplate(request.getBodyTemplate());
        entity.setStatus(resolveStatus(request.getStatus()));
        entity.setRemark(request.getRemark());
        entity.setUpdatedBy(operatorId);
        notificationTemplateMapper.updateById(entity);
        return toTemplateVO(entity);
    }

    @Override
    public PageResponse<NotificationLogVO> pageLogs(NotificationLogPageQuery query) {
        validateLogWindow(query);
        IPage<NotificationLogEntity> page = notificationLogMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<NotificationLogEntity>()
                        .like(StringUtils.hasText(query.getKeyword()), NotificationLogEntity::getRecipient, query.getKeyword())
                        .eq(StringUtils.hasText(query.getChannelType()), NotificationLogEntity::getChannelType, query.getChannelType())
                        .eq(StringUtils.hasText(query.getSendStatus()), NotificationLogEntity::getSendStatus, query.getSendStatus())
                        .eq(query.getStudentId() != null, NotificationLogEntity::getStudentId, query.getStudentId())
                        .eq(query.getPackageId() != null, NotificationLogEntity::getPackageId, query.getPackageId())
                        .eq(query.getLessonRecordId() != null, NotificationLogEntity::getLessonRecordId, query.getLessonRecordId())
                        .ge(query.getScheduledFrom() != null, NotificationLogEntity::getScheduledAt, query.getScheduledFrom())
                        .le(query.getScheduledTo() != null, NotificationLogEntity::getScheduledAt, query.getScheduledTo())
                        .orderByDesc(NotificationLogEntity::getScheduledAt)
                        .orderByDesc(NotificationLogEntity::getId)
        );
        if (page.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        return PageResponse.of(page.getRecords().stream().map(this::toLogVO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public NotificationLogVO getLog(Long id) {
        return toLogVO(requireLog(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void requeueFailedLog(Long id, Long operatorId) {
        NotificationLogEntity entity = requireLog(id);
        if (!"FAILED".equalsIgnoreCase(entity.getSendStatus())) {
            throw new BusinessException("NOTIFICATION_LOG_NOT_REQUEUEABLE", "Only failed notification logs can be requeued.");
        }
        entity.setSendStatus("PENDING");
        entity.setErrorMessage(null);
        entity.setResponsePayload(null);
        entity.setSentAt(null);
        entity.setUpdatedBy(operatorId);
        notificationLogMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationDispatchResultVO dispatchPendingEmails(NotificationDispatchRequest request, Long operatorId) {
        ensureDispatchEnabled();
        int batchSize = Math.min(Optional.ofNullable(request.getBatchSize()).orElse(notificationEmailProperties.getMaxBatchSize()),
                notificationEmailProperties.getMaxBatchSize());
        List<NotificationLogEntity> pendingLogs = notificationLogMapper.selectList(new LambdaQueryWrapper<NotificationLogEntity>()
                .eq(NotificationLogEntity::getChannelType, "EMAIL")
                .eq(NotificationLogEntity::getSendStatus, "PENDING")
                .lt(NotificationLogEntity::getRetryCount, notificationEmailProperties.getMaxRetries())
                .orderByAsc(NotificationLogEntity::getScheduledAt)
                .orderByAsc(NotificationLogEntity::getId)
                .last("LIMIT " + batchSize));

        List<Long> successLogIds = new ArrayList<>();
        List<Long> failedLogIds = new ArrayList<>();
        int claimedCount = 0;

        for (NotificationLogEntity logEntity : pendingLogs) {
            if (!claimForProcessing(logEntity.getId(), operatorId)) {
                continue;
            }
            claimedCount++;
            try {
                sendOne(logEntity, resolveFromAddress(), resolveFromName());
                markSendSuccess(logEntity.getId(), operatorId);
                successLogIds.add(logEntity.getId());
            } catch (Exception exception) {
                markSendFailed(logEntity, operatorId, exception);
                failedLogIds.add(logEntity.getId());
            }
        }

        return NotificationDispatchResultVO.builder()
                .requestedBatchSize(batchSize)
                .claimedCount(claimedCount)
                .successCount(successLogIds.size())
                .failedCount(failedLogIds.size())
                .successLogIds(successLogIds)
                .failedLogIds(failedLogIds)
                .build();
    }

    @Override
    public BalanceConsistencyVO checkBalanceConsistency() {
        List<CoursePackageEntity> activePackages = coursePackageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CoursePackageEntity>()
                        .eq(CoursePackageEntity::getPackageStatus, "ACTIVE")
                        .in(CoursePackageEntity::getIsDeleted, 0));

        List<BalanceConsistencyVO.PackageDiscrepancy> discrepancies = new java.util.ArrayList<>();

        for (CoursePackageEntity pkg : activePackages) {
            HourLedgerEntity lastLedger = hourLedgerMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HourLedgerEntity>()
                            .eq(HourLedgerEntity::getPackageId, pkg.getId())
                            .orderByDesc(HourLedgerEntity::getId)
                            .last("LIMIT 1"));

            if (lastLedger == null) {
                continue;
            }

            int ledgerBalance = lastLedger.getBalanceAfterUnits();
            int packageBalance = pkg.getRemainingUnits() != null ? pkg.getRemainingUnits() : 0;

            if (ledgerBalance != packageBalance) {
                String discrepancyType = ledgerBalance > packageBalance ? "UNDER_DEBIT" : "OVER_DEBIT";
                discrepancies.add(new BalanceConsistencyVO.PackageDiscrepancy(
                        pkg.getId(),
                        pkg.getPackageNo(),
                        pkg.getStudentId(),
                        packageBalance,
                        ledgerBalance,
                        ledgerBalance,
                        discrepancyType,
                        String.format("课包余额（%d）与账本余额（%d）不一致，建议将课包余额修正为 %d。", packageBalance, ledgerBalance, ledgerBalance)
                ));
            }
        }

        return new BalanceConsistencyVO(activePackages.size(), discrepancies.size(), discrepancies);
    }

    // ── Sender Config ──────────────────────────────────────────────────────────

    @Override
    @Cacheable(value = "systemConfig:notificationConfig", key = "'current'")
    public NotificationConfigVO getNotificationConfig() {
        NotificationConfigEntity entity = findNotificationConfig();
        return toConfigVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "systemConfig:notificationConfig", allEntries = true)
    public NotificationConfigVO updateNotificationConfig(NotificationConfigUpdateRequest request, Long operatorId) {
        NotificationConfigEntity entity = findNotificationConfig();
        if (entity == null) {
            entity = new NotificationConfigEntity();
            entity.setId(1L);
            entity.setCreatedBy(operatorId);
        }
        entity.setEnabled(request.getEnabled() != null ? (request.getEnabled() ? 1 : 0) : 0);
        entity.setFromAddress(request.getFromAddress());
        entity.setFromName(request.getFromName());
        entity.setAuthCode(request.getAuthCode());
        entity.setStrictAllowlist(request.getStrictAllowlist() != null ? (request.getStrictAllowlist() ? 1 : 0) : 1);
        entity.setAllowedRecipients(request.getAllowedRecipients());
        entity.setLowBalanceThreshold(request.getLowBalanceThreshold() != null ? request.getLowBalanceThreshold() : 2);
        entity.setLowBalanceRecipients(request.getLowBalanceRecipients());
        entity.setUpdatedBy(operatorId);
        notificationConfigMapper.insertOrUpdate(entity);
        return toConfigVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendTestEmail(Long operatorId) {
        NotificationConfigEntity config = findNotificationConfig();
        if (config == null || !StringUtils.hasText(config.getFromAddress())) {
            throw new BusinessException("EMAIL_NOT_CONFIGURED", "Email sender address is not configured yet.");
        }

        String subject = "[JIN BEST TUTORING] Test email - system configuration verified";
        String body = "This is a test email sent from the JBT class deduction system.\n\n" +
                "If you received this email, your email sender configuration is working correctly.\n\n" +
                "Sender: " + config.getFromAddress() + "\n" +
                "Time: " + AppTime.nowOffsetDateTime() + "\n\n" +
                "Best regards,\nJIN BEST TUTORING";

        // Send directly using the configured auth code
        sendDirect(config.getFromAddress(), config.getFromAddress(), subject, body);

        log.info("Test email sent to {} by operator {}", config.getFromAddress(), operatorId);
    }

    // ── Trigger Rules ──────────────────────────────────────────────────────────

    @Override
    @Cacheable(value = "systemConfig:notificationTriggerRules", key = "'listAll'")
    public List<NotificationTriggerRuleVO> listTriggerRules() {
        return notificationTriggerRuleMapper.selectList(
                        new LambdaQueryWrapper<NotificationTriggerRuleEntity>()
                                .eq(NotificationTriggerRuleEntity::getIsDeleted, 0)
                                .orderByAsc(NotificationTriggerRuleEntity::getId))
                .stream()
                .map(this::toTriggerRuleVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "systemConfig:notificationTriggerRules", allEntries = true)
    public NotificationTriggerRuleVO updateTriggerRule(Long id, NotificationTriggerRuleUpdateRequest request, Long operatorId) {
        NotificationTriggerRuleEntity entity = notificationTriggerRuleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("TRIGGER_RULE_NOT_FOUND", "Trigger rule not found.");
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled() ? 1 : 0);
        }
        if (StringUtils.hasText(request.getRecipientType())) {
            entity.setRecipientType(request.getRecipientType());
        }
        if (request.getCustomRecipients() != null) {
            entity.setCustomRecipients(request.getCustomRecipients());
        }
        if (request.getThresholdValue() != null) {
            entity.setThresholdValue(request.getThresholdValue());
        }
        if (request.getRemark() != null) {
            entity.setRemark(request.getRemark());
        }
        if (request.getTemplateId() != null) {
            entity.setTemplateId(request.getTemplateId());
        }
        entity.setUpdatedBy(operatorId);
        notificationTriggerRuleMapper.updateById(entity);
        return toTriggerRuleVO(entity);
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BroadcastResultVO broadcast(NotificationBroadcastRequest request, Long operatorId) {
        List<String> recipients = resolveBroadcastRecipients(request);
        if (recipients.isEmpty()) {
            throw new BusinessException("NO_RECIPIENTS", "No recipients found for the broadcast.");
        }

        BrandConfigEntity brand = findCurrentBrandConfig();
        String brandName = brand != null && StringUtils.hasText(brand.getBrandName()) ? brand.getBrandName() : "JIN BEST TUTORING";

        // Render subject and body with brand name
        String renderedSubject = request.getSubject().replace("${brandName}", brandName);
        String renderedBody = request.getBody().replace("${brandName}", brandName);

        List<NotificationLogEntity> logs = new ArrayList<>();
        LocalDateTime now = AppTime.nowLocalDateTime();
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(Map.of(
                    "subject", renderedSubject,
                    "body", renderedBody,
                    "broadcast", true
            ));
        } catch (JsonProcessingException e) {
            payloadJson = "{}";
        }

        for (String recipient : recipients) {
            NotificationLogEntity log = new NotificationLogEntity();
            log.setChannelType("EMAIL");
            log.setRecipient(recipient);
            log.setSendStatus("PENDING");
            log.setRetryCount(0);
            log.setScheduledAt(now);
            log.setCreatedBy(operatorId);
            log.setUpdatedBy(operatorId);
            log.setRequestPayload(payloadJson);
            logs.add(log);
        }

        for (NotificationLogEntity logEntity : logs) {
            notificationLogMapper.insert(logEntity);
        }

        List<String> preview = recipients.size() > 5 ? recipients.subList(0, 5) : recipients;
        return BroadcastResultVO.builder()
                .totalRecipients(recipients.size())
                .queuedCount(logs.size())
                .recipientPreview(preview)
                .build();
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    private void ensureDispatchEnabled() {
        NotificationConfigEntity config = findNotificationConfig();
        boolean enabled = config != null && config.getEnabled() != null && config.getEnabled() == 1;
        if (!enabled) {
            // Fallback to YAML config for backward compatibility
            enabled = notificationEmailProperties.isEnabled();
        }
        if (!enabled) {
            throw new BusinessException("EMAIL_DISPATCH_DISABLED", "Email dispatch is currently disabled.");
        }
        String fromAddress = resolveFromAddress();
        if (!StringUtils.hasText(fromAddress)) {
            throw new BusinessException("EMAIL_FROM_ADDRESS_MISSING", "Email from-address is not configured.");
        }
    }

    private String resolveFromAddress() {
        NotificationConfigEntity config = findNotificationConfig();
        if (config != null && StringUtils.hasText(config.getFromAddress())) {
            return config.getFromAddress();
        }
        return notificationEmailProperties.getFromAddress();
    }

    private String resolveFromName() {
        NotificationConfigEntity config = findNotificationConfig();
        if (config != null && StringUtils.hasText(config.getFromName())) {
            return config.getFromName();
        }
        return notificationEmailProperties.getFromName();
    }

    private void sendOne(NotificationLogEntity logEntity, String fromAddress, String fromName) throws Exception {
        RenderedMessage renderedMessage = parseRenderedMessage(logEntity.getRequestPayload());
        ensureRecipientAllowed(logEntity.getRecipient());
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
        if (StringUtils.hasText(fromName)) {
            helper.setFrom(fromAddress, fromName);
        } else {
            helper.setFrom(fromAddress);
        }
        helper.setTo(logEntity.getRecipient());
        helper.setSubject(renderedMessage.subject());
        helper.setText(renderedMessage.body(), false);
        javaMailSender.send(mimeMessage);
    }

    private void sendDirect(String to, String fromAddress, String subject, String body) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            javaMailSender.send(mimeMessage);
        } catch (Exception e) {
            log.error("Failed to send test email to {}", to, e);
            throw new BusinessException("EMAIL_SEND_FAILED", "Failed to send test email: " + e.getMessage());
        }
    }

    private void ensureRecipientAllowed(String recipient) {
        NotificationConfigEntity config = findNotificationConfig();
        boolean strict = false;
        List<String> allowed = Collections.emptyList();

        if (config != null && config.getStrictAllowlist() != null && config.getStrictAllowlist() == 1) {
            strict = true;
            allowed = parseJsonArray(config.getAllowedRecipients());
        }

        // Fallback to YAML config
        if (!strict && notificationEmailProperties.isStrictAllowlist()) {
            strict = true;
            allowed = notificationEmailProperties.getAllowedRecipients();
        }

        if (!strict) {
            return;
        }

        if (allowed.isEmpty()) {
            throw new BusinessException("EMAIL_ALLOWLIST_EMPTY", "Strict allowlist is enabled but no allowed recipients are configured.");
        }
        Set<String> allowedSet = allowed.stream()
                .filter(StringUtils::hasText)
                .map(r -> r.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (!allowedSet.contains(normalizeRecipient(recipient))) {
            throw new BusinessException("EMAIL_RECIPIENT_NOT_ALLOWED",
                    "Recipient " + recipient + " is not in the configured email allowlist.");
        }
    }

    private List<String> resolveBroadcastRecipients(NotificationBroadcastRequest request) {
        String preset = request.getRecipientPreset();

        // 1. Custom explicit recipients list (highest priority)
        if (request.getRecipients() != null && !request.getRecipients().isEmpty()) {
            return request.getRecipients().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .filter(e -> e.contains("@"))
                    .toList();
        }

        if (!StringUtils.hasText(preset)) {
            return Collections.emptyList();
        }

        // 2. Preset-based recipient resolution
        return switch (preset) {
            case "ALL_STUDENTS" -> resolveAllStudentGuardianEmails(request.getStudentKeyword());
            case "ALL_ENROLLED_STUDENTS" -> resolveAllEnrolledStudentGuardianEmails(request.getStudentKeyword());
            case "ALL_TEACHERS" -> resolveAllTeacherEmails();
            case "ALL_ADMIN_USERS" -> resolveAllAdminEmails();
            default -> Collections.emptyList();
        };
    }

    /**
     * All students (including inactive) with a valid guardian email.
     */
    private List<String> resolveAllStudentGuardianEmails(String keyword) {
        LambdaQueryWrapper<StudentEntity> wrapper = new LambdaQueryWrapper<StudentEntity>()
                .select(StudentEntity::getId)
                .like(StringUtils.hasText(keyword), StudentEntity::getStudentName, keyword);
        List<StudentEntity> students = studentMapper.selectList(wrapper);
        return resolveGuardianEmailsForStudents(students);
    }

    /**
     * Only students with at least one ACTIVE course package.
     */
    private List<String> resolveAllEnrolledStudentGuardianEmails(String keyword) {
        LambdaQueryWrapper<CoursePackageEntity> pkgWrapper = new LambdaQueryWrapper<CoursePackageEntity>()
                .select(CoursePackageEntity::getStudentId)
                .eq(CoursePackageEntity::getPackageStatus, "ACTIVE")
                .in(CoursePackageEntity::getIsDeleted, 0)
                .isNotNull(CoursePackageEntity::getStudentId)
                .groupBy(CoursePackageEntity::getStudentId);
        List<CoursePackageEntity> activePackages = coursePackageMapper.selectList(pkgWrapper);
        List<Long> enrolledStudentIds = activePackages.stream()
                .map(CoursePackageEntity::getStudentId)
                .distinct()
                .toList();

        if (enrolledStudentIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<StudentEntity> studentWrapper = new LambdaQueryWrapper<StudentEntity>()
                .in(StudentEntity::getId, enrolledStudentIds)
                .like(StringUtils.hasText(keyword), StudentEntity::getStudentName, keyword);
        List<StudentEntity> students = studentMapper.selectList(studentWrapper);
        return resolveGuardianEmailsForStudents(students);
    }

    /**
     * All teachers with a linked user account and a non-empty email address.
     */
    private List<String> resolveAllTeacherEmails() {
        LambdaQueryWrapper<UserAccountEntity> wrapper = new LambdaQueryWrapper<UserAccountEntity>()
                .select(UserAccountEntity::getEmail)
                .eq(UserAccountEntity::getUserType, "TEACHER")
                .isNotNull(UserAccountEntity::getEmail)
                .ne(UserAccountEntity::getEmail, "")
                .in(UserAccountEntity::getIsDeleted, 0);
        List<UserAccountEntity> accounts = userAccountMapper.selectList(wrapper);
        return accounts.stream()
                .map(UserAccountEntity::getEmail)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    /**
     * All admin users with a non-empty email address.
     */
    private List<String> resolveAllAdminEmails() {
        LambdaQueryWrapper<UserAccountEntity> wrapper = new LambdaQueryWrapper<UserAccountEntity>()
                .select(UserAccountEntity::getEmail)
                .eq(UserAccountEntity::getUserType, "ADMIN")
                .isNotNull(UserAccountEntity::getEmail)
                .ne(UserAccountEntity::getEmail, "")
                .in(UserAccountEntity::getIsDeleted, 0);
        List<UserAccountEntity> accounts = userAccountMapper.selectList(wrapper);
        return accounts.stream()
                .map(UserAccountEntity::getEmail)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> resolveGuardianEmailsForStudents(List<StudentEntity> students) {
        if (students.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> studentIds = students.stream().map(StudentEntity::getId).toList();
        LambdaQueryWrapper<GuardianContactEntity> contactWrapper = new LambdaQueryWrapper<GuardianContactEntity>()
                .select(GuardianContactEntity::getEmail)
                .in(GuardianContactEntity::getStudentId, studentIds)
                .isNotNull(GuardianContactEntity::getEmail)
                .ne(GuardianContactEntity::getEmail, "")
                .in(GuardianContactEntity::getIsDeleted, 0);
        List<GuardianContactEntity> contacts = guardianContactMapper.selectList(contactWrapper);
        return contacts.stream()
                .map(GuardianContactEntity::getEmail)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> parseJsonArray(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON array: {}", json);
            return Collections.emptyList();
        }
    }

    private NotificationConfigEntity findNotificationConfig() {
        return notificationConfigMapper.selectById(1L);
    }

    private boolean claimForProcessing(Long id, Long operatorId) {
        return notificationLogMapper.update(null, new UpdateWrapper<NotificationLogEntity>()
                .eq("id", id)
                .eq("send_status", "PENDING")
                .set("send_status", "PROCESSING")
                .set("updated_by", operatorId)) == 1;
    }

    private RenderedMessage parseRenderedMessage(String requestPayload) throws JsonProcessingException {
        Map<String, Object> payload = objectMapper.readValue(requestPayload, new TypeReference<>() {});
        String subject = Objects.toString(payload.get("subject"), null);
        String body = Objects.toString(payload.get("body"), null);
        if (!StringUtils.hasText(subject) || !StringUtils.hasText(body)) {
            throw new BusinessException("NOTIFICATION_PAYLOAD_INVALID", "Notification payload is missing rendered subject or body.");
        }
        return new RenderedMessage(subject, body);
    }

    private void markSendSuccess(Long id, Long operatorId) {
        try {
            notificationLogMapper.update(null, new UpdateWrapper<NotificationLogEntity>()
                    .eq("id", id)
                    .eq("send_status", "PROCESSING")
                    .set("send_status", "SUCCESS")
                    .set("sent_at", AppTime.nowLocalDateTime())
                    .set("error_message", null)
                    .set("response_payload", objectMapper.writeValueAsString(Map.of(
                            "status", "SUCCESS",
                            "dispatchedAt", AppTime.nowOffsetDateTime().toString()
                    )))
                    .set("updated_by", operatorId));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize success payload for log {}", id, e);
        }
    }

    private void markSendFailed(NotificationLogEntity logEntity, Long operatorId, Exception exception) {
        try {
            notificationLogMapper.update(null, new UpdateWrapper<NotificationLogEntity>()
                    .eq("id", logEntity.getId())
                    .eq("send_status", "PROCESSING")
                    .set("send_status", "FAILED")
                    .set("retry_count", Optional.ofNullable(logEntity.getRetryCount()).orElse(0) + 1)
                    .set("error_message", abbreviate(exception.getMessage(), 500))
                    .set("response_payload", buildFailurePayload(exception))
                    .set("updated_by", operatorId));
        } catch (Exception updateException) {
            log.error("Failed to update notification log {} after send failure", logEntity.getId(), updateException);
        }
    }

    private String buildFailurePayload(Exception exception) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "status", "FAILED",
                    "errorType", exception.getClass().getSimpleName(),
                    "message", abbreviate(exception.getMessage(), 1000)
            ));
        } catch (JsonProcessingException e) {
            return "{\"status\":\"FAILED\"}";
        }
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String normalizeRecipient(String recipient) {
        return recipient == null ? "" : recipient.trim().toLowerCase(Locale.ROOT);
    }

    private BrandConfigEntity findCurrentBrandConfig() {
        return brandConfigMapper.selectOne(new LambdaQueryWrapper<BrandConfigEntity>()
                .eq(BrandConfigEntity::getStatus, "ACTIVE")
                .orderByAsc(BrandConfigEntity::getId)
                .last("LIMIT 1"));
    }

    private NotificationTemplateEntity requireTemplate(Long id) {
        NotificationTemplateEntity entity = notificationTemplateMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("NOTIFICATION_TEMPLATE_NOT_FOUND", "Notification template not found.");
        }
        return entity;
    }

    private NotificationLogEntity requireLog(Long id) {
        NotificationLogEntity entity = notificationLogMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("NOTIFICATION_LOG_NOT_FOUND", "Notification log not found.");
        }
        return entity;
    }

    private void validateLogWindow(NotificationLogPageQuery query) {
        if (query.getScheduledFrom() == null || query.getScheduledTo() == null) {
            return;
        }
        if (!query.getScheduledTo().isAfter(query.getScheduledFrom())) {
            throw new BusinessException("INVALID_NOTIFICATION_WINDOW", "Scheduled end time must be later than the start time.");
        }
        if (query.getScheduledFrom().plusDays(MAX_QUERY_WINDOW_DAYS).isBefore(query.getScheduledTo())) {
            throw new BusinessException("NOTIFICATION_WINDOW_TOO_LARGE", "Notification query window cannot exceed 90 days.");
        }
    }

    private String resolveStatus(String status) {
        return StringUtils.hasText(status) ? status : "ACTIVE";
    }

    private BrandConfigVO toBrandConfigVO(BrandConfigEntity entity) {
        if (entity == null) return null;
        return BrandConfigVO.builder()
                .id(entity.getId())
                .brandName(entity.getBrandName())
                .logoUrl(entity.getLogoUrl())
                .themeColor(entity.getThemeColor())
                .supportEmail(entity.getSupportEmail())
                .receiptFooterText(entity.getReceiptFooterText())
                .status(entity.getStatus())
                .build();
    }

    private NotificationTemplateVO toTemplateVO(NotificationTemplateEntity entity) {
        return NotificationTemplateVO.builder()
                .id(entity.getId())
                .templateCode(entity.getTemplateCode())
                .templateName(entity.getTemplateName())
                .channelType(entity.getChannelType())
                .subjectTemplate(entity.getSubjectTemplate())
                .bodyTemplate(entity.getBodyTemplate())
                .status(entity.getStatus())
                .remark(entity.getRemark())
                .build();
    }

    private NotificationLogVO toLogVO(NotificationLogEntity entity) {
        return NotificationLogVO.builder()
                .id(entity.getId())
                .channelType(entity.getChannelType())
                .templateId(entity.getTemplateId())
                .studentId(entity.getStudentId())
                .packageId(entity.getPackageId())
                .lessonRecordId(entity.getLessonRecordId())
                .recipient(entity.getRecipient())
                .sendStatus(entity.getSendStatus())
                .requestPayload(entity.getRequestPayload())
                .responsePayload(entity.getResponsePayload())
                .errorMessage(entity.getErrorMessage())
                .scheduledAt(entity.getScheduledAt())
                .sentAt(entity.getSentAt())
                .retryCount(entity.getRetryCount())
                .build();
    }

    private NotificationConfigVO toConfigVO(NotificationConfigEntity entity) {
        if (entity == null) return null;
        return NotificationConfigVO.builder()
                .id(entity.getId())
                .enabled(entity.getEnabled() != null && entity.getEnabled() == 1)
                .fromAddress(entity.getFromAddress())
                .fromName(entity.getFromName())
                .strictAllowlist(entity.getStrictAllowlist() != null && entity.getStrictAllowlist() == 1)
                .allowedRecipients(entity.getAllowedRecipients())
                .lowBalanceThreshold(entity.getLowBalanceThreshold())
                .lowBalanceRecipients(entity.getLowBalanceRecipients())
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }

    private NotificationTriggerRuleVO toTriggerRuleVO(NotificationTriggerRuleEntity entity) {
        String triggerName = switch (entity.getTriggerType()) {
            case "LESSON_CONFIRMED" -> "Lesson Confirmed";
            case "LOW_BALANCE" -> "Low Balance Warning";
            default -> entity.getTriggerType();
        };
        NotificationTemplateEntity template = entity.getTemplateId() != null
                ? notificationTemplateMapper.selectById(entity.getTemplateId())
                : null;

        return NotificationTriggerRuleVO.builder()
                .id(entity.getId())
                .triggerType(entity.getTriggerType())
                .triggerName(triggerName)
                .templateId(entity.getTemplateId())
                .templateName(template != null ? template.getTemplateName() : null)
                .enabled(entity.getEnabled() != null && entity.getEnabled() == 1)
                .recipientType(entity.getRecipientType())
                .customRecipients(entity.getCustomRecipients())
                .thresholdValue(entity.getThresholdValue())
                .remark(entity.getRemark())
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null)
                .build();
    }

    private record RenderedMessage(String subject, String body) {
    }
}