package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.mapper.GroupCourseProjectMapper;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.dto.ScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.dto.WeeklyScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.LessonAdjustmentLogEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonAdjustmentLogMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.service.ScheduleAdjustmentService;
import com.classdeduction.system.modules.schedule.service.ClassHolidayService;
import com.classdeduction.system.modules.schedule.service.RecurringScheduleRuleService;
import com.classdeduction.system.modules.schedule.vo.LessonOccurrenceVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleConflictDetailVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleAdjustmentResultVO;
import com.classdeduction.system.modules.schedule.vo.WeeklyScheduleAdjustmentResultVO;
import com.classdeduction.system.modules.schedule.util.PrivateLessonAllocation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 调课服务：负责对既有课次进行变更，并记录前后快照日志。
 */
@Service
@RequiredArgsConstructor
public class ScheduleAdjustmentServiceImpl implements ScheduleAdjustmentService {

    // 本周临时调整支持的动作类型：修改、新增、删除（取消）。
    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_DELETE = "DELETE";

    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final GroupCourseProjectMapper groupCourseProjectMapper;
    private final LessonAdjustmentLogMapper lessonAdjustmentLogMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final JsonHelper jsonHelper;
    private final ClassHolidayService classHolidayService;
    private final RecurringScheduleRuleService recurringScheduleRuleService;

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ScheduleAdjustmentResultVO adjust(ScheduleAdjustmentRequest request) {
        return adjustInternal(request, null);
    }

    private ScheduleAdjustmentResultVO adjustInternal(ScheduleAdjustmentRequest request, CurrentUserResponse teacherUser) {
        LessonOccurrenceEntity source = lockGroupOccurrenceForAdjustment(request.getOccurrenceId());
        if (teacherUser != null) {
            validateTeacherAdjustment(request, source, teacherUser);
            request.setPlannedTeacherId(source.getPlannedTeacherId());
            request.setPlannedClassroomId(source.getPlannedClassroomId());
            request.setAdjustedBy(teacherUser.userId());
        }
        ensureOneOnOnePackageActive(source.getPackageId());
        Map<String, Object> beforeSnapshot = toSnapshot(source);
        validateAdjustmentType(request.getAdjustmentType());
        validateResourceAssignment(request);
        ClassGroupEntity sourceClassGroup = source.getClassGroupId() == null ? null
                : classGroupMapper.selectById(source.getClassGroupId());
        if (sourceClassGroup != null && request.getPlannedStartAt() != null
                && classHolidayService.isHoliday(sourceClassGroup.getGroupProjectId(),
                request.getPlannedStartAt().toLocalDate())) {
            throw new BusinessException("CLASS_LESSON_ON_HOLIDAY",
                    "A group-class lesson cannot be scheduled during a class holiday.");
        }
        // Lock the involved student/teacher/classroom before checking conflicts so that
        // concurrent adjustments touching the same resource are serialized (no check-then-write race).
        Long targetTeacherId = request.getPlannedTeacherId() != null ? request.getPlannedTeacherId() : source.getPlannedTeacherId();
        Long targetClassroomId = request.getPlannedClassroomId() != null ? request.getPlannedClassroomId() : source.getPlannedClassroomId();
        lockAdjustmentResources(studentIdsForOccurrence(source), setOf(targetTeacherId), setOf(targetClassroomId));
        boolean createsMakeup = "MAKEUP_CREATE".equals(request.getAdjustmentType());
        if (!createsMakeup) {
            validateOccurrenceAdjustable(source);
            ensureNoLessonRecords(List.of(source.getId()));
        } else if (source.getClassGroupId() != null) {
            throw new BusinessException("CLASS_MAKEUP_NOT_SUPPORTED",
                    "Group class lessons do not support makeup time. Adjust the actual attendance record instead.");
        }

        LessonOccurrenceEntity targetOccurrence;
        Long createdOccurrenceId = null;
        if (createsMakeup) {
            targetOccurrence = createMakeupOccurrence(source, request);
            createdOccurrenceId = targetOccurrence.getId();
        } else {
            targetOccurrence = applyUpdate(source, request);
            requireOccurrenceUpdate(lessonOccurrenceMapper.updateById(targetOccurrence));
            int unitDelta = (targetOccurrence.getPlannedUnits() == null ? 0 : targetOccurrence.getPlannedUnits())
                    - (source.getPlannedUnits() == null ? 0 : source.getPlannedUnits());
            if (unitDelta != 0 && source.getPackageId() != null) {
                if (source.getScheduleSlotId() == null || !"REGULAR".equals(source.getOccurrenceType())) {
                    throw new BusinessException("LESSON_SLOT_REQUIRED",
                            "This lesson cannot change duration from Adjust. Change its time, teacher, or classroom, or edit the recurring schedule.");
                }
                // 先在原课位未来未处理课次间平衡差额，再校准该课位缺失日期；不跨老师或重置周期。
                rebalanceFutureSlotOccurrences(source, unitDelta, request.getAdjustedBy());
                recurringScheduleRuleService.reconcileScheduleSlot(source.getPackageId(), source.getScheduleSlotId());
            }
        }

        LessonAdjustmentLogEntity log = new LessonAdjustmentLogEntity();
        log.setOccurrenceId(source.getId());
        log.setAdjustmentType(request.getAdjustmentType());
        log.setBeforeSnapshot(jsonHelper.toJson(beforeSnapshot));
        log.setAfterSnapshot(jsonHelper.toJson(toSnapshot(targetOccurrence)));
        log.setReason(request.getReason());
        log.setAdjustedBy(request.getAdjustedBy());
        log.setAdjustedAt(AppTime.nowLocalDateTime());
        lessonAdjustmentLogMapper.insert(log);
        if (source.getPackageId() != null) {
            refreshPackageEstimatedEndDate(source.getPackageId());
        }

        return ScheduleAdjustmentResultVO.builder()
                .adjustmentLogId(log.getId())
                .affectedOccurrenceId(source.getId())
                .createdOccurrenceId(createdOccurrenceId)
                .adjustmentType(request.getAdjustmentType())
                .build();
    }

    /**
     * 教师调课入口：只允许调整本人负责且尚未产生消课记录的课次；已过期但未消课的课次仍可补处理。
     * 最终仍复用管理员调课的冲突校验和日志流程，避免前端权限绕过后产生资源冲突。
     */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ScheduleAdjustmentResultVO adjustByTeacher(ScheduleAdjustmentRequest request, CurrentUserResponse currentUser) {
        if (currentUser == null || currentUser.teacherId() == null) {
            throw new BusinessException("TEACHER_PROFILE_REQUIRED", "This account is not linked to a teacher profile.");
        }
        return adjustInternal(request, currentUser);
    }

    private void validateTeacherAdjustment(ScheduleAdjustmentRequest request,
                                          LessonOccurrenceEntity source,
                                          CurrentUserResponse currentUser) {
        if (!Objects.equals(source.getPlannedTeacherId(), currentUser.teacherId())) {
            throw new BusinessException("LESSON_NOT_ASSIGNED_TO_TEACHER", "You can only adjust lessons assigned to you.");
        }
        if (!"RESCHEDULE".equals(request.getAdjustmentType())) {
            throw new BusinessException("TEACHER_ADJUSTMENT_TYPE_NOT_ALLOWED", "Teachers can only adjust lesson time.");
        }
        if (request.getPlannedTeacherId() != null && !Objects.equals(request.getPlannedTeacherId(), source.getPlannedTeacherId())) {
            throw new BusinessException("TEACHER_CANNOT_REASSIGN_LESSON", "Teachers cannot reassign a lesson to another teacher.");
        }
        if (request.getPlannedClassroomId() != null && !Objects.equals(request.getPlannedClassroomId(), source.getPlannedClassroomId())) {
            throw new BusinessException("TEACHER_CANNOT_CHANGE_CLASSROOM", "Teachers can only adjust the lesson time.");
        }
    }

    /** 班课调课沿用项目→实际班级→课次的行锁顺序，再读取最新假期和排课数据。 */
    private LessonOccurrenceEntity lockGroupOccurrenceForAdjustment(Long occurrenceId) {
        LessonOccurrenceEntity initial = requireOccurrence(occurrenceId);
        if (initial.getClassGroupId() == null) {
            return initial;
        }
        ClassGroupEntity initialClass = classGroupMapper.selectById(initial.getClassGroupId());
        if (initialClass == null) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class not found.");
        }
        if (initialClass.getGroupProjectId() != null) {
            groupCourseProjectMapper.selectByIdForUpdate(initialClass.getGroupProjectId());
        }
        ClassGroupEntity lockedClass = classGroupMapper.selectByIdForUpdate(initialClass.getId());
        if (lockedClass == null || !"ACTIVE".equals(lockedClass.getStatus())) {
            throw new BusinessException("CLASS_NOT_FOUND", "Class not found or not active.");
        }
        LessonOccurrenceEntity lockedOccurrence = lessonOccurrenceMapper.selectByIdForUpdate(occurrenceId);
        if (lockedOccurrence == null || !Objects.equals(lockedOccurrence.getClassGroupId(), lockedClass.getId())) {
            throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "Lesson occurrence not found.");
        }
        return lockedOccurrence;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WeeklyScheduleAdjustmentResultVO adjustWeekly(WeeklyScheduleAdjustmentRequest request, CurrentUserResponse currentUser) {
        LocalDate currentWeekStartDate = currentWeekStartDate();
        validateAdjustableWeekRequest(request, currentWeekStartDate);
        CoursePackageEntity lockedPackage = coursePackageMapper.selectByIdForUpdate(request.getPackageId());
        if (lockedPackage == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        ensureOneOnOnePackageActive(request.getPackageId());

        LocalDate requestedWeekStart = request.getWeekStartDate();
        LocalDateTime weekStart = requestedWeekStart.atStartOfDay();
        LocalDateTime weekEnd = requestedWeekStart.plusDays(7).atStartOfDay();

        // 1. 按动作分组并做字段级校验（UPDATE/DELETE 需 occurrenceId，CREATE 需完整计划字段与原因）。
        List<WeeklyScheduleAdjustmentRequest.Item> updateItems = new ArrayList<>();
        List<WeeklyScheduleAdjustmentRequest.Item> createItems = new ArrayList<>();
        List<WeeklyScheduleAdjustmentRequest.Item> deleteItems = new ArrayList<>();
        for (WeeklyScheduleAdjustmentRequest.Item item : request.getItems()) {
            String action = resolveAction(item);
            validateItemByAction(item, action);
            switch (action) {
                case ACTION_CREATE -> createItems.add(item);
                case ACTION_DELETE -> deleteItems.add(item);
                default -> updateItems.add(item);
            }
        }

        // 2. 加载 UPDATE/DELETE 的源课次并校验归属、状态、是否在当前周内、未核销。
        List<Long> sourceIds = collectSourceOccurrenceIds(updateItems, deleteItems);
        Map<Long, LessonOccurrenceEntity> sourceById = new LinkedHashMap<>();
        Long studentIdFromSources = null;
        if (!sourceIds.isEmpty()) {
            List<LessonOccurrenceEntity> sourceOccurrences = loadOccurrencesInRequestOrder(sourceIds);
            studentIdFromSources = validateWeeklySources(request, sourceOccurrences, weekStart, weekEnd);
            ensureNoLessonRecords(sourceIds);
            sourceOccurrences.forEach(occurrence -> sourceById.put(occurrence.getId(), occurrence));
        }

        // 3. 推导本批次所属学生：优先取源课次，否则从该课包已有课次中解析。
        Long studentId = resolveStudentId(request.getPackageId(), studentIdFromSources);

        // 4. 统计调整前当前周课时总量。
        List<LessonOccurrenceEntity> weeklyOccurrences = listWeeklyOccurrences(request.getPackageId(), studentId, weekStart, weekEnd);
        Integer originalWeeklyUnits = sumReservedUnits(weeklyOccurrences);

        // 5. 构造有效课次集合（UPDATE 改后 + CREATE 新增），用于冲突校验。
        List<LessonOccurrenceEntity> updatedOccurrences = updateItems.stream()
                .map(item -> buildWeeklyAdjustedOccurrence(sourceById.get(item.getOccurrenceId()), item))
                .toList();
        String uniquePackageSubject = createItems.isEmpty()
                ? null
                : coursePackageMapper.selectUniqueActiveOneOnOneSubjectName(request.getPackageId());
        List<LessonOccurrenceEntity> createdOccurrences = createItems.stream()
                .map(item -> buildCreatedOccurrence(request.getPackageId(), studentId, uniquePackageSubject, item, weekStart, weekEnd))
                .toList();

        List<LessonOccurrenceEntity> effectiveOccurrences = new ArrayList<>();
        effectiveOccurrences.addAll(updatedOccurrences);
        effectiveOccurrences.addAll(createdOccurrences);

        // 忽略本批次涉及的原课次（UPDATE/DELETE），避免旧时间占用造成误报。
        Set<Long> ignoredOccurrenceIds = new HashSet<>(sourceIds);
        // 冲突检查前先锁定本批次涉及的学生/老师/教室，串行化并发调课，避免检查后写入被穿透。
        Set<Long> affectedStudentIds = new LinkedHashSet<>(setOf(studentId));
        effectiveOccurrences.forEach(occurrence -> affectedStudentIds.addAll(studentIdsForOccurrence(occurrence)));
        lockAdjustmentResources(affectedStudentIds,
                collectResourceIds(effectiveOccurrences, LessonOccurrenceEntity::getPlannedTeacherId),
                collectResourceIds(effectiveOccurrences, LessonOccurrenceEntity::getPlannedClassroomId));
        ensureNoConflicts(effectiveOccurrences, ignoredOccurrenceIds);

        // 课时可用量按本次修改前的账面余额和未核销排课计算，避免把本批次新增的课次重复计入占用。
        int previouslyAllocatedUnits = sumReservedUnits(listUnredeemedPackageOccurrences(request.getPackageId()));

        // 6. 事务内依次写入：新增、修改、删除（取消）并落库调整日志。
        for (int i = 0; i < createdOccurrences.size(); i++) {
            LessonOccurrenceEntity created = createdOccurrences.get(i);
            lessonOccurrenceMapper.insert(created);
            insertAdjustmentLog(created.getId(), "WEEKLY_TEMP_CREATE",
                    Map.of(), toSnapshot(created), resolveItemReason(createItems.get(i), request), currentUser == null ? null : currentUser.userId());
        }
        for (int i = 0; i < updatedOccurrences.size(); i++) {
            LessonOccurrenceEntity updated = updatedOccurrences.get(i);
            LessonOccurrenceEntity source = sourceById.get(updated.getId());
            Map<String, Object> beforeSnapshot = toSnapshot(source);
            requireOccurrenceUpdate(lessonOccurrenceMapper.updateById(updated));
            insertAdjustmentLog(source.getId(), "WEEKLY_TEMP_UPDATE",
                    beforeSnapshot, toSnapshot(updated), resolveItemReason(updateItems.get(i), request), currentUser == null ? null : currentUser.userId());
        }
        Set<Long> deletedIds = new LinkedHashSet<>();
        for (WeeklyScheduleAdjustmentRequest.Item item : deleteItems) {
            LessonOccurrenceEntity source = sourceById.get(item.getOccurrenceId());
            Map<String, Object> beforeSnapshot = toSnapshot(source);
            source.setPlanningStatus("CANCELLED");
            requireOccurrenceUpdate(lessonOccurrenceMapper.updateById(source));
            deletedIds.add(source.getId());
            insertAdjustmentLog(source.getId(), "WEEKLY_TEMP_DELETE",
                    beforeSnapshot, toSnapshot(source), resolveItemReason(item, request), currentUser == null ? null : currentUser.userId());
        }

        // 7. 计算调整后课时与各动作计数。
        Integer adjustedWeeklyUnits = calculateAdjustedWeeklyUnits(
                originalWeeklyUnits, sourceById, updatedOccurrences, createdOccurrences, deletedIds, weekStart, weekEnd);
        int unitDelta = calculatePackageUnitDelta(sourceById, updatedOccurrences, createdOccurrences, deletedIds);
        // 若历史数据本身已超额分配，本次新增课时需先释放超额，再覆盖本次增加量。
        int requiredFutureReleaseUnits = Math.max(0,
                previouslyAllocatedUnits + unitDelta - safeUnits(lockedPackage.getRemainingUnits()));
        List<LessonOccurrenceEntity> releasedFutureOccurrences = releaseExplicitFutureOccurrences(
                request, studentId, requiredFutureReleaseUnits, currentUser == null ? null : currentUser.userId());
        List<LessonOccurrenceEntity> affectedOccurrences = new ArrayList<>();
        affectedOccurrences.addAll(updatedOccurrences);
        affectedOccurrences.addAll(createdOccurrences);
        affectedOccurrences.addAll(releasedFutureOccurrences);
        return WeeklyScheduleAdjustmentResultVO.builder()
                .originalWeeklyUnits(originalWeeklyUnits)
                .adjustedWeeklyUnits(adjustedWeeklyUnits)
                .unitDelta(unitDelta)
                .createdCount(createdOccurrences.size())
                .updatedCount(updatedOccurrences.size())
                .deletedCount(deletedIds.size())
                .adjustedOccurrences(affectedOccurrences.stream()
                        .sorted(Comparator.comparing(LessonOccurrenceEntity::getPlannedStartAt))
                        .map(this::toOccurrenceVO)
                        .toList())
                .build();
    }

    private LessonOccurrenceEntity createMakeupOccurrence(LessonOccurrenceEntity source, ScheduleAdjustmentRequest request) {
        LessonOccurrenceEntity newOccurrence = new LessonOccurrenceEntity();
        newOccurrence.setLessonNo(BusinessNoGenerator.generateLessonOccurrenceNo());
        newOccurrence.setPackageId(source.getPackageId());
        newOccurrence.setClassGroupId(source.getClassGroupId());
        newOccurrence.setPrivateLessonGroupId(source.getPrivateLessonGroupId());
        newOccurrence.setStudentId(source.getStudentId());
        newOccurrence.setScheduleRuleId(source.getScheduleRuleId());
        newOccurrence.setScheduleSlotId(source.getScheduleSlotId());
        newOccurrence.setAllocationOwnerTeacherId(source.getAllocationOwnerTeacherId());
        newOccurrence.setOccurrenceType("MAKEUP");
        newOccurrence.setSubjectName(source.getSubjectName());
        newOccurrence.setSourceOccurrenceId(source.getId());
        newOccurrence.setPlanningStatus("SCHEDULED");
        newOccurrence.setConflictChecked(1);
        applyRequestedPlanningFields(newOccurrence, source, request);
        if (!StringUtils.hasText(newOccurrence.getSubjectName())) {
            throw new BusinessException("LESSON_SUBJECT_REQUIRED",
                    "Enter the course name before creating a makeup lesson because the original lesson has no recorded course name.");
        }
        validatePlannedWindow(newOccurrence.getPlannedStartAt(), newOccurrence.getPlannedEndAt(), newOccurrence.getPlannedUnits());
        validateTeacher(newOccurrence.getPlannedTeacherId());
        validateClassroom(newOccurrence.getPlannedClassroomId());
        ensureNoConflicts(newOccurrence, null);
        lessonOccurrenceMapper.insert(newOccurrence);
        return newOccurrence;
    }

    private LessonOccurrenceEntity applyUpdate(LessonOccurrenceEntity source, ScheduleAdjustmentRequest request) {
        LessonOccurrenceEntity updated = new LessonOccurrenceEntity();
        updated.setId(source.getId());
        updated.setLessonNo(source.getLessonNo());
        updated.setPackageId(source.getPackageId());
        updated.setClassGroupId(source.getClassGroupId());
        updated.setPrivateLessonGroupId(source.getPrivateLessonGroupId());
        updated.setStudentId(source.getStudentId());
        updated.setScheduleRuleId(source.getScheduleRuleId());
        updated.setScheduleSlotId(source.getScheduleSlotId());
        updated.setAllocationOwnerTeacherId(source.getAllocationOwnerTeacherId());
        updated.setAllocationAnchorDate(source.getAllocationAnchorDate());
        updated.setOccurrenceType(source.getOccurrenceType());
        updated.setSubjectName(source.getSubjectName());
        updated.setSourceOccurrenceId(source.getSourceOccurrenceId());
        updated.setNotes(source.getNotes());
        updated.setConflictChecked(1);
        updated.setPlanningStatus("ADJUSTED");
        updated.setClassroomOverridden(source.getClassroomOverridden());
        applyRequestedPlanningFields(updated, source, request);
        validatePlannedWindow(updated.getPlannedStartAt(), updated.getPlannedEndAt(), updated.getPlannedUnits());
        validateTeacher(updated.getPlannedTeacherId());
        validateClassroom(updated.getPlannedClassroomId());
        ensureNoConflicts(updated, source.getId());
        return updated;
    }

    private void applyRequestedPlanningFields(LessonOccurrenceEntity target, LessonOccurrenceEntity source, ScheduleAdjustmentRequest request) {
        target.setPlannedTeacherId(request.getPlannedTeacherId() != null ? request.getPlannedTeacherId() : source.getPlannedTeacherId());
        target.setSubjectName(resolveAdjustedSubjectName(request.getSubjectName(), source.getSubjectName()));
        String requestedMode = request.getDeliveryMode() == null ? null : request.getDeliveryMode().trim().toUpperCase();
        if (requestedMode != null && !List.of("IN_PERSON", "ONLINE").contains(requestedMode)) {
            throw new BusinessException("INVALID_DELIVERY_MODE", "Choose either in-person or online lesson delivery.");
        }
        if ("ONLINE".equals(requestedMode)) {
            if (request.getPlannedClassroomId() != null) {
                throw new BusinessException("ONLINE_CLASSROOM_NOT_ALLOWED", "Online lessons cannot be assigned a classroom.");
            }
            target.setPlannedClassroomId(null);
            target.setDeliveryMode("ONLINE");
            target.setClassroomOverridden(1);
        } else if ("IN_PERSON".equals(requestedMode)) {
            target.setPlannedClassroomId(request.getPlannedClassroomId());
            target.setDeliveryMode("IN_PERSON");
            target.setClassroomOverridden(1);
        } else if (request.getPlannedClassroomId() != null) {
            target.setPlannedClassroomId(request.getPlannedClassroomId());
            target.setDeliveryMode("IN_PERSON");
            target.setClassroomOverridden(1);
        } else {
            target.setPlannedClassroomId(source.getPlannedClassroomId());
            target.setDeliveryMode(source.getDeliveryMode());
        }
        target.setPlannedStartAt(request.getPlannedStartAt() != null ? request.getPlannedStartAt() : source.getPlannedStartAt());
        target.setPlannedEndAt(request.getPlannedEndAt() != null ? request.getPlannedEndAt() : source.getPlannedEndAt());
        target.setPlannedUnits(request.getPlannedUnits() != null ? request.getPlannedUnits() : source.getPlannedUnits());
    }

    /** 单课调整中的空课程名代表未修改；只有明确提交非空名称才覆盖课次快照。 */
    private String resolveAdjustedSubjectName(String requestedSubjectName, String sourceSubjectName) {
        return StringUtils.hasText(requestedSubjectName) ? requestedSubjectName.trim() : sourceSubjectName;
    }

    private String resolveAction(WeeklyScheduleAdjustmentRequest.Item item) {
        // 缺省动作按 UPDATE 处理以兼容旧调用；大小写归一化。
        if (item.getAction() == null || item.getAction().isBlank()) {
            return ACTION_UPDATE;
        }
        String action = item.getAction().trim().toUpperCase();
        if (!ACTION_UPDATE.equals(action) && !ACTION_CREATE.equals(action) && !ACTION_DELETE.equals(action)) {
            throw new BusinessException("UNSUPPORTED_WEEKLY_ADJUSTMENT_ACTION", "Unsupported weekly adjustment action: " + item.getAction() + ".");
        }
        return action;
    }

    /**
     * 按动作校验单条 Item 字段：因不同动作必填项不同，此处在 service 层显式校验。
     */
    private void validateItemByAction(WeeklyScheduleAdjustmentRequest.Item item, String action) {
        switch (action) {
            case ACTION_CREATE -> {
                if (item.getOccurrenceId() != null) {
                    throw new BusinessException("WEEKLY_ADJUSTMENT_OCCURRENCE_NOT_ALLOWED", "Occurrence ID must be empty when creating a lesson.");
                }
                requirePlanningFields(item);
            }
            case ACTION_DELETE -> {
                if (item.getOccurrenceId() == null) {
                    throw new BusinessException("WEEKLY_ADJUSTMENT_OCCURRENCE_REQUIRED", "Occurrence ID is required to delete a lesson.");
                }
            }
            default -> {
                if (item.getOccurrenceId() == null) {
                    throw new BusinessException("WEEKLY_ADJUSTMENT_OCCURRENCE_REQUIRED", "Occurrence ID is required to update a lesson.");
                }
                requirePlanningFields(item);
            }
        }
    }

    private void requirePlanningFields(WeeklyScheduleAdjustmentRequest.Item item) {
        if (item.getPlannedTeacherId() == null
                || item.getPlannedStartAt() == null
                || item.getPlannedEndAt() == null
                || item.getPlannedUnits() == null) {
            throw new BusinessException("INCOMPLETE_ADJUSTMENT", "Adjusted lesson must include a teacher, start time, end time, and planned units.");
        }
    }

    /** 临时调课允许 Online 不绑定实体教室；其它授课方式仍要求有效教室。 */
    private Long resolveClassroomId(WeeklyScheduleAdjustmentRequest.Item item) {
        return "ONLINE".equalsIgnoreCase(item.getDeliveryMode()) ? null : item.getPlannedClassroomId();
    }

    /** 保留旧请求兼容性；实体教室为可选资源，未选教室时仍按线下课保存。 */
    private String resolveDeliveryMode(WeeklyScheduleAdjustmentRequest.Item item, String fallback) {
        String mode = item.getDeliveryMode();
        if (mode == null || mode.isBlank()) {
            if (item.getPlannedClassroomId() != null) return "IN_PERSON";
            return fallback == null ? "IN_PERSON" : fallback;
        }
        if ("ONLINE".equalsIgnoreCase(mode)) return "ONLINE";
        if ("IN_PERSON".equalsIgnoreCase(mode)) return "IN_PERSON";
        throw new BusinessException("INVALID_DELIVERY_MODE", "Delivery mode must be in-person or online.");
    }

    /**
     * 汇总 UPDATE/DELETE 的源课次 ID，并校验批次内不允许重复。
     */
    private List<Long> collectSourceOccurrenceIds(List<WeeklyScheduleAdjustmentRequest.Item> updateItems,
                                                  List<WeeklyScheduleAdjustmentRequest.Item> deleteItems) {
        List<Long> occurrenceIds = new ArrayList<>();
        updateItems.forEach(item -> occurrenceIds.add(item.getOccurrenceId()));
        deleteItems.forEach(item -> occurrenceIds.add(item.getOccurrenceId()));
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(occurrenceIds);
        if (uniqueIds.size() != occurrenceIds.size()) {
            throw new BusinessException("DUPLICATE_WEEKLY_ADJUSTMENT_ITEM", "Duplicate occurrence adjustment items are not allowed.");
        }
        return new ArrayList<>(uniqueIds);
    }

    /**
     * 推导本批次所属学生：优先源课次推导出的学生，否则从该课包任意未取消课次解析。
     */
    private Long resolveStudentId(Long packageId, Long studentIdFromSources) {
        if (studentIdFromSources != null) {
            return studentIdFromSources;
        }
        LessonOccurrenceEntity reference = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getPackageId, packageId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt)
                        .last("limit 1"))
                .stream()
                .findFirst()
                .orElse(null);
        if (reference == null || reference.getStudentId() == null) {
            throw new BusinessException("WEEKLY_ADJUSTMENT_STUDENT_REQUIRED", "Unable to resolve the student for the requested package.");
        }
        return reference.getStudentId();
    }

    private List<LessonOccurrenceEntity> loadOccurrencesInRequestOrder(List<Long> requestedIds) {
        List<LessonOccurrenceEntity> loaded = lessonOccurrenceMapper.selectBatchIds(requestedIds);
        if (loaded == null || loaded.size() != requestedIds.size()) {
            throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "One or more lesson occurrences were not found.");
        }
        Map<Long, LessonOccurrenceEntity> occurrenceById = loaded.stream()
                .collect(Collectors.toMap(LessonOccurrenceEntity::getId, Function.identity()));
        return requestedIds.stream()
                .map(id -> {
                    LessonOccurrenceEntity occurrence = occurrenceById.get(id);
                    if (occurrence == null) {
                        throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "One or more lesson occurrences were not found.");
                    }
                    return occurrence;
                })
                .toList();
    }

    private Long validateWeeklySources(WeeklyScheduleAdjustmentRequest request,
                                       List<LessonOccurrenceEntity> occurrences,
                                       LocalDateTime weekStart,
                                       LocalDateTime weekEnd) {
        Long studentId = null;
        for (LessonOccurrenceEntity occurrence : occurrences) {
            if (!Objects.equals(request.getPackageId(), occurrence.getPackageId())) {
                throw new BusinessException("WEEKLY_ADJUSTMENT_PACKAGE_MISMATCH", "All adjusted occurrences must belong to the requested package.");
            }
            if (studentId == null) {
                studentId = occurrence.getStudentId();
            } else if (!Objects.equals(studentId, occurrence.getStudentId())) {
                throw new BusinessException("WEEKLY_ADJUSTMENT_STUDENT_MISMATCH", "All adjusted occurrences must belong to the same student.");
            }
            validateOccurrenceAdjustable(occurrence);
            if (!isInsideWeek(occurrence.getPlannedStartAt(), occurrence.getPlannedEndAt(), weekStart, weekEnd)) {
                throw new BusinessException("OCCURRENCE_OUT_OF_WEEK", "Only lesson occurrences inside the requested week can be adjusted.");
            }
        }
        return studentId;
    }

    private LocalDate currentWeekStartDate() {
        // 自然周以周六为起点（周六、周日 + 之后五个工作日），便于用户在周末提前调整下周工作日的排课。
        return AppTime.nowLocalDateTime()
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
    }

    private void validateAdjustableWeekRequest(WeeklyScheduleAdjustmentRequest request, LocalDate currentWeekStartDate) {
        LocalDate oldestAllowedWeekStart = currentWeekStartDate.minusWeeks(4);
        if (request.getWeekStartDate().isBefore(oldestAllowedWeekStart)
                || request.getWeekStartDate().isAfter(currentWeekStartDate)
                || !DayOfWeek.SATURDAY.equals(request.getWeekStartDate().getDayOfWeek())) {
            throw new BusinessException(
                    "WEEKLY_ADJUSTMENT_WEEK_OUT_OF_RANGE",
                    "Choose a Saturday-to-Friday week from the current week or one of the previous four weeks."
            );
        }
        if (request.getWeekStartDate().isBefore(currentWeekStartDate)
                && (request.getReason() == null || request.getReason().isBlank())) {
            throw new BusinessException("WEEKLY_ADJUSTMENT_REASON_REQUIRED", "Enter a reason when adjusting a past week.");
        }
    }

    private void validateOccurrenceAdjustable(LessonOccurrenceEntity occurrence) {
        if ("CANCELLED".equals(occurrence.getPlanningStatus())) {
            throw new BusinessException("LESSON_OCCURRENCE_CANCELLED", "Cancelled lesson occurrences cannot be adjusted.");
        }
        if ("MERGED".equals(occurrence.getPlanningStatus())) {
            throw new BusinessException("LESSON_OCCURRENCE_REDEEMED", "Redeemed lesson occurrences cannot be adjusted.");
        }
    }

    /**
     * 暂停中的 1 对 1 课包只允许修改周期排课配置，不允许通过临时调课产生或变更课次。
     * 恢复课程后由恢复流程统一按最新时段配置重新生成课次，避免暂停期间绕过课时分配。
     */
    private void ensureOneOnOnePackageActive(Long packageId) {
        if (packageId == null) {
            return;
        }
        CoursePackageEntity coursePackage = coursePackageMapper.selectById(packageId);
        if (coursePackage == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        if ("ONE_ON_ONE".equals(coursePackage.getTeachingMode())
                && !"ACTIVE".equals(coursePackage.getPackageStatus())) {
            throw new BusinessException("PACKAGE_NOT_ACTIVE",
                    "Schedule adjustments are unavailable while this 1-on-1 package is paused or inactive. Resume the package first.");
        }
    }

    private void ensureNoLessonRecords(List<Long> occurrenceIds) {
        Long recordCount = lessonRecordMapper.selectCount(new LambdaQueryWrapper<LessonRecordEntity>()
                .in(LessonRecordEntity::getOccurrenceId, occurrenceIds));
        if (recordCount != null && recordCount > 0) {
            throw new BusinessException("LESSON_OCCURRENCE_REDEEMED", "Redeemed lesson occurrences cannot be adjusted.");
        }
    }

    private List<LessonOccurrenceEntity> listWeeklyOccurrences(Long packageId, Long studentId, LocalDateTime weekStart, LocalDateTime weekEnd) {
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPackageId, packageId)
                .eq(LessonOccurrenceEntity::getStudentId, studentId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ge(LessonOccurrenceEntity::getPlannedStartAt, weekStart)
                .lt(LessonOccurrenceEntity::getPlannedStartAt, weekEnd));
    }

    private List<LessonOccurrenceEntity> listUnredeemedPackageOccurrences(Long packageId) {
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPackageId, packageId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED"));
    }

    /** 与私教消课页面共用预留口径：待确认课次仍占余额，已确认或无需消课记录不再占用。 */
    private int sumReservedUnits(List<LessonOccurrenceEntity> occurrences) {
        if (occurrences.isEmpty()) return 0;
        List<Long> occurrenceIds = occurrences.stream().map(LessonOccurrenceEntity::getId).toList();
        Map<Long, LessonRecordEntity> recordByOccurrence = new LinkedHashMap<>();
        lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getOccurrenceId, occurrenceIds)
                        .orderByDesc(LessonRecordEntity::getCreatedAt)
                        .orderByDesc(LessonRecordEntity::getId))
                .forEach(record -> recordByOccurrence.putIfAbsent(record.getOccurrenceId(), record));
        return PrivateLessonAllocation.reservedUnits(occurrences, recordByOccurrence);
    }

    /** 只释放管理员明确勾选的未核销未来课次；差额不足时事务回滚，不自动改动其他排课。 */
    private List<LessonOccurrenceEntity> releaseExplicitFutureOccurrences(WeeklyScheduleAdjustmentRequest request,
                                                                          Long studentId,
                                                                          int requiredUnits,
                                                                          Long adjustedBy) {
        List<Long> requestedIds = request.getReleaseFutureOccurrenceIds() == null
                ? List.of()
                : request.getReleaseFutureOccurrenceIds().stream().filter(Objects::nonNull).distinct().toList();
        if (requiredUnits <= 0) {
            if (!requestedIds.isEmpty()) {
                throw new BusinessException("UNNEEDED_FUTURE_RELEASE", "Future lessons were selected, but no additional package hours need to be released.");
            }
            return List.of();
        }
        if (requestedIds.isEmpty()) {
            throw new BusinessException("PACKAGE_UNALLOCATED_HOURS_INSUFFICIENT",
                    "The package has insufficient unallocated hours. Select future unconfirmed lessons to adjust.");
        }

        LocalDateTime now = AppTime.nowLocalDateTime();
        List<LessonOccurrenceEntity> selected = new ArrayList<>();
        for (Long id : requestedIds) {
            LessonOccurrenceEntity occurrence = lessonOccurrenceMapper.selectByIdForUpdate(id);
            if (occurrence == null
                    || !Objects.equals(request.getPackageId(), occurrence.getPackageId())
                    || !Objects.equals(studentId, occurrence.getStudentId())
                    || !List.of("SCHEDULED", "ADJUSTED").contains(occurrence.getPlanningStatus())
                    || occurrence.getPlannedStartAt() == null
                    || !occurrence.getPlannedStartAt().isAfter(now)
                    || !occurrence.getPlannedStartAt().toLocalDate().isAfter(request.getWeekStartDate().plusDays(6))) {
                throw new BusinessException("INVALID_FUTURE_RELEASE_OCCURRENCE",
                        "Only this package's unconfirmed future lessons after the selected week can be adjusted.");
            }
            selected.add(occurrence);
        }
        ensureNoLessonRecords(requestedIds);

        int selectedUnits = sumPlannedUnits(selected);
        if (selectedUnits < requiredUnits) {
            throw new BusinessException("FUTURE_RELEASE_HOURS_INSUFFICIENT",
                    "The selected future lessons do not cover the additional scheduled hours. Select more lessons.");
        }

        int toRelease = requiredUnits;
        List<LessonOccurrenceEntity> released = new ArrayList<>();
        for (LessonOccurrenceEntity occurrence : selected) {
            if (toRelease <= 0) break;
            int currentUnits = safeUnits(occurrence.getPlannedUnits());
            int releasedUnits = Math.min(currentUnits, toRelease);
            Map<String, Object> before = toSnapshot(occurrence);
            if (releasedUnits == currentUnits) {
                occurrence.setPlanningStatus("CANCELLED");
            } else {
                updatePlannedUnits(occurrence, currentUnits - releasedUnits);
                occurrence.setPlanningStatus("ADJUSTED");
            }
            requireOccurrenceUpdate(lessonOccurrenceMapper.updateById(occurrence));
            insertAdjustmentLog(occurrence.getId(), "WEEKLY_TEMP_RELEASE_FUTURE",
                    before, toSnapshot(occurrence), request.getReason(), adjustedBy);
            released.add(occurrence);
            toRelease -= releasedUnits;
        }
        return released;
    }

    private int safeUnits(Integer units) {
        return units == null ? 0 : Math.max(units, 0);
    }

    private LessonOccurrenceEntity buildWeeklyAdjustedOccurrence(LessonOccurrenceEntity source,
                                                                 WeeklyScheduleAdjustmentRequest.Item item) {
        LessonOccurrenceEntity adjusted = new LessonOccurrenceEntity();
        adjusted.setId(source.getId());
        adjusted.setLessonNo(source.getLessonNo());
        adjusted.setPackageId(source.getPackageId());
        adjusted.setClassGroupId(source.getClassGroupId());
        adjusted.setPrivateLessonGroupId(source.getPrivateLessonGroupId());
        adjusted.setStudentId(source.getStudentId());
        adjusted.setScheduleRuleId(source.getScheduleRuleId());
        adjusted.setScheduleSlotId(source.getScheduleSlotId());
        adjusted.setAllocationOwnerTeacherId(source.getAllocationOwnerTeacherId());
        adjusted.setAllocationAnchorDate(source.getAllocationAnchorDate());
        adjusted.setOccurrenceType(source.getOccurrenceType());
        adjusted.setSubjectName(source.getSubjectName());
        adjusted.setSourceOccurrenceId(source.getSourceOccurrenceId());
        adjusted.setNotes(source.getNotes());
        adjusted.setConflictChecked(1);
        adjusted.setPlanningStatus("ADJUSTED");
        adjusted.setPlannedTeacherId(item.getPlannedTeacherId());
        adjusted.setPlannedClassroomId(resolveClassroomId(item));
        adjusted.setClassroomOverridden(1);
        adjusted.setDeliveryMode(resolveDeliveryMode(item, source.getDeliveryMode()));
        adjusted.setPlannedStartAt(item.getPlannedStartAt());
        adjusted.setPlannedEndAt(item.getPlannedEndAt());
        adjusted.setPlannedUnits(item.getPlannedUnits());

        validatePlannedWindow(adjusted.getPlannedStartAt(), adjusted.getPlannedEndAt(), adjusted.getPlannedUnits());
        validateTeacher(adjusted.getPlannedTeacherId());
        validateClassroom(adjusted.getPlannedClassroomId());
        return adjusted;
    }

    /**
     * 构造 CREATE 新增的临时课次：临时课不绑定排课规则，按 ADHOC 处理。
     */
    private LessonOccurrenceEntity buildCreatedOccurrence(Long packageId,
                                                          Long studentId,
                                                          String subjectName,
                                                          WeeklyScheduleAdjustmentRequest.Item item,
                                                          LocalDateTime weekStart,
                                                          LocalDateTime weekEnd) {
        LessonOccurrenceEntity created = new LessonOccurrenceEntity();
        created.setLessonNo(BusinessNoGenerator.generateLessonOccurrenceNo());
        created.setPackageId(packageId);
        created.setPrivateLessonGroupId(resolvePrivateLessonGroupId(packageId));
        created.setStudentId(studentId);
        created.setScheduleRuleId(null);
        created.setScheduleSlotId(null);
        created.setOccurrenceType("ADHOC");
        String resolvedSubjectName = resolveAdjustedSubjectName(item.getSubjectName(), subjectName);
        if (!StringUtils.hasText(resolvedSubjectName)) {
            throw new BusinessException("LESSON_SUBJECT_REQUIRED",
                    "No course name can be inferred for this package. Choose or enter the course name before adding a lesson.");
        }
        created.setSubjectName(resolvedSubjectName);
        created.setPlanningStatus("SCHEDULED");
        created.setConflictChecked(1);
        created.setPlannedTeacherId(item.getPlannedTeacherId());
        created.setPlannedClassroomId(resolveClassroomId(item));
        created.setDeliveryMode(resolveDeliveryMode(item, null));
        created.setPlannedStartAt(item.getPlannedStartAt());
        created.setPlannedEndAt(item.getPlannedEndAt());
        created.setPlannedUnits(item.getPlannedUnits());

        validatePlannedWindow(created.getPlannedStartAt(), created.getPlannedEndAt(), created.getPlannedUnits());
        if (!isInsideWeek(created.getPlannedStartAt(), created.getPlannedEndAt(), weekStart, weekEnd)) {
            throw new BusinessException("ADJUSTED_OCCURRENCE_OUT_OF_WEEK", "Created lesson time must stay inside the requested week.");
        }
        validateTeacher(created.getPlannedTeacherId());
        validateClassroom(created.getPlannedClassroomId());
        return created;
    }

    /**
     * 单条原因优先取 itemReason，缺省回退到整体请求原因。
     */
    private String resolveItemReason(WeeklyScheduleAdjustmentRequest.Item item, WeeklyScheduleAdjustmentRequest request) {
        if (item.getItemReason() != null && !item.getItemReason().isBlank()) {
            return item.getItemReason();
        }
        return request.getReason();
    }

    private boolean isInsideWeek(LocalDateTime startAt, LocalDateTime endAt, LocalDateTime weekStart, LocalDateTime weekEnd) {
        return startAt != null && endAt != null
                && !startAt.isBefore(weekStart)
                && !endAt.isAfter(weekEnd);
    }

    private Integer sumPlannedUnits(List<LessonOccurrenceEntity> occurrences) {
        return occurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedUnits)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
    }

    /**
     * 计算调整后当前周课时：在原始周课次基础上应用 UPDATE(改后)、剔除 DELETE、叠加 CREATE。
     */
    private Integer calculateAdjustedWeeklyUnits(int originalWeeklyUnits,
                                                 Map<Long, LessonOccurrenceEntity> sourceById,
                                                 List<LessonOccurrenceEntity> updatedOccurrences,
                                                 List<LessonOccurrenceEntity> createdOccurrences,
                                                 Set<Long> deletedIds,
                                                 LocalDateTime weekStart,
                                                 LocalDateTime weekEnd) {
        int adjusted = originalWeeklyUnits;
        for (LessonOccurrenceEntity updated : updatedOccurrences) {
            LessonOccurrenceEntity source = sourceById.get(updated.getId());
            adjusted -= safeUnits(source.getPlannedUnits());
            if (isInsideWeek(updated.getPlannedStartAt(), updated.getPlannedEndAt(), weekStart, weekEnd)) {
                adjusted += safeUnits(updated.getPlannedUnits());
            }
        }
        for (Long deletedId : deletedIds) {
            adjusted -= safeUnits(sourceById.get(deletedId).getPlannedUnits());
        }
        return adjusted + createdOccurrences.stream()
                .filter(item -> isInsideWeek(item.getPlannedStartAt(), item.getPlannedEndAt(), weekStart, weekEnd))
                .mapToInt(item -> safeUnits(item.getPlannedUnits()))
                .sum();
    }

    /** 计算课包总排课占用的变化量，跨周移动不改变总量，只有新增、删除或改课时会改变。 */
    private int calculatePackageUnitDelta(Map<Long, LessonOccurrenceEntity> sourceById,
                                          List<LessonOccurrenceEntity> updatedOccurrences,
                                          List<LessonOccurrenceEntity> createdOccurrences,
                                          Set<Long> deletedIds) {
        int delta = sumPlannedUnits(createdOccurrences);
        for (LessonOccurrenceEntity updated : updatedOccurrences) {
            delta += safeUnits(updated.getPlannedUnits())
                    - safeUnits(sourceById.get(updated.getId()).getPlannedUnits());
        }
        for (Long deletedId : deletedIds) {
            delta -= safeUnits(sourceById.get(deletedId).getPlannedUnits());
        }
        return delta;
    }

    private void updatePlannedUnits(LessonOccurrenceEntity occurrence, int plannedUnits) {
        occurrence.setPlannedUnits(plannedUnits);
        occurrence.setPlannedEndAt(occurrence.getPlannedStartAt().plusMinutes(plannedUnits * 30L));
    }

    /** 单课时长变化在同一课位内守恒，只触碰尚无消课记录的未来普通课次。 */
    private void rebalanceFutureSlotOccurrences(LessonOccurrenceEntity source,
                                                int unitDelta,
                                                Long adjustedBy) {
        LocalDateTime now = AppTime.nowLocalDateTime();
        List<LessonOccurrenceEntity> future = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getPackageId, source.getPackageId())
                        .eq(LessonOccurrenceEntity::getScheduleRuleId, source.getScheduleRuleId())
                        .eq(LessonOccurrenceEntity::getScheduleSlotId, source.getScheduleSlotId())
                        .eq(LessonOccurrenceEntity::getOccurrenceType, "REGULAR")
                        .eq(LessonOccurrenceEntity::getPlanningStatus, "SCHEDULED")
                        .ge(LessonOccurrenceEntity::getPlannedStartAt, now)
                        .ne(LessonOccurrenceEntity::getId, source.getId())
                        .orderByDesc(LessonOccurrenceEntity::getPlannedStartAt));
        if (future.isEmpty()) {
            throw new BusinessException("FUTURE_SCHEDULE_NOT_ENOUGH",
                    "This lesson's duration cannot change because no unprocessed future lessons remain in its original schedule slot.");
        }
        Set<Long> occurrenceIds = future.stream().map(LessonOccurrenceEntity::getId).collect(Collectors.toSet());
        Set<Long> recordedIds = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getOccurrenceId, occurrenceIds))
                .stream().map(LessonRecordEntity::getOccurrenceId).filter(Objects::nonNull).collect(Collectors.toSet());
        future = future.stream().filter(item -> !recordedIds.contains(item.getId())).toList();
        if (future.isEmpty()) {
            throw new BusinessException("FUTURE_SCHEDULE_NOT_ENOUGH",
                    "This lesson's duration cannot change because all future lessons in its original slot have records.");
        }

        if (unitDelta > 0) {
            int toReduce = unitDelta;
            for (LessonOccurrenceEntity occurrence : future) {
                if (toReduce <= 0) break;
                LessonOccurrenceEntity before = copyForAdjustment(occurrence);
                int units = safeUnits(occurrence.getPlannedUnits());
                int reduction = Math.min(units, toReduce);
                if (reduction == units) {
                    occurrence.setPlanningStatus("CANCELLED");
                } else {
                    updatePlannedUnits(occurrence, units - reduction);
                }
                if (lessonOccurrenceMapper.updateById(occurrence) != 1) {
                    throw new BusinessException("LESSON_CHANGED", "A future lesson changed during adjustment. Refresh and try again.");
                }
                insertAdjustmentLog(occurrence.getId(), "ONE_OFF_DURATION_BALANCE", toSnapshot(before),
                        toSnapshot(occurrence), "Balanced a one-off duration increase within its original schedule slot.", adjustedBy);
                toReduce -= reduction;
            }
            if (toReduce > 0) {
                throw new BusinessException("FUTURE_SCHEDULE_NOT_ENOUGH",
                        "There are not enough unprocessed future hours in this schedule slot to cover the increase.");
            }
            return;
        }

        int toAdd = -unitDelta;
        for (LessonOccurrenceEntity occurrence : future) {
            LessonOccurrenceEntity before = copyForAdjustment(occurrence);
            int originalUnits = safeUnits(occurrence.getPlannedUnits());
            LocalDateTime originalEnd = occurrence.getPlannedEndAt();
            updatePlannedUnits(occurrence, originalUnits + toAdd);
            try {
                ensureNoConflicts(occurrence, occurrence.getId());
                lockAdjustmentResources(studentIdsForOccurrence(occurrence), setOf(occurrence.getPlannedTeacherId()),
                        setOf(occurrence.getPlannedClassroomId()));
                if (lessonOccurrenceMapper.updateById(occurrence) != 1) {
                    throw new BusinessException("LESSON_CHANGED", "A future lesson changed during adjustment. Refresh and try again.");
                }
                insertAdjustmentLog(occurrence.getId(), "ONE_OFF_DURATION_BALANCE", toSnapshot(before),
                        toSnapshot(occurrence), "Balanced a one-off duration decrease within its original schedule slot.", adjustedBy);
                return;
            } catch (BusinessException conflict) {
                occurrence.setPlannedUnits(originalUnits);
                occurrence.setPlannedEndAt(originalEnd);
                if (!"SCHEDULE_CONFLICT".equals(conflict.getCode())) throw conflict;
            }
        }
        throw new BusinessException("FUTURE_SCHEDULE_CONFLICT",
                "No unprocessed future lesson in this schedule slot can safely absorb the released hours.");
    }

    private LessonOccurrenceEntity copyForAdjustment(LessonOccurrenceEntity source) {
        LessonOccurrenceEntity copy = new LessonOccurrenceEntity();
        copy.setId(source.getId());
        copy.setLessonNo(source.getLessonNo());
        copy.setPackageId(source.getPackageId());
        copy.setScheduleRuleId(source.getScheduleRuleId());
        copy.setScheduleSlotId(source.getScheduleSlotId());
        copy.setAllocationOwnerTeacherId(source.getAllocationOwnerTeacherId());
        copy.setAllocationAnchorDate(source.getAllocationAnchorDate());
        copy.setPlannedTeacherId(source.getPlannedTeacherId());
        copy.setPlannedClassroomId(source.getPlannedClassroomId());
        copy.setDeliveryMode(source.getDeliveryMode());
        copy.setSubjectName(source.getSubjectName());
        copy.setPlannedStartAt(source.getPlannedStartAt());
        copy.setPlannedEndAt(source.getPlannedEndAt());
        copy.setPlannedUnits(source.getPlannedUnits());
        copy.setPlanningStatus(source.getPlanningStatus());
        return copy;
    }

    /** 单课调课后按该课包所有有效课次更新预计结束日，供课包及 Data Summary 共用。 */
    private void refreshPackageEstimatedEndDate(Long packageId) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectById(packageId);
        if (coursePackage == null) {
            return;
        }
        LocalDate latestEndDate = lessonOccurrenceMapper.selectList(
                        new LambdaQueryWrapper<LessonOccurrenceEntity>()
                                .eq(LessonOccurrenceEntity::getPackageId, packageId)
                                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED"))
                .stream()
                .map(LessonOccurrenceEntity::getPlannedEndAt)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (latestEndDate != null && !Objects.equals(latestEndDate, coursePackage.getEstimatedEndDate())) {
            coursePackage.setEstimatedEndDate(latestEndDate);
            coursePackageMapper.updateById(coursePackage);
        }
        PrivateLessonGroupMemberEntity membership = privateLessonGroupMemberMapper.selectOne(
                new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .eq(PrivateLessonGroupMemberEntity::getPackageId, packageId)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                        .last("LIMIT 1"));
        if (latestEndDate != null && membership != null) {
            for (PrivateLessonGroupMemberEntity member : privateLessonGroupMemberMapper.selectList(
                    new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                            .eq(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, membership.getPrivateLessonGroupId())
                            .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                            .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0))) {
                CoursePackageEntity memberPackage = coursePackageMapper.selectById(member.getPackageId());
                if (memberPackage != null && !Objects.equals(memberPackage.getEstimatedEndDate(), latestEndDate)) {
                    memberPackage.setEstimatedEndDate(latestEndDate);
                    coursePackageMapper.updateById(memberPackage);
                }
            }
        }
    }

    private void validateAdjustmentType(String adjustmentType) {
        List<String> allowed = List.of(
                "RESCHEDULE",
                "CHANGE_ROOM",
                "CHANGE_TEACHER",
                "ASSIGN_RESOURCES",
                "MAKEUP_CREATE");
        if (!allowed.contains(adjustmentType)) {
            throw new BusinessException("UNSUPPORTED_ADJUSTMENT_TYPE", "Unsupported adjustment type.");
        }
    }

    private void validateResourceAssignment(ScheduleAdjustmentRequest request) {
        if (!"ASSIGN_RESOURCES".equals(request.getAdjustmentType())) {
            return;
        }
        if (request.getPlannedTeacherId() == null) {
            throw new BusinessException("RESOURCE_ASSIGNMENT_INCOMPLETE",
                    "Assigning a lesson requires a teacher.");
        }
    }

    private void validatePlannedWindow(LocalDateTime startAt, LocalDateTime endAt, Integer plannedUnits) {
        if (startAt == null || endAt == null || plannedUnits == null) {
            throw new BusinessException("INCOMPLETE_ADJUSTMENT", "Adjusted lesson must include start time, end time, and planned units.");
        }
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException("INVALID_ADJUSTMENT_TIME_RANGE", "Adjusted end time must be later than the start time.");
        }
        long minutes = java.time.temporal.ChronoUnit.MINUTES.between(startAt, endAt);
        if (minutes != plannedUnits * 30L) {
            throw new BusinessException("ADJUSTMENT_UNITS_MISMATCH", "Adjusted lesson duration must match planned units at 0.5h per unit.");
        }
    }

    private void validateTeacher(Long teacherId) {
        if (teacherId == null) {
            return;
        }
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        if (teacher == null || !"ACTIVE".equalsIgnoreCase(teacher.getStatus())) {
            throw new BusinessException("TEACHER_NOT_AVAILABLE", "The selected teacher does not exist or is not active.");
        }
    }

    private void validateClassroom(Long classroomId) {
        if (classroomId == null) {
            return;
        }
        ClassroomEntity classroom = classroomMapper.selectById(classroomId);
        if (classroom == null || !"ACTIVE".equalsIgnoreCase(classroom.getStatus())) {
            throw new BusinessException("CLASSROOM_NOT_AVAILABLE", "The selected classroom does not exist or is not active.");
        }
    }

    /**
     * 以固定顺序（学生 -> 老师 -> 教室，且各自按 ID 升序）加行锁，避免并发调课交叉加锁产生死锁，
     * 同时把"冲突检查 + 写入"串行化到锁范围内。忽略空 ID（如未分配老师/教室的课次）。
     */
    private void lockAdjustmentResources(Set<Long> studentIds, Set<Long> teacherIds, Set<Long> classroomIds) {
        sortedNonNull(studentIds).forEach(studentMapper::selectByIdForUpdate);
        sortedNonNull(teacherIds).forEach(teacherMapper::selectByIdForUpdate);
        sortedNonNull(classroomIds).forEach(classroomMapper::selectByIdForUpdate);
    }

    private List<Long> sortedNonNull(Set<Long> ids) {
        return ids.stream().filter(Objects::nonNull).sorted().toList();
    }

    private Set<Long> setOf(Long id) {
        return id == null ? Set.of() : Set.of(id);
    }

    private Long resolvePrivateLessonGroupId(Long packageId) {
        if (packageId == null) {
            return null;
        }
        PrivateLessonGroupMemberEntity member = privateLessonGroupMemberMapper.selectOne(
                new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .eq(PrivateLessonGroupMemberEntity::getPackageId, packageId)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                        .last("LIMIT 1"));
        return member == null ? null : member.getPrivateLessonGroupId();
    }

    private Set<Long> studentIdsForOccurrence(LessonOccurrenceEntity occurrence) {
        if (occurrence.getPrivateLessonGroupId() != null) {
            return privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                            .eq(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, occurrence.getPrivateLessonGroupId())
                            .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                            .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0))
                    .stream().map(PrivateLessonGroupMemberEntity::getStudentId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        if (occurrence.getStudentId() != null) {
            return setOf(occurrence.getStudentId());
        }
        if (occurrence.getClassGroupId() == null) {
            return Set.of();
        }
        return activeStudentIdsByClassOnDate(occurrence.getClassGroupId(), occurrence.getPlannedStartAt());
    }

    private Set<Long> activeStudentIdsByClass(Long classGroupId) {
        return classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classGroupId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0))
                .stream()
                .map(ClassEnrollmentEntity::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> activeStudentIdsByClassOnDate(Long classGroupId, LocalDateTime lessonStartAt) {
        if (lessonStartAt == null) {
            return activeStudentIdsByClass(classGroupId);
        }
        LocalDate lessonDate = lessonStartAt.toLocalDate();
        return classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classGroupId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                        .le(ClassEnrollmentEntity::getEnrollmentDate, lessonDate)
                        .ge(ClassEnrollmentEntity::getExpiryDate, lessonDate))
                .stream()
                .filter(enrollment -> enrollmentIncludesLessonDate(enrollment, lessonDate))
                .map(ClassEnrollmentEntity::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean enrollmentIncludesLessonDate(ClassEnrollmentEntity enrollment, LocalDate lessonDate) {
        if (enrollment == null || lessonDate == null || enrollment.getCreatedAt() == null) {
            return true;
        }
        return !lessonDate.isBefore(enrollment.getCreatedAt().toLocalDate());
    }

    private Set<Long> collectResourceIds(List<LessonOccurrenceEntity> occurrences,
                                         Function<LessonOccurrenceEntity, Long> extractor) {
        return occurrences.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void requireOccurrenceUpdate(int updated) {
        if (updated != 1) {
            throw new BusinessException("OCCURRENCE_CONCURRENT_UPDATE",
                    "The lesson occurrence was updated by another request. Please retry.");
        }
    }

    private void ensureNoConflicts(LessonOccurrenceEntity target, Long ignoredOccurrenceId) {
        for (Long studentId : studentIdsForOccurrence(target)) {
            if (hasStudentConflict(target, studentId, ignoredOccurrenceId)) {
                throwConflict("STUDENT", studentId, "Student conflict detected for the adjusted lesson.",
                        target, LessonOccurrenceEntity::getStudentId, studentId, ignoredOccurrenceId);
            }
        }
        ensureNoClassStudentConflicts(target, ignoredOccurrenceId);
        if (target.getPlannedTeacherId() != null
                && hasConflict(target, LessonOccurrenceEntity::getPlannedTeacherId, target.getPlannedTeacherId(), ignoredOccurrenceId)) {
            throwConflict("TEACHER", target.getPlannedTeacherId(), "Teacher conflict detected for the adjusted lesson.",
                    target, LessonOccurrenceEntity::getPlannedTeacherId, target.getPlannedTeacherId(), ignoredOccurrenceId);
        }
        if (target.getPlannedClassroomId() != null
                && hasConflict(target, LessonOccurrenceEntity::getPlannedClassroomId, target.getPlannedClassroomId(), ignoredOccurrenceId)) {
            throwConflict("CLASSROOM", target.getPlannedClassroomId(), "Classroom conflict detected for the adjusted lesson.",
                    target, LessonOccurrenceEntity::getPlannedClassroomId, target.getPlannedClassroomId(), ignoredOccurrenceId);
        }
    }

    private void ensureNoClassStudentConflicts(LessonOccurrenceEntity target, Long ignoredOccurrenceId) {
        if (target.getClassGroupId() == null) {
            return;
        }
        for (Long studentId : activeStudentIdsByClassOnDate(target.getClassGroupId(), target.getPlannedStartAt())) {
            if (hasStudentConflict(target, studentId, ignoredOccurrenceId)) {
                throw new BusinessException("SCHEDULE_CONFLICT", "Student conflict detected for the adjusted class occurrence.");
            }
        }
    }

    /**
     * 批量调课需要忽略本批次原课次，避免旧时间占用误报，同时显式检查调整后课次之间的重叠。
     */
    private void ensureNoConflicts(List<LessonOccurrenceEntity> targets, Set<Long> ignoredOccurrenceIds) {
        ensureNoInternalConflicts(targets);
        for (LessonOccurrenceEntity target : targets) {
            for (Long studentId : studentIdsForOccurrence(target)) {
                if (hasStudentConflict(target, studentId, ignoredOccurrenceIds)) {
                    throwConflict("STUDENT", studentId, "Student conflict detected for the adjusted lesson.",
                            target, LessonOccurrenceEntity::getStudentId, studentId, ignoredOccurrenceIds);
                }
            }
            if (target.getPlannedTeacherId() != null
                    && hasConflict(target, LessonOccurrenceEntity::getPlannedTeacherId, target.getPlannedTeacherId(), ignoredOccurrenceIds)) {
                throwConflict("TEACHER", target.getPlannedTeacherId(), "Teacher conflict detected for the adjusted lesson.",
                        target, LessonOccurrenceEntity::getPlannedTeacherId, target.getPlannedTeacherId(), ignoredOccurrenceIds);
            }
            if (target.getPlannedClassroomId() != null
                    && hasConflict(target, LessonOccurrenceEntity::getPlannedClassroomId, target.getPlannedClassroomId(), ignoredOccurrenceIds)) {
                throwConflict("CLASSROOM", target.getPlannedClassroomId(), "Classroom conflict detected for the adjusted lesson.",
                        target, LessonOccurrenceEntity::getPlannedClassroomId, target.getPlannedClassroomId(), ignoredOccurrenceIds);
            }
        }
    }

    private void ensureNoInternalConflicts(List<LessonOccurrenceEntity> targets) {
        for (int i = 0; i < targets.size(); i++) {
            LessonOccurrenceEntity left = targets.get(i);
            for (int j = i + 1; j < targets.size(); j++) {
                LessonOccurrenceEntity right = targets.get(j);
                if (!overlaps(left, right)) {
                    continue;
                }
                if (!Collections.disjoint(studentIdsForOccurrence(left), studentIdsForOccurrence(right))) {
                    throw new BusinessException("SCHEDULE_CONFLICT", "Adjusted lessons overlap for the same student.");
                }
                if (left.getPlannedTeacherId() != null && Objects.equals(left.getPlannedTeacherId(), right.getPlannedTeacherId())) {
                    throw new BusinessException("SCHEDULE_CONFLICT", "Adjusted lessons overlap for the same teacher.");
                }
                if (left.getPlannedClassroomId() != null && Objects.equals(left.getPlannedClassroomId(), right.getPlannedClassroomId())) {
                    throw new BusinessException("SCHEDULE_CONFLICT", "Adjusted lessons overlap for the same classroom.");
                }
            }
        }
    }

    private boolean overlaps(LessonOccurrenceEntity left, LessonOccurrenceEntity right) {
        return left.getPlannedStartAt().isBefore(right.getPlannedEndAt())
                && left.getPlannedEndAt().isAfter(right.getPlannedStartAt());
    }

    private boolean hasConflict(LessonOccurrenceEntity target,
                                com.baomidou.mybatisplus.core.toolkit.support.SFunction<LessonOccurrenceEntity, Long> field,
                                Long fieldValue,
                                Long ignoredOccurrenceId) {
        LambdaQueryWrapper<LessonOccurrenceEntity> wrapper = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(field, fieldValue)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, target.getPlannedEndAt())
                .gt(LessonOccurrenceEntity::getPlannedEndAt, target.getPlannedStartAt());
        if (ignoredOccurrenceId != null) {
            wrapper.ne(LessonOccurrenceEntity::getId, ignoredOccurrenceId);
        }
        return lessonOccurrenceMapper.selectCount(wrapper) > 0;
    }

    private boolean hasStudentConflict(LessonOccurrenceEntity target, Long studentId, Long ignoredOccurrenceId) {
        List<Long> classIds = classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getStudentId, studentId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0))
                .stream()
                .map(ClassEnrollmentEntity::getClassId)
                .filter(Objects::nonNull)
                .toList();
        LambdaQueryWrapper<LessonOccurrenceEntity> wrapper = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, target.getPlannedEndAt())
                .gt(LessonOccurrenceEntity::getPlannedEndAt, target.getPlannedStartAt())
                .and(nested -> {
                    nested.eq(LessonOccurrenceEntity::getStudentId, studentId)
                            .or(!classIds.isEmpty(), inner -> inner.in(LessonOccurrenceEntity::getClassGroupId, classIds));
                    List<Long> privateGroupIds = privateGroupIdsForStudent(studentId);
                    if (!privateGroupIds.isEmpty()) {
                        nested.or().in(LessonOccurrenceEntity::getPrivateLessonGroupId, privateGroupIds);
                    }
                });
        if (ignoredOccurrenceId != null) {
            wrapper.ne(LessonOccurrenceEntity::getId, ignoredOccurrenceId);
        }
        return lessonOccurrenceMapper.selectCount(wrapper) > 0;
    }

    private boolean hasStudentConflict(LessonOccurrenceEntity target,
                                       Long studentId,
                                       Set<Long> ignoredOccurrenceIds) {
        List<Long> classIds = classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getStudentId, studentId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0))
                .stream().map(ClassEnrollmentEntity::getClassId).filter(Objects::nonNull).toList();
        List<Long> privateGroupIds = privateGroupIdsForStudent(studentId);
        LambdaQueryWrapper<LessonOccurrenceEntity> wrapper = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, target.getPlannedEndAt())
                .gt(LessonOccurrenceEntity::getPlannedEndAt, target.getPlannedStartAt())
                .and(nested -> {
                    nested.eq(LessonOccurrenceEntity::getStudentId, studentId)
                            .or(!classIds.isEmpty(), inner -> inner.in(LessonOccurrenceEntity::getClassGroupId, classIds));
                    if (!privateGroupIds.isEmpty()) {
                        nested.or().in(LessonOccurrenceEntity::getPrivateLessonGroupId, privateGroupIds);
                    }
                });
        if (ignoredOccurrenceIds != null && !ignoredOccurrenceIds.isEmpty()) {
            wrapper.notIn(LessonOccurrenceEntity::getId, ignoredOccurrenceIds);
        }
        return lessonOccurrenceMapper.selectCount(wrapper) > 0;
    }

    private List<Long> privateGroupIdsForStudent(Long studentId) {
        return privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .eq(PrivateLessonGroupMemberEntity::getStudentId, studentId)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0))
                .stream().map(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId)
                .filter(Objects::nonNull).distinct().toList();
    }

    private boolean hasConflict(LessonOccurrenceEntity target,
                                com.baomidou.mybatisplus.core.toolkit.support.SFunction<LessonOccurrenceEntity, Long> field,
                                Long fieldValue,
                                Set<Long> ignoredOccurrenceIds) {
        LambdaQueryWrapper<LessonOccurrenceEntity> wrapper = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(field, fieldValue)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, target.getPlannedEndAt())
                .gt(LessonOccurrenceEntity::getPlannedEndAt, target.getPlannedStartAt());
        if (ignoredOccurrenceIds != null && !ignoredOccurrenceIds.isEmpty()) {
            wrapper.notIn(LessonOccurrenceEntity::getId, ignoredOccurrenceIds);
        }
        return lessonOccurrenceMapper.selectCount(wrapper) > 0;
    }

    private void throwConflict(String resourceType,
                               Long resourceId,
                               String message,
                               LessonOccurrenceEntity target,
                               com.baomidou.mybatisplus.core.toolkit.support.SFunction<LessonOccurrenceEntity, Long> field,
                               Long fieldValue,
                               Long ignoredOccurrenceId) {
        LessonOccurrenceEntity existing = findFirstConflict(target, field, fieldValue, ignoredOccurrenceId, null);
        throw new BusinessException("SCHEDULE_CONFLICT", message,
                existing == null ? Collections.emptyList() : List.of(buildConflictDetail(resourceType, resourceId, target, existing)));
    }

    private void throwConflict(String resourceType,
                               Long resourceId,
                               String message,
                               LessonOccurrenceEntity target,
                               com.baomidou.mybatisplus.core.toolkit.support.SFunction<LessonOccurrenceEntity, Long> field,
                               Long fieldValue,
                               Set<Long> ignoredOccurrenceIds) {
        LessonOccurrenceEntity existing = findFirstConflict(target, field, fieldValue, null, ignoredOccurrenceIds);
        throw new BusinessException("SCHEDULE_CONFLICT", message,
                existing == null ? Collections.emptyList() : List.of(buildConflictDetail(resourceType, resourceId, target, existing)));
    }

    private LessonOccurrenceEntity findFirstConflict(LessonOccurrenceEntity target,
                                                     com.baomidou.mybatisplus.core.toolkit.support.SFunction<LessonOccurrenceEntity, Long> field,
                                                     Long fieldValue,
                                                     Long ignoredOccurrenceId,
                                                     Set<Long> ignoredOccurrenceIds) {
        LambdaQueryWrapper<LessonOccurrenceEntity> wrapper = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(field, fieldValue)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, target.getPlannedEndAt())
                .gt(LessonOccurrenceEntity::getPlannedEndAt, target.getPlannedStartAt())
                .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt)
                .last("LIMIT 1");
        if (ignoredOccurrenceId != null) {
            wrapper.ne(LessonOccurrenceEntity::getId, ignoredOccurrenceId);
        }
        if (ignoredOccurrenceIds != null && !ignoredOccurrenceIds.isEmpty()) {
            wrapper.notIn(LessonOccurrenceEntity::getId, ignoredOccurrenceIds);
        }
        List<LessonOccurrenceEntity> conflicts = lessonOccurrenceMapper.selectList(wrapper);
        return conflicts == null || conflicts.isEmpty() ? null : conflicts.get(0);
    }

    private ScheduleConflictDetailVO buildConflictDetail(String resourceType, Long resourceId,
                                                         LessonOccurrenceEntity target,
                                                         LessonOccurrenceEntity existing) {
        return ScheduleConflictDetailVO.builder()
                .resourceType(resourceType)
                .resourceId(resourceId)
                .resourceName(resolveResourceName(resourceType, resourceId))
                .requestedStartAt(target.getPlannedStartAt())
                .requestedEndAt(target.getPlannedEndAt())
                .conflictStartAt(existing.getPlannedStartAt())
                .conflictEndAt(existing.getPlannedEndAt())
                .existingOccurrenceId(existing.getId())
                .existingLessonNo(existing.getLessonNo())
                .build();
    }

    private String resolveResourceName(String resourceType, Long resourceId) {
        if (resourceId == null) {
            return null;
        }
        if ("STUDENT".equals(resourceType)) {
            StudentEntity student = studentMapper.selectById(resourceId);
            return student == null ? null : student.getStudentName();
        }
        if ("TEACHER".equals(resourceType)) {
            TeacherEntity teacher = teacherMapper.selectById(resourceId);
            return teacher == null ? null : teacher.getTeacherName();
        }
        if ("CLASSROOM".equals(resourceType)) {
            ClassroomEntity classroom = classroomMapper.selectById(resourceId);
            return classroom == null ? null : classroom.getRoomName();
        }
        return null;
    }

    private LessonOccurrenceEntity requireOccurrence(Long occurrenceId) {
        LessonOccurrenceEntity occurrence = lessonOccurrenceMapper.selectById(occurrenceId);
        if (occurrence == null) {
            throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "Lesson occurrence not found.");
        }
        if ("CANCELLED".equals(occurrence.getPlanningStatus())) {
            throw new BusinessException("LESSON_OCCURRENCE_CANCELLED", "Cancelled lesson occurrences cannot be adjusted.");
        }
        return occurrence;
    }

    private Map<String, Object> toSnapshot(LessonOccurrenceEntity occurrence) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", occurrence.getId());
        snapshot.put("lessonNo", occurrence.getLessonNo());
        snapshot.put("packageId", occurrence.getPackageId());
        snapshot.put("classGroupId", occurrence.getClassGroupId());
        snapshot.put("studentId", occurrence.getStudentId());
        snapshot.put("occurrenceType", occurrence.getOccurrenceType());
        snapshot.put("scheduleRuleId", occurrence.getScheduleRuleId());
        snapshot.put("scheduleSlotId", occurrence.getScheduleSlotId());
        snapshot.put("allocationOwnerTeacherId", occurrence.getAllocationOwnerTeacherId());
        snapshot.put("allocationAnchorDate", occurrence.getAllocationAnchorDate());
        snapshot.put("plannedTeacherId", occurrence.getPlannedTeacherId());
        snapshot.put("subjectName", occurrence.getSubjectName());
        snapshot.put("deliveryMode", occurrence.getDeliveryMode());
        snapshot.put("plannedClassroomId", occurrence.getPlannedClassroomId());
        snapshot.put("plannedStartAt", occurrence.getPlannedStartAt());
        snapshot.put("plannedEndAt", occurrence.getPlannedEndAt());
        snapshot.put("plannedUnits", occurrence.getPlannedUnits());
        snapshot.put("planningStatus", occurrence.getPlanningStatus());
        snapshot.put("sourceOccurrenceId", occurrence.getSourceOccurrenceId());
        return snapshot;
    }

    private void insertAdjustmentLog(Long occurrenceId,
                                     String adjustmentType,
                                     Map<String, Object> beforeSnapshot,
                                     Map<String, Object> afterSnapshot,
                                     String reason,
                                     Long adjustedBy) {
        LessonAdjustmentLogEntity log = new LessonAdjustmentLogEntity();
        log.setOccurrenceId(occurrenceId);
        log.setAdjustmentType(adjustmentType);
        log.setBeforeSnapshot(jsonHelper.toJson(beforeSnapshot));
        log.setAfterSnapshot(jsonHelper.toJson(afterSnapshot));
        log.setReason(reason);
        log.setAdjustedBy(adjustedBy);
        log.setAdjustedAt(AppTime.nowLocalDateTime());
        lessonAdjustmentLogMapper.insert(log);
    }

    private LessonOccurrenceVO toOccurrenceVO(LessonOccurrenceEntity occurrence) {
        return LessonOccurrenceVO.builder()
                .id(occurrence.getId())
                .lessonNo(occurrence.getLessonNo())
                .packageId(occurrence.getPackageId())
                .classGroupId(occurrence.getClassGroupId())
                .studentId(occurrence.getStudentId())
                .scheduleRuleId(occurrence.getScheduleRuleId())
                .scheduleSlotId(occurrence.getScheduleSlotId())
                .occurrenceType(occurrence.getOccurrenceType())
                .plannedTeacherId(occurrence.getPlannedTeacherId())
                .deliveryMode(occurrence.getDeliveryMode())
                .plannedClassroomId(occurrence.getPlannedClassroomId())
                .plannedStartAt(occurrence.getPlannedStartAt())
                .plannedEndAt(occurrence.getPlannedEndAt())
                .plannedUnits(occurrence.getPlannedUnits())
                .planningStatus(occurrence.getPlanningStatus())
                .sourceOccurrenceId(occurrence.getSourceOccurrenceId())
                .conflictChecked(occurrence.getConflictChecked() != null && occurrence.getConflictChecked() == 1)
                .notes(occurrence.getNotes())
                .build();
    }

}
