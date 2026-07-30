package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.dto.ScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.dto.WeeklyScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.LessonAdjustmentLogEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonAdjustmentLogMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.service.ScheduleAdjustmentService;
import com.classdeduction.system.modules.schedule.vo.LessonOccurrenceVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleConflictDetailVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleAdjustmentResultVO;
import com.classdeduction.system.modules.schedule.vo.WeeklyScheduleAdjustmentResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter LESSON_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // 本周临时调整支持的动作类型：修改、新增、删除（取消）。
    private static final String ACTION_UPDATE = "UPDATE";
    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_DELETE = "DELETE";

    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonAdjustmentLogMapper lessonAdjustmentLogMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final JsonHelper jsonHelper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleAdjustmentResultVO adjust(ScheduleAdjustmentRequest request) {
        LessonOccurrenceEntity source = requireOccurrence(request.getOccurrenceId());
        Map<String, Object> beforeSnapshot = toSnapshot(source);
        validateAdjustmentType(request.getAdjustmentType());
        validateResourceAssignment(request);
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
                adjustFutureOccurrences(source.getPackageId(), targetOccurrence.getPlannedEndAt(), -unitDelta);
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

        return ScheduleAdjustmentResultVO.builder()
                .adjustmentLogId(log.getId())
                .affectedOccurrenceId(source.getId())
                .createdOccurrenceId(createdOccurrenceId)
                .adjustmentType(request.getAdjustmentType())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WeeklyScheduleAdjustmentResultVO adjustWeekly(WeeklyScheduleAdjustmentRequest request) {
        LocalDate currentWeekStartDate = currentWeekStartDate();
        validateCurrentWeekRequest(request.getWeekStartDate(), currentWeekStartDate);

        LocalDateTime weekStart = currentWeekStartDate.atStartOfDay();
        LocalDateTime weekEnd = currentWeekStartDate.plusDays(7).atStartOfDay();

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
        Integer originalWeeklyUnits = sumPlannedUnits(weeklyOccurrences);

        // 5. 构造有效课次集合（UPDATE 改后 + CREATE 新增），用于冲突校验。
        List<LessonOccurrenceEntity> updatedOccurrences = updateItems.stream()
                .map(item -> buildWeeklyAdjustedOccurrence(sourceById.get(item.getOccurrenceId()), item, weekStart, weekEnd))
                .toList();
        List<LessonOccurrenceEntity> createdOccurrences = createItems.stream()
                .map(item -> buildCreatedOccurrence(request.getPackageId(), studentId, item, weekStart, weekEnd))
                .toList();

        List<LessonOccurrenceEntity> effectiveOccurrences = new ArrayList<>();
        effectiveOccurrences.addAll(updatedOccurrences);
        effectiveOccurrences.addAll(createdOccurrences);

        // 忽略本批次涉及的原课次（UPDATE/DELETE），避免旧时间占用造成误报。
        Set<Long> ignoredOccurrenceIds = new HashSet<>(sourceIds);
        // 冲突检查前先锁定本批次涉及的学生/老师/教室，串行化并发调课，避免检查后写入被穿透。
        lockAdjustmentResources(setOf(studentId),
                collectResourceIds(effectiveOccurrences, LessonOccurrenceEntity::getPlannedTeacherId),
                collectResourceIds(effectiveOccurrences, LessonOccurrenceEntity::getPlannedClassroomId));
        ensureNoConflicts(effectiveOccurrences, ignoredOccurrenceIds);

        // 6. 事务内依次写入：新增、修改、删除（取消）并落库调整日志。
        for (int i = 0; i < createdOccurrences.size(); i++) {
            LessonOccurrenceEntity created = createdOccurrences.get(i);
            lessonOccurrenceMapper.insert(created);
            insertAdjustmentLog(created.getId(), "WEEKLY_TEMP_CREATE",
                    Map.of(), toSnapshot(created), resolveItemReason(createItems.get(i), request), request.getAdjustedBy());
        }
        for (int i = 0; i < updatedOccurrences.size(); i++) {
            LessonOccurrenceEntity updated = updatedOccurrences.get(i);
            LessonOccurrenceEntity source = sourceById.get(updated.getId());
            Map<String, Object> beforeSnapshot = toSnapshot(source);
            requireOccurrenceUpdate(lessonOccurrenceMapper.updateById(updated));
            insertAdjustmentLog(source.getId(), "WEEKLY_TEMP_UPDATE",
                    beforeSnapshot, toSnapshot(updated), resolveItemReason(updateItems.get(i), request), request.getAdjustedBy());
        }
        Set<Long> deletedIds = new LinkedHashSet<>();
        for (WeeklyScheduleAdjustmentRequest.Item item : deleteItems) {
            LessonOccurrenceEntity source = sourceById.get(item.getOccurrenceId());
            Map<String, Object> beforeSnapshot = toSnapshot(source);
            source.setPlanningStatus("CANCELLED");
            requireOccurrenceUpdate(lessonOccurrenceMapper.updateById(source));
            deletedIds.add(source.getId());
            insertAdjustmentLog(source.getId(), "WEEKLY_TEMP_DELETE",
                    beforeSnapshot, toSnapshot(source), resolveItemReason(item, request), request.getAdjustedBy());
        }

        // 7. 计算调整后课时与各动作计数。
        Integer adjustedWeeklyUnits = calculateAdjustedWeeklyUnits(weeklyOccurrences, updatedOccurrences, createdOccurrences, deletedIds);
        // 本周临时调课改变了课时总量时，反向调整后续课次，保持课包排课总量不变。
        adjustFutureOccurrences(request.getPackageId(), weekEnd, originalWeeklyUnits - adjustedWeeklyUnits);
        List<LessonOccurrenceEntity> affectedOccurrences = new ArrayList<>();
        affectedOccurrences.addAll(updatedOccurrences);
        affectedOccurrences.addAll(createdOccurrences);
        return WeeklyScheduleAdjustmentResultVO.builder()
                .originalWeeklyUnits(originalWeeklyUnits)
                .adjustedWeeklyUnits(adjustedWeeklyUnits)
                .unitDelta(adjustedWeeklyUnits - originalWeeklyUnits)
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
        newOccurrence.setLessonNo(generateLessonNo());
        newOccurrence.setPackageId(source.getPackageId());
        newOccurrence.setClassGroupId(source.getClassGroupId());
        newOccurrence.setStudentId(source.getStudentId());
        newOccurrence.setScheduleRuleId(source.getScheduleRuleId());
        newOccurrence.setScheduleSlotId(source.getScheduleSlotId());
        newOccurrence.setOccurrenceType("MAKEUP");
        newOccurrence.setSourceOccurrenceId(source.getId());
        newOccurrence.setPlanningStatus("SCHEDULED");
        newOccurrence.setConflictChecked(1);
        applyRequestedPlanningFields(newOccurrence, source, request);
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
        updated.setStudentId(source.getStudentId());
        updated.setScheduleRuleId(source.getScheduleRuleId());
        updated.setScheduleSlotId(source.getScheduleSlotId());
        updated.setOccurrenceType(source.getOccurrenceType());
        updated.setSourceOccurrenceId(source.getSourceOccurrenceId());
        updated.setNotes(source.getNotes());
        updated.setConflictChecked(1);
        updated.setPlanningStatus("ADJUSTED");
        applyRequestedPlanningFields(updated, source, request);
        validatePlannedWindow(updated.getPlannedStartAt(), updated.getPlannedEndAt(), updated.getPlannedUnits());
        validateTeacher(updated.getPlannedTeacherId());
        validateClassroom(updated.getPlannedClassroomId());
        ensureNoConflicts(updated, source.getId());
        return updated;
    }

    private void applyRequestedPlanningFields(LessonOccurrenceEntity target, LessonOccurrenceEntity source, ScheduleAdjustmentRequest request) {
        target.setPlannedTeacherId(request.getPlannedTeacherId() != null ? request.getPlannedTeacherId() : source.getPlannedTeacherId());
        target.setPlannedClassroomId(request.getPlannedClassroomId() != null ? request.getPlannedClassroomId() : source.getPlannedClassroomId());
        target.setPlannedStartAt(request.getPlannedStartAt() != null ? request.getPlannedStartAt() : source.getPlannedStartAt());
        target.setPlannedEndAt(request.getPlannedEndAt() != null ? request.getPlannedEndAt() : source.getPlannedEndAt());
        target.setPlannedUnits(request.getPlannedUnits() != null ? request.getPlannedUnits() : source.getPlannedUnits());
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
                || item.getPlannedClassroomId() == null
                || item.getPlannedStartAt() == null
                || item.getPlannedEndAt() == null
                || item.getPlannedUnits() == null) {
            throw new BusinessException("INCOMPLETE_ADJUSTMENT", "Adjusted lesson must include teacher, classroom, start time, end time, and planned units.");
        }
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

    private void validateCurrentWeekRequest(LocalDate requestedWeekStartDate, LocalDate currentWeekStartDate) {
        if (!Objects.equals(requestedWeekStartDate, currentWeekStartDate)) {
            throw new BusinessException(
                    "WEEKLY_ADJUSTMENT_NOT_CURRENT_WEEK",
                    "Weekly adjustments are only allowed for the current natural week starting "
                            + currentWeekStartDate + "."
            );
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

    private LessonOccurrenceEntity buildWeeklyAdjustedOccurrence(LessonOccurrenceEntity source,
                                                                 WeeklyScheduleAdjustmentRequest.Item item,
                                                                 LocalDateTime weekStart,
                                                                 LocalDateTime weekEnd) {
        LessonOccurrenceEntity adjusted = new LessonOccurrenceEntity();
        adjusted.setId(source.getId());
        adjusted.setLessonNo(source.getLessonNo());
        adjusted.setPackageId(source.getPackageId());
        adjusted.setClassGroupId(source.getClassGroupId());
        adjusted.setStudentId(source.getStudentId());
        adjusted.setScheduleRuleId(source.getScheduleRuleId());
        adjusted.setScheduleSlotId(source.getScheduleSlotId());
        adjusted.setOccurrenceType(source.getOccurrenceType());
        adjusted.setSourceOccurrenceId(source.getSourceOccurrenceId());
        adjusted.setNotes(source.getNotes());
        adjusted.setConflictChecked(1);
        adjusted.setPlanningStatus("ADJUSTED");
        adjusted.setPlannedTeacherId(item.getPlannedTeacherId());
        adjusted.setPlannedClassroomId(item.getPlannedClassroomId());
        adjusted.setPlannedStartAt(item.getPlannedStartAt());
        adjusted.setPlannedEndAt(item.getPlannedEndAt());
        adjusted.setPlannedUnits(item.getPlannedUnits());

        validatePlannedWindow(adjusted.getPlannedStartAt(), adjusted.getPlannedEndAt(), adjusted.getPlannedUnits());
        if (!isInsideWeek(adjusted.getPlannedStartAt(), adjusted.getPlannedEndAt(), weekStart, weekEnd)) {
            throw new BusinessException("ADJUSTED_OCCURRENCE_OUT_OF_WEEK", "Adjusted lesson time must stay inside the requested week.");
        }
        validateTeacher(adjusted.getPlannedTeacherId());
        validateClassroom(adjusted.getPlannedClassroomId());
        return adjusted;
    }

    /**
     * 构造 CREATE 新增的临时课次：临时课不绑定排课规则，按 ADHOC 处理。
     */
    private LessonOccurrenceEntity buildCreatedOccurrence(Long packageId,
                                                          Long studentId,
                                                          WeeklyScheduleAdjustmentRequest.Item item,
                                                          LocalDateTime weekStart,
                                                          LocalDateTime weekEnd) {
        LessonOccurrenceEntity created = new LessonOccurrenceEntity();
        created.setLessonNo(generateLessonNo());
        created.setPackageId(packageId);
        created.setStudentId(studentId);
        created.setScheduleRuleId(null);
        created.setScheduleSlotId(null);
        created.setOccurrenceType("ADHOC");
        created.setPlanningStatus("SCHEDULED");
        created.setConflictChecked(1);
        created.setPlannedTeacherId(item.getPlannedTeacherId());
        created.setPlannedClassroomId(item.getPlannedClassroomId());
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
    private Integer calculateAdjustedWeeklyUnits(List<LessonOccurrenceEntity> weeklyOccurrences,
                                                 List<LessonOccurrenceEntity> updatedOccurrences,
                                                 List<LessonOccurrenceEntity> createdOccurrences,
                                                 Set<Long> deletedIds) {
        Map<Long, LessonOccurrenceEntity> updatedById = updatedOccurrences.stream()
                .collect(Collectors.toMap(LessonOccurrenceEntity::getId, Function.identity()));
        int base = weeklyOccurrences.stream()
                .filter(item -> !deletedIds.contains(item.getId()))
                .map(item -> updatedById.getOrDefault(item.getId(), item))
                .map(LessonOccurrenceEntity::getPlannedUnits)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
        int created = createdOccurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedUnits)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
        return base + created;
    }

    /**
     * 将本周调课产生的课时差额分摊到后续课次，确保课包的未来计划总时长不会漂移。
     * delta > 0 表示需要增加后续课时；delta < 0 表示需要减少后续课时。
     */
    private void adjustFutureOccurrences(Long packageId, LocalDateTime startAfter, int delta) {
        if (delta == 0) {
            return;
        }
        List<LessonOccurrenceEntity> futureOccurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getPackageId, packageId)
                        .eq(LessonOccurrenceEntity::getPlanningStatus, "SCHEDULED")
                        .ge(LessonOccurrenceEntity::getPlannedStartAt, startAfter)
                        .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));
        futureOccurrences = excludeRecordedOccurrences(futureOccurrences);
        if (futureOccurrences.isEmpty()) {
            throw new BusinessException("FUTURE_SCHEDULE_NOT_ENOUGH", "No future lesson occurrences are available to keep the package schedule balanced.");
        }

        if (delta > 0) {
            increaseFutureOccurrenceWithoutConflict(futureOccurrences, delta);
            return;
        }

        int toReduce = -delta;
        for (int i = futureOccurrences.size() - 1; i >= 0 && toReduce > 0; i--) {
            LessonOccurrenceEntity occurrence = futureOccurrences.get(i);
            int currentUnits = occurrence.getPlannedUnits() == null ? 0 : occurrence.getPlannedUnits();
            if (currentUnits <= toReduce) {
                occurrence.setPlanningStatus("CANCELLED");
                lessonOccurrenceMapper.updateById(occurrence);
                toReduce -= currentUnits;
                continue;
            }
            updatePlannedUnits(occurrence, currentUnits - toReduce);
            lessonOccurrenceMapper.updateById(occurrence);
            toReduce = 0;
        }
        if (toReduce > 0) {
            throw new BusinessException("FUTURE_SCHEDULE_NOT_ENOUGH", "Future lesson occurrences are not enough to keep the package schedule balanced.");
        }
    }

    private void updatePlannedUnits(LessonOccurrenceEntity occurrence, int plannedUnits) {
        occurrence.setPlannedUnits(plannedUnits);
        occurrence.setPlannedEndAt(occurrence.getPlannedStartAt().plusMinutes(plannedUnits * 30L));
    }

    private void increaseFutureOccurrenceWithoutConflict(List<LessonOccurrenceEntity> futureOccurrences, int delta) {
        for (int i = futureOccurrences.size() - 1; i >= 0; i--) {
            LessonOccurrenceEntity occurrence = futureOccurrences.get(i);
            int originalUnits = occurrence.getPlannedUnits() == null ? 0 : occurrence.getPlannedUnits();
            LocalDateTime originalEndAt = occurrence.getPlannedEndAt();
            updatePlannedUnits(occurrence, originalUnits + delta);
            if (!hasAnyConflict(occurrence, occurrence.getId())) {
                lessonOccurrenceMapper.updateById(occurrence);
                return;
            }
            occurrence.setPlannedUnits(originalUnits);
            occurrence.setPlannedEndAt(originalEndAt);
        }
        throw new BusinessException("FUTURE_SCHEDULE_CONFLICT",
                "No future lesson has enough free time to keep the package schedule balanced.");
    }

    private boolean hasAnyConflict(LessonOccurrenceEntity occurrence, Long ignoredOccurrenceId) {
        return hasConflict(occurrence, LessonOccurrenceEntity::getStudentId, occurrence.getStudentId(), ignoredOccurrenceId)
                || (occurrence.getPlannedTeacherId() != null
                && hasConflict(occurrence, LessonOccurrenceEntity::getPlannedTeacherId, occurrence.getPlannedTeacherId(), ignoredOccurrenceId))
                || (occurrence.getPlannedClassroomId() != null
                && hasConflict(occurrence, LessonOccurrenceEntity::getPlannedClassroomId, occurrence.getPlannedClassroomId(), ignoredOccurrenceId));
    }

    private List<LessonOccurrenceEntity> excludeRecordedOccurrences(List<LessonOccurrenceEntity> occurrences) {
        if (occurrences.isEmpty()) {
            return occurrences;
        }
        Set<Long> occurrenceIds = occurrences.stream()
                .map(LessonOccurrenceEntity::getId)
                .collect(Collectors.toSet());
        List<LessonRecordEntity> records = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                .in(LessonRecordEntity::getOccurrenceId, occurrenceIds));
        Set<Long> recordedOccurrenceIds = (records == null ? Collections.<LessonRecordEntity>emptyList() : records).stream()
                .map(LessonRecordEntity::getOccurrenceId)
                .collect(Collectors.toSet());
        if (recordedOccurrenceIds.isEmpty()) {
            return occurrences;
        }
        return occurrences.stream()
                .filter(occurrence -> !recordedOccurrenceIds.contains(occurrence.getId()))
                .toList();
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
        if (request.getPlannedTeacherId() == null || request.getPlannedClassroomId() == null) {
            throw new BusinessException("RESOURCE_ASSIGNMENT_INCOMPLETE",
                    "Assigning a lesson requires both a teacher and a classroom.");
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

    private Set<Long> studentIdsForOccurrence(LessonOccurrenceEntity occurrence) {
        if (occurrence.getStudentId() != null) {
            return setOf(occurrence.getStudentId());
        }
        if (occurrence.getClassGroupId() == null) {
            return Set.of();
        }
        return activeStudentIdsByClass(occurrence.getClassGroupId());
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
        if (hasConflict(target, LessonOccurrenceEntity::getStudentId, target.getStudentId(), ignoredOccurrenceId)) {
            throwConflict("STUDENT", target.getStudentId(), "Student conflict detected for the adjusted lesson.",
                    target, LessonOccurrenceEntity::getStudentId, target.getStudentId(), ignoredOccurrenceId);
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
        for (Long studentId : activeStudentIdsByClass(target.getClassGroupId())) {
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
            if (hasConflict(target, LessonOccurrenceEntity::getStudentId, target.getStudentId(), ignoredOccurrenceIds)) {
                throwConflict("STUDENT", target.getStudentId(), "Student conflict detected for the adjusted lesson.",
                        target, LessonOccurrenceEntity::getStudentId, target.getStudentId(), ignoredOccurrenceIds);
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
                if (Objects.equals(left.getStudentId(), right.getStudentId())) {
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
                .and(nested -> nested
                        .eq(LessonOccurrenceEntity::getStudentId, studentId)
                        .or(!classIds.isEmpty(), inner -> inner.in(LessonOccurrenceEntity::getClassGroupId, classIds)));
        if (ignoredOccurrenceId != null) {
            wrapper.ne(LessonOccurrenceEntity::getId, ignoredOccurrenceId);
        }
        return lessonOccurrenceMapper.selectCount(wrapper) > 0;
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
                existing == null ? Collections.emptyList() : List.of(buildConflictDetail(resourceType, resourceId, existing)));
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
                existing == null ? Collections.emptyList() : List.of(buildConflictDetail(resourceType, resourceId, existing)));
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

    private ScheduleConflictDetailVO buildConflictDetail(String resourceType, Long resourceId, LessonOccurrenceEntity existing) {
        return ScheduleConflictDetailVO.builder()
                .resourceType(resourceType)
                .resourceId(resourceId)
                .resourceName(resolveResourceName(resourceType, resourceId))
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
        snapshot.put("plannedTeacherId", occurrence.getPlannedTeacherId());
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

    private String generateLessonNo() {
        return "LS" + LESSON_NO_FORMATTER.format(AppTime.nowLocalDateTime())
                + String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000));
    }
}
