package com.classdeduction.system.modules.lesson.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.finance.mapper.TeacherPaymentItemMapper;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.lesson.dto.LessonRecordConfirmRequest;
import com.classdeduction.system.modules.lesson.dto.LessonRecordRevokeRequest;
import com.classdeduction.system.modules.lesson.dto.LessonRecordPageQuery;
import com.classdeduction.system.modules.lesson.entity.HourLedgerEntity;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.entity.LessonRecordRevokeAuditEntity;
import com.classdeduction.system.modules.lesson.entity.LessonRecordMakeupEffectEntity;
import com.classdeduction.system.modules.lesson.entity.LessonStatusRuleEntity;
import com.classdeduction.system.modules.lesson.entity.PackageBalanceSnapshotEntity;
import com.classdeduction.system.modules.lesson.mapper.HourLedgerMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordRevokeAuditMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMakeupEffectMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonStatusRuleMapper;
import com.classdeduction.system.modules.lesson.mapper.PackageBalanceSnapshotMapper;
import com.classdeduction.system.modules.lesson.service.LessonRecordService;
import com.classdeduction.system.modules.lesson.vo.LessonRecordConfirmResultVO;
import com.classdeduction.system.modules.lesson.vo.LessonRecordDetailVO;
import com.classdeduction.system.modules.lesson.vo.LessonRecordListItemVO;
import com.classdeduction.system.modules.systemconfig.service.NotificationOutboxService;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.util.PrivateLessonAllocation;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.service.RecurringScheduleRuleService;
import com.classdeduction.system.modules.schedule.util.PrivateSchedulePeriod;
import com.classdeduction.system.modules.systemconfig.mapper.NotificationTriggerRuleMapper;
import com.classdeduction.system.modules.systemconfig.entity.NotificationTriggerRuleEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
    private static final String CONFIRM_STAGE_PENDING_ADMIN = "PENDING_ADMIN";
    private static final String CONFIRM_STAGE_CONFIRMED = "CONFIRMED";
    private static final String CONFIRM_STAGE_REJECTED = "REJECTED";
    private static final Set<String> ALLOWED_TEACHER_CONFIRM_STATUSES = Set.of("COMPLETED", "NO_SHOW", "FORMAL_LEAVE");
    private static final int MAX_CONFIRM_QUERY_DAYS = 180;

    private final LessonRecordMapper lessonRecordMapper;
    private final LessonRecordRevokeAuditMapper lessonRecordRevokeAuditMapper;
    private final LessonRecordMakeupEffectMapper lessonRecordMakeupEffectMapper;
    private final ClassGroupMapper classGroupMapper;
    private final HourLedgerMapper hourLedgerMapper;
    private final PackageBalanceSnapshotMapper packageBalanceSnapshotMapper;
    private final LessonStatusRuleMapper lessonStatusRuleMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final RecurringScheduleRuleMapper recurringScheduleRuleMapper;
    private final RecurringScheduleSlotMapper recurringScheduleSlotMapper;
    private final RecurringScheduleRuleService recurringScheduleRuleService;
    private final CoursePackageMapper coursePackageMapper;
    private final PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
    private final TeacherPaymentItemMapper teacherPaymentItemMapper;
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
                .eq(StringUtils.hasText(query.getConfirmStage()), LessonRecordEntity::getConfirmStage, query.getConfirmStage())
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
                .plannedStartAt(record.getPlannedStartAt())
                .plannedEndAt(record.getPlannedEndAt())
                .plannedUnits(UnitConverter.toHoursDecimal(record.getPlannedUnits()))
                .resultStatus(record.getResultStatus())
                .actualStartAt(record.getActualStartAt())
                .actualEndAt(record.getActualEndAt())
                .actualUnits(UnitConverter.toHoursDecimal(record.getActualUnits()))
                .shouldDeduct(record.getShouldDeduct() != null && record.getShouldDeduct() == 1)
                .deductedUnits(UnitConverter.toHoursDecimal(record.getDeductedUnits()))
                .shouldExtendEndDate(record.getShouldExtendEndDate() != null && record.getShouldExtendEndDate() == 1)
                .confirmStage(record.getConfirmStage())
                .confirmedByUserId(record.getConfirmedByUserId())
                .confirmedAt(record.getConfirmedAt())
                .teacherConfirmedAt(record.getTeacherConfirmedAt())
                .teacherConfirmedByUserId(record.getTeacherConfirmedByUserId())
                .adminConfirmedAt(record.getAdminConfirmedAt())
                .adminConfirmedByUserId(record.getAdminConfirmedByUserId())
                .rejectReason(record.getRejectReason())
                .rejectedAt(record.getRejectedAt())
                .rejectedByUserId(record.getRejectedByUserId())
                .recordSource(record.getRecordSource())
                .notes(record.getNotes())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonRecordConfirmResultVO confirmLesson(LessonRecordConfirmRequest request, CurrentUserResponse currentUser) {
        currentUser = defaultAdminIfMissing(currentUser);
        LessonOccurrenceEntity occurrence = requireConfirmableOccurrence(request.getOccurrenceId());
        if (occurrence.getPrivateLessonGroupId() != null) {
            if (isTeacherUser(currentUser)) {
                return initialConfirmPrivateLessonGroup(request, currentUser, occurrence);
            }
            return confirmPrivateLessonGroup(request, currentUser, occurrence);
        }
        if (isTeacherUser(currentUser)) {
            return initialConfirmLesson(request, currentUser);
        }

        LessonRecordEntity pendingRecord = findPendingRecordByOccurrenceId(request.getOccurrenceId());
        if (pendingRecord != null) {
            return adminConfirmLesson(pendingRecord.getId(), currentUser);
        }
        return adminDirectConfirmLesson(request, currentUser);
    }

    /**
     * 共享私教课的整组消课入口：校验所有成员后，在一个事务内写入每个学生的记录和流水。
     * 老师工资仍以同一个 occurrence 为结算单位，因此这里不会生成第二套 occurrence。
     */
    private LessonRecordConfirmResultVO confirmPrivateLessonGroup(LessonRecordConfirmRequest request,
                                                                  CurrentUserResponse currentUser,
                                                                  LessonOccurrenceEntity occurrence) {
        List<PrivateLessonGroupMemberEntity> members = activePrivateLessonGroupMembers(occurrence.getPrivateLessonGroupId());
        if (members.isEmpty()) {
            throw new BusinessException("PRIVATE_LESSON_GROUP_EMPTY", "This private lesson group has no active students.");
        }
        Map<Long, CoursePackageEntity> packageMap = coursePackageMapper.selectBatchIds(
                        members.stream().map(PrivateLessonGroupMemberEntity::getPackageId).toList())
                .stream()
                .collect(Collectors.toMap(CoursePackageEntity::getId, item -> item));
        for (PrivateLessonGroupMemberEntity member : members) {
            CoursePackageEntity coursePackage = packageMap.get(member.getPackageId());
            if (!canFinalizePrivateLessonPackage(coursePackage, request.getResultStatus())) {
                throw new BusinessException("PRIVATE_LESSON_MEMBER_PACKAGE_INVALID",
                        "A private lesson member package is missing or inactive for student " + member.getStudentId() + ".");
            }
        }

        List<LessonRecordEntity> existingRecords = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                .eq(LessonRecordEntity::getOccurrenceId, occurrence.getId())
                .eq(LessonRecordEntity::getIsDeleted, 0));
        Map<Long, LessonRecordEntity> existingRecordByPackage = existingRecords.stream()
                .filter(record -> record.getPackageId() != null)
                .collect(Collectors.toMap(LessonRecordEntity::getPackageId, item -> item, (left, right) -> left));
        if (!existingRecords.isEmpty()) {
            boolean completePendingGroup = existingRecordByPackage.size() == members.size()
                    && existingRecords.size() == members.size()
                    && existingRecords.stream().allMatch(record -> CONFIRM_STAGE_PENDING_ADMIN.equals(record.getConfirmStage())
                    || CONFIRM_STAGE_REJECTED.equals(record.getConfirmStage()));
            if (!completePendingGroup) {
                throw new BusinessException("LESSON_ALREADY_CONFIRMED", "This shared private lesson has already been confirmed.");
            }
        }

        PrivateLessonGroupMemberEntity primaryMember = members.stream()
                .filter(item -> Objects.equals(item.getPackageId(), occurrence.getPackageId()))
                .findFirst()
                .orElse(members.get(0));
        CoursePackageEntity primaryPackage = packageMap.get(primaryMember.getPackageId());
        StudentEntity primaryStudent = requireStudent(primaryMember.getStudentId());
        LessonStatusRuleEntity statusRule = requireActiveRule(request.getResultStatus());
        ConfirmationDecision decision = buildDecision(request, occurrence, primaryPackage, statusRule);
        if (decision.shouldDeduct()) {
            for (PrivateLessonGroupMemberEntity member : members) {
                CoursePackageEntity coursePackage = packageMap.get(member.getPackageId());
                if (decision.deductedUnits() > safeUnits(coursePackage.getRemainingUnits())) {
                    StudentEntity student = studentMapper.selectById(member.getStudentId());
                    throw new BusinessException("INSUFFICIENT_REMAINING_UNITS",
                            "Student " + (student == null ? member.getStudentId() : student.getStudentName())
                                    + " does not have enough remaining units for this shared lesson.");
                }
            }
        }

        if (requiresConfirmedOccurrenceConflictCheck(statusRule)) {
            for (PrivateLessonGroupMemberEntity member : members) {
                ensureNoConflictForConfirmedOccurrence(occurrence, decision, member.getStudentId());
            }
        }
        int originalPlannedUnits = occurrence.getPlannedUnits();
        MakeupPlan makeupPlan = buildMakeupIfNeeded(primaryPackage, primaryStudent, occurrence,
                originalPlannedUnits, decision, statusRule, existingRecordByPackage.get(primaryPackage.getId()));
        LessonOccurrenceEntity makeup = makeupPlan.occurrence();
        if (makeup != null) {
            makeup.setPrivateLessonGroupId(occurrence.getPrivateLessonGroupId());
            ensureNoConflictForMakeupOccurrence(makeup);
        }

        List<LessonRecordEntity> records = new ArrayList<>();
        List<PackageUpdateResult> packageUpdates = new ArrayList<>();
        LocalDateTime confirmedAt = AppTime.nowLocalDateTime();
        for (PrivateLessonGroupMemberEntity member : members) {
            CoursePackageEntity coursePackage = packageMap.get(member.getPackageId());
            StudentEntity student = requireStudent(member.getStudentId());
            LessonRecordEntity record = existingRecordByPackage.get(member.getPackageId());
            if (record == null) {
                record = new LessonRecordEntity();
            }
            fillRecordFromDecision(record, occurrence, coursePackage, student, statusRule, decision, request.getNotes());
            record.setConfirmStage(CONFIRM_STAGE_CONFIRMED);
            record.setRecordSource(resolveRecordSource(currentUser));
            record.setConfirmedByUserId(currentUser.userId());
            record.setConfirmedAt(confirmedAt);
            record.setAdminConfirmedAt(confirmedAt);
            record.setAdminConfirmedByUserId(currentUser.userId());
            record.setRejectReason(null);
            record.setRejectedAt(null);
            record.setRejectedByUserId(null);
            if (record.getId() == null) {
                lessonRecordMapper.insert(record);
            } else {
                lessonRecordMapper.updateById(record);
            }
            records.add(record);
            packageUpdates.add(applyPackageAndLedgerChanges(coursePackage, occurrence, statusRule, record,
                    currentUser.userId(), decision));
        }

        applyActualValuesToOccurrence(occurrence, decision);
        occurrence.setPlanningStatus("MERGED");
        lessonOccurrenceMapper.updateById(occurrence);
        if (makeup != null) {
            lessonOccurrenceMapper.insert(makeup);
            recordGeneratedMakeup(records.get(0), makeup);
        }
        applyLeaveCarryover(makeupPlan);
        // 共享课每个成员都必须在扣课后仍能覆盖有效排课与保留课次。
        for (PrivateLessonGroupMemberEntity member : members) {
            CoursePackageEntity updated = coursePackageMapper.selectById(member.getPackageId());
            recurringScheduleRuleService.validateBalanceReduction(member.getPackageId(), updated.getRemainingUnits());
        }
        reconcileScheduleAfterConfirmation(primaryPackage.getId());

        LocalDate makeupEndDate = makeup == null ? null : makeup.getPlannedEndAt().toLocalDate();
        for (int index = 0; index < members.size(); index++) {
            CoursePackageEntity coursePackage = packageMap.get(members.get(index).getPackageId());
            refreshEstimatedEndDateAfterScheduleChange(coursePackage, originalPlannedUnits, decision,
                    makeupEndDate);
        }

        for (int index = 0; index < members.size(); index++) {
            CoursePackageEntity coursePackage = packageMap.get(members.get(index).getPackageId());
            StudentEntity student = requireStudent(members.get(index).getStudentId());
            notificationOutboxService.queueLessonBalanceEmail(records.get(index), coursePackage, student, statusRule.getStatusName());
            queueLowBalanceWarningIfNeeded(coursePackage, student, packageUpdates.get(index));
        }

        int primaryIndex = members.indexOf(primaryMember);
        PackageUpdateResult primaryUpdate = packageUpdates.get(primaryIndex);
        return LessonRecordConfirmResultVO.builder()
                .lessonRecordId(records.get(primaryIndex).getId())
                .occurrenceId(occurrence.getId())
                .ledgerId(primaryUpdate.ledgerId())
                .deductedUnits(UnitConverter.toHoursDecimal(decision.deductedUnits()))
                .remainingUnits(UnitConverter.toHoursDecimal(primaryUpdate.remainingUnits()))
                .packageCompleted(packageUpdates.stream().allMatch(PackageUpdateResult::packageCompleted))
                .build();
    }

    /**
     * 多人私教沿用原有教师初确认流程：先为整组建立待复核记录，不扣课、不结算工资。
     * 管理员随后确认任意一条成员记录时，会复用同一 occurrence 把整组一次性结算。
     */
    private LessonRecordConfirmResultVO initialConfirmPrivateLessonGroup(LessonRecordConfirmRequest request,
                                                                          CurrentUserResponse currentUser,
                                                                          LessonOccurrenceEntity occurrence) {
        validateTeacherInitialConfirm(request, occurrence, currentUser);
        List<PrivateLessonGroupMemberEntity> members = activePrivateLessonGroupMembers(occurrence.getPrivateLessonGroupId());
        if (members.isEmpty()) {
            throw new BusinessException("PRIVATE_LESSON_GROUP_EMPTY", "This private lesson group has no active students.");
        }
        List<LessonRecordEntity> existingRecords = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                .eq(LessonRecordEntity::getOccurrenceId, occurrence.getId())
                .eq(LessonRecordEntity::getIsDeleted, 0));
        Map<Long, LessonRecordEntity> existingRecordByPackage = existingRecords.stream()
                .filter(record -> record.getPackageId() != null)
                .collect(Collectors.toMap(LessonRecordEntity::getPackageId, item -> item, (left, right) -> left));
        if (!existingRecords.isEmpty() && (existingRecords.size() != members.size()
                || existingRecordByPackage.size() != members.size()
                || existingRecords.stream().anyMatch(record -> !CONFIRM_STAGE_PENDING_ADMIN.equals(record.getConfirmStage())
                && !CONFIRM_STAGE_REJECTED.equals(record.getConfirmStage())))) {
            throw new BusinessException("LESSON_ALREADY_CONFIRMED", "This shared private lesson has already been submitted.");
        }

        Map<Long, CoursePackageEntity> packageMap = coursePackageMapper.selectBatchIds(
                        members.stream().map(PrivateLessonGroupMemberEntity::getPackageId).toList())
                .stream()
                .collect(Collectors.toMap(CoursePackageEntity::getId, item -> item));
        PrivateLessonGroupMemberEntity primaryMember = members.stream()
                .filter(item -> Objects.equals(item.getPackageId(), occurrence.getPackageId()))
                .findFirst()
                .orElse(members.get(0));
        CoursePackageEntity primaryPackage = packageMap.get(primaryMember.getPackageId());
        if (!"ACTIVE".equals(primaryPackage == null ? null : primaryPackage.getPackageStatus())) {
            throw new BusinessException("PRIVATE_LESSON_MEMBER_PACKAGE_INVALID", "The primary private lesson package is not active.");
        }
        LessonStatusRuleEntity statusRule = requireActiveRule(request.getResultStatus());
        ConfirmationDecision decision = buildDecision(request, occurrence, primaryPackage, statusRule);
        LocalDateTime submittedAt = AppTime.nowLocalDateTime();
        LessonRecordEntity primaryRecord = null;
        for (PrivateLessonGroupMemberEntity member : members) {
            CoursePackageEntity coursePackage = packageMap.get(member.getPackageId());
            if (coursePackage == null || !"ACTIVE".equals(coursePackage.getPackageStatus())) {
                throw new BusinessException("PRIVATE_LESSON_MEMBER_PACKAGE_INVALID",
                        "A private lesson member package is missing or inactive for student " + member.getStudentId() + ".");
            }
            StudentEntity student = requireStudent(member.getStudentId());
            LessonRecordEntity record = existingRecordByPackage.get(member.getPackageId());
            if (record == null) {
                record = new LessonRecordEntity();
            }
            fillRecordFromDecision(record, occurrence, coursePackage, student, statusRule, decision, request.getNotes());
            record.setConfirmStage(CONFIRM_STAGE_PENDING_ADMIN);
            record.setRecordSource("TEACHER");
            record.setConfirmedByUserId(null);
            record.setConfirmedAt(null);
            record.setTeacherConfirmedAt(submittedAt);
            record.setTeacherConfirmedByUserId(currentUser.userId());
            record.setAdminConfirmedAt(null);
            record.setAdminConfirmedByUserId(null);
            record.setRejectReason(null);
            record.setRejectedAt(null);
            record.setRejectedByUserId(null);
            if (record.getId() == null) {
                lessonRecordMapper.insert(record);
            } else {
                lessonRecordMapper.updateById(record);
            }
            if (Objects.equals(member.getPackageId(), primaryMember.getPackageId())) {
                primaryRecord = record;
            }
        }
        return LessonRecordConfirmResultVO.builder()
                .lessonRecordId(primaryRecord == null ? null : primaryRecord.getId())
                .occurrenceId(occurrence.getId())
                .ledgerId(null)
                .deductedUnits(UnitConverter.toHoursDecimal(decision.deductedUnits()))
                .remainingUnits(UnitConverter.toHoursDecimal(safeUnits(primaryPackage.getRemainingUnits())))
                .packageCompleted(false)
                .build();
    }

    private boolean canFinalizePrivateLessonPackage(CoursePackageEntity coursePackage, String resultStatus) {
        if (coursePackage == null) return false;
        return "ACTIVE".equals(coursePackage.getPackageStatus())
                || ("CANCELLED".equals(resultStatus) && "PAUSED".equals(coursePackage.getPackageStatus()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonRecordConfirmResultVO initialConfirmLesson(LessonRecordConfirmRequest request, CurrentUserResponse currentUser) {
        currentUser = defaultAdminIfMissing(currentUser);
        LessonOccurrenceEntity occurrence = requireConfirmableOccurrence(request.getOccurrenceId());
        validateTeacherInitialConfirm(request, occurrence, currentUser);
        LessonRecordEntity existingRecord = findLatestRecordByOccurrenceId(occurrence.getId());
        if (existingRecord != null && !CONFIRM_STAGE_REJECTED.equals(existingRecord.getConfirmStage())) {
            throw new BusinessException("LESSON_ALREADY_CONFIRMED", "This planned lesson has already been confirmed.");
        }
        CoursePackageEntity coursePackage = requirePackage(occurrence.getPackageId());
        StudentEntity student = requireStudent(occurrence.getStudentId());
        LessonStatusRuleEntity rule = requireActiveRule(request.getResultStatus());
        ConfirmationDecision decision = buildDecision(request, occurrence, coursePackage, rule);

        LessonRecordEntity record = existingRecord == null ? new LessonRecordEntity() : existingRecord;
        fillRecordFromDecision(record, occurrence, coursePackage, student, rule, decision, request.getNotes());
        record.setConfirmStage(CONFIRM_STAGE_PENDING_ADMIN);
        record.setConfirmedByUserId(null);
        record.setConfirmedAt(null);
        record.setTeacherConfirmedAt(AppTime.nowLocalDateTime());
        record.setTeacherConfirmedByUserId(currentUser.userId());
        record.setAdminConfirmedAt(null);
        record.setAdminConfirmedByUserId(null);
        record.setRejectReason(null);
        record.setRejectedAt(null);
        record.setRejectedByUserId(null);
        record.setRecordSource("TEACHER");
        record.setNotes(request.getNotes());
        if (record.getId() == null) {
            lessonRecordMapper.insert(record);
        } else {
            lessonRecordMapper.updateById(record);
        }

        return LessonRecordConfirmResultVO.builder()
                .lessonRecordId(record.getId())
                .occurrenceId(record.getOccurrenceId())
                .ledgerId(null)
                .deductedUnits(UnitConverter.toHoursDecimal(record.getDeductedUnits()))
                .remainingUnits(UnitConverter.toHoursDecimal(coursePackage.getRemainingUnits()))
                .packageCompleted(false)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonRecordConfirmResultVO adminConfirmLesson(Long recordId, CurrentUserResponse currentUser) {
        currentUser = defaultAdminIfMissing(currentUser);
        LessonRecordEntity record = requireRecord(recordId);
        LessonOccurrenceEntity occurrence = requireConfirmableOccurrence(record.getOccurrenceId());
        if (occurrence.getPrivateLessonGroupId() != null) {
            if (!CONFIRM_STAGE_PENDING_ADMIN.equals(record.getConfirmStage())) {
                throw new BusinessException("INVALID_CONFIRM_STAGE", "Only pending lesson records can be finally confirmed.");
            }
            return confirmPrivateLessonGroup(requestFromRecord(record, occurrence), currentUser, occurrence);
        }
        if (!CONFIRM_STAGE_PENDING_ADMIN.equals(record.getConfirmStage())) {
            throw new BusinessException("INVALID_CONFIRM_STAGE", "Only pending lesson records can be finally confirmed.");
        }
        CoursePackageEntity coursePackage = requirePackage(record.getPackageId());
        StudentEntity student = requireStudent(record.getStudentId());
        LessonStatusRuleEntity rule = requireActiveRule(record.getResultStatus());
        ConfirmationDecision decision = buildDecision(record);
        return settleConfirmedRecord(record, occurrence, coursePackage, student, rule, decision, currentUser, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LessonRecordConfirmResultVO adminConfirmLesson(Long recordId,
                                                          LessonRecordConfirmRequest request,
                                                          CurrentUserResponse currentUser) {
        if (request == null) {
            return adminConfirmLesson(recordId, currentUser);
        }
        currentUser = defaultAdminIfMissing(currentUser);
        LessonRecordEntity record = requireRecord(recordId);
        LessonOccurrenceEntity occurrence = requireConfirmableOccurrence(record.getOccurrenceId());
        if (request.getOccurrenceId() != null && !Objects.equals(request.getOccurrenceId(), occurrence.getId())) {
            throw new BusinessException("OCCURRENCE_MISMATCH", "The request occurrence does not match this pending lesson record.");
        }
        if (occurrence.getPrivateLessonGroupId() != null) {
            if (!CONFIRM_STAGE_PENDING_ADMIN.equals(record.getConfirmStage())) {
                throw new BusinessException("INVALID_CONFIRM_STAGE", "Only pending lesson records can be finally confirmed.");
            }
            return confirmPrivateLessonGroup(mergeAdminConfirmRequest(record, occurrence, request), currentUser, occurrence);
        }
        if (!CONFIRM_STAGE_PENDING_ADMIN.equals(record.getConfirmStage())) {
            throw new BusinessException("INVALID_CONFIRM_STAGE", "Only pending lesson records can be finally confirmed.");
        }
        CoursePackageEntity coursePackage = requirePackage(record.getPackageId());
        StudentEntity student = requireStudent(record.getStudentId());
        LessonRecordConfirmRequest normalizedRequest = mergeAdminConfirmRequest(record, occurrence, request);
        LessonStatusRuleEntity rule = requireActiveRule(normalizedRequest.getResultStatus());
        ConfirmationDecision decision = buildDecision(normalizedRequest, occurrence, coursePackage, rule);
        fillRecordFromDecision(record, occurrence, coursePackage, student, rule, decision, normalizedRequest.getNotes());
        record.setRecordSource(resolveRecordSource(currentUser));
        return settleConfirmedRecord(record, occurrence, coursePackage, student, rule, decision, currentUser, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectLesson(Long recordId, String rejectReason, CurrentUserResponse currentUser) {
        currentUser = defaultAdminIfMissing(currentUser);
        if (!StringUtils.hasText(rejectReason)) {
            throw new BusinessException("REJECT_REASON_REQUIRED", "Reject reason is required.");
        }
        LessonRecordEntity record = requireRecord(recordId);
        // 与改排课、最终确认共用 occurrence → record 的锁顺序，避免拒绝提交和回收同时覆盖状态。
        LessonOccurrenceEntity occurrence = lessonOccurrenceMapper.selectByIdForUpdate(record.getOccurrenceId());
        if (occurrence == null) {
            throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "Lesson occurrence not found.");
        }
        record = lessonRecordMapper.selectByIdForUpdate(recordId);
        if (record == null) {
            throw new BusinessException("LESSON_RECORD_NOT_FOUND", "Lesson record not found.");
        }
        if (!CONFIRM_STAGE_PENDING_ADMIN.equals(record.getConfirmStage())) {
            throw new BusinessException("INVALID_CONFIRM_STAGE", "Only pending lesson records can be rejected.");
        }
        if (occurrence != null && occurrence.getPrivateLessonGroupId() != null) {
            List<LessonRecordEntity> groupRecords = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                    .eq(LessonRecordEntity::getOccurrenceId, occurrence.getId())
                    .eq(LessonRecordEntity::getConfirmStage, CONFIRM_STAGE_PENDING_ADMIN)
                    .eq(LessonRecordEntity::getIsDeleted, 0));
            groupRecords = lessonRecordMapper.selectByIdsForUpdate(groupRecords.stream()
                    .map(LessonRecordEntity::getId).sorted().toList());
            groupRecords = groupRecords.stream()
                    .filter(item -> CONFIRM_STAGE_PENDING_ADMIN.equals(item.getConfirmStage()))
                    .toList();
            if (groupRecords.isEmpty()) {
                throw new BusinessException("INVALID_CONFIRM_STAGE", "Only pending lesson records can be rejected.");
            }
            LocalDateTime rejectedAt = AppTime.nowLocalDateTime();
            for (LessonRecordEntity groupRecord : groupRecords) {
                groupRecord.setConfirmStage(CONFIRM_STAGE_REJECTED);
                groupRecord.setRejectReason(rejectReason);
                groupRecord.setRejectedAt(rejectedAt);
                groupRecord.setRejectedByUserId(currentUser.userId());
                lessonRecordMapper.updateById(groupRecord);
            }
            restoreOccurrence(occurrence.getId());
            return;
        }
        record.setConfirmStage(CONFIRM_STAGE_REJECTED);
        record.setRejectReason(rejectReason);
        record.setRejectedAt(AppTime.nowLocalDateTime());
        record.setRejectedByUserId(currentUser.userId());
        lessonRecordMapper.updateById(record);
        restoreOccurrence(record.getOccurrenceId());
    }

    private LessonRecordConfirmResultVO adminDirectConfirmLesson(LessonRecordConfirmRequest request, CurrentUserResponse currentUser) {
        LessonOccurrenceEntity occurrence = requireConfirmableOccurrence(request.getOccurrenceId());
        LessonRecordEntity existingRecord = findLatestRecordByOccurrenceId(occurrence.getId());
        if (existingRecord != null && !CONFIRM_STAGE_REJECTED.equals(existingRecord.getConfirmStage())) {
            throw new BusinessException("LESSON_ALREADY_CONFIRMED", "This planned lesson has already been confirmed.");
        }
        CoursePackageEntity coursePackage = requirePackage(occurrence.getPackageId());
        StudentEntity student = requireStudent(occurrence.getStudentId());
        LessonStatusRuleEntity rule = requireActiveRule(request.getResultStatus());
        ConfirmationDecision decision = buildDecision(request, occurrence, coursePackage, rule);

        LessonRecordEntity record = existingRecord == null ? new LessonRecordEntity() : existingRecord;
        fillRecordFromDecision(record, occurrence, coursePackage, student, rule, decision, request.getNotes());
        record.setConfirmStage(CONFIRM_STAGE_CONFIRMED);
        record.setRecordSource(resolveRecordSource(currentUser));
        record.setTeacherConfirmedAt(null);
        record.setTeacherConfirmedByUserId(null);
        record.setRejectReason(null);
        record.setRejectedAt(null);
        record.setRejectedByUserId(null);
        if (record.getId() == null) {
            lessonRecordMapper.insert(record);
        } else {
            lessonRecordMapper.updateById(record);
        }
        return settleConfirmedRecord(record, occurrence, coursePackage, student, rule, decision, currentUser, false);
    }

    private LessonRecordConfirmResultVO settleConfirmedRecord(LessonRecordEntity record,
                                                              LessonOccurrenceEntity occurrence,
                                                              CoursePackageEntity coursePackage,
                                                              StudentEntity student,
                                                              LessonStatusRuleEntity rule,
                                                              ConfirmationDecision decision,
                                                              CurrentUserResponse currentUser,
                                                              boolean requirePendingClaim) {
        if (decision.shouldDeduct() && decision.deductedUnits() > coursePackage.getRemainingUnits()) {
            throw new BusinessException("INSUFFICIENT_REMAINING_UNITS", "The package does not have enough remaining units for this confirmation.");
        }
        if (requiresConfirmedOccurrenceConflictCheck(rule)) {
            ensureNoConflictForConfirmedOccurrence(occurrence, decision);
        }
        int originalPlannedUnits = occurrence.getPlannedUnits();
        MakeupPlan makeupPlan = buildMakeupIfNeeded(
                coursePackage, student, occurrence, originalPlannedUnits, decision, rule, record);
        LessonOccurrenceEntity makeup = makeupPlan.occurrence();
        if (makeup != null) {
            ensureNoConflictForMakeupOccurrence(makeup);
        }

        LocalDateTime confirmedAt = AppTime.nowLocalDateTime();
        record.setConfirmStage(CONFIRM_STAGE_CONFIRMED);
        record.setConfirmedByUserId(currentUser.userId());
        record.setConfirmedAt(confirmedAt);
        record.setAdminConfirmedAt(confirmedAt);
        record.setAdminConfirmedByUserId(currentUser.userId());
        record.setRejectReason(null);
        record.setRejectedAt(null);
        record.setRejectedByUserId(null);
        if (requirePendingClaim) {
            claimPendingRecordForFinalConfirm(record);
        } else {
            lessonRecordMapper.updateById(record);
        }

        applyActualValuesToOccurrence(occurrence, decision);
        occurrence.setPlanningStatus("MERGED");
        lessonOccurrenceMapper.updateById(occurrence);

        PackageUpdateResult packageUpdate = applyPackageAndLedgerChanges(coursePackage, occurrence, rule, record, currentUser.userId(), decision);
        if (makeup != null) {
            lessonOccurrenceMapper.insert(makeup);
            recordGeneratedMakeup(record, makeup);
        }
        applyLeaveCarryover(makeupPlan);
        // 实际扣课可能大于原预留；在同一事务内复核余额，避免旧课扣课挤占当前分配。
        CoursePackageEntity updatedPackage = coursePackageMapper.selectById(coursePackage.getId());
        recurringScheduleRuleService.validateBalanceReduction(coursePackage.getId(), updatedPackage.getRemainingUnits());
        reconcileScheduleAfterConfirmation(coursePackage.getId());
        LocalDate makeupEndDate = makeup == null ? null : makeup.getPlannedEndAt().toLocalDate();
        refreshEstimatedEndDateAfterScheduleChange(coursePackage, originalPlannedUnits, decision,
                makeupEndDate);

        notificationOutboxService.queueLessonBalanceEmail(record, coursePackage, student, rule.getStatusName());
        queueLowBalanceWarningIfNeeded(coursePackage, student, packageUpdate);

        return LessonRecordConfirmResultVO.builder()
                .lessonRecordId(record.getId())
                .occurrenceId(record.getOccurrenceId())
                .ledgerId(packageUpdate.ledgerId())
                .deductedUnits(UnitConverter.toHoursDecimal(decision.deductedUnits()))
                .remainingUnits(UnitConverter.toHoursDecimal(packageUpdate.remainingUnits()))
                .packageCompleted(packageUpdate.packageCompleted())
                .build();
    }

    private void claimPendingRecordForFinalConfirm(LessonRecordEntity record) {
        int updated = lessonRecordMapper.update(record, new LambdaUpdateWrapper<LessonRecordEntity>()
                .eq(LessonRecordEntity::getId, record.getId())
                .eq(LessonRecordEntity::getConfirmStage, CONFIRM_STAGE_PENDING_ADMIN)
                .eq(LessonRecordEntity::getIsDeleted, 0));
        if (updated != 1) {
            throw new BusinessException("INVALID_CONFIRM_STAGE", "Only pending lesson records can be finally confirmed.");
        }
    }

    private void ensureNoConflictForConfirmedOccurrence(LessonOccurrenceEntity occurrence,
                                                        ConfirmationDecision decision) {
        ensureNoConflictForConfirmedOccurrence(occurrence, decision, occurrence.getStudentId());
    }

    /** 请假没有实际占用原课次时段；补课课次仍由单独的冲突校验负责。 */
    private boolean requiresConfirmedOccurrenceConflictCheck(LessonStatusRuleEntity rule) {
        return rule == null || !"FORMAL_LEAVE".equalsIgnoreCase(rule.getStatusCode());
    }

    private void ensureNoConflictForConfirmedOccurrence(LessonOccurrenceEntity occurrence,
                                                        ConfirmationDecision decision,
                                                        Long studentId) {
        LessonOccurrenceEntity confirmed = new LessonOccurrenceEntity();
        confirmed.setId(occurrence.getId());
        confirmed.setStudentId(studentId);
        confirmed.setPlannedTeacherId(decision.actualTeacherId() != null
                ? decision.actualTeacherId() : occurrence.getPlannedTeacherId());
        confirmed.setPlannedClassroomId(decision.actualClassroomId() != null
                ? decision.actualClassroomId() : occurrence.getPlannedClassroomId());
        confirmed.setPlannedStartAt(decision.actualStartAt() != null
                ? decision.actualStartAt() : occurrence.getPlannedStartAt());
        confirmed.setPlannedEndAt(decision.actualEndAt() != null
                ? decision.actualEndAt() : occurrence.getPlannedEndAt());
        ensureNoConflictForOccurrence(confirmed, "for the confirmed lesson");
    }

    private void queueLowBalanceWarningIfNeeded(CoursePackageEntity coursePackage,
                                                StudentEntity student,
                                                PackageUpdateResult packageUpdate) {
        NotificationTriggerRuleEntity lowBalanceRule = notificationTriggerRuleMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NotificationTriggerRuleEntity>()
                        .eq(NotificationTriggerRuleEntity::getTriggerType, "LOW_BALANCE_HOUR")
                        .eq(NotificationTriggerRuleEntity::getEnabled, 1)
                        .eq(NotificationTriggerRuleEntity::getIsDeleted, 0)
                        .last("LIMIT 1"));
        if (lowBalanceRule != null && lowBalanceRule.getThresholdValue() != null
                && packageUpdate.remainingUnits() <= lowBalanceRule.getThresholdValue()) {
            notificationOutboxService.queueLowBalanceWarning(coursePackage, student);
        }
    }

    /** 请假补课沿用当前排课课位；只有旧周期请假才增加当前分配额。 */
    private MakeupPlan buildMakeupIfNeeded(CoursePackageEntity coursePackage,
                                           StudentEntity student,
                                           LessonOccurrenceEntity occurrence,
                                           int originalPlannedUnits,
                                           ConfirmationDecision decision,
                                           LessonStatusRuleEntity statusRule,
                                           LessonRecordEntity record) {
        if (!decision.shouldExtendEndDate()) {
            return new MakeupPlan(null, null, null, 0);
        }
        LessonOccurrenceEntity lastOccurrence = lessonOccurrenceMapper.selectOne(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPackageId, coursePackage.getId())
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .orderByDesc(LessonOccurrenceEntity::getPlannedStartAt)
                .last("LIMIT 1"));
        if (lastOccurrence == null) {
            return new MakeupPlan(null, null, null, 0);
        }
        RecurringScheduleRuleEntity currentRule = "FORMAL_LEAVE".equals(statusRule.getStatusCode())
                ? currentPrivateRule(coursePackage.getId(), occurrence.getPrivateLessonGroupId()) : null;
        List<RecurringScheduleSlotEntity> currentSlots = currentRule == null ? List.of()
                : recurringScheduleSlotMapper.selectList(new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                        .eq(RecurringScheduleSlotEntity::getRuleId, currentRule.getId())
                        .eq(RecurringScheduleSlotEntity::getSlotStatus, "ACTIVE"));
        boolean previousPeriodLeave = currentRule != null && !currentSlots.isEmpty()
                && !PrivateSchedulePeriod.contains(occurrence, record, currentRule,
                        PrivateSchedulePeriod.startAt(currentRule));
        RecurringScheduleSlotEntity targetSlot = currentSlots.stream()
                .filter(slot -> Objects.equals(slot.getId(), occurrence.getScheduleSlotId()))
                .findFirst()
                .orElseGet(() -> currentSlots.stream()
                        .filter(slot -> Objects.equals(slot.getTeacherId(), occurrence.getPlannedTeacherId()))
                        .findFirst().orElse(currentSlots.isEmpty() ? null : currentSlots.get(0)));
        if (targetSlot != null && (targetSlot.getStartTime() == null || targetSlot.getWeekday() == null)) {
            throw new BusinessException("INVALID_SCHEDULE_SLOT", "The current weekly slot needs a day and time before leave can be extended.");
        }
        LessonOccurrenceEntity currentTail = null;
        if (targetSlot != null) {
            currentTail = lessonOccurrenceMapper.selectOne(
                    new LambdaQueryWrapper<LessonOccurrenceEntity>()
                            .eq(LessonOccurrenceEntity::getScheduleRuleId, currentRule.getId())
                            .eq(LessonOccurrenceEntity::getScheduleSlotId, targetSlot.getId())
                            .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                            .orderByDesc(LessonOccurrenceEntity::getPlannedStartAt)
                            .last("LIMIT 1"));
            if (currentTail != null) {
                lastOccurrence = currentTail;
            }
        }
        LessonOccurrenceEntity makeup = new LessonOccurrenceEntity();
        makeup.setLessonNo(BusinessNoGenerator.generateLessonOccurrenceNo());
        makeup.setPackageId(coursePackage.getId());
        makeup.setStudentId(student.getId());
        makeup.setScheduleRuleId(targetSlot == null ? lastOccurrence.getScheduleRuleId() : currentRule.getId());
        makeup.setScheduleSlotId(targetSlot == null ? lastOccurrence.getScheduleSlotId() : targetSlot.getId());
        makeup.setAllocationOwnerTeacherId(targetSlot == null
                ? lastOccurrence.getAllocationOwnerTeacherId() : targetSlot.getTeacherId());
        makeup.setOccurrenceType("MAKEUP");
        makeup.setLeaveCarryoverUnits(previousPeriodLeave ? originalPlannedUnits : 0);
        makeup.setPlannedTeacherId(targetSlot == null ? lastOccurrence.getPlannedTeacherId() : targetSlot.getTeacherId());
        makeup.setSubjectName(targetSlot == null ? lastOccurrence.getSubjectName() : targetSlot.getSubjectName());
        makeup.setPlannedClassroomId(targetSlot == null ? lastOccurrence.getPlannedClassroomId() : targetSlot.getClassroomId());
        makeup.setDeliveryMode(targetSlot == null ? lastOccurrence.getDeliveryMode() : targetSlot.getDeliveryMode());
        LocalDateTime makeupStart = lastOccurrence.getPlannedStartAt().plusWeeks(1);
        if (targetSlot != null) {
            LocalDateTime now = AppTime.nowLocalDateTime();
            LocalDateTime after = currentTail == null ? now : currentTail.getPlannedStartAt();
            LocalDate firstDate = currentRule.getStartDate() == null ? after.toLocalDate()
                    : currentRule.getStartDate();
            makeupStart = LocalDateTime.of(firstDate.isAfter(after.toLocalDate()) ? firstDate : after.toLocalDate(),
                    targetSlot.getStartTime());
            while (!makeupStart.isAfter(after)
                    || makeupStart.getDayOfWeek().getValue() != targetSlot.getWeekday()) {
                makeupStart = makeupStart.plusDays(1);
            }
        }
        makeup.setPlannedStartAt(makeupStart);
        makeup.setPlannedEndAt(makeupStart.plusMinutes(originalPlannedUnits * 30L));
        makeup.setPlannedUnits(originalPlannedUnits);
        makeup.setPlanningStatus("SCHEDULED");
        makeup.setSourceOccurrenceId(occurrence.getId());
        makeup.setConflictChecked(1);
        if (targetSlot != null) {
            // 现有排课可能在末尾占用若干周，补课顺延到首个不冲突的同一时段。
            for (int week = 0; week < 104 && (makeup.getPlannedStartAt().isBefore(AppTime.nowLocalDateTime())
                    || hasAnyAdjustedOccurrenceConflict(makeup)); week++) {
                makeup.setPlannedStartAt(makeup.getPlannedStartAt().plusWeeks(1));
                makeup.setPlannedEndAt(makeup.getPlannedEndAt().plusWeeks(1));
            }
            if (makeup.getPlannedStartAt().isBefore(AppTime.nowLocalDateTime())
                    || hasAnyAdjustedOccurrenceConflict(makeup)) {
                throw new BusinessException("MAKEUP_SLOT_UNAVAILABLE", "No available weekly time was found for the makeup lesson.");
            }
        }
        return new MakeupPlan(makeup, previousPeriodLeave ? currentRule.getId() : null,
                previousPeriodLeave ? targetSlot.getId() : null,
                previousPeriodLeave ? originalPlannedUnits : 0);
    }

    private RecurringScheduleRuleEntity currentPrivateRule(Long packageId, Long groupId) {
        return recurringScheduleRuleMapper.selectOne(new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                .eq(RecurringScheduleRuleEntity::getRuleStatus, "ACTIVE")
                .and(scope -> scope.eq(RecurringScheduleRuleEntity::getPackageId, packageId)
                        .or(groupId != null, group -> group.eq(RecurringScheduleRuleEntity::getPrivateLessonGroupId, groupId)))
                .orderByDesc(RecurringScheduleRuleEntity::getCreatedAt)
                .last("LIMIT 1"));
    }

    private void applyLeaveCarryover(MakeupPlan plan) {
        if (plan.carryoverSlotId() == null) return;
        if (recurringScheduleSlotMapper.addLeaveCarryover(
                plan.carryoverSlotId(), plan.carryoverRuleId(), plan.carryoverUnits()) != 1) {
            throw new BusinessException("SCHEDULE_CHANGED", "The schedule changed while confirming leave. Please retry.");
        }
    }

    /** 撤销旧周期请假时同步撤回自动分配；已有后续改排课时先要求人工核对。 */
    private void reverseLeaveCarryoverIfNeeded(LessonRecordEntity record, LessonOccurrenceEntity source) {
        if (!"FORMAL_LEAVE".equals(record.getResultStatus()) || source == null) return;
        LessonOccurrenceEntity makeup = generatedMakeup(record);
        if (makeup == null || safeUnits(makeup.getLeaveCarryoverUnits()) <= 0) return;
        RecurringScheduleRuleEntity currentRule = currentPrivateRule(makeup.getPackageId(), source.getPrivateLessonGroupId());
        if (currentRule == null || !Objects.equals(makeup.getScheduleRuleId(), currentRule.getId())) {
            throw new BusinessException("MAKEUP_SCHEDULE_CHANGED",
                    "The schedule changed after this leave. Review the current allocation before revoking it.");
        }
        if (recurringScheduleSlotMapper.removeLeaveCarryover(makeup.getScheduleSlotId(), currentRule.getId(),
                makeup.getLeaveCarryoverUnits()) != 1) {
            throw new BusinessException("SCHEDULE_CHANGED", "The schedule changed while revoking leave. Please retry.");
        }
        makeup.setLeaveCarryoverUnits(0);
        lessonOccurrenceMapper.updateById(makeup);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void revokeLesson(Long recordId, LessonRecordRevokeRequest request, CurrentUserResponse currentUser) {
        currentUser = defaultAdminIfMissing(currentUser);
        LessonRecordEntity record = requireRecord(recordId);
        LessonOccurrenceEntity recordOccurrence = lessonOccurrenceMapper.selectById(record.getOccurrenceId());
        if (recordOccurrence == null) {
            throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "Lesson occurrence not found.");
        }
        if (recordOccurrence != null && recordOccurrence.getClassGroupId() != null) {
            // 班课撤销会恢复共享课次，先锁实际班级，再锁记录和课次，与确认/退款保持一致。
            Long classGroupId = recordOccurrence.getClassGroupId();
            if (classGroupMapper.selectByIdForUpdate(classGroupId) == null) {
                throw new BusinessException("CLASS_NOT_FOUND", "Class not found.");
            }
            record = lessonRecordMapper.selectByIdForUpdate(recordId);
            if (record == null) {
                throw new BusinessException("LESSON_RECORD_NOT_FOUND", "Lesson record not found.");
            }
            recordOccurrence = lessonOccurrenceMapper.selectByIdForUpdate(record.getOccurrenceId());
            if (recordOccurrence == null
                    || !Objects.equals(classGroupId, recordOccurrence.getClassGroupId())) {
                throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "Lesson occurrence not found.");
            }
        } else {
            recordOccurrence = lessonOccurrenceMapper.selectByIdForUpdate(record.getOccurrenceId());
            if (recordOccurrence == null) {
                throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "Lesson occurrence not found.");
            }
        }
        if (recordOccurrence != null && recordOccurrence.getPrivateLessonGroupId() != null) {
            List<LessonRecordEntity> groupRecords = lessonRecordMapper.selectByOccurrenceIdForUpdate(recordOccurrence.getId());
            record = groupRecords.stream().filter(item -> Objects.equals(item.getId(), recordId)).findFirst()
                    .orElseThrow(() -> new BusinessException("LESSON_RECORD_NOT_FOUND", "Lesson record not found."));
            validateRevokeVersion(record, request);
            revokePrivateLessonGroup(recordOccurrence, groupRecords, recordId, currentUser, request);
            return;
        }

        record = lessonRecordMapper.selectByIdForUpdate(recordId);
        if (record == null) {
            throw new BusinessException("LESSON_RECORD_NOT_FOUND", "Lesson record not found.");
        }
        validateRevokeVersion(record, request);
        if (!CONFIRM_STAGE_CONFIRMED.equals(record.getConfirmStage())) {
            throw new BusinessException("LESSON_NOT_FINALIZED", "Only an admin-finalized lesson result can be revoked.");
        }
        validateOccurrenceForRevoke(recordOccurrence, record);
        validateNotSettled(List.of(record));

        // 班课记录（不关联课包、不扣课时）：仅作废记录并按需恢复课次。
        if (record.getPackageId() == null) {
            writeRevokeAudit(record, request.getReason(), currentUser);
            lessonRecordMapper.deleteById(record.getId());
            restoreOccurrence(record);
            return;
        }

        // 一对一记录：回滚课包课时 + 写冲正账本 + 恢复课次 + 作废联动补课课次。
        CoursePackageEntity coursePackage = requirePackageForRevoke(record.getPackageId());
        if ("FORMAL_LEAVE".equals(record.getResultStatus())) {
            validateGeneratedMakeupsCanBeCancelled(record);
            reverseLeaveCarryoverIfNeeded(record, recordOccurrence);
            cancelGeneratedMakeups(record);
        }
        int deducted = record.getDeductedUnits() == null ? 0 : record.getDeductedUnits();
        // 撤销只冲正本次实际扣课；未来课次由当前排课规则校准，不能回放旧快照覆盖后来安排。
        if (deducted > 0) {
            int newConsumed = Math.max(0, coursePackage.getConsumedUnits() - deducted);
            int newRemaining = coursePackage.getRemainingUnits() + deducted;

            HourLedgerEntity reversal = new HourLedgerEntity();
            reversal.setPackageId(coursePackage.getId());
            reversal.setLessonRecordId(record.getId());
            reversal.setOccurrenceId(record.getOccurrenceId());
            reversal.setLedgerType("REVOKE");
            reversal.setChangeUnits(deducted);
            reversal.setBalanceAfterUnits(newRemaining);
            reversal.setBusinessDate(AppTime.nowLocalDateTime().toLocalDate());
            reversal.setReasonCode("REVOKE");
            reversal.setReasonDesc("Reversed " + deducted + " units for revoked lesson record " + record.getId() + ".");
            reversal.setOperatedBy(currentUser.userId());
            reversal.setOperatedAt(AppTime.nowLocalDateTime());
            hourLedgerMapper.insert(reversal);

            coursePackage.setConsumedUnits(newConsumed);
            coursePackage.setRemainingUnits(newRemaining);
            upsertBalanceSnapshot(coursePackage, reversal.getId());
        }
        updatePackageAfterRevoke(coursePackage);

        writeRevokeAudit(record, request.getReason(), currentUser);
        lessonRecordMapper.deleteById(record.getId());
        restoreOccurrence(record);
        recurringScheduleRuleService.reconcilePackageSchedule(coursePackage.getId());
    }

    private void revokePrivateLessonGroup(LessonOccurrenceEntity occurrence,
                                          List<LessonRecordEntity> records,
                                          Long targetRecordId,
                                          CurrentUserResponse currentUser,
                                          LessonRecordRevokeRequest request) {
        if (records.isEmpty()) {
            throw new BusinessException("LESSON_RECORD_NOT_FOUND", "No active lesson records exist for this shared lesson.");
        }
        if (records.stream().anyMatch(record -> !CONFIRM_STAGE_CONFIRMED.equals(record.getConfirmStage()))) {
            throw new BusinessException("PRIVATE_GROUP_NOT_FINALIZED", "All students in a shared lesson must have a finalized result before it can be revoked.");
        }
        validateOccurrenceForRevoke(occurrence, records.get(0));
        validateNotSettled(records);
        List<Long> packageIds = records.stream().map(LessonRecordEntity::getPackageId).filter(Objects::nonNull)
                .distinct().sorted().toList();
        Map<Long, CoursePackageEntity> packages = new LinkedHashMap<>();
        for (Long packageId : packageIds) packages.put(packageId, requirePackageForRevoke(packageId));
        boolean formalLeave = records.stream().anyMatch(record -> "FORMAL_LEAVE".equals(record.getResultStatus()));
        if (formalLeave) {
            LessonRecordEntity sourceRecord = records.stream()
                    .filter(record -> !lessonRecordMakeupEffectMapper.findBySourceLessonRecordId(record.getId()).isEmpty())
                    .findFirst().orElse(records.get(0));
            validateGeneratedMakeupsCanBeCancelled(sourceRecord);
            reverseLeaveCarryoverIfNeeded(sourceRecord, occurrence);
            cancelGeneratedMakeups(sourceRecord);
        }
        // 共享课的公共排课不回滚历史快照；成员课包冲正后按当前规则统一校准。
        for (LessonRecordEntity record : records) {
            if (Objects.equals(record.getId(), targetRecordId)) validateRevokeVersion(record, request);
            CoursePackageEntity coursePackage = packages.get(record.getPackageId());
            int deducted = record.getDeductedUnits() == null ? 0 : record.getDeductedUnits();
            if (deducted > 0) {
                int newConsumed = Math.max(0, safeUnits(coursePackage.getConsumedUnits()) - deducted);
                int newRemaining = safeUnits(coursePackage.getRemainingUnits()) + deducted;
                HourLedgerEntity reversal = new HourLedgerEntity();
                reversal.setPackageId(coursePackage.getId());
                reversal.setLessonRecordId(record.getId());
                reversal.setOccurrenceId(record.getOccurrenceId());
                reversal.setLedgerType("REVOKE");
                reversal.setChangeUnits(deducted);
                reversal.setBalanceAfterUnits(newRemaining);
                reversal.setBusinessDate(AppTime.nowLocalDateTime().toLocalDate());
                reversal.setReasonCode("REVOKE");
                reversal.setReasonDesc("Reversed shared private lesson record " + record.getId() + ".");
                reversal.setOperatedBy(currentUser.userId());
                reversal.setOperatedAt(AppTime.nowLocalDateTime());
                hourLedgerMapper.insert(reversal);
                coursePackage.setConsumedUnits(newConsumed);
                coursePackage.setRemainingUnits(newRemaining);
                upsertBalanceSnapshot(coursePackage, reversal.getId());
            }
            updatePackageAfterRevoke(coursePackage);
            writeRevokeAudit(record, request.getReason(), currentUser);
            lessonRecordMapper.deleteById(record.getId());
        }
        restoreOccurrence(occurrence.getId());
        if (!packageIds.isEmpty()) {
            recurringScheduleRuleService.reconcilePackageSchedule(packageIds.get(0));
        }
    }

    private List<PrivateLessonGroupMemberEntity> activePrivateLessonGroupMembers(Long groupId) {
        return privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                .eq(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, groupId)
                .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                .orderByAsc(PrivateLessonGroupMemberEntity::getParticipantIndex));
    }

    private int safeUnits(Integer units) {
        return units == null ? 0 : units;
    }

    private CurrentUserResponse defaultAdminIfMissing(CurrentUserResponse currentUser) {
        if (currentUser != null) {
            return currentUser;
        }
        return new CurrentUserResponse(2049011197944602625L, "admin", "System Administrator", "ADMIN", null, null, java.util.List.of("ADMIN"));
    }

    private boolean isTeacherUser(CurrentUserResponse currentUser) {
        return currentUser.roleCodes() != null && currentUser.roleCodes().contains("TEACHER");
    }

    private void validateTeacherInitialConfirm(LessonRecordConfirmRequest request,
                                               LessonOccurrenceEntity occurrence,
                                               CurrentUserResponse currentUser) {
        if (!isTeacherUser(currentUser)) {
            return;
        }
        if (currentUser.teacherId() == null) {
            throw new BusinessException("FORBIDDEN", "Only linked teacher accounts can confirm lessons as a teacher.");
        }
        if (!ALLOWED_TEACHER_CONFIRM_STATUSES.contains(request.getResultStatus())) {
            throw new BusinessException("TEACHER_STATUS_NOT_ALLOWED", "Teachers can only confirm completed, no-show, or formal-leave results.");
        }
        if (!canTeacherHandleOccurrence(currentUser.teacherId(), occurrence)) {
            throw new BusinessException("FORBIDDEN", "Teachers can only confirm lessons assigned to them or handed over to them.");
        }

    }

    /** 老师提交考勤时按当前 slot 接手人授权；课次原老师快照和消课归属不随授权变化。 */
    private boolean canTeacherHandleOccurrence(Long teacherId, LessonOccurrenceEntity occurrence) {
        if (teacherId == null || occurrence.getScheduleSlotId() == null
                && occurrence.getHistoryOwnerLineageId() == null) {
            return Objects.equals(teacherId, occurrence.getPlannedTeacherId());
        }
        RecurringScheduleSlotEntity sourceSlot = occurrence.getScheduleSlotId() == null ? null : recurringScheduleSlotMapper
                .selectIncludingDeletedByIds(List.of(occurrence.getScheduleSlotId())).stream().findFirst().orElse(null);
        if (sourceSlot == null && occurrence.getHistoryOwnerLineageId() == null) {
            return Objects.equals(teacherId, occurrence.getPlannedTeacherId());
        }
        Long lineageId = occurrence.getHistoryOwnerLineageId() != null ? occurrence.getHistoryOwnerLineageId()
                : sourceSlot.getSlotLineageId() == null ? sourceSlot.getId() : sourceSlot.getSlotLineageId();
        LambdaQueryWrapper<RecurringScheduleRuleEntity> ruleQuery = new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                .eq(RecurringScheduleRuleEntity::getRuleStatus, "ACTIVE")
                .and(scope -> scope.eq(RecurringScheduleRuleEntity::getPackageId, occurrence.getPackageId())
                        .or(occurrence.getPrivateLessonGroupId() != null,
                                group -> group.eq(RecurringScheduleRuleEntity::getPrivateLessonGroupId,
                                        occurrence.getPrivateLessonGroupId())));
        List<Long> activeRuleIds = recurringScheduleRuleMapper.selectList(ruleQuery).stream()
                .map(RecurringScheduleRuleEntity::getId).filter(Objects::nonNull).toList();
        if (!activeRuleIds.isEmpty()) {
            List<RecurringScheduleSlotEntity> currentSlots = recurringScheduleSlotMapper.selectList(
                    new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                            .in(RecurringScheduleSlotEntity::getRuleId, activeRuleIds)
                            .eq(RecurringScheduleSlotEntity::getSlotStatus, "ACTIVE"));
            List<RecurringScheduleSlotEntity> sameLineage = currentSlots.stream()
                    .filter(slot -> Objects.equals(lineageId,
                            slot.getSlotLineageId() == null ? slot.getId() : slot.getSlotLineageId()))
                    .toList();
            if (!sameLineage.isEmpty()) {
                return sameLineage.stream().anyMatch(slot -> Objects.equals(slot.getTeacherId(), teacherId));
            }
            RecurringScheduleSlotEntity legacyMatch = PrivateLessonAllocation.findUniqueCurrentSlot(sourceSlot, currentSlots);
            if (legacyMatch != null) {
                return Objects.equals(legacyMatch.getTeacherId(), teacherId);
            }
        }
        return Objects.equals(teacherId, occurrence.getPlannedTeacherId());
    }

    private LessonRecordEntity findPendingRecordByOccurrenceId(Long occurrenceId) {
        return lessonRecordMapper.selectOne(new LambdaQueryWrapper<LessonRecordEntity>()
                .eq(LessonRecordEntity::getOccurrenceId, occurrenceId)
                .eq(LessonRecordEntity::getConfirmStage, CONFIRM_STAGE_PENDING_ADMIN)
                .last("LIMIT 1"));
    }

    private LessonRecordEntity findLatestRecordByOccurrenceId(Long occurrenceId) {
        return lessonRecordMapper.selectOne(new LambdaQueryWrapper<LessonRecordEntity>()
                .eq(LessonRecordEntity::getOccurrenceId, occurrenceId)
                .orderByDesc(LessonRecordEntity::getCreatedAt)
                .orderByDesc(LessonRecordEntity::getId)
                .last("LIMIT 1"));
    }

    private void fillRecordFromDecision(LessonRecordEntity record,
                                        LessonOccurrenceEntity occurrence,
                                        CoursePackageEntity coursePackage,
                                        StudentEntity student,
                                        LessonStatusRuleEntity rule,
                                        ConfirmationDecision decision,
                                        String notes) {
        record.setOccurrenceId(occurrence.getId());
        record.setPackageId(coursePackage.getId());
        record.setStudentId(student.getId());
        // 计划老师/教室仅作快照留痕，若引用的主数据已被删除则置空，
        // 避免把悬空外键写入 lesson_record 触发 FK 约束失败（500）。
        record.setPlannedTeacherId(existingTeacherIdOrNull(occurrence.getPlannedTeacherId()));
        record.setActualTeacherId(decision.actualTeacherId());
        record.setPlannedClassroomId(existingClassroomIdOrNull(occurrence.getPlannedClassroomId()));
        record.setActualClassroomId(decision.actualClassroomId());
        // 保存确认前的原计划快照，用于撤销确认时恢复当前课次并反向平衡后续课次。
        record.setPlannedStartAt(occurrence.getPlannedStartAt());
        record.setPlannedEndAt(occurrence.getPlannedEndAt());
        record.setPlannedUnits(occurrence.getPlannedUnits());
        record.setResultStatus(rule.getStatusCode());
        record.setActualStartAt(decision.actualStartAt());
        record.setActualEndAt(decision.actualEndAt());
        record.setActualUnits(decision.actualUnits());
        record.setShouldDeduct(decision.shouldDeduct() ? 1 : 0);
        record.setDeductedUnits(decision.deductedUnits());
        record.setShouldExtendEndDate(decision.shouldExtendEndDate() ? 1 : 0);
        record.setNotes(notes);
    }

    /**
     * 返回仍存在的教室ID，否则返回 null。
     * 计划教室是历史快照，引用的教室可能已被删除，写库前需剔除悬空引用。
     */
    private Long existingClassroomIdOrNull(Long classroomId) {
        if (classroomId == null) {
            return null;
        }
        return classroomMapper.selectById(classroomId) == null ? null : classroomId;
    }

    /**
     * 返回仍存在的老师ID，否则返回 null。
     * 计划老师是历史快照，引用的老师可能已被删除，写库前需剔除悬空引用。
     */
    private Long existingTeacherIdOrNull(Long teacherId) {
        if (teacherId == null) {
            return null;
        }
        return teacherMapper.selectById(teacherId) == null ? null : teacherId;
    }

    private LessonRecordConfirmRequest mergeAdminConfirmRequest(LessonRecordEntity record,
                                                                LessonOccurrenceEntity occurrence,
                                                                LessonRecordConfirmRequest request) {
        LessonRecordConfirmRequest merged = new LessonRecordConfirmRequest();
        merged.setOccurrenceId(occurrence.getId());
        String resultStatus = StringUtils.hasText(request.getResultStatus()) ? request.getResultStatus() : record.getResultStatus();
        merged.setResultStatus(resultStatus);
        // 管理员改为请假等未上课结果时，老师提交的实际上课快照不能继续参与复核。
        if (COMPLETED_STATUSES.contains(resultStatus)) {
            merged.setActualTeacherId(request.getActualTeacherId() != null ? request.getActualTeacherId() : record.getActualTeacherId());
            merged.setActualClassroomId(request.getActualClassroomId() != null ? request.getActualClassroomId() : record.getActualClassroomId());
            merged.setActualStartAt(request.getActualStartAt() != null ? request.getActualStartAt() : record.getActualStartAt());
            merged.setActualEndAt(request.getActualEndAt() != null ? request.getActualEndAt() : record.getActualEndAt());
            merged.setActualUnits(request.getActualUnits() != null ? request.getActualUnits() : record.getActualUnits());
        } else {
            merged.setActualUnits(0);
        }
        // 管理员复核修改 actual hours/status 时，默认按规则重新计算扣课与顺延；
        // 只有显式传入 override 字段才保留人工覆盖，避免沿用老师提交时的旧扣课数。
        merged.setOverrideShouldDeduct(request.getOverrideShouldDeduct());
        merged.setOverrideDeductedUnits(request.getOverrideDeductedUnits());
        merged.setOverrideShouldExtendEndDate(request.getOverrideShouldExtendEndDate());
        merged.setNotes(request.getNotes() != null ? request.getNotes() : record.getNotes());
        return merged;
    }

    private LessonRecordConfirmRequest requestFromRecord(LessonRecordEntity record,
                                                         LessonOccurrenceEntity occurrence) {
        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(occurrence.getId());
        request.setResultStatus(record.getResultStatus());
        request.setActualTeacherId(record.getActualTeacherId());
        request.setActualClassroomId(record.getActualClassroomId());
        request.setActualStartAt(record.getActualStartAt());
        request.setActualEndAt(record.getActualEndAt());
        request.setActualUnits(record.getActualUnits());
        request.setNotes(record.getNotes());
        return request;
    }

    private void applyActualValuesToOccurrence(LessonOccurrenceEntity occurrence, ConfirmationDecision decision) {
        // 终确认后再把实际值同步回课次，避免待复核数据提前影响课表。
        if (decision.actualTeacherId() != null) {
            occurrence.setPlannedTeacherId(decision.actualTeacherId());
        }
        if (decision.actualClassroomId() != null) {
            occurrence.setPlannedClassroomId(decision.actualClassroomId());
        }
        if (decision.actualUnits() > 0) {
            occurrence.setPlannedUnits(decision.actualUnits());
        }
        if (decision.actualStartAt() != null) {
            occurrence.setPlannedStartAt(decision.actualStartAt());
            occurrence.setPlannedEndAt(decision.actualEndAt());
        }
    }

    private ConfirmationDecision buildDecision(LessonRecordEntity record) {
        return new ConfirmationDecision(
                record.getActualTeacherId(),
                record.getActualClassroomId(),
                record.getActualStartAt(),
                record.getActualEndAt(),
                record.getActualUnits() == null ? 0 : record.getActualUnits(),
                record.getShouldDeduct() != null && record.getShouldDeduct() == 1,
                record.getDeductedUnits() == null ? 0 : record.getDeductedUnits(),
                record.getShouldExtendEndDate() != null && record.getShouldExtendEndDate() == 1
        );
    }

    private void reconcileScheduleAfterConfirmation(Long packageId) {
        // 以每个当前课位保存的 Allocated 为准补齐或裁减，不跨老师挪动未来课次。
        recurringScheduleRuleService.reconcilePackageSchedule(packageId);
    }

    private void refreshEstimatedEndDateAfterScheduleChange(CoursePackageEntity coursePackage,
                                                            int originalPlannedUnits,
                                                            ConfirmationDecision decision,
                                                            LocalDate makeupEndDate) {
        boolean futureScheduleChanged = decision.shouldExtendEndDate()
                || (decision.shouldDeduct() && originalPlannedUnits != decision.deductedUnits());
        if (!futureScheduleChanged) {
            return;
        }

        // 排课校准也会更新课包结束日期；此处必须读取同一事务内的最新版本，不能复用校准前的实体。
        CoursePackageEntity latestPackage = coursePackageMapper.selectByIdForUpdate(coursePackage.getId());
        if (latestPackage == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        LocalDate estimatedEndDate = recomputeEstimatedEndDate(
                latestPackage.getId(), latestPackage.getRemainingUnits());
        if (decision.shouldExtendEndDate() && makeupEndDate != null
                && (estimatedEndDate == null || makeupEndDate.isAfter(estimatedEndDate))) {
            // Formal Leave 的补课可能落在余额推算的最后一课之后；结束日不能早于实际补课日期。
            estimatedEndDate = makeupEndDate;
        }
        if (Objects.equals(latestPackage.getEstimatedEndDate(), estimatedEndDate)) {
            coursePackage.setEstimatedEndDate(estimatedEndDate);
            return;
        }
        latestPackage.setEstimatedEndDate(estimatedEndDate);
        if (coursePackageMapper.updateById(latestPackage) != 1) {
            throw new BusinessException("PACKAGE_CONCURRENT_UPDATE", "The package was updated by another request. Please retry.");
        }
        coursePackage.setEstimatedEndDate(estimatedEndDate);
    }

    private void validateRevokeVersion(LessonRecordEntity record, LessonRecordRevokeRequest request) {
        if (request.getExpectedVersion() == null || !Objects.equals(record.getVersion(), request.getExpectedVersion())) {
            throw new BusinessException("LESSON_RECORD_CHANGED", "This lesson record changed. Refresh the page and try again.");
        }
    }

    private void validateOccurrenceForRevoke(LessonOccurrenceEntity occurrence, LessonRecordEntity record) {
        if (occurrence == null || !"MERGED".equals(occurrence.getPlanningStatus())) {
            throw new BusinessException("LESSON_OCCURRENCE_CHANGED", "The planned lesson is no longer in its finalized state.");
        }
        if (occurrence.getPrivateLessonGroupId() == null) return;
        RecurringScheduleRuleEntity currentRule = currentPrivateRule(record.getPackageId(), occurrence.getPrivateLessonGroupId());
        boolean oldPeriodLeave = false;
        if ("FORMAL_LEAVE".equals(record.getResultStatus())) {
            oldPeriodLeave = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                            .eq(LessonOccurrenceEntity::getSourceOccurrenceId, occurrence.getId())
                            .gt(LessonOccurrenceEntity::getLeaveCarryoverUnits, 0))
                    .size() > 0;
        }
        if (currentRule == null || !oldPeriodLeave && !Objects.equals(currentRule.getId(), occurrence.getScheduleRuleId())) {
            throw new BusinessException("SCHEDULE_PERIOD_CHANGED",
                    "The schedule period changed after this lesson. Review the historical schedule before undoing it.");
        }
    }

    private void validateNotSettled(List<LessonRecordEntity> records) {
        for (LessonRecordEntity record : records) {
            if (teacherPaymentItemMapper.countByLessonRecordId(record.getId()) > 0) {
                throw new BusinessException("LESSON_ALREADY_SETTLED",
                        "This lesson is included in teacher payroll and cannot be undone until that settlement is corrected.");
            }
        }
    }

    private LessonOccurrenceEntity generatedMakeup(LessonRecordEntity record) {
        return lessonRecordMakeupEffectMapper.findBySourceLessonRecordId(record.getId()).stream()
                .findFirst()
                .map(effect -> lessonOccurrenceMapper.selectById(effect.getMakeupOccurrenceId()))
                .orElse(null);
    }

    private void recordGeneratedMakeup(LessonRecordEntity sourceRecord, LessonOccurrenceEntity makeup) {
        LessonRecordMakeupEffectEntity effect = new LessonRecordMakeupEffectEntity();
        effect.setSourceLessonRecordId(sourceRecord.getId());
        effect.setMakeupOccurrenceId(makeup.getId());
        effect.setGeneratedVersion(makeup.getVersion() == null ? 0 : makeup.getVersion());
        effect.setPlannedStartAt(makeup.getPlannedStartAt());
        effect.setPlannedEndAt(makeup.getPlannedEndAt());
        effect.setPlannedUnits(makeup.getPlannedUnits());
        effect.setScheduleRuleId(makeup.getScheduleRuleId());
        effect.setScheduleSlotId(makeup.getScheduleSlotId());
        effect.setAllocationOwnerTeacherId(makeup.getAllocationOwnerTeacherId());
        effect.setAllocationAnchorDate(makeup.getAllocationAnchorDate());
        effect.setPlannedTeacherId(makeup.getPlannedTeacherId());
        effect.setSubjectName(makeup.getSubjectName());
        effect.setDeliveryMode(makeup.getDeliveryMode());
        effect.setPlannedClassroomId(makeup.getPlannedClassroomId());
        effect.setLeaveCarryoverUnits(makeup.getLeaveCarryoverUnits());
        effect.setPlanningStatus(makeup.getPlanningStatus());
        effect.setSourceOccurrenceId(makeup.getSourceOccurrenceId());
        lessonRecordMakeupEffectMapper.insert(effect);
    }

    private void validateGeneratedMakeupsCanBeCancelled(LessonRecordEntity sourceRecord) {
        List<LessonRecordMakeupEffectEntity> effects = lessonRecordMakeupEffectMapper
                .findBySourceLessonRecordId(sourceRecord.getId());
        if (effects.isEmpty()) {
            boolean hasUntrackedMakeup = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                            .eq(LessonOccurrenceEntity::getSourceOccurrenceId, sourceRecord.getOccurrenceId())
                            .eq(LessonOccurrenceEntity::getOccurrenceType, "MAKEUP")
                            .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED"))
                    .size() > 0;
            if (hasUntrackedMakeup) {
                throw new BusinessException("MAKEUP_HISTORY_NOT_REVERSIBLE",
                        "This leave predates makeup undo tracking. Review its linked schedule manually before undoing it.");
            }
            return;
        }
        for (LessonRecordMakeupEffectEntity effect : effects) {
            LessonOccurrenceEntity makeup = lessonOccurrenceMapper.selectByIdForUpdate(effect.getMakeupOccurrenceId());
            if (makeup == null || !"MAKEUP".equals(makeup.getOccurrenceType())
                    || !Objects.equals(makeup.getSourceOccurrenceId(), sourceRecord.getOccurrenceId())) {
                throw new BusinessException("MAKEUP_SCHEDULE_CHANGED",
                        "The makeup lesson changed after this leave. Review the schedule before undoing it.");
            }
            if ("MERGED".equals(makeup.getPlanningStatus())) {
                throw new BusinessException("MAKEUP_ALREADY_CONFIRMED",
                        "Revoke the processed makeup lesson before undoing its source leave.");
            }
            if ("CANCELLED".equals(makeup.getPlanningStatus())) continue;
            if (!matchesGeneratedMakeupSnapshot(effect, makeup)) {
                throw new BusinessException("MAKEUP_SCHEDULE_CHANGED",
                        "The makeup lesson changed after this leave. Review the schedule before undoing it.");
            }
            if (lessonRecordMapper.selectCount(new LambdaQueryWrapper<LessonRecordEntity>()
                    .eq(LessonRecordEntity::getOccurrenceId, makeup.getId())
                    .eq(LessonRecordEntity::getIsDeleted, 0)) > 0) {
                throw new BusinessException("MAKEUP_HAS_RECORD", "The makeup lesson has an attendance record and cannot be removed automatically.");
            }
        }
    }

    /** 以业务排课快照判断人工变更；旧记录没有快照时保留原版本号保护。 */
    private boolean matchesGeneratedMakeupSnapshot(LessonRecordMakeupEffectEntity effect,
                                                    LessonOccurrenceEntity makeup) {
        if (effect.getPlannedStartAt() == null) {
            return Objects.equals(makeup.getVersion(), effect.getGeneratedVersion());
        }
        return Objects.equals(makeup.getPlannedStartAt(), effect.getPlannedStartAt())
                && Objects.equals(makeup.getPlannedEndAt(), effect.getPlannedEndAt())
                && Objects.equals(makeup.getPlannedUnits(), effect.getPlannedUnits())
                && Objects.equals(makeup.getScheduleRuleId(), effect.getScheduleRuleId())
                && Objects.equals(makeup.getScheduleSlotId(), effect.getScheduleSlotId())
                && Objects.equals(makeup.getAllocationOwnerTeacherId(), effect.getAllocationOwnerTeacherId())
                && Objects.equals(makeup.getAllocationAnchorDate(), effect.getAllocationAnchorDate())
                && Objects.equals(makeup.getPlannedTeacherId(), effect.getPlannedTeacherId())
                && Objects.equals(makeup.getSubjectName(), effect.getSubjectName())
                && Objects.equals(makeup.getDeliveryMode(), effect.getDeliveryMode())
                && Objects.equals(makeup.getPlannedClassroomId(), effect.getPlannedClassroomId())
                && Objects.equals(makeup.getLeaveCarryoverUnits(), effect.getLeaveCarryoverUnits())
                && Objects.equals(makeup.getPlanningStatus(), effect.getPlanningStatus())
                && Objects.equals(makeup.getSourceOccurrenceId(), effect.getSourceOccurrenceId());
    }

    private void updatePackageAfterRevoke(CoursePackageEntity coursePackage) {
        String status = coursePackage.getPackageStatus();
        if (status == null || !Set.of("ACTIVE", "PAUSED", "COMPLETED").contains(status)) {
            throw new BusinessException("PACKAGE_NOT_REVERSIBLE", "This course package is not active for lesson reversal.");
        }
        if ("COMPLETED".equals(status) && coursePackage.getRemainingUnits() > 0) {
            coursePackage.setPackageStatus("ACTIVE");
            coursePackage.setCompletedAt(null);
        }
        coursePackage.setEstimatedEndDate(recomputeEstimatedEndDate(
                coursePackage.getId(), safeUnits(coursePackage.getRemainingUnits())));
        if (coursePackageMapper.updateById(coursePackage) != 1) {
            throw new BusinessException("PACKAGE_CONCURRENT_UPDATE", "The package was updated by another request. Please retry.");
        }
    }

    private void writeRevokeAudit(LessonRecordEntity record, String reason, CurrentUserResponse currentUser) {
        LessonRecordRevokeAuditEntity audit = new LessonRecordRevokeAuditEntity();
        audit.setLessonRecordId(record.getId());
        audit.setOccurrenceId(record.getOccurrenceId());
        audit.setPackageId(record.getPackageId());
        audit.setResultStatus(record.getResultStatus());
        audit.setDeductedUnits(safeUnits(record.getDeductedUnits()));
        audit.setReason(reason == null ? "" : reason.trim());
        audit.setRevokedByUserId(currentUser.userId());
        audit.setRevokedAt(AppTime.nowLocalDateTime());
        lessonRecordRevokeAuditMapper.insert(audit);
    }

    private void cancelGeneratedMakeups(LessonRecordEntity sourceRecord) {
        List<LessonRecordMakeupEffectEntity> effects = lessonRecordMakeupEffectMapper
                .findBySourceLessonRecordId(sourceRecord.getId());
        for (LessonRecordMakeupEffectEntity effect : effects) {
            LessonOccurrenceEntity makeup = lessonOccurrenceMapper.selectByIdForUpdate(effect.getMakeupOccurrenceId());
            if (makeup != null && !"CANCELLED".equals(makeup.getPlanningStatus())) {
                makeup.setPlanningStatus("CANCELLED");
                lessonOccurrenceMapper.updateById(makeup);
            }
        }
    }

    private void restoreOccurrence(Long occurrenceId) {
        LessonOccurrenceEntity occurrence = lessonOccurrenceMapper.selectById(occurrenceId);
        if (occurrence != null && "MERGED".equals(occurrence.getPlanningStatus())) {
            occurrence.setPlanningStatus("SCHEDULED");
            lessonOccurrenceMapper.updateById(occurrence);
        }
    }

    private void restoreOccurrence(LessonRecordEntity record) {
        LessonOccurrenceEntity occurrence = lessonOccurrenceMapper.selectById(record.getOccurrenceId());
        if (occurrence == null) {
            return;
        }
        occurrence.setPlanningStatus("SCHEDULED");
        if (record.getPlannedStartAt() != null) {
            occurrence.setPlannedStartAt(record.getPlannedStartAt());
        }
        if (record.getPlannedEndAt() != null) {
            occurrence.setPlannedEndAt(record.getPlannedEndAt());
        }
        if (record.getPlannedUnits() != null && record.getPlannedUnits() > 0) {
            occurrence.setPlannedUnits(record.getPlannedUnits());
        }
        // Confirmation may have overwritten the occurrence's teacher/classroom with the
        // actual ones; revoking must restore the original plan so the calendar and later
        // conflict detection no longer reflect the confirmed (and now cancelled) values.
        occurrence.setPlannedTeacherId(record.getPlannedTeacherId());
        occurrence.setPlannedClassroomId(record.getPlannedClassroomId());
        lessonOccurrenceMapper.updateById(occurrence);
    }

    private CoursePackageEntity requirePackageForRevoke(Long id) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectByIdForUpdate(id);
        if (coursePackage == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        return coursePackage;
    }

    private PackageUpdateResult applyPackageAndLedgerChanges(CoursePackageEntity coursePackage,
                                                             LessonOccurrenceEntity occurrence,
                                                             LessonStatusRuleEntity rule,
                                                             LessonRecordEntity record,
                                                             Long operatorId,
                                                             ConfirmationDecision decision) {
        // 实际授课老师可能是单课代课老师；余额由课包账本与原课位额度核对，不能按代课老师旧分配表拦截。
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
        // 只有余额耗尽才自动完结；排课暂时结束但仍有课时的课包必须留在排课列表。
        if (packageCompleted) {
            coursePackage.setPackageStatus("COMPLETED");
        }
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
                        .eq(LessonRecordEntity::getPackageId, packageId)
                        .eq(LessonRecordEntity::getConfirmStage, CONFIRM_STAGE_CONFIRMED))
                .stream()
                .map(LessonRecordEntity::getOccurrenceId)
                .toList();
        PrivateLessonGroupMemberEntity groupMember = privateLessonGroupMemberMapper.selectOne(
                new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .eq(PrivateLessonGroupMemberEntity::getPackageId, packageId)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                        .last("LIMIT 1"));
        List<LessonOccurrenceEntity> futureOccurrences = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .and(scope -> scope.eq(LessonOccurrenceEntity::getPackageId, packageId)
                        .or(groupMember != null && groupMember.getPrivateLessonGroupId() != null,
                                group -> group.eq(LessonOccurrenceEntity::getPrivateLessonGroupId,
                                        groupMember == null ? null : groupMember.getPrivateLessonGroupId())))
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

    private void ensureNoConflictForAdjustedOccurrence(LessonOccurrenceEntity occurrence) {
        ensureNoConflictForOccurrence(occurrence, "after adjusting future lesson duration");
    }

    private void ensureNoConflictForMakeupOccurrence(LessonOccurrenceEntity occurrence) {
        ensureNoConflictForOccurrence(occurrence, "for the automatic makeup lesson");
    }

    private void ensureNoConflictForOccurrence(LessonOccurrenceEntity occurrence, String conflictContext) {
        if (hasStudentOccurrenceConflict(occurrence, occurrence.getStudentId())) {
            throw new BusinessException("SCHEDULE_CONFLICT", "Student conflict detected " + conflictContext + ".");
        }
        if (occurrence.getPlannedTeacherId() != null
                && hasAdjustedOccurrenceConflict(occurrence, LessonOccurrenceEntity::getPlannedTeacherId, occurrence.getPlannedTeacherId())) {
            throw new BusinessException("SCHEDULE_CONFLICT", "Teacher conflict detected " + conflictContext + ".");
        }
        if (occurrence.getPlannedClassroomId() != null
                && hasAdjustedOccurrenceConflict(occurrence, LessonOccurrenceEntity::getPlannedClassroomId, occurrence.getPlannedClassroomId())) {
            throw new BusinessException("SCHEDULE_CONFLICT", "Classroom conflict detected " + conflictContext + ".");
        }
    }

    private boolean hasAnyAdjustedOccurrenceConflict(LessonOccurrenceEntity occurrence) {
        return hasStudentOccurrenceConflict(occurrence, occurrence.getStudentId())
                || (occurrence.getPlannedTeacherId() != null
                && hasAdjustedOccurrenceConflict(occurrence, LessonOccurrenceEntity::getPlannedTeacherId, occurrence.getPlannedTeacherId()))
                || (occurrence.getPlannedClassroomId() != null
                && hasAdjustedOccurrenceConflict(occurrence, LessonOccurrenceEntity::getPlannedClassroomId, occurrence.getPlannedClassroomId()));
    }

    private boolean hasAdjustedOccurrenceConflict(LessonOccurrenceEntity occurrence,
                                                  com.baomidou.mybatisplus.core.toolkit.support.SFunction<LessonOccurrenceEntity, Long> field,
                                                  Long fieldValue) {
        Long count = lessonOccurrenceMapper.selectCount(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(field, fieldValue)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ne(occurrence.getId() != null, LessonOccurrenceEntity::getId, occurrence.getId())
                .lt(LessonOccurrenceEntity::getPlannedStartAt, occurrence.getPlannedEndAt())
                .gt(LessonOccurrenceEntity::getPlannedEndAt, occurrence.getPlannedStartAt()));
        return count != null && count > 0;
    }

    private boolean hasStudentOccurrenceConflict(LessonOccurrenceEntity occurrence, Long studentId) {
        if (studentId == null) {
            return false;
        }
        List<Long> groupIds = privateLessonGroupMemberMapper.selectList(
                        new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                                .eq(PrivateLessonGroupMemberEntity::getStudentId, studentId)
                                .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                                .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0))
                .stream().map(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId)
                .filter(Objects::nonNull).distinct().toList();
        Long count = lessonOccurrenceMapper.selectCount(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ne(occurrence.getId() != null, LessonOccurrenceEntity::getId, occurrence.getId())
                .lt(LessonOccurrenceEntity::getPlannedStartAt, occurrence.getPlannedEndAt())
                .gt(LessonOccurrenceEntity::getPlannedEndAt, occurrence.getPlannedStartAt())
                .and(scope -> scope.eq(LessonOccurrenceEntity::getStudentId, studentId)
                        .or(!groupIds.isEmpty(), group -> group.in(LessonOccurrenceEntity::getPrivateLessonGroupId, groupIds))));
        return count != null && count > 0;
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
        Long actualTeacherId = actualData.actualHappened()
                ? normalizeActualTeacherId(request, occurrence) : null;
        Long actualClassroomId = actualData.actualHappened()
                ? normalizeActualClassroomId(request, occurrence) : null;

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
        // 未上课状态按结果规则扣课或顺延；界面中残留的实际时间、老师和课时不代表上过课。
        if (!COMPLETED_STATUSES.contains(request.getResultStatus())) {
            return new ActualLessonData(null, null, 0);
        }
        LocalDateTime startAt = request.getActualStartAt();
        LocalDateTime endAt = request.getActualEndAt();
        Integer actualUnits = request.getActualUnits();

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

        return new ActualLessonData(startAt, endAt, actualUnits);
    }

    private Long normalizeActualTeacherId(LessonRecordConfirmRequest request, LessonOccurrenceEntity occurrence) {
        Long actualTeacherId = request.getActualTeacherId();
        if (actualTeacherId == null) {
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

    private Long normalizeActualClassroomId(LessonRecordConfirmRequest request, LessonOccurrenceEntity occurrence) {
        Long requestedClassroomId = request.getActualClassroomId();
        if (requestedClassroomId != null) {
            // 显式选择的教室必须有效（前端下拉仅展示在用教室）。
            ClassroomEntity classroom = classroomMapper.selectById(requestedClassroomId);
            if (classroom == null || !"ACTIVE".equalsIgnoreCase(classroom.getStatus())) {
                throw new BusinessException("CLASSROOM_NOT_AVAILABLE", "The selected actual classroom does not exist or is not active.");
            }
            return requestedClassroomId;
        }
        // 未显式选择时尝试沿用计划教室；若计划教室已被删除/停用则留空，
        // 允许在没有有效教室的情况下完成确认（教室非确认必填项）。
        if (occurrence.getPlannedClassroomId() != null) {
            ClassroomEntity plannedClassroom = classroomMapper.selectById(occurrence.getPlannedClassroomId());
            if (plannedClassroom != null && "ACTIVE".equalsIgnoreCase(plannedClassroom.getStatus())) {
                return occurrence.getPlannedClassroomId();
            }
        }
        return null;
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
                            .actualUnits(UnitConverter.toHoursDecimal(item.getActualUnits()))
                            .shouldDeduct(item.getShouldDeduct() != null && item.getShouldDeduct() == 1)
                            .deductedUnits(UnitConverter.toHoursDecimal(item.getDeductedUnits()))
                            .shouldExtendEndDate(item.getShouldExtendEndDate() != null && item.getShouldExtendEndDate() == 1)
                            .confirmStage(item.getConfirmStage())
                            .teacherConfirmedAt(item.getTeacherConfirmedAt())
                            .teacherConfirmedByUserId(item.getTeacherConfirmedByUserId())
                            .adminConfirmedAt(item.getAdminConfirmedAt())
                            .adminConfirmedByUserId(item.getAdminConfirmedByUserId())
                            .rejectReason(item.getRejectReason())
                            .rejectedAt(item.getRejectedAt())
                            .rejectedByUserId(item.getRejectedByUserId())
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
        // 教师提交和管理员确认先锁 occurrence，使改排课能原子判断、回收待处理提交。
        LessonOccurrenceEntity occurrence = lessonOccurrenceMapper.selectByIdForUpdate(id);
        if (occurrence == null) {
            throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "Lesson occurrence not found.");
        }
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

    private record ConfirmationDecision(Long actualTeacherId,
                                        Long actualClassroomId,
                                        LocalDateTime actualStartAt,
                                        LocalDateTime actualEndAt,
                                        int actualUnits,
                                        boolean shouldDeduct,
                                        int deductedUnits,
                                        boolean shouldExtendEndDate) {
    }

    private record MakeupPlan(LessonOccurrenceEntity occurrence, Long carryoverRuleId,
                              Long carryoverSlotId, int carryoverUnits) {
    }

    private record PackageUpdateResult(Long ledgerId, int remainingUnits, boolean packageCompleted) {
    }
}
