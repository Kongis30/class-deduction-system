package com.classdeduction.system.modules.lesson.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.lesson.dto.LessonRecordConfirmRequest;
import com.classdeduction.system.modules.lesson.dto.LessonRecordPageQuery;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.entity.LessonStatusRuleEntity;
import com.classdeduction.system.modules.lesson.entity.PackageBalanceSnapshotEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonStatusRuleMapper;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.lesson.service.LessonRecordService;
import com.classdeduction.system.modules.lesson.vo.LessonRecordConfirmResultVO;
import com.classdeduction.system.modules.lesson.vo.LessonRecordDetailVO;
import com.classdeduction.system.modules.lesson.vo.LessonRecordListItemVO;
import com.classdeduction.system.modules.systemconfig.service.NotificationOutboxService;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationTriggerRuleMapper;
import com.classdeduction.system.modules.systemconfig.entity.NotificationTriggerRuleEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 课次确认服务：把实际上课结果、规则判定、课时流水和课包余额放到同一事务里处理。
 */
@Service
@RequiredArgsConstructor
public class LessonRecordServiceImpl implements LessonRecordService {

    private static final Set<String> COMPLETED_STATUSES = Set.of("COMPLETED", "MAKEUP_COMPLETED", "SUBSTITUTED");
    private static final Set<String> NON_CONFIRMABLE_PLANNING_STATUSES = Set.of("CANCELLED", "MERGED");
    private static final int MAX_CONFIRM_QUERY_DAYS = 180;

    private final LessonRecordMapper lessonRecordMapper;
    private final HourLedgerMapper hourLedgerMapper;
    private final PackageBalanceSnapshotMapper packageBalanceSnapshotMapper;
    private final LessonStatusRuleMapper lessonStatusRuleMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final NotificationOutboxService notificationOutboxService;
    private final NotificationTriggerRuleMapper notificationTriggerRuleMapper;

    @Override
    public PageResponse<LessonRecordListItemVO> pageRecords(LessonRecordPageQuery query) {
        validateConfirmedWindow(query);
        List<Long> matchedOccurrenceIds = findMatchedOccurrenceIds(query.getKeyword());
        List<Long> matchedStudentIds = findMatchedStudentIds(query.getKeyword());
        if (StringUtils.hasText(query.getKeyword()) && matchedOccurrenceIds.isEmpty() && matchedStudentIds.isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        LambdaQueryWrapper<LessonRecordEntity> wrapper = new LambdaQueryWrapper<LessonRecordEntity>()
                .and(StringUtils.hasText(query.getKeyword()), nested -> nested
                        .in(!matchedOccurrenceIds.isEmpty(), LessonRecordEntity::getOccurrenceId, matchedOccurrenceIds)
                        .or(!matchedStudentIds.isEmpty(), inner -> inner.in(LessonRecordEntity::getStudentId, matchedStudentIds)))
                .eq(query.getPackageId() != null, LessonRecordEntity::getPackageId, query.getPackageId())
                .eq(query.getStudentId() != null, LessonRecordEntity::getStudentId, query.getStudentId())
                .eq(query.getTeacherId() != null, LessonRecordEntity::getActualTeacherId, query.getTeacherId())
                .eq(StringUtils.hasText(query.getResultStatus()), LessonRecordEntity::getResultStatus, query.getResultStatus())
                .eq(StringUtils.hasText(query.getRecordSource()), LessonRecordEntity::getRecordSource, query.getRecordSource())
                .ge(query.getConfirmedFrom() != null, LessonRecordEntity::getConfirmedAt, query.getConfirmedFrom())
                .le(query.getConfirmedTo() != null, LessonRecordEntity::getConfirmedAt, query.getConfirmedTo())
                .orderByDesc(LessonRecordEntity::getConfirmedAt)
                .orderByDesc(LessonRecordEntity::getId);
        IPage<LessonRecordEntity> page = lessonRecordMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        if (page.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        return PageResponse.of(toListItems(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public LessonRecordDetailVO getRecord(Long id) {
        LessonRecordEntity record = requireRecord(id);
        LessonOccurrenceEntity occurrence = requireOccurrence(record.getOccurrenceId());
        CoursePackageEntity coursePackage = requirePackage(record.getPackageId());
        StudentEntity student = studentMapper.selectById(record.getStudentId());
        TeacherEntity plannedTeacher = record.getPlannedTeacherId() == null ? null : teacherMapper.selectById(record.getPlannedTeacherId());
        TeacherEntity actualTeacher = record.getActualTeacherId() == null ? null : teacherMapper.selectById(record.getActualTeacherId());
        ClassroomEntity plannedClassroom = record.getPlannedClassroomId() == null ? null : classroomMapper.selectById(record.getPlannedClassroomId());
        ClassroomEntity actualClassroom = record.getActualClassroomId() == null ? null : classroomMapper.selectById(record.getActualClassroomId());

        return LessonRecordDetailVO.builder()
                .id(record.getId())
                .occurrenceId(record.getOccurrenceId())
                .lessonNo(occurrence.getLessonNo())
                .packageId(record.getPackageId())
                .packageNo(coursePackage.getPackageNo())
                .studentId(record.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .plannedTeacherId(record.getPlannedTeacherId())
                .plannedTeacherName(plannedTeacher == null ? null : plannedTeacher.getTeacherName())
                .actualTeacherId(record.getActualTeacherId())
                .actualTeacherName(actualTeacher == null ? null : actualTeacher.getTeacherName())
                .plannedClassroomId(record.getPlannedClassroomId())
                .plannedClassroomName(plannedClassroom == null ? null : plannedClassroom.getRoomName())
                .actualClassroomId(record.getActualClassroomId())
                .actualClassroomName(actualClassroom == null ? null : actualClassroom.getRoomName())
                .resultStatus(record.getResultStatus())
                .actualStartAt(record.getActualStartAt())
                .actualEndAt(record.getActualEndAt())
                .actualUnits(UnitConverter.toHours(record.getActualUnits()))
                .shouldDeduct(record.getShouldDeduct() != null && record.getShouldDeduct() == 1)
                .deductedUnits(UnitConverter.toHours(record.getDeductedUnits()))
                .shouldExtendEndDate(record.getShouldExtendEndDate() != null && record.getShouldExtendEndDate() == 1)
                .confirmedByUserId(record.getConfirmedByUserId())
                .confirmedAt(record.getConfirmedAt())
                .recordSource(record.getRecordSource())
                .notes(record.getNotes())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonRecordConfirmResultVO confirmLesson(LessonRecordConfirmRequest request, CurrentUserResponse currentUser) {
        if (currentUser == null) {
            // In dev mode (auth disabled), fall back to a default admin identity
            // Dev-mode default: use the bootstrapped admin account which always exists (id from data init)
            currentUser = new CurrentUserResponse(2049011197944602625L, "admin", "System Administrator", "ADMIN", null, null, java.util.List.of("ADMIN"));
        }
        LessonOccurrenceEntity occurrence = requireConfirmableOccurrence(request.getOccurrenceId());
        if (lessonRecordMapper.selectOne(new LambdaQueryWrapper<LessonRecordEntity>()
                .eq(LessonRecordEntity::getOccurrenceId, occurrence.getId())) != null) {
            throw new BusinessException("LESSON_ALREADY_CONFIRMED", "This planned lesson has already been confirmed.");
        }
        CoursePackageEntity coursePackage = requirePackage(occurrence.getPackageId());
        StudentEntity student = requireStudent(occurrence.getStudentId());
        LessonStatusRuleEntity rule = requireActiveRule(request.getResultStatus());

        ConfirmationDecision decision = buildDecision(request, occurrence, coursePackage, rule);
        if (decision.shouldDeduct() && decision.deductedUnits() > coursePackage.getRemainingUnits()) {
            throw new BusinessException("INSUFFICIENT_REMAINING_UNITS", "The package does not have enough remaining units for this confirmation.");
        }

        LessonRecordEntity record = new LessonRecordEntity();
        record.setOccurrenceId(occurrence.getId());
        record.setPackageId(coursePackage.getId());
        record.setStudentId(student.getId());
        record.setPlannedTeacherId(occurrence.getPlannedTeacherId());
        record.setActualTeacherId(decision.actualTeacherId());
        record.setPlannedClassroomId(occurrence.getPlannedClassroomId());
        record.setActualClassroomId(decision.actualClassroomId());
        record.setResultStatus(rule.getStatusCode());
        record.setActualStartAt(decision.actualStartAt());
        record.setActualEndAt(decision.actualEndAt());
        record.setActualUnits(decision.actualUnits());
        record.setShouldDeduct(decision.shouldDeduct() ? 1 : 0);
        record.setDeductedUnits(decision.deductedUnits());
        record.setShouldExtendEndDate(decision.shouldExtendEndDate() ? 1 : 0);
        record.setConfirmedByUserId(currentUser.userId());
        record.setConfirmedAt(AppTime.nowLocalDateTime());
        record.setRecordSource(resolveRecordSource(currentUser));
        record.setNotes(request.getNotes());
        lessonRecordMapper.insert(record);

        // Mark the planned occurrence as merged — it is now a confirmed lesson record
        occurrence.setPlanningStatus("MERGED");
        lessonOccurrenceMapper.updateById(occurrence);

        PackageUpdateResult packageUpdate = applyPackageAndLedgerChanges(coursePackage, occurrence, rule, record, currentUser.userId(), decision);

        // Push one makeup occurrence to the end when extension is enabled (e.g. FORMAL_LEAVE, CANCELLED)
        if (decision.shouldExtendEndDate()) {
            LessonOccurrenceEntity lastOccurrence = lessonOccurrenceMapper.selectOne(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                    .eq(LessonOccurrenceEntity::getPackageId, coursePackage.getId())
                    .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                    .orderByDesc(LessonOccurrenceEntity::getPlannedStartAt)
                    .last("LIMIT 1"));
            if (lastOccurrence != null) {
                LessonOccurrenceEntity makeup = new LessonOccurrenceEntity();
                makeup.setLessonNo(generateLessonNo());
                makeup.setPackageId(coursePackage.getId());
                makeup.setStudentId(student.getId());
                makeup.setScheduleRuleId(lastOccurrence.getScheduleRuleId());
                makeup.setScheduleSlotId(lastOccurrence.getScheduleSlotId());
                makeup.setOccurrenceType("MAKEUP");
                makeup.setPlannedTeacherId(lastOccurrence.getPlannedTeacherId());
                makeup.setPlannedClassroomId(lastOccurrence.getPlannedClassroomId());
                makeup.setPlannedStartAt(lastOccurrence.getPlannedStartAt().plusWeeks(1));
                makeup.setPlannedEndAt(lastOccurrence.getPlannedEndAt().plusWeeks(1));
                makeup.setPlannedUnits(occurrence.getPlannedUnits());
                makeup.setPlanningStatus("SCHEDULED");
                makeup.setSourceOccurrenceId(occurrence.getId());
                makeup.setConflictChecked(1);
                lessonOccurrenceMapper.insert(makeup);

                // Refresh estimated end date with the new makeup occurrence included
                var newEndDate = recomputeEstimatedEndDate(coursePackage.getId(), packageUpdate.remainingUnits());
                coursePackage.setEstimatedEndDate(newEndDate);
                coursePackageMapper.updateById(coursePackage);
            }
        }

        // Dynamic adjustment: when actual duration differs from planned, redistribute the delta
        // across future occurrences (e.g., a 2h lesson confirmed as 1h → surplus goes to last lesson)
        int adjustmentDelta = decision.shouldDeduct()
                ? occurrence.getPlannedUnits() - decision.deductedUnits()
                : 0;
        if (adjustmentDelta != 0) {
            adjustFutureOccurrences(coursePackage.getId(), adjustmentDelta);
        }

        notificationOutboxService.queueLessonBalanceEmail(record, coursePackage, student, rule.getStatusName());

        // LOW_BALANCE 预警：扣课后余额低于阈值时发邮件
        NotificationTriggerRuleEntity lowBalanceRule = notificationTriggerRuleMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NotificationTriggerRuleEntity>()
                        .eq(NotificationTriggerRuleEntity::getTriggerType, "LOW_BALANCE")
                        .eq(NotificationTriggerRuleEntity::getEnabled, 1)
                        .eq(NotificationTriggerRuleEntity::getIsDeleted, 0)
                        .last("LIMIT 1"));
        if (lowBalanceRule != null && lowBalanceRule.getThresholdValue() != null
                && packageUpdate.remainingUnits() <= lowBalanceRule.getThresholdValue()) {
            notificationOutboxService.queueLowBalanceWarning(coursePackage, student);
        }

        return LessonRecordConfirmResultVO.builder()
                .lessonRecordId(record.getId())
                .occurrenceId(record.getOccurrenceId())
                .ledgerId(packageUpdate.ledgerId())
                .deductedUnits(UnitConverter.toHours(decision.deductedUnits()))
                .remainingUnits(UnitConverter.toHours(packageUpdate.remainingUnits()))
                .packageCompleted(packageUpdate.packageCompleted())
                .build();
    }

    private PackageUpdateResult applyPackageAndLedgerChanges(CoursePackageEntity coursePackage,
                                                             LessonOccurrenceEntity occurrence,
                                                             LessonStatusRuleEntity rule,
                                                             LessonRecordEntity record,
                                                             Long operatorId,
                                                             ConfirmationDecision decision) {
        int newConsumedUnits = coursePackage.getConsumedUnits() + decision.deductedUnits();
        int newRemainingUnits = coursePackage.getRemainingUnits() - decision.deductedUnits();
        boolean packageCompleted = newRemainingUnits == 0;

        HourLedgerEntity ledger = new HourLedgerEntity();
        ledger.setPackageId(coursePackage.getId());
        ledger.setLessonRecordId(record.getId());
        ledger.setOccurrenceId(occurrence.getId());
        ledger.setLedgerType(decision.shouldDeduct() ? "DEDUCT" : "NO_DEDUCT_AUDIT");
        ledger.setChangeUnits(decision.shouldDeduct() ? -decision.deductedUnits() : 0);
        ledger.setBalanceAfterUnits(newRemainingUnits);
        ledger.setBusinessDate(resolveBusinessDate(record.getConfirmedAt(), occurrence.getPlannedStartAt()));
        ledger.setReasonCode(rule.getStatusCode());
        ledger.setReasonDesc(buildLedgerReason(rule, decision.shouldDeduct(), decision.deductedUnits()));
        ledger.setOperatedBy(operatorId);
        ledger.setOperatedAt(record.getConfirmedAt());
        hourLedgerMapper.insert(ledger);

        coursePackage.setConsumedUnits(newConsumedUnits);
        coursePackage.setRemainingUnits(newRemainingUnits);
        coursePackage.setPackageStatus(packageCompleted ? "COMPLETED" : "ACTIVE");
        coursePackage.setCompletedAt(packageCompleted ? record.getConfirmedAt() : null);
        coursePackage.setEstimatedEndDate(packageCompleted
                ? record.getConfirmedAt().toLocalDate()
                : recomputeEstimatedEndDate(coursePackage.getId(), newRemainingUnits));
        int updated = coursePackageMapper.updateById(coursePackage);
        if (updated != 1) {
            throw new BusinessException("PACKAGE_CONCURRENT_UPDATE", "The package was updated by another request. Please retry.");
        }

        upsertBalanceSnapshot(coursePackage, ledger.getId());
        return new PackageUpdateResult(ledger.getId(), newRemainingUnits, packageCompleted);
    }

    /**
     * 预计结束日期不直接按“+7天”这类经验值顺延，而是按未来还可用的计划课次重新推算。
     */
    private LocalDate recomputeEstimatedEndDate(Long packageId, int remainingUnits) {
        if (remainingUnits <= 0) {
            return AppTime.nowLocalDateTime().toLocalDate();
        }
        List<Long> confirmedOccurrenceIds = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                        .eq(LessonRecordEntity::getPackageId, packageId))
                .stream()
                .map(LessonRecordEntity::getOccurrenceId)
                .toList();
        List<LessonOccurrenceEntity> futureOccurrences = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPackageId, packageId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .gt(LessonOccurrenceEntity::getPlannedStartAt, AppTime.nowLocalDateTime())
                .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));

        int accumulatedUnits = 0;
        LocalDate candidateDate = null;
        for (LessonOccurrenceEntity occurrence : futureOccurrences) {
            if (confirmedOccurrenceIds.contains(occurrence.getId())) {
                continue;
            }
            accumulatedUnits += occurrence.getPlannedUnits();
            candidateDate = occurrence.getPlannedEndAt().toLocalDate();
            if (accumulatedUnits >= remainingUnits) {
                return candidateDate;
            }
        }
        return candidateDate;
    }

    /**
     * Redistribute a surplus or deficit from the confirmed lesson across future scheduled occurrences.
     * Surplus (confirmed lesson used fewer units than planned) is added to the last occurrence.
     * Deficit (confirmed lesson used more units than planned) is subtracted from the last occurrence(s),
     * with each occurrence keeping at least 1 unit (0.5h).
     */
    private void adjustFutureOccurrences(Long packageId, int delta) {
        if (delta == 0) return;
        List<LessonOccurrenceEntity> futureOccurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getPackageId, packageId)
                        .eq(LessonOccurrenceEntity::getPlanningStatus, "SCHEDULED")
                        .gt(LessonOccurrenceEntity::getPlannedStartAt, AppTime.nowLocalDateTime())
                        .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));

        if (futureOccurrences.isEmpty()) return;

        if (delta > 0) {
            // Surplus: add to the last occurrence
            LessonOccurrenceEntity last = futureOccurrences.get(futureOccurrences.size() - 1);
            last.setPlannedUnits(last.getPlannedUnits() + delta);
            lessonOccurrenceMapper.updateById(last);
        } else {
            // Deficit: reduce from last occurrence backwards, min 1 unit per occurrence
            int toReduce = -delta;
            for (int i = futureOccurrences.size() - 1; i >= 0 && toReduce > 0; i--) {
                LessonOccurrenceEntity occ = futureOccurrences.get(i);
                int current = occ.getPlannedUnits();
                int canReduce = Math.min(current - 1, toReduce);
                if (canReduce > 0) {
                    occ.setPlannedUnits(current - canReduce);
                    lessonOccurrenceMapper.updateById(occ);
                    toReduce -= canReduce;
                }
            }
        }
    }

    private void upsertBalanceSnapshot(CoursePackageEntity coursePackage, Long lastLedgerId) {
        PackageBalanceSnapshotEntity snapshot = packageBalanceSnapshotMapper.selectById(coursePackage.getId());
        if (snapshot == null) {
            snapshot = new PackageBalanceSnapshotEntity();
            snapshot.setPackageId(coursePackage.getId());
            snapshot.setTotalUnits(coursePackage.getTotalUnits());
            snapshot.setConsumedUnits(coursePackage.getConsumedUnits());
            snapshot.setRemainingUnits(coursePackage.getRemainingUnits());
            snapshot.setLastLedgerId(lastLedgerId);
            snapshot.setSnapshotAt(AppTime.nowLocalDateTime());
            packageBalanceSnapshotMapper.insert(snapshot);
            return;
        }
        snapshot.setTotalUnits(coursePackage.getTotalUnits());
        snapshot.setConsumedUnits(coursePackage.getConsumedUnits());
        snapshot.setRemainingUnits(coursePackage.getRemainingUnits());
        snapshot.setLastLedgerId(lastLedgerId);
        snapshot.setSnapshotAt(AppTime.nowLocalDateTime());
        packageBalanceSnapshotMapper.updateById(snapshot);
    }

    private ConfirmationDecision buildDecision(LessonRecordConfirmRequest request,
                                               LessonOccurrenceEntity occurrence,
                                               CoursePackageEntity coursePackage,
                                               LessonStatusRuleEntity rule) {
        validateManualOverride(rule, request);
        ActualLessonData actualData = normalizeActualData(request, occurrence);
        Long actualTeacherId = normalizeActualTeacherId(request, occurrence, actualData.actualHappened());
        Long actualClassroomId = normalizeActualClassroomId(request, occurrence, actualData.actualHappened());

        boolean shouldDeduct = request.getOverrideShouldDeduct() != null
                ? request.getOverrideShouldDeduct()
                : rule.getDefaultShouldDeduct() != null && rule.getDefaultShouldDeduct() == 1;
        if (request.getOverrideDeductedUnits() != null) {
            shouldDeduct = shouldDeduct || request.getOverrideDeductedUnits() > 0;
        }
        boolean shouldExtend = request.getOverrideShouldExtendEndDate() != null
                ? request.getOverrideShouldExtendEndDate()
                : rule.getDefaultShouldExtend() != null && rule.getDefaultShouldExtend() == 1;

        int deductedUnits = resolveDeductedUnits(request, occurrence, rule, actualData.actualUnits(), shouldDeduct);
        if (deductedUnits > 0 && !shouldDeduct) {
            throw new BusinessException("INVALID_DEDUCTION_OVERRIDE", "Deducted units require deduction to be enabled.");
        }
        if (shouldDeduct && deductedUnits <= 0) {
            throw new BusinessException("INVALID_DEDUCTED_UNITS", "Deducted units must be greater than 0 when deduction is enabled.");
        }
        if (!shouldDeduct) {
            deductedUnits = 0;
        }

        return new ConfirmationDecision(
                actualTeacherId,
                actualClassroomId,
                actualData.actualStartAt(),
                actualData.actualEndAt(),
                actualData.actualUnits(),
                shouldDeduct,
                deductedUnits,
                shouldExtend
        );
    }

    private void validateManualOverride(LessonStatusRuleEntity rule, LessonRecordConfirmRequest request) {
        boolean hasOverride = request.getOverrideShouldDeduct() != null
                || request.getOverrideDeductedUnits() != null
                || request.getOverrideShouldExtendEndDate() != null;
        if (hasOverride && (rule.getAllowManualOverride() == null || rule.getAllowManualOverride() != 1)) {
            throw new BusinessException("MANUAL_OVERRIDE_NOT_ALLOWED", "Manual override is not allowed for this lesson status.");
        }
    }

    private ActualLessonData normalizeActualData(LessonRecordConfirmRequest request, LessonOccurrenceEntity occurrence) {
        LocalDateTime startAt = request.getActualStartAt();
        LocalDateTime endAt = request.getActualEndAt();
        Integer actualUnits = request.getActualUnits();
        boolean completedLike = COMPLETED_STATUSES.contains(request.getResultStatus());

        if ((startAt == null) != (endAt == null)) {
            throw new BusinessException("INCOMPLETE_ACTUAL_TIME", "Actual start and end times must either both be set or both be empty.");
        }
        if (startAt != null) {
            if (!endAt.isAfter(startAt)) {
                throw new BusinessException("INVALID_ACTUAL_TIME_RANGE", "Actual end time must be later than the start time.");
            }
            long minutes = ChronoUnit.MINUTES.between(startAt, endAt);
            if (minutes % 30 != 0) {
                throw new BusinessException("INVALID_ACTUAL_DURATION", "Actual lesson duration must be in 30-minute increments.");
            }
            int unitsFromTime = (int) (minutes / 30);
            if (actualUnits == null) {
                actualUnits = unitsFromTime;
            } else if (!Objects.equals(actualUnits, unitsFromTime)) {
                throw new BusinessException("ACTUAL_UNITS_MISMATCH", "Actual units must match the provided actual time range.");
            }
        }
        if (completedLike) {
            if (startAt == null) {
                startAt = occurrence.getPlannedStartAt();
                endAt = occurrence.getPlannedEndAt();
            }
            if (actualUnits == null) {
                actualUnits = occurrence.getPlannedUnits();
            }
            if (actualUnits <= 0) {
                throw new BusinessException("INVALID_ACTUAL_UNITS", "Completed lesson statuses require positive actual units.");
            }
        } else if (actualUnits == null) {
            actualUnits = 0;
        }

        return new ActualLessonData(startAt, endAt, actualUnits);
    }

    private Long normalizeActualTeacherId(LessonRecordConfirmRequest request, LessonOccurrenceEntity occurrence, boolean actualHappened) {
        Long actualTeacherId = request.getActualTeacherId();
        if (actualTeacherId == null && actualHappened) {
            actualTeacherId = occurrence.getPlannedTeacherId();
        }
        if (actualTeacherId != null) {
            TeacherEntity teacher = teacherMapper.selectById(actualTeacherId);
            if (teacher == null || !"ACTIVE".equalsIgnoreCase(teacher.getStatus())) {
                throw new BusinessException("TEACHER_NOT_AVAILABLE", "The selected actual teacher does not exist or is not active.");
            }
        }
        return actualTeacherId;
    }

    private Long normalizeActualClassroomId(LessonRecordConfirmRequest request, LessonOccurrenceEntity occurrence, boolean actualHappened) {
        Long actualClassroomId = request.getActualClassroomId();
        if (actualClassroomId == null && actualHappened) {
            actualClassroomId = occurrence.getPlannedClassroomId();
        }
        if (actualClassroomId != null) {
            ClassroomEntity classroom = classroomMapper.selectById(actualClassroomId);
            if (classroom == null || !"ACTIVE".equalsIgnoreCase(classroom.getStatus())) {
                throw new BusinessException("CLASSROOM_NOT_AVAILABLE", "The selected actual classroom does not exist or is not active.");
            }
        }
        return actualClassroomId;
    }

    private int resolveDeductedUnits(LessonRecordConfirmRequest request,
                                     LessonOccurrenceEntity occurrence,
                                     LessonStatusRuleEntity rule,
                                     int actualUnits,
                                     boolean shouldDeduct) {
        if (request.getOverrideDeductedUnits() != null) {
            return request.getOverrideDeductedUnits();
        }
        if (!shouldDeduct) {
            return 0;
        }
        return switch (rule.getDeductMode()) {
            case "ACTUAL_UNITS" -> actualUnits;
            case "PLANNED_UNITS" -> occurrence.getPlannedUnits();
            case "FIXED_UNITS" -> rule.getFixedUnits() == null ? 0 : rule.getFixedUnits();
            case "NONE" -> 0;
            default -> throw new BusinessException("UNSUPPORTED_DEDUCT_MODE", "Unsupported deduct mode for lesson status rule.");
        };
    }

    private String resolveRecordSource(CurrentUserResponse currentUser) {
        return currentUser.roleCodes().contains("TEACHER") ? "TEACHER" : "STAFF";
    }

    private LocalDate resolveBusinessDate(LocalDateTime confirmedAt, LocalDateTime plannedStartAt) {
        return plannedStartAt != null ? plannedStartAt.toLocalDate() : confirmedAt.toLocalDate();
    }

    private String buildLedgerReason(LessonStatusRuleEntity rule, boolean shouldDeduct, int deductedUnits) {
        if (!shouldDeduct) {
            return "No deduction for status " + rule.getStatusName() + ".";
        }
        return "Deducted " + deductedUnits + " units for status " + rule.getStatusName() + ".";
    }

    private List<LessonRecordListItemVO> toListItems(List<LessonRecordEntity> records) {
        Map<Long, LessonOccurrenceEntity> occurrenceMap = listOccurrencesByIds(records.stream().map(LessonRecordEntity::getOccurrenceId).collect(Collectors.toSet()));
        Map<Long, CoursePackageEntity> packageMap = listPackagesByIds(records.stream().map(LessonRecordEntity::getPackageId).collect(Collectors.toSet()));
        Map<Long, StudentEntity> studentMap = listStudentsByIds(records.stream().map(LessonRecordEntity::getStudentId).collect(Collectors.toSet()));
        Map<Long, TeacherEntity> teacherMap = listTeachersByIds(records.stream().map(LessonRecordEntity::getActualTeacherId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, ClassroomEntity> classroomMap = listClassroomsByIds(records.stream().map(LessonRecordEntity::getActualClassroomId).filter(Objects::nonNull).collect(Collectors.toSet()));

        return records.stream()
                .map(item -> {
                    LessonOccurrenceEntity occurrence = occurrenceMap.get(item.getOccurrenceId());
                    CoursePackageEntity coursePackage = packageMap.get(item.getPackageId());
                    StudentEntity student = studentMap.get(item.getStudentId());
                    TeacherEntity teacher = teacherMap.get(item.getActualTeacherId());
                    ClassroomEntity classroom = classroomMap.get(item.getActualClassroomId());
                    return LessonRecordListItemVO.builder()
                            .id(item.getId())
                            .occurrenceId(item.getOccurrenceId())
                            .lessonNo(occurrence == null ? null : occurrence.getLessonNo())
                            .packageId(item.getPackageId())
                            .packageNo(coursePackage == null ? null : coursePackage.getPackageNo())
                            .packageName(coursePackage == null ? null : coursePackage.getPackageName())
                            .studentId(item.getStudentId())
                            .studentName(student == null ? null : student.getStudentName())
                            .resultStatus(item.getResultStatus())
                            .actualTeacherId(item.getActualTeacherId())
                            .actualTeacherName(teacher == null ? null : teacher.getTeacherName())
                            .actualClassroomId(item.getActualClassroomId())
                            .actualClassroomName(classroom == null ? null : classroom.getRoomName())
                            .actualUnits(UnitConverter.toHours(item.getActualUnits()))
                            .shouldDeduct(item.getShouldDeduct() != null && item.getShouldDeduct() == 1)
                            .deductedUnits(UnitConverter.toHours(item.getDeductedUnits()))
                            .shouldExtendEndDate(item.getShouldExtendEndDate() != null && item.getShouldExtendEndDate() == 1)
                            .recordSource(item.getRecordSource())
                            .confirmedAt(item.getConfirmedAt())
                            .build();
                })
                .toList();
    }

    private void validateConfirmedWindow(LessonRecordPageQuery query) {
        if (query.getConfirmedFrom() != null && query.getConfirmedTo() != null) {
            if (!query.getConfirmedTo().isAfter(query.getConfirmedFrom())) {
                throw new BusinessException("INVALID_CONFIRM_WINDOW", "Confirmation end time must be later than the start time.");
            }
            if (query.getConfirmedFrom().plusDays(MAX_CONFIRM_QUERY_DAYS).isBefore(query.getConfirmedTo())) {
                throw new BusinessException("CONFIRM_WINDOW_TOO_LARGE", "Confirmation query window cannot exceed 180 days.");
            }
        }
    }

    private List<Long> findMatchedOccurrenceIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .and(wrapper -> wrapper.like(LessonOccurrenceEntity::getLessonNo, keyword)
                                .or()
                                .like(LessonOccurrenceEntity::getOccurrenceType, keyword)))
                .stream()
                .map(LessonOccurrenceEntity::getId)
                .toList();
    }

    private List<Long> findMatchedStudentIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        return studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                        .and(wrapper -> wrapper.like(StudentEntity::getStudentName, keyword)
                                .or()
                                .like(StudentEntity::getStudentNo, keyword)))
                .stream()
                .map(StudentEntity::getId)
                .toList();
    }

    private LessonRecordEntity requireRecord(Long id) {
        LessonRecordEntity record = lessonRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("LESSON_RECORD_NOT_FOUND", "Lesson record not found.");
        }
        return record;
    }

    private LessonOccurrenceEntity requireOccurrence(Long id) {
        LessonOccurrenceEntity occurrence = lessonOccurrenceMapper.selectById(id);
        if (occurrence == null) {
            throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "Lesson occurrence not found.");
        }
        return occurrence;
    }

    private LessonOccurrenceEntity requireConfirmableOccurrence(Long id) {
        LessonOccurrenceEntity occurrence = requireOccurrence(id);
        if (NON_CONFIRMABLE_PLANNING_STATUSES.contains(occurrence.getPlanningStatus())) {
            throw new BusinessException("LESSON_OCCURRENCE_NOT_CONFIRMABLE", "This planned lesson cannot be confirmed in its current planning status.");
        }
        return occurrence;
    }

    private CoursePackageEntity requirePackage(Long id) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectById(id);
        if (coursePackage == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        if (Set.of("DRAFT", "CLOSED").contains(coursePackage.getPackageStatus())) {
            throw new BusinessException("PACKAGE_NOT_CONFIRMABLE", "The package is not in a confirmable status.");
        }
        return coursePackage;
    }

    private StudentEntity requireStudent(Long id) {
        StudentEntity student = studentMapper.selectById(id);
        if (student == null) {
            throw new BusinessException("NOT_FOUND", "Student not found.");
        }
        return student;
    }

    private LessonStatusRuleEntity requireActiveRule(String statusCode) {
        LessonStatusRuleEntity rule = lessonStatusRuleMapper.selectOne(new LambdaQueryWrapper<LessonStatusRuleEntity>()
                .eq(LessonStatusRuleEntity::getStatusCode, statusCode)
                .eq(LessonStatusRuleEntity::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (rule == null) {
            throw new BusinessException("LESSON_STATUS_RULE_NOT_FOUND", "Lesson status rule not found or disabled.");
        }
        return rule;
    }

    private Map<Long, LessonOccurrenceEntity> listOccurrencesByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return lessonOccurrenceMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(LessonOccurrenceEntity::getId, item -> item));
    }

    private Map<Long, CoursePackageEntity> listPackagesByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return coursePackageMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CoursePackageEntity::getId, item -> item));
    }

    private Map<Long, StudentEntity> listStudentsByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return studentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(StudentEntity::getId, item -> item));
    }

    private Map<Long, TeacherEntity> listTeachersByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return teacherMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(TeacherEntity::getId, item -> item));
    }

    private Map<Long, ClassroomEntity> listClassroomsByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return classroomMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ClassroomEntity::getId, item -> item));
    }

    private record ActualLessonData(LocalDateTime actualStartAt,
                                    LocalDateTime actualEndAt,
                                    int actualUnits) {
        boolean actualHappened() {
            return actualStartAt != null || actualEndAt != null || actualUnits > 0;
        }
    }

    private String generateLessonNo() {
        return "LS" + AppTime.nowLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000));
    }

    private record ConfirmationDecision(Long actualTeacherId,
                                        Long actualClassroomId,
                                        LocalDateTime actualStartAt,
                                        LocalDateTime actualEndAt,
                                        int actualUnits,
                                        boolean shouldDeduct,
                                        int deductedUnits,
                                        boolean shouldExtendEndDate) {
    }

    private record PackageUpdateResult(Long ledgerId, int remainingUnits, boolean packageCompleted) {
    }
}
