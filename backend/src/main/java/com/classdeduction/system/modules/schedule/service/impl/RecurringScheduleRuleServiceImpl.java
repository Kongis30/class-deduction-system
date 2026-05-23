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
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
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
    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;

    @Override
    public PageResponse<RecurringScheduleRuleListItemVO> pageRules(RecurringScheduleRulePageQuery query) {
        Page<RecurringScheduleRuleEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<Long> matchedPackageIds = findMatchedPackageIds(query.getKeyword());
        LambdaQueryWrapper<RecurringScheduleRuleEntity> wrapper = new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                .and(StringUtils.hasText(query.getKeyword()), nested -> nested
                        .like(RecurringScheduleRuleEntity::getNotes, query.getKeyword())
                        .or(!matchedPackageIds.isEmpty(), inner -> inner.in(RecurringScheduleRuleEntity::getPackageId, matchedPackageIds)))
                .eq(StringUtils.hasText(query.getRuleStatus()), RecurringScheduleRuleEntity::getRuleStatus, query.getRuleStatus())
                .eq(query.getTeacherId() != null, RecurringScheduleRuleEntity::getPrimaryTeacherId, query.getTeacherId())
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
        Map<Long, TeacherEntity> teacherMap = listTeachersByIds(result.getRecords().stream().map(RecurringScheduleRuleEntity::getPrimaryTeacherId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, ClassroomEntity> classroomMap = listClassroomsByIds(result.getRecords().stream().map(RecurringScheduleRuleEntity::getDefaultClassroomId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, Integer> slotCountMap = countSlotsByRuleId(result.getRecords().stream().map(RecurringScheduleRuleEntity::getId).toList());
        Map<Long, List<LessonOccurrenceEntity>> occurrenceGroup = listOccurrencesByRuleIds(result.getRecords().stream().map(RecurringScheduleRuleEntity::getId).toList());
        LocalDateTime now = AppTime.nowLocalDateTime();

        List<RecurringScheduleRuleListItemVO> records = result.getRecords().stream()
                .map(rule -> {
                    CoursePackageEntity coursePackage = packageMap.get(rule.getPackageId());
                    StudentEntity student = coursePackage == null ? null : studentMap.get(coursePackage.getStudentId());
                    TeacherEntity teacher = teacherMap.get(rule.getPrimaryTeacherId());
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
                            .primaryTeacherId(rule.getPrimaryTeacherId())
                            .primaryTeacherName(teacher == null ? null : teacher.getTeacherName())
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
        TeacherEntity teacher = rule.getPrimaryTeacherId() == null ? null : teacherMapper.selectById(rule.getPrimaryTeacherId());
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
                .primaryTeacherId(rule.getPrimaryTeacherId())
                .primaryTeacherName(teacher == null ? null : teacher.getTeacherName())
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
        TeacherEntity teacher = resolveTeacher(request, coursePackage.getId());
        ClassroomEntity classroom = resolveClassroom(request.getDefaultClassroomId());
        validateRuleDates(request);
        validateSlots(request.getSlots());

        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setPackageId(coursePackage.getId());
        rule.setPrimaryTeacherId(teacher == null ? null : teacher.getId());
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
        ensureNoConflicts(generatedOccurrences, student.getId(), rule.getPrimaryTeacherId(), rule.getDefaultClassroomId());
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
        TeacherEntity teacher = request.getPrimaryTeacherId() != null
                ? resolveTeacherById(request.getPrimaryTeacherId())
                : (existingRule.getPrimaryTeacherId() != null
                        ? resolveTeacherById(existingRule.getPrimaryTeacherId())
                        : null);
        ClassroomEntity classroom = request.getDefaultClassroomId() != null
                ? resolveClassroom(request.getDefaultClassroomId())
                : (existingRule.getDefaultClassroomId() != null
                        ? resolveClassroom(existingRule.getDefaultClassroomId())
                        : null);

        // d) Validate dates and slots
        validateUpdateRuleDates(request);
        validateSlots(request.getSlots());

        // e) Update rule entity
        String newStatus = request.getRuleStatus() != null ? request.getRuleStatus() : existingRule.getRuleStatus();
        if (!List.of("ACTIVE", "PAUSED", "ENDED").contains(newStatus)) {
            throw new BusinessException("INVALID_RULE_STATUS", "Rule status must be ACTIVE, PAUSED, or ENDED.");
        }
        existingRule.setPrimaryTeacherId(teacher == null ? null : teacher.getId());
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
        Set<Long> confirmedOccIds = !occIds.isEmpty() ? lessonRecordMapper.selectList(
                        new LambdaQueryWrapper<LessonRecordEntity>().in(LessonRecordEntity::getOccurrenceId, occIds))
                .stream().map(LessonRecordEntity::getOccurrenceId).collect(Collectors.toSet())
                : Collections.emptySet();

        // Delete all unconfirmed, non-adjusted/cancelled occurrences from today's start onward.
        // Using startOfDay ensures we clean up the entire current day, not just future instants.
        LocalDateTime todayStart = AppTime.nowLocalDateTime().toLocalDate().atStartOfDay();
        List<LessonOccurrenceEntity> toDelete = new ArrayList<>();
        List<LessonOccurrenceEntity> toPreserve = new ArrayList<>();
        for (LessonOccurrenceEntity occ : allOccurrences) {
            boolean isConfirmed = confirmedOccIds.contains(occ.getId());
            boolean isAdjustedOrCancelled = "ADJUSTED".equals(occ.getPlanningStatus()) || "CANCELLED".equals(occ.getPlanningStatus());
            boolean isFromTodayOnward = !occ.getPlannedStartAt().isBefore(todayStart) && "SCHEDULED".equals(occ.getPlanningStatus());
            if (isConfirmed || isAdjustedOrCancelled || !isFromTodayOnward) {
                toPreserve.add(occ);
            } else {
                toDelete.add(occ);
            }
        }

        // g) Delete classified occurrences FIRST (before slot FK cleanup)
        int confirmedCount = (int) toPreserve.stream().filter(o -> confirmedOccIds.contains(o.getId())).count();
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

        // i) Build and insert new occurrences
        List<LessonOccurrenceEntity> newOccurrences = buildOccurrences(existingRule, newSlots, coursePackage, student.getId());

        // j) Conflict check excluding this rule's preserved occurrences
        ensureNoConflictsExcludingRule(newOccurrences, student.getId(),
                existingRule.getPrimaryTeacherId(), existingRule.getDefaultClassroomId(), id);

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
                                                Long studentId, Long teacherId, Long classroomId, Long excludeRuleId) {
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
        List<LessonOccurrenceEntity> teacherOccurrences = teacherId == null ? Collections.emptyList() : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPlannedTeacherId, teacherId)
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
        for (LessonOccurrenceEntity candidate : candidates) {
            if (hasOverlap(candidate, studentOccurrences)) {
                conflicts.add("Student conflict at " + candidate.getPlannedStartAt());
            }
            if (teacherId != null && hasOverlap(candidate, teacherOccurrences)) {
                conflicts.add("Teacher conflict at " + candidate.getPlannedStartAt());
            }
            if (classroomId != null && hasOverlap(candidate, classroomOccurrences)) {
                conflicts.add("Classroom conflict at " + candidate.getPlannedStartAt());
            }
            if (conflicts.size() >= 3) {
                break;
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BusinessException("SCHEDULE_CONFLICT", String.join("; ", conflicts));
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

    private TeacherEntity resolveTeacher(RecurringScheduleRuleCreateRequest request, Long packageId) {
        Long teacherId = request.getPrimaryTeacherId();
        if (teacherId == null) {
            PackageTeacherAssignmentEntity assignment = packageTeacherAssignmentMapper.selectOne(new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                    .eq(PackageTeacherAssignmentEntity::getPackageId, packageId)
                    .eq(PackageTeacherAssignmentEntity::getIsPrimary, 1)
                    .eq(PackageTeacherAssignmentEntity::getAssignmentStatus, "ACTIVE")
                    .orderByDesc(PackageTeacherAssignmentEntity::getEffectiveFrom)
                    .last("LIMIT 1"));
            teacherId = assignment == null ? null : assignment.getTeacherId();
        }
        if (teacherId == null) {
            return null;
        }
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        if (teacher == null || !"ACTIVE".equalsIgnoreCase(teacher.getStatus())) {
            throw new BusinessException("TEACHER_NOT_AVAILABLE", "The selected teacher does not exist or is not active.");
        }
        return teacher;
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
        entity.setWeekday(request.getWeekday());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setPlannedUnits(request.getPlannedUnits());
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

        List<LessonOccurrenceEntity> occurrences = new ArrayList<>();
        for (LocalDate date = generationStart; !date.isAfter(generationEnd); date = date.plusDays(1)) {
            int weekday = toWeekday(date.getDayOfWeek());
            for (RecurringScheduleSlotEntity slot : slots) {
                if (!"ACTIVE".equals(slot.getSlotStatus()) || !Objects.equals(slot.getWeekday(), weekday)) {
                    continue;
                }
                LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
                occurrence.setLessonNo(generateLessonNo());
                occurrence.setPackageId(coursePackage.getId());
                occurrence.setStudentId(studentId);
                occurrence.setScheduleRuleId(rule.getId());
                occurrence.setScheduleSlotId(slot.getId());
                occurrence.setOccurrenceType("REGULAR");
                occurrence.setPlannedTeacherId(rule.getPrimaryTeacherId());
                occurrence.setPlannedClassroomId(rule.getDefaultClassroomId());
                occurrence.setPlannedStartAt(LocalDateTime.of(date, slot.getStartTime()));
                occurrence.setPlannedEndAt(LocalDateTime.of(date, slot.getEndTime()));
                occurrence.setPlannedUnits(slot.getPlannedUnits());
                occurrence.setPlanningStatus("SCHEDULED");
                occurrence.setConflictChecked(1);
                occurrences.add(occurrence);
            }
        }
        return occurrences;
    }

    /**
     * 冲突检测一次性拉取时间窗口内相关课次，避免按课次逐条扫库。
     */
    private void ensureNoConflicts(List<LessonOccurrenceEntity> generatedOccurrences,
                                   Long studentId,
                                   Long teacherId,
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
        List<LessonOccurrenceEntity> teacherOccurrences = teacherId == null ? Collections.emptyList() : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPlannedTeacherId, teacherId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        List<LessonOccurrenceEntity> classroomOccurrences = classroomId == null ? Collections.emptyList() : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPlannedClassroomId, classroomId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));

        List<String> conflicts = new ArrayList<>();
        for (LessonOccurrenceEntity candidate : generatedOccurrences) {
            if (hasOverlap(candidate, studentOccurrences)) {
                conflicts.add("Student conflict at " + candidate.getPlannedStartAt());
            }
            if (teacherId != null && hasOverlap(candidate, teacherOccurrences)) {
                conflicts.add("Teacher conflict at " + candidate.getPlannedStartAt());
            }
            if (classroomId != null && hasOverlap(candidate, classroomOccurrences)) {
                conflicts.add("Classroom conflict at " + candidate.getPlannedStartAt());
            }
            if (conflicts.size() >= 3) {
                break;
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BusinessException("SCHEDULE_CONFLICT", String.join("; ", conflicts));
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

    private boolean overlaps(LocalDateTime startA, LocalDateTime endA, LocalDateTime startB, LocalDateTime endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }

    private String generateLessonNo() {
        return "LS" + AppTime.nowLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000));
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
        log.info("requirePackage: looking up packageId={}", packageId);
        CoursePackageEntity coursePackage = coursePackageMapper.selectById(packageId);
        if (coursePackage == null) {
            log.warn("requirePackage: course package NOT FOUND for packageId={}", packageId);
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        log.info("requirePackage: found package packageNo={}, status={}", coursePackage.getPackageNo(), coursePackage.getPackageStatus());
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

    private RecurringScheduleSlotVO toSlotVO(RecurringScheduleSlotEntity slot) {
        return RecurringScheduleSlotVO.builder()
                .id(slot.getId())
                .weekday(slot.getWeekday())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .plannedUnits(slot.getPlannedUnits())
                .slotStatus(slot.getSlotStatus())
                .build();
    }
}
