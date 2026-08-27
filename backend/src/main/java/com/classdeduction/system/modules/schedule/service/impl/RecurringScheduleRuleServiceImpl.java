package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleCreateRequest;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRulePageQuery;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleUpdateRequest;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleSlotRequest;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.service.RecurringScheduleRuleService;
import com.classdeduction.system.modules.schedule.vo.RecurringScheduleRuleDetailVO;
import com.classdeduction.system.modules.schedule.vo.RecurringScheduleRuleListItemVO;
import com.classdeduction.system.modules.schedule.vo.RecurringScheduleSlotVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleConflictDetailVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleRuleCreateResultVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleRuleUpdateResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 排课规则服务：负责规则创建、时段校验、课次生成和冲突检测。
 *
 * <p>1对1 排课业务规则（逐时段独立分配课时）：
 * <ul>
 *   <li>报名确定课时包总课时 totalUnits = 购买 + 赠送（1 unit = 0.5h）。</li>
 *   <li>每个排课时段（slot）自带老师、课程名和本段分配课时 allocatedUnits。</li>
 *   <li>各时段相互独立按周排满：每次课时长取 min(plannedUnits, 剩余分配)，
 *       除不尽时最后一次课只排余数（结束时间随之缩短）。</li>
 *   <li>校验：各时段 allocatedUnits 之和 ≤ 课时包总课时；生成前再按当前包余额
 *       进行安全校验，确保不会覆盖已保留课次。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringScheduleRuleServiceImpl implements RecurringScheduleRuleService {

    private static final int MAX_GENERATION_HORIZON_DAYS = 180;

    private final RecurringScheduleRuleMapper recurringScheduleRuleMapper;
    private final RecurringScheduleSlotMapper recurringScheduleSlotMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;

    @Override
    public PageResponse<RecurringScheduleRuleListItemVO> pageRules(RecurringScheduleRulePageQuery query) {
        Page<RecurringScheduleRuleEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<Long> matchedPackageIds = findMatchedPackageIds(query.getKeyword());
        List<Long> matchedRuleIds = findMatchedRuleIdsBySlotTeacher(query.getTeacherId());
        LambdaQueryWrapper<RecurringScheduleRuleEntity> wrapper = new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                .and(StringUtils.hasText(query.getKeyword()), nested -> nested
                        .like(RecurringScheduleRuleEntity::getNotes, query.getKeyword())
                        .or(!matchedPackageIds.isEmpty(), inner -> inner.in(RecurringScheduleRuleEntity::getPackageId, matchedPackageIds)))
                .eq(StringUtils.hasText(query.getRuleStatus()), RecurringScheduleRuleEntity::getRuleStatus, query.getRuleStatus())
                .in(query.getTeacherId() != null, RecurringScheduleRuleEntity::getId, matchedRuleIds)
                .eq(query.getClassroomId() != null, RecurringScheduleRuleEntity::getDefaultClassroomId, query.getClassroomId())
                .eq(query.getPackageId() != null, RecurringScheduleRuleEntity::getPackageId, query.getPackageId())
                .orderByDesc(RecurringScheduleRuleEntity::getCreatedAt);
        IPage<RecurringScheduleRuleEntity> result = recurringScheduleRuleMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }

        Set<Long> packageIds = result.getRecords().stream().map(RecurringScheduleRuleEntity::getPackageId).collect(Collectors.toSet());
        Map<Long, CoursePackageEntity> packageMap = listPackagesByIds(packageIds);
        Map<Long, StudentEntity> studentMap = listStudentsByIds(packageMap.values().stream().map(CoursePackageEntity::getStudentId).collect(Collectors.toSet()));
        Map<Long, ClassroomEntity> classroomMap = listClassroomsByIds(result.getRecords().stream().map(RecurringScheduleRuleEntity::getDefaultClassroomId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, Integer> slotCountMap = countSlotsByRuleId(result.getRecords().stream().map(RecurringScheduleRuleEntity::getId).toList());
        Map<Long, List<LessonOccurrenceEntity>> occurrenceGroup = listOccurrencesByRuleIds(result.getRecords().stream().map(RecurringScheduleRuleEntity::getId).toList());
        LocalDateTime now = AppTime.nowLocalDateTime();

        List<RecurringScheduleRuleListItemVO> records = result.getRecords().stream()
                .map(rule -> {
                    CoursePackageEntity coursePackage = packageMap.get(rule.getPackageId());
                    StudentEntity student = coursePackage == null ? null : studentMap.get(coursePackage.getStudentId());
                    ClassroomEntity classroom = classroomMap.get(rule.getDefaultClassroomId());
                    List<LessonOccurrenceEntity> occurrences = occurrenceGroup.getOrDefault(rule.getId(), Collections.emptyList());
                    LocalDateTime nextLessonStartAt = occurrences.stream()
                            .filter(item -> !"CANCELLED".equals(item.getPlanningStatus()))
                            .map(LessonOccurrenceEntity::getPlannedStartAt)
                            .filter(item -> !item.isBefore(now))
                            .min(LocalDateTime::compareTo)
                            .orElse(null);
                    return RecurringScheduleRuleListItemVO.builder()
                            .id(rule.getId())
                            .packageId(rule.getPackageId())
                            .packageNo(coursePackage == null ? null : coursePackage.getPackageNo())
                            .studentId(coursePackage == null ? null : coursePackage.getStudentId())
                            .studentName(student == null ? null : student.getStudentName())
                            .defaultClassroomId(rule.getDefaultClassroomId())
                            .defaultClassroomName(classroom == null ? null : classroom.getRoomName())
                            .ruleStatus(rule.getRuleStatus())
                            .startDate(rule.getStartDate())
                            .endDate(rule.getEndDate())
                            .generationHorizonDays(rule.getGenerationHorizonDays())
                            .slotCount(slotCountMap.getOrDefault(rule.getId(), 0))
                            .generatedOccurrenceCount(occurrences.size())
                            .nextLessonStartAt(nextLessonStartAt)
                            .createdAt(rule.getCreatedAt())
                            .build();
                })
                .toList();
        return PageResponse.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public RecurringScheduleRuleDetailVO getRule(Long id) {
        RecurringScheduleRuleEntity rule = requireRule(id);
        CoursePackageEntity coursePackage = requirePackage(rule.getPackageId());
        StudentEntity student = studentMapper.selectById(coursePackage.getStudentId());
        ClassroomEntity classroom = rule.getDefaultClassroomId() == null ? null : classroomMapper.selectById(rule.getDefaultClassroomId());
        List<RecurringScheduleSlotEntity> slots = recurringScheduleSlotMapper.selectList(new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                .eq(RecurringScheduleSlotEntity::getRuleId, id)
                .orderByAsc(RecurringScheduleSlotEntity::getWeekday)
                .orderByAsc(RecurringScheduleSlotEntity::getStartTime));
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getScheduleRuleId, id)
                .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));
        LocalDateTime nextLessonStartAt = occurrences.stream()
                .filter(item -> !"CANCELLED".equals(item.getPlanningStatus()))
                .map(LessonOccurrenceEntity::getPlannedStartAt)
                .filter(item -> !item.isBefore(AppTime.nowLocalDateTime()))
                .min(LocalDateTime::compareTo)
                .orElse(null);
        return RecurringScheduleRuleDetailVO.builder()
                .id(rule.getId())
                .packageId(rule.getPackageId())
                .packageNo(coursePackage.getPackageNo())
                .packageName(coursePackage.getPackageName())
                .studentId(coursePackage.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .defaultClassroomId(rule.getDefaultClassroomId())
                .defaultClassroomName(classroom == null ? null : classroom.getRoomName())
                .ruleStatus(rule.getRuleStatus())
                .startDate(rule.getStartDate())
                .endDate(rule.getEndDate())
                .generationHorizonDays(rule.getGenerationHorizonDays())
                .notes(rule.getNotes())
                .generatedOccurrenceCount(occurrences.size())
                .nextLessonStartAt(nextLessonStartAt)
                .slots(slots.stream().map(this::toSlotVO).toList())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleRuleCreateResultVO createRule(RecurringScheduleRuleCreateRequest request) {
        CoursePackageEntity coursePackage = requireActivePackage(request.getPackageId());
        StudentEntity student = requireStudent(coursePackage.getStudentId());
        ClassroomEntity classroom = resolveClassroom(request.getDefaultClassroomId());
        validateRuleDates(request);
        validateSlots(request.getSlots());
        validateSlotTeachers(request.getSlots());
        validateSlotAllocations(request.getSlots(), coursePackage);

        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setPackageId(coursePackage.getId());
        rule.setPrimaryTeacherId(null);
        rule.setDefaultClassroomId(classroom == null ? null : classroom.getId());
        rule.setRuleStatus("ACTIVE");
        rule.setStartDate(request.getStartDate());
        rule.setEndDate(request.getEndDate());
        rule.setGenerationHorizonDays(normalizeGenerationHorizon(request.getGenerationHorizonDays()));
        rule.setNotes(request.getNotes());
        recurringScheduleRuleMapper.insert(rule);

        List<RecurringScheduleSlotEntity> slots = request.getSlots().stream()
                .map(slotRequest -> toSlotEntity(rule.getId(), slotRequest))
                .toList();
        slots.forEach(recurringScheduleSlotMapper::insert);

        List<LessonOccurrenceEntity> generatedOccurrences = buildOccurrences(rule, slots, coursePackage, student.getId());
        List<LessonOccurrenceEntity> existingOccurrences = listActivePackageOccurrences(coursePackage.getId());
        Map<Long, LessonRecordEntity> recordByOccurrenceId = listActiveRecordsByOccurrenceId(existingOccurrences);
        generatedOccurrences = removeOccurrencesOverlappingPreserved(generatedOccurrences, existingOccurrences);
        generatedOccurrences = capGeneratedOccurrencesToPackageBalance(generatedOccurrences, existingOccurrences, recordByOccurrenceId, coursePackage);
        ensureNoConflicts(generatedOccurrences, student.getId(), rule.getDefaultClassroomId());
        generatedOccurrences.forEach(lessonOccurrenceMapper::insert);

        LocalDateTime firstStart = generatedOccurrences.stream().map(LessonOccurrenceEntity::getPlannedStartAt).min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime lastEnd = generatedOccurrences.stream().map(LessonOccurrenceEntity::getPlannedEndAt).max(LocalDateTime::compareTo).orElse(null);
        return ScheduleRuleCreateResultVO.builder()
                .ruleId(rule.getId())
                .slotCount(slots.size())
                .generatedOccurrenceCount(generatedOccurrences.size())
                .firstGeneratedStartAt(firstStart)
                .lastGeneratedEndAt(lastEnd)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int renewActiveRuleOccurrences() {
        List<RecurringScheduleRuleEntity> activeRules = recurringScheduleRuleMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                        .eq(RecurringScheduleRuleEntity::getRuleStatus, "ACTIVE"));
        int inserted = 0;
        for (RecurringScheduleRuleEntity rule : activeRules) {
            try {
                inserted += renewOneRule(rule);
            } catch (RuntimeException ex) {
                // 单条规则续期失败不应阻断其它规则（如冲突），记录后继续。
                log.warn("Failed to renew occurrences for schedule rule id={}: {}", rule.getId(), ex.getMessage());
            }
        }
        return inserted;
    }

    private int renewOneRule(RecurringScheduleRuleEntity rule) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectById(rule.getPackageId());
        if (coursePackage == null || !"ACTIVE".equals(coursePackage.getPackageStatus())) {
            return 0;
        }
        List<RecurringScheduleSlotEntity> slots = recurringScheduleSlotMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                        .eq(RecurringScheduleSlotEntity::getRuleId, rule.getId())
                        .eq(RecurringScheduleSlotEntity::getSlotStatus, "ACTIVE"));
        if (slots.isEmpty()) {
            return 0;
        }

        List<LessonOccurrenceEntity> candidates = buildOccurrences(rule, slots, coursePackage, coursePackage.getStudentId());
        if (candidates.isEmpty()) {
            return 0;
        }

        // 幂等去重：跳过该规则下已存在（非取消）的相同开始时间课次。
        Set<LocalDateTime> existingStarts = lessonOccurrenceMapper.selectList(
                        new LambdaQueryWrapper<LessonOccurrenceEntity>()
                                .eq(LessonOccurrenceEntity::getScheduleRuleId, rule.getId())
                                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED"))
                .stream()
                .map(LessonOccurrenceEntity::getPlannedStartAt)
                .collect(Collectors.toSet());
        List<LessonOccurrenceEntity> toInsert = candidates.stream()
                .filter(occ -> !existingStarts.contains(occ.getPlannedStartAt()))
                .toList();
        List<LessonOccurrenceEntity> existingOccurrences = listActivePackageOccurrences(coursePackage.getId());
        Map<Long, LessonRecordEntity> recordByOccurrenceId = listActiveRecordsByOccurrenceId(existingOccurrences);
        toInsert = removeOccurrencesOverlappingPreserved(toInsert, existingOccurrences);
        toInsert = capGeneratedOccurrencesToPackageBalance(toInsert, existingOccurrences, recordByOccurrenceId, coursePackage);
        if (toInsert.isEmpty()) {
            return 0;
        }

        ensureNoConflicts(toInsert, coursePackage.getStudentId(), rule.getDefaultClassroomId());
        toInsert.forEach(lessonOccurrenceMapper::insert);
        return toInsert.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleRuleUpdateResultVO updateRule(Long id, RecurringScheduleRuleUpdateRequest request) {
        // a) Load and validate existing rule
        RecurringScheduleRuleEntity existingRule = requireRule(id);
        if ("ENDED".equalsIgnoreCase(existingRule.getRuleStatus())) {
            throw new BusinessException("RULE_NOT_EDITABLE", "Ended rules cannot be edited.");
        }

        // b) Validate package
        CoursePackageEntity coursePackage = requirePackage(existingRule.getPackageId());
        StudentEntity student = requireStudent(coursePackage.getStudentId());

        // c) Resolve teacher and classroom: use request value if provided, otherwise keep existing
        ClassroomEntity classroom = request.getDefaultClassroomId() != null
                ? resolveClassroom(request.getDefaultClassroomId())
                : (existingRule.getDefaultClassroomId() != null
                        ? resolveClassroom(existingRule.getDefaultClassroomId())
                        : null);

        // d) Validate dates and slots
        validateUpdateRuleDates(request);
        validateSlots(request.getSlots());
        validateSlotTeachers(request.getSlots());
        validateSlotAllocations(request.getSlots(), coursePackage);

        // e) Update rule entity
        String newStatus = request.getRuleStatus() != null ? request.getRuleStatus() : existingRule.getRuleStatus();
        if (!List.of("ACTIVE", "PAUSED", "ENDED").contains(newStatus)) {
            throw new BusinessException("INVALID_RULE_STATUS", "Rule status must be ACTIVE, PAUSED, or ENDED.");
        }
        if ("PAUSED".equals(coursePackage.getPackageStatus()) && !"PAUSED".equals(newStatus)) {
            throw new BusinessException("PACKAGE_PAUSED",
                    "Keep this schedule paused while the 1-on-1 package is paused. Resume the package to generate lessons.");
        }
        existingRule.setPrimaryTeacherId(null);
        existingRule.setDefaultClassroomId(classroom == null ? null : classroom.getId());
        existingRule.setRuleStatus(newStatus);
        existingRule.setStartDate(request.getStartDate());
        existingRule.setEndDate(request.getEndDate() != null ? request.getEndDate() : existingRule.getEndDate());
        existingRule.setGenerationHorizonDays(request.getGenerationHorizonDays() != null
                ? normalizeGenerationHorizon(request.getGenerationHorizonDays())
                : existingRule.getGenerationHorizonDays());
        existingRule.setNotes(request.getNotes());
        recurringScheduleRuleMapper.updateById(existingRule);

        // f) Classify existing occurrences BEFORE deleting slots (FK constraint)
        List<LessonOccurrenceEntity> allOccurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getScheduleRuleId, id));
        Set<Long> occIds = allOccurrences.stream().map(LessonOccurrenceEntity::getId).collect(Collectors.toSet());
        List<LessonRecordEntity> activeRecords = !occIds.isEmpty() ? lessonRecordMapper.selectList(
                new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getOccurrenceId, occIds)
                        .ne(LessonRecordEntity::getConfirmStage, "REJECTED")) : Collections.emptyList();
        Map<Long, LessonRecordEntity> recordByOccurrenceId = activeRecords.stream()
                .collect(Collectors.toMap(LessonRecordEntity::getOccurrenceId, item -> item, (left, right) -> left));
        Set<Long> recordedOccIds = recordByOccurrenceId.keySet();

        // Delete all unconfirmed, non-adjusted/cancelled occurrences from today's start onward.
        // Using startOfDay ensures we clean up the entire current day, not just future instants.
        LocalDateTime todayStart = AppTime.nowLocalDateTime().toLocalDate().atStartOfDay();
        List<LessonOccurrenceEntity> toDelete = new ArrayList<>();
        List<LessonOccurrenceEntity> toPreserve = new ArrayList<>();
        for (LessonOccurrenceEntity occ : allOccurrences) {
            boolean isConfirmed = recordedOccIds.contains(occ.getId());
            boolean isAdjustedOrCancelled = "ADJUSTED".equals(occ.getPlanningStatus()) || "CANCELLED".equals(occ.getPlanningStatus());
            boolean isFromTodayOnward = !occ.getPlannedStartAt().isBefore(todayStart) && "SCHEDULED".equals(occ.getPlanningStatus());
            if (isConfirmed || isAdjustedOrCancelled || !isFromTodayOnward) {
                toPreserve.add(occ);
            } else {
                toDelete.add(occ);
            }
        }

        // g) Delete classified occurrences FIRST (before slot FK cleanup)
        int confirmedCount = (int) toPreserve.stream().filter(o -> recordedOccIds.contains(o.getId())).count();
        if (!toDelete.isEmpty()) {
            lessonOccurrenceMapper.delete(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                    .in(LessonOccurrenceEntity::getId, toDelete.stream().map(LessonOccurrenceEntity::getId).toList()));
        }

        // h) Nullify slot references on preserved occurrences, then replace slots
        if (!toPreserve.isEmpty()) {
            List<LessonOccurrenceEntity> updates = toPreserve.stream().map(occ -> {
                LessonOccurrenceEntity u = new LessonOccurrenceEntity();
                u.setId(occ.getId());
                u.setScheduleSlotId(null);
                return u;
            }).toList();
            updates.forEach(lessonOccurrenceMapper::updateById);
        }

        recurringScheduleSlotMapper.delete(new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                .eq(RecurringScheduleSlotEntity::getRuleId, id));
        List<RecurringScheduleSlotEntity> newSlots = request.getSlots().stream()
                .map(slotRequest -> toSlotEntity(id, slotRequest))
                .toList();
        newSlots.forEach(recurringScheduleSlotMapper::insert);

        // i) Build and insert new occurrences only for an active package and rule.
        // A paused package may update its future slot configuration, but lesson generation
        // must wait for the explicit resume action so its restart date is respected.
        List<LessonOccurrenceEntity> newOccurrences = Collections.emptyList();
        if ("ACTIVE".equals(coursePackage.getPackageStatus()) && "ACTIVE".equals(existingRule.getRuleStatus())) {
            newOccurrences = buildOccurrences(existingRule, newSlots, coursePackage, student.getId());
            newOccurrences = removeOccurrencesOverlappingPreserved(newOccurrences, toPreserve);
            newOccurrences = capGeneratedOccurrencesToPackageBalance(newOccurrences, toPreserve, recordByOccurrenceId, coursePackage);
        }

        // j) Conflict check excluding this rule's preserved occurrences
        ensureNoConflictsExcludingRule(newOccurrences, student.getId(),
                existingRule.getDefaultClassroomId(), id);

        newOccurrences.forEach(lessonOccurrenceMapper::insert);

        // k) Return result
        LocalDateTime firstStart = newOccurrences.stream().map(LessonOccurrenceEntity::getPlannedStartAt).min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime lastEnd = newOccurrences.stream().map(LessonOccurrenceEntity::getPlannedEndAt).max(LocalDateTime::compareTo).orElse(null);
        return new ScheduleRuleUpdateResultVO(
                id, newSlots.size(), toPreserve.size(), toDelete.size(),
                newOccurrences.size(), confirmedCount, firstStart, lastEnd);
    }

    private void validateUpdateRuleDates(RecurringScheduleRuleUpdateRequest request) {
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("INVALID_RULE_DATE_RANGE", "Rule end date cannot be earlier than the start date.");
        }
    }

    private TeacherEntity resolveTeacherById(Long teacherId) {
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        if (teacher == null || !"ACTIVE".equalsIgnoreCase(teacher.getStatus())) {
            throw new BusinessException("TEACHER_NOT_AVAILABLE", "The selected teacher does not exist or is not active.");
        }
        return teacher;
    }

    private void ensureNoConflictsExcludingRule(List<LessonOccurrenceEntity> candidates,
                                                Long studentId, Long classroomId, Long excludeRuleId) {
        if (candidates.isEmpty()) {
            return;
        }
        LocalDateTime windowStart = candidates.stream().map(LessonOccurrenceEntity::getPlannedStartAt).min(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime windowEnd = candidates.stream().map(LessonOccurrenceEntity::getPlannedEndAt).max(LocalDateTime::compareTo).orElseThrow();

        List<LessonOccurrenceEntity> studentOccurrences = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getStudentId, studentId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ne(LessonOccurrenceEntity::getScheduleRuleId, excludeRuleId)
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        Set<Long> teacherIds = candidates.stream()
                .map(LessonOccurrenceEntity::getPlannedTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<LessonOccurrenceEntity> teacherOccurrences = teacherIds.isEmpty() ? Collections.emptyList() : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getPlannedTeacherId, teacherIds)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ne(LessonOccurrenceEntity::getScheduleRuleId, excludeRuleId)
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        List<LessonOccurrenceEntity> classroomOccurrences = classroomId == null ? Collections.emptyList() : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPlannedClassroomId, classroomId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ne(LessonOccurrenceEntity::getScheduleRuleId, excludeRuleId)
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));

        List<String> conflicts = new ArrayList<>();
        List<ScheduleConflictDetailVO> details = new ArrayList<>();
        for (LessonOccurrenceEntity candidate : candidates) {
            LessonOccurrenceEntity studentConflict = firstOverlap(candidate, studentOccurrences);
            if (studentConflict != null) {
                conflicts.add("Student conflict at " + candidate.getPlannedStartAt());
                details.add(buildConflictDetail("STUDENT", studentId, resolveStudentName(studentId), studentConflict));
            }
            Long candidateTeacherId = candidate.getPlannedTeacherId();
            LessonOccurrenceEntity teacherConflict = firstOverlap(candidate, teacherOccurrences.stream()
                    .filter(existing -> Objects.equals(existing.getPlannedTeacherId(), candidateTeacherId))
                    .toList());
            if (candidateTeacherId != null && teacherConflict != null) {
                conflicts.add("Teacher conflict at " + candidate.getPlannedStartAt());
                details.add(buildConflictDetail("TEACHER", candidateTeacherId,
                        resolveTeacherName(candidateTeacherId), teacherConflict));
            }
            LessonOccurrenceEntity classroomConflict = firstOverlap(candidate, classroomOccurrences);
            if (classroomId != null && classroomConflict != null) {
                conflicts.add("Classroom conflict at " + candidate.getPlannedStartAt());
                details.add(buildConflictDetail("CLASSROOM", classroomId, resolveClassroomName(classroomId), classroomConflict));
            }
            if (conflicts.size() >= 3) {
                break;
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BusinessException("SCHEDULE_CONFLICT", String.join("; ", conflicts), details);
        }
    }

    private int normalizeGenerationHorizon(Integer generationHorizonDays) {
        int horizon = generationHorizonDays == null ? 90 : generationHorizonDays;
        if (horizon <= 0 || horizon > MAX_GENERATION_HORIZON_DAYS) {
            throw new BusinessException("INVALID_GENERATION_HORIZON", "Generation horizon must be between 1 and 180 days.");
        }
        return horizon;
    }

    private void validateRuleDates(RecurringScheduleRuleCreateRequest request) {
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("INVALID_RULE_DATE_RANGE", "Rule end date cannot be earlier than the start date.");
        }
    }

    /**
     * 这里同时校验时间段本身和单位换算，避免排课时间和课时单位不一致。
     */
    private void validateSlots(List<RecurringScheduleSlotRequest> slots) {
        Map<Integer, List<RecurringScheduleSlotRequest>> grouped = slots.stream()
                .collect(Collectors.groupingBy(RecurringScheduleSlotRequest::getWeekday));
        for (Map.Entry<Integer, List<RecurringScheduleSlotRequest>> entry : grouped.entrySet()) {
            List<RecurringScheduleSlotRequest> sameDaySlots = entry.getValue().stream()
                    .sorted(Comparator.comparing(RecurringScheduleSlotRequest::getStartTime))
                    .toList();
            RecurringScheduleSlotRequest previous = null;
            for (RecurringScheduleSlotRequest slot : sameDaySlots) {
                if (!slot.getEndTime().isAfter(slot.getStartTime())) {
                    throw new BusinessException("INVALID_SLOT_TIME_RANGE", "Slot end time must be later than the start time.");
                }
                long minutes = ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime());
                if (minutes != slot.getPlannedUnits() * 30L) {
                    throw new BusinessException("SLOT_UNITS_MISMATCH", "Slot duration must exactly match planned units at 0.5h per unit.");
                }
                if (previous != null && slot.getStartTime().isBefore(previous.getEndTime())) {
                    throw new BusinessException("OVERLAPPING_SLOTS", "Recurring slots on the same weekday cannot overlap.");
                }
                previous = slot;
            }
        }
    }

    private void validateSlotTeachers(List<RecurringScheduleSlotRequest> slots) {
        slots.stream()
                .map(RecurringScheduleSlotRequest::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::resolveTeacherById);
    }

    /**
     * 校验各时段分配课时总和不超过课时包可分配课时（总课时含赠送，扣除报名时录入的已用课时）。
     */
    private void validateSlotAllocations(List<RecurringScheduleSlotRequest> slots, CoursePackageEntity coursePackage) {
        int allocatedSum = 0;
        for (RecurringScheduleSlotRequest slot : slots) {
            if (slot.getTeacherId() == null || !StringUtils.hasText(slot.getSubjectName())
                    || slot.getAllocatedUnits() == null || slot.getAllocatedUnits() <= 0) {
                throw new BusinessException("INCOMPLETE_SLOT_ALLOCATION",
                        "Each schedule slot requires a teacher, course/subject, and allocated hours.");
            }
            allocatedSum += slot.getAllocatedUnits();
        }
        int allocatableUnits = allocatableUnits(coursePackage);
        if (allocatedSum > allocatableUnits) {
            throw new BusinessException("SLOT_ALLOCATION_EXCEEDS_PACKAGE",
                    "Total allocated hours across slots cannot exceed the package's remaining hours.");
        }
    }

    /**
     * 课包可分配课时（unit）：优先用剩余课时，回退到总课时扣除已用课时。
     */
    private int allocatableUnits(CoursePackageEntity coursePackage) {
        Integer remaining = coursePackage.getRemainingUnits();
        if (remaining != null) {
            return Math.max(0, remaining);
        }
        int total = coursePackage.getTotalUnits() == null ? 0 : coursePackage.getTotalUnits();
        int consumed = coursePackage.getConsumedUnits() == null ? 0 : coursePackage.getConsumedUnits();
        return Math.max(0, total - consumed);
    }

    private ClassroomEntity resolveClassroom(Long classroomId) {
        if (classroomId == null) {
            return null;
        }
        ClassroomEntity classroom = classroomMapper.selectById(classroomId);
        if (classroom == null || !"ACTIVE".equalsIgnoreCase(classroom.getStatus())) {
            throw new BusinessException("CLASSROOM_NOT_AVAILABLE", "The selected classroom does not exist or is not active.");
        }
        return classroom;
    }

    private RecurringScheduleSlotEntity toSlotEntity(Long ruleId, RecurringScheduleSlotRequest request) {
        RecurringScheduleSlotEntity entity = new RecurringScheduleSlotEntity();
        entity.setRuleId(ruleId);
        entity.setTeacherId(request.getTeacherId());
        entity.setSubjectName(request.getSubjectName());
        entity.setWeekday(request.getWeekday());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setPlannedUnits(request.getPlannedUnits());
        entity.setAllocatedUnits(request.getAllocatedUnits());
        entity.setSlotStatus("ACTIVE");
        return entity;
    }

    private List<LessonOccurrenceEntity> buildOccurrences(RecurringScheduleRuleEntity rule,
                                                          List<RecurringScheduleSlotEntity> slots,
                                                          CoursePackageEntity coursePackage,
                                                          Long studentId) {
        LocalDate generationStart = Collections.max(List.of(rule.getStartDate(), AppTime.nowLocalDateTime().toLocalDate()));
        LocalDate naturalWindowEnd = generationStart.plusDays(rule.getGenerationHorizonDays() - 1L);
        LocalDate generationEnd = rule.getEndDate() == null ? naturalWindowEnd : Collections.min(List.of(rule.getEndDate(), naturalWindowEnd));
        if (generationEnd.isBefore(generationStart)) {
            return Collections.emptyList();
        }

        // 逐时段独立生成：每个 slot 用自己的 allocatedUnits 排满，最后一次课不足整次时只排余数。
        List<LessonOccurrenceEntity> occurrences = new ArrayList<>();
        for (RecurringScheduleSlotEntity slot : slots) {
            if (!"ACTIVE".equals(slot.getSlotStatus())) {
                continue;
            }
            requireCompleteSlotAllocation(slot);
            int plannedUnits = slot.getPlannedUnits() == null ? 0 : slot.getPlannedUnits();
            if (plannedUnits <= 0) {
                continue;
            }
            int remaining = slot.getAllocatedUnits();
            for (LocalDate date = generationStart; !date.isAfter(generationEnd); date = date.plusDays(1)) {
                if (remaining <= 0) {
                    break;
                }
                if (!Objects.equals(slot.getWeekday(), toWeekday(date.getDayOfWeek()))) {
                    continue;
                }
                int thisUnits = Math.min(plannedUnits, remaining);
                LocalDateTime start = LocalDateTime.of(date, slot.getStartTime());
                LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
                occurrence.setLessonNo(BusinessNoGenerator.generateLessonOccurrenceNo());
                occurrence.setPackageId(coursePackage.getId());
                occurrence.setStudentId(studentId);
                occurrence.setScheduleRuleId(rule.getId());
                occurrence.setScheduleSlotId(slot.getId());
                occurrence.setOccurrenceType("REGULAR");
                occurrence.setPlannedTeacherId(slot.getTeacherId());
                occurrence.setPlannedClassroomId(rule.getDefaultClassroomId());
                occurrence.setPlannedStartAt(start);
                // 末次课不足整次时按余数收尾，结束时间随之缩短（每 unit = 30 分钟）。
                occurrence.setPlannedEndAt(start.plusMinutes(thisUnits * 30L));
                occurrence.setPlannedUnits(thisUnits);
                occurrence.setPlanningStatus("SCHEDULED");
                occurrence.setConflictChecked(1);
                occurrences.add(occurrence);
                remaining -= thisUnits;
            }
        }
        occurrences.sort(Comparator.comparing(LessonOccurrenceEntity::getPlannedStartAt));
        return occurrences;
    }

    private void requireCompleteSlotAllocation(RecurringScheduleSlotEntity slot) {
        if (slot.getTeacherId() == null || !StringUtils.hasText(slot.getSubjectName())
                || slot.getAllocatedUnits() == null || slot.getAllocatedUnits() <= 0) {
            throw new BusinessException("INCOMPLETE_SLOT_ALLOCATION",
                    "Each schedule slot requires a teacher, course/subject, and allocated hours. Update this schedule before generating more lessons.");
        }
    }

    private List<LessonOccurrenceEntity> listActivePackageOccurrences(Long packageId) {
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPackageId, packageId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));
    }

    private Map<Long, LessonRecordEntity> listActiveRecordsByOccurrenceId(List<LessonOccurrenceEntity> occurrences) {
        if (occurrences.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> occurrenceIds = occurrences.stream()
                .map(LessonOccurrenceEntity::getId)
                .collect(Collectors.toSet());
        return lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getOccurrenceId, occurrenceIds)
                        .ne(LessonRecordEntity::getConfirmStage, "REJECTED"))
                .stream()
                .collect(Collectors.toMap(LessonRecordEntity::getOccurrenceId, item -> item, (left, right) -> left));
    }

    private List<LessonOccurrenceEntity> removeOccurrencesOverlappingPreserved(List<LessonOccurrenceEntity> candidates,
                                                                               List<LessonOccurrenceEntity> preservedOccurrences) {
        if (candidates.isEmpty() || preservedOccurrences.isEmpty()) {
            return candidates;
        }
        List<LessonOccurrenceEntity> activePreserved = preservedOccurrences.stream()
                .filter(occurrence -> !"CANCELLED".equals(occurrence.getPlanningStatus()))
                .toList();
        return candidates.stream()
                .filter(candidate -> !hasOverlap(candidate, activePreserved))
                .toList();
    }

    /**
     * 按当前课时包余额限制生成课次，避免新课次覆盖已保留或已确认课次的课时。
     */
    private List<LessonOccurrenceEntity> capGeneratedOccurrencesToPackageBalance(List<LessonOccurrenceEntity> candidates,
                                                                                 List<LessonOccurrenceEntity> preservedOccurrences,
                                                                                 Map<Long, LessonRecordEntity> recordByOccurrenceId,
                                                                                 CoursePackageEntity coursePackage) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        // 已被"计划中但尚未消课"的课次占用的课时（已消课的课次已计入课包 consumedUnits，不能重复扣）。
        int reservedPlannedUnits = preservedOccurrences.stream()
                .filter(occurrence -> !"CANCELLED".equals(occurrence.getPlanningStatus()))
                .filter(occurrence -> !isConsumedOccurrence(recordByOccurrenceId.get(occurrence.getId())))
                .mapToInt(occurrence -> occurrence.getPlannedUnits() == null ? 0 : occurrence.getPlannedUnits())
                .sum();
        // 可分配基数用课包剩余课时（已扣报名时录入的已用课时与历史消课），再扣掉计划中未消课的占用。
        int remainingUnits = Math.max(0, allocatableUnits(coursePackage) - reservedPlannedUnits);
        if (remainingUnits == 0) {
            return Collections.emptyList();
        }

        List<LessonOccurrenceEntity> capped = new ArrayList<>();
        int remaining = remainingUnits;
        for (LessonOccurrenceEntity candidate : candidates.stream()
                .sorted(Comparator.comparing(LessonOccurrenceEntity::getPlannedStartAt))
                .toList()) {
            int plannedUnits = candidate.getPlannedUnits() == null ? 0 : candidate.getPlannedUnits();
            if (plannedUnits <= 0 || remaining <= 0) {
                break;
            }
            if (plannedUnits <= remaining) {
                capped.add(candidate);
                remaining -= plannedUnits;
                continue;
            }
            updateCandidatePlannedUnits(candidate, remaining);
            capped.add(candidate);
            break;
        }
        return capped;
    }

    /** 该课次是否已消课（已计入课包 consumedUnits，不能在排课时重复扣减）。 */
    private boolean isConsumedOccurrence(LessonRecordEntity record) {
        return record != null
                && record.getShouldDeduct() != null && record.getShouldDeduct() == 1
                && record.getDeductedUnits() != null && record.getDeductedUnits() > 0;
    }

    private void updateCandidatePlannedUnits(LessonOccurrenceEntity occurrence, int plannedUnits) {
        occurrence.setPlannedUnits(plannedUnits);
        occurrence.setPlannedEndAt(occurrence.getPlannedStartAt().plusMinutes(plannedUnits * 30L));
    }

    /**
     * 冲突检测一次性拉取时间窗口内相关课次，避免按课次逐条扫库。
     */
    private void ensureNoConflicts(List<LessonOccurrenceEntity> generatedOccurrences,
                                   Long studentId,
                                   Long classroomId) {
        if (generatedOccurrences.isEmpty()) {
            return;
        }
        LocalDateTime windowStart = generatedOccurrences.stream().map(LessonOccurrenceEntity::getPlannedStartAt).min(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime windowEnd = generatedOccurrences.stream().map(LessonOccurrenceEntity::getPlannedEndAt).max(LocalDateTime::compareTo).orElseThrow();

        List<LessonOccurrenceEntity> studentOccurrences = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getStudentId, studentId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        Set<Long> teacherIds = generatedOccurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<LessonOccurrenceEntity> teacherOccurrences = teacherIds.isEmpty() ? Collections.emptyList() : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getPlannedTeacherId, teacherIds)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        List<LessonOccurrenceEntity> classroomOccurrences = classroomId == null ? Collections.emptyList() : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPlannedClassroomId, classroomId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));

        List<String> conflicts = new ArrayList<>();
        List<ScheduleConflictDetailVO> details = new ArrayList<>();
        for (LessonOccurrenceEntity candidate : generatedOccurrences) {
            LessonOccurrenceEntity studentConflict = firstOverlap(candidate, studentOccurrences);
            if (studentConflict != null) {
                conflicts.add("Student conflict at " + candidate.getPlannedStartAt());
                details.add(buildConflictDetail("STUDENT", studentId, resolveStudentName(studentId), studentConflict));
            }
            Long candidateTeacherId = candidate.getPlannedTeacherId();
            LessonOccurrenceEntity teacherConflict = firstOverlap(candidate, teacherOccurrences.stream()
                    .filter(existing -> Objects.equals(existing.getPlannedTeacherId(), candidateTeacherId))
                    .toList());
            if (candidateTeacherId != null && teacherConflict != null) {
                conflicts.add("Teacher conflict at " + candidate.getPlannedStartAt());
                details.add(buildConflictDetail("TEACHER", candidateTeacherId,
                        resolveTeacherName(candidateTeacherId), teacherConflict));
            }
            LessonOccurrenceEntity classroomConflict = firstOverlap(candidate, classroomOccurrences);
            if (classroomId != null && classroomConflict != null) {
                conflicts.add("Classroom conflict at " + candidate.getPlannedStartAt());
                details.add(buildConflictDetail("CLASSROOM", classroomId, resolveClassroomName(classroomId), classroomConflict));
            }
            if (conflicts.size() >= 3) {
                break;
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BusinessException("SCHEDULE_CONFLICT", String.join("; ", conflicts), details);
        }
    }

    private boolean hasOverlap(LessonOccurrenceEntity candidate, List<LessonOccurrenceEntity> existingOccurrences) {
        return existingOccurrences.stream().anyMatch(existing -> overlaps(
                candidate.getPlannedStartAt(),
                candidate.getPlannedEndAt(),
                existing.getPlannedStartAt(),
                existing.getPlannedEndAt()
        ));
    }

    private LessonOccurrenceEntity firstOverlap(LessonOccurrenceEntity candidate, List<LessonOccurrenceEntity> existingOccurrences) {
        return existingOccurrences.stream()
                .filter(existing -> overlaps(candidate.getPlannedStartAt(), candidate.getPlannedEndAt(),
                        existing.getPlannedStartAt(), existing.getPlannedEndAt()))
                .findFirst()
                .orElse(null);
    }

    private ScheduleConflictDetailVO buildConflictDetail(String resourceType,
                                                         Long resourceId,
                                                         String resourceName,
                                                         LessonOccurrenceEntity existing) {
        return ScheduleConflictDetailVO.builder()
                .resourceType(resourceType)
                .resourceId(resourceId)
                .resourceName(resourceName)
                .conflictStartAt(existing.getPlannedStartAt())
                .conflictEndAt(existing.getPlannedEndAt())
                .existingOccurrenceId(existing.getId())
                .existingLessonNo(existing.getLessonNo())
                .build();
    }

    private String resolveStudentName(Long studentId) {
        StudentEntity student = studentId == null ? null : studentMapper.selectById(studentId);
        return student == null ? null : student.getStudentName();
    }

    private String resolveTeacherName(Long teacherId) {
        TeacherEntity teacher = teacherId == null ? null : teacherMapper.selectById(teacherId);
        return teacher == null ? null : teacher.getTeacherName();
    }

    private String resolveClassroomName(Long classroomId) {
        ClassroomEntity classroom = classroomId == null ? null : classroomMapper.selectById(classroomId);
        return classroom == null ? null : classroom.getRoomName();
    }

    private boolean overlaps(LocalDateTime startA, LocalDateTime endA, LocalDateTime startB, LocalDateTime endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }

    private int toWeekday(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> 1;
            case TUESDAY -> 2;
            case WEDNESDAY -> 3;
            case THURSDAY -> 4;
            case FRIDAY -> 5;
            case SATURDAY -> 6;
            case SUNDAY -> 7;
        };
    }

    private RecurringScheduleRuleEntity requireRule(Long id) {
        RecurringScheduleRuleEntity rule = recurringScheduleRuleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException("SCHEDULE_RULE_NOT_FOUND", "Recurring schedule rule not found.");
        }
        return rule;
    }

    private CoursePackageEntity requireActivePackage(Long packageId) {
        CoursePackageEntity coursePackage = requirePackage(packageId);
        if (!"ACTIVE".equalsIgnoreCase(coursePackage.getPackageStatus())) {
            throw new BusinessException("PACKAGE_NOT_ACTIVE", "Only active course packages can be scheduled.");
        }
        return coursePackage;
    }

    private CoursePackageEntity requirePackage(Long packageId) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectById(packageId);
        if (coursePackage == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        return coursePackage;
    }

    private StudentEntity requireStudent(Long studentId) {
        StudentEntity student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException("NOT_FOUND", "Student not found.");
        }
        return student;
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

    private Map<Long, Integer> countSlotsByRuleId(List<Long> ruleIds) {
        if (ruleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return recurringScheduleSlotMapper.selectList(new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                        .in(RecurringScheduleSlotEntity::getRuleId, ruleIds))
                .stream()
                .collect(Collectors.groupingBy(RecurringScheduleSlotEntity::getRuleId, Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
    }

    private Map<Long, List<LessonOccurrenceEntity>> listOccurrencesByRuleIds(List<Long> ruleIds) {
        if (ruleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .in(LessonOccurrenceEntity::getScheduleRuleId, ruleIds)
                        .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt))
                .stream()
                .collect(Collectors.groupingBy(LessonOccurrenceEntity::getScheduleRuleId));
    }

    private List<Long> findMatchedPackageIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        List<Long> matchedStudentIds = studentMapper.selectList(new LambdaQueryWrapper<StudentEntity>()
                        .and(wrapper -> wrapper.like(StudentEntity::getStudentName, keyword)
                                .or()
                                .like(StudentEntity::getStudentNo, keyword)))
                .stream()
                .map(StudentEntity::getId)
                .toList();
        return coursePackageMapper.selectList(new LambdaQueryWrapper<CoursePackageEntity>()
                        .and(wrapper -> wrapper.like(CoursePackageEntity::getPackageNo, keyword)
                                .or()
                                .like(CoursePackageEntity::getPackageName, keyword)
                                .or(!matchedStudentIds.isEmpty(), nested -> nested.in(CoursePackageEntity::getStudentId, matchedStudentIds))))
                .stream()
                .map(CoursePackageEntity::getId)
                .toList();
    }

    private List<Long> findMatchedRuleIdsBySlotTeacher(Long teacherId) {
        if (teacherId == null) {
            return Collections.emptyList();
        }
        return recurringScheduleSlotMapper.selectList(new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                        .eq(RecurringScheduleSlotEntity::getTeacherId, teacherId)
                        .eq(RecurringScheduleSlotEntity::getSlotStatus, "ACTIVE"))
                .stream()
                .map(RecurringScheduleSlotEntity::getRuleId)
                .distinct()
                .toList();
    }

    private RecurringScheduleSlotVO toSlotVO(RecurringScheduleSlotEntity slot) {
        return RecurringScheduleSlotVO.builder()
                .id(slot.getId())
                .teacherId(slot.getTeacherId())
                .subjectName(slot.getSubjectName())
                .weekday(slot.getWeekday())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .plannedUnits(slot.getPlannedUnits())
                .allocatedUnits(slot.getAllocatedUnits())
                .slotStatus(slot.getSlotStatus())
                .build();
    }
}
