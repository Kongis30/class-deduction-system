package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.enrollment.vo.OneOnOneScheduleCourseRow;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleCreateRequest;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRulePageQuery;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleRuleUpdateRequest;
import com.classdeduction.system.modules.schedule.dto.RecurringScheduleSlotRequest;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.LessonAdjustmentLogEntity;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleStartingBalanceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonAdjustmentLogMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleStartingBalanceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.service.RecurringScheduleRuleService;
import com.classdeduction.system.modules.schedule.util.ScheduleSlotTime;
import com.classdeduction.system.modules.schedule.util.PrivateLessonAllocation;
import com.classdeduction.system.modules.schedule.vo.RecurringScheduleRuleDetailVO;
import com.classdeduction.system.modules.schedule.vo.RecurringScheduleRuleListItemVO;
import com.classdeduction.system.modules.schedule.vo.RecurringScheduleSlotVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleConflictDetailVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleStudentOptionVO;
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
 *   <li>时段分配额属于当前排课周期；实质修改周排课时新建规则版本，
 *       新周期以课包当前余额为 Starting Hours，旧消课留在旧周期。</li>
 *   <li>仍保留的待确认课次和单次例外继续占用课包余额，不能被新周期重复分配。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringScheduleRuleServiceImpl implements RecurringScheduleRuleService {

    private final RecurringScheduleRuleMapper recurringScheduleRuleMapper;
    private final RecurringScheduleStartingBalanceMapper startingBalanceMapper;
    private final RecurringScheduleSlotMapper recurringScheduleSlotMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonAdjustmentLogMapper lessonAdjustmentLogMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final PrivateLessonGroupMapper privateLessonGroupMapper;
    private final PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final JsonHelper jsonHelper;

    @Override
    public List<ScheduleStudentOptionVO> listOneOnOneScheduleStudents() {
        // 每个课包独立展示；共享课仅用主课包作一个排课入口。
        Map<Long, ScheduleStudentOptionBuilder> students = new LinkedHashMap<>();
        for (OneOnOneScheduleCourseRow row : coursePackageMapper.selectSchedulableOneOnOneStudentCourseGrades()) {
            ScheduleStudentOptionBuilder student = students.computeIfAbsent(
                    row.studentId(), id -> new ScheduleStudentOptionBuilder(id, row.studentName(), row.studentGrade()));
            student.addCourseGrade(row);
        }
        return students.values().stream()
                .map(ScheduleStudentOptionBuilder::build)
                .toList();
    }

    private static final class ScheduleStudentOptionBuilder {
        private final Long id;
        private final String studentName;
        private final String studentGrade;
        private final Map<String, ScheduleStudentOptionVO.CourseGradeOption> courseGrades = new LinkedHashMap<>();

        private ScheduleStudentOptionBuilder(Long id, String studentName, String studentGrade) {
            this.id = id;
            this.studentName = studentName;
            this.studentGrade = studentGrade;
        }

        private void addCourseGrade(OneOnOneScheduleCourseRow row) {
            String courseKey = (row.courseName() == null ? "" : row.courseName().trim().toLowerCase(Locale.ROOT))
                    + "::" + (row.gradeBand() == null ? "" : row.gradeBand().trim().toLowerCase(Locale.ROOT));
            boolean sharedGroup = row.privateLessonGroupId() != null;
            String key = row.packageId() == null ? courseKey : "package::" + row.packageId();
            ScheduleStudentOptionVO.CourseGradeOption incoming = new ScheduleStudentOptionVO.CourseGradeOption(
                    row.courseName(), row.gradeBand(), row.hasSchedule(),
                    row.packageId(), row.packageStatus(),
                    row.privateLessonGroupId(), row.participantCount(),
                    sharedGroup ? row.participantNames() : null,
                    sharedGroup ? row.participantStudentIds() : null);
            courseGrades.merge(key, incoming, (existing, next) -> new ScheduleStudentOptionVO.CourseGradeOption(
                    existing.courseName(), existing.gradeBand(), existing.hasSchedule() && next.hasSchedule(),
                    existing.packageId(), existing.packageStatus(), existing.privateLessonGroupId(), existing.participantCount(),
                    existing.participantNames(), existing.participantStudentIds()));
        }

        private ScheduleStudentOptionVO build() {
            return new ScheduleStudentOptionVO(id, studentName, studentGrade, List.copyOf(courseGrades.values()));
        }
    }

    @Override
    public PageResponse<RecurringScheduleRuleListItemVO> pageRules(RecurringScheduleRulePageQuery query) {
        Page<RecurringScheduleRuleEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<Long> matchedPackageIds = findMatchedPackageIds(query.getKeyword());
        List<Long> matchedRuleIds = findMatchedRuleIdsBySlotTeacher(query.getTeacherId());
        List<Long> matchedClassroomRuleIds = query.getClassroomId() == null
                ? Collections.emptyList() : findMatchedRuleIdsByClassroom(query.getClassroomId());
        if (query.getClassroomId() != null && matchedClassroomRuleIds.isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        LambdaQueryWrapper<RecurringScheduleRuleEntity> wrapper = new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                .and(StringUtils.hasText(query.getKeyword()), nested -> nested
                        .like(RecurringScheduleRuleEntity::getNotes, query.getKeyword())
                        .or(!matchedPackageIds.isEmpty(), inner -> inner.in(RecurringScheduleRuleEntity::getPackageId, matchedPackageIds)))
                .eq(StringUtils.hasText(query.getRuleStatus()), RecurringScheduleRuleEntity::getRuleStatus, query.getRuleStatus())
                .in(query.getTeacherId() != null, RecurringScheduleRuleEntity::getId, matchedRuleIds)
                .in(query.getClassroomId() != null, RecurringScheduleRuleEntity::getId, matchedClassroomRuleIds)
                .eq(query.getPackageId() != null, RecurringScheduleRuleEntity::getPackageId, query.getPackageId())
                .orderByDesc(RecurringScheduleRuleEntity::getCreatedAt)
                .orderByDesc(RecurringScheduleRuleEntity::getId);
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
        RuleDeductionUnits deductions = ruleDeductionUnits(id, coursePackage.getId());
        List<LessonOccurrenceEntity> preservedForBalance = new ArrayList<>(occurrences);
        preservedForBalance.addAll(loadEndedPeriodOccurrences(coursePackage.getId(), id));
        Map<Long, LessonRecordEntity> recordByOccurrenceId = latestRecordsForPackage(
                preservedForBalance.stream().map(LessonOccurrenceEntity::getId).toList(), coursePackage.getId());
        int startingUnits = packageStartingUnits(coursePackage);
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
                .notes(rule.getNotes())
                .generatedOccurrenceCount(occurrences.size())
                .nextLessonStartAt(nextLessonStartAt)
                .deductedHours(deductions.confirmed() * 0.5)
                .pendingHours(deductions.pending() * 0.5)
                .otherReservedHours(otherRuleReservedUnits(coursePackage.getId(), id) * 0.5)
                .preservedReservedHours(preservedNonPendingUnits(preservedForBalance, recordByOccurrenceId,
                        AppTime.nowLocalDateTime()) * 0.5)
                .startingHours(startingUnits * 0.5)
                .slots(slots.stream().map(slot -> toSlotVO(slot,
                        rolloverAllocatedUnits(slot, occurrences, recordByOccurrenceId,
                                AppTime.nowLocalDateTime()))).toList())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleRuleCreateResultVO createRule(RecurringScheduleRuleCreateRequest request) {
        CoursePackageEntity coursePackage = requireActivePackage(request.getPackageId());
        Long privateLessonGroupId = resolvePrivateLessonGroupId(request.getPrivateLessonGroupId(), coursePackage.getId());
        coursePackage = primaryPackageForPrivateGroup(coursePackage, privateLessonGroupId);
        StudentEntity student = requireStudent(coursePackage.getStudentId());
        ClassroomEntity classroom = resolveClassroom(request.getDefaultClassroomId());
        validateRuleDates(request);
        validateSlots(request.getSlots());
        normalizeSlotClassrooms(request.getSlots(), classroom == null ? null : classroom.getId());
        validateSlotTeachers(request.getSlots());
        validateSlotAllocations(request.getSlots(), coursePackage, null);

        RecurringScheduleRuleEntity rule = new RecurringScheduleRuleEntity();
        rule.setPackageId(coursePackage.getId());
        rule.setPrivateLessonGroupId(privateLessonGroupId);
        rule.setSupersedesRuleId(null);
        rule.setPrimaryTeacherId(null);
        rule.setDefaultClassroomId(classroom == null ? null : classroom.getId());
        rule.setRuleStatus("ACTIVE");
        rule.setStartDate(request.getStartDate());
        rule.setEndDate(request.getEndDate());
        rule.setNotes(request.getNotes());
        recurringScheduleRuleMapper.insert(rule);

        List<RecurringScheduleSlotEntity> slots = request.getSlots().stream()
                .map(slotRequest -> toSlotEntity(rule.getId(), slotRequest))
                .toList();
        rule.setDefaultClassroomId(firstSlotClassroomId(slots));
        recurringScheduleRuleMapper.updateById(rule);
        slots.forEach(recurringScheduleSlotMapper::insert);
        saveStartingBalances(rule, coursePackage, privateLessonGroupId);

        List<LessonOccurrenceEntity> generatedOccurrences = buildOccurrences(rule, slots, coursePackage, student.getId());
        List<LessonOccurrenceEntity> existingOccurrences = listActivePackageOccurrences(coursePackage.getId());
        Map<Long, LessonRecordEntity> recordByOccurrenceId = listActiveRecordsByOccurrenceId(existingOccurrences, coursePackage.getId());
        generatedOccurrences = removeOccurrencesOverlappingPreserved(generatedOccurrences, existingOccurrences);
        generatedOccurrences = capGeneratedOccurrencesToRuleAllocation(generatedOccurrences, existingOccurrences,
                recordByOccurrenceId, slots, rule.getId());
        generatedOccurrences = capGeneratedOccurrencesToPackageBalance(generatedOccurrences, existingOccurrences, recordByOccurrenceId, coursePackage);
        ensureNoConflictsForPrivateLessonGroup(generatedOccurrences, student.getId(), privateLessonGroupId);
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
        coursePackage = primaryPackageForPrivateGroup(coursePackage, rule.getPrivateLessonGroupId());
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
        Map<Long, LessonRecordEntity> recordByOccurrenceId = listActiveRecordsByOccurrenceId(existingOccurrences, coursePackage.getId());
        toInsert = removeOccurrencesOverlappingPreserved(toInsert, existingOccurrences);
        toInsert = capGeneratedOccurrencesToRuleAllocation(toInsert, existingOccurrences,
                recordByOccurrenceId, slots, rule.getId());
        toInsert = capGeneratedOccurrencesToPackageBalance(toInsert, existingOccurrences, recordByOccurrenceId, coursePackage);
        if (toInsert.isEmpty()) {
            return 0;
        }

        ensureNoConflictsForPrivateLessonGroup(toInsert, coursePackage.getStudentId(), rule.getPrivateLessonGroupId());
        toInsert.forEach(lessonOccurrenceMapper::insert);
        return toInsert.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleRuleUpdateResultVO updateRule(Long id, RecurringScheduleRuleUpdateRequest request) {
        return updateRuleInternal(id, request, true, true);
    }

    // 余额变动触发的内部刷新保留原分配值，直到管理员在编辑弹窗中重新分配剩余课时。
    private ScheduleRuleUpdateResultVO updateRuleInternal(Long id, RecurringScheduleRuleUpdateRequest request,
                                                          boolean validateRemainingAllocation) {
        return updateRuleInternal(id, request, validateRemainingAllocation, false);
    }

    private ScheduleRuleUpdateResultVO updateRuleInternal(Long id, RecurringScheduleRuleUpdateRequest request,
                                                          boolean validateRemainingAllocation,
                                                          boolean allowNewPeriod) {
        // a) Load and validate existing rule
        RecurringScheduleRuleEntity existingRule = requireRule(id);
        if ("ENDED".equalsIgnoreCase(existingRule.getRuleStatus())) {
            throw new BusinessException("RULE_NOT_EDITABLE", "Ended rules cannot be edited.");
        }

        // b) Validate package
        CoursePackageEntity coursePackage = requirePackage(existingRule.getPackageId());
        Long privateLessonGroupId = resolvePrivateLessonGroupId(
                request.getPrivateLessonGroupId(), existingRule.getPackageId());
        coursePackage = primaryPackageForPrivateGroup(coursePackage, privateLessonGroupId);
        StudentEntity student = requireStudent(coursePackage.getStudentId());

        // c) Resolve teacher and classroom: use request value if provided, otherwise keep existing
        boolean needsLegacyDefaultClassroom = request.getSlots() != null && request.getSlots().stream()
                .anyMatch(slot -> slot.getDeliveryMode() == null && slot.getClassroomId() == null);
        ClassroomEntity classroom = request.getDefaultClassroomId() != null
                ? resolveClassroom(request.getDefaultClassroomId())
                : (needsLegacyDefaultClassroom && existingRule.getDefaultClassroomId() != null
                        ? resolveClassroom(existingRule.getDefaultClassroomId()) : null);

        // d) Validate dates and slots
        validateUpdateRuleDates(request);
        validateSlots(request.getSlots());
        normalizeSlotClassrooms(request.getSlots(), classroom == null ? null : classroom.getId());
        validateSlotTeachers(request.getSlots());
        String newStatus = request.getRuleStatus() != null ? request.getRuleStatus() : existingRule.getRuleStatus();
        if (!List.of("ACTIVE", "PAUSED", "ENDED").contains(newStatus)) {
            throw new BusinessException("INVALID_RULE_STATUS", "Rule status must be ACTIVE, PAUSED, or ENDED.");
        }
        if ("PAUSED".equals(coursePackage.getPackageStatus()) && !"PAUSED".equals(newStatus)) {
            throw new BusinessException("PACKAGE_PAUSED",
                    "Keep this schedule paused while the 1-on-1 package is paused. Resume the package to generate lessons.");
        }

        // f) Classify existing occurrences BEFORE deleting slots (FK constraint)
        List<LessonOccurrenceEntity> allOccurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getScheduleRuleId, id));
        List<RecurringScheduleSlotEntity> existingSlots = recurringScheduleSlotMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                        .eq(RecurringScheduleSlotEntity::getRuleId, id));
        if (isCourseDetailsOnlyChange(existingRule, existingSlots, request, privateLessonGroupId, newStatus)) {
            Set<Long> existingOccurrenceIds = allOccurrences.stream()
                    .map(LessonOccurrenceEntity::getId).collect(Collectors.toSet());
            Map<Long, LessonRecordEntity> existingRecords = latestRecordsForPackage(
                    existingOccurrenceIds, coursePackage.getId());
            return updateCourseDetailsOnly(existingRule, existingSlots, allOccurrences, existingRecords,
                    request, coursePackage, student.getId(), privateLessonGroupId);
        }
        boolean startsNewPeriod = allowNewPeriod
                && !"ENDED".equals(newStatus)
                && hasScheduleDefinitionChanged(existingRule, existingSlots, request, privateLessonGroupId);
        if (validateRemainingAllocation && !startsNewPeriod && !"ENDED".equals(newStatus)) {
            validateSlotAllocations(request.getSlots(), coursePackage, id);
        }
        Map<Long, RecurringScheduleSlotEntity> existingSlotsById = existingSlots.stream()
                .collect(Collectors.toMap(RecurringScheduleSlotEntity::getId, slot -> slot));
        Set<Long> preservedSlotIds = allOccurrences.stream()
                .map(LessonOccurrenceEntity::getScheduleSlotId)
                .filter(Objects::nonNull)
                .filter(slotId -> !existingSlotsById.containsKey(slotId))
                .collect(Collectors.toSet());
        if (!preservedSlotIds.isEmpty()) {
            recurringScheduleSlotMapper.selectIncludingDeletedByIds(preservedSlotIds)
                    .forEach(slot -> existingSlotsById.put(slot.getId(), slot));
        }
        Set<Long> occIds = allOccurrences.stream().map(LessonOccurrenceEntity::getId).collect(Collectors.toSet());
        Map<Long, LessonRecordEntity> recordByOccurrenceId =
                latestRecordsForPackage(occIds, coursePackage.getId());
        Set<Long> recordedOccIds = recordByOccurrenceId.keySet();

        // 保留已经开始的课次；同一天内尚未开始的普通课次仍随新周期重新生成。
        LocalDateTime effectiveAt = AppTime.nowLocalDateTime();
        List<LessonOccurrenceEntity> toDelete = new ArrayList<>();
        List<LessonOccurrenceEntity> toPreserve = new ArrayList<>();
        for (LessonOccurrenceEntity occ : allOccurrences) {
            boolean isConfirmed = recordedOccIds.contains(occ.getId());
            boolean isAdjustedOrCancelled = "ADJUSTED".equals(occ.getPlanningStatus()) || "CANCELLED".equals(occ.getPlanningStatus());
            boolean isNotStarted = occ.getPlannedStartAt() != null && !occ.getPlannedStartAt().isBefore(effectiveAt)
                    && "SCHEDULED".equals(occ.getPlanningStatus());
            if (isConfirmed || isAdjustedOrCancelled || !isNotStarted) {
                toPreserve.add(occ);
            } else {
                toDelete.add(occ);
            }
        }

        List<LessonOccurrenceEntity> endedPeriodOccurrences = loadEndedPeriodOccurrences(coursePackage.getId(), id);
        Set<Long> balanceOccurrenceIds = new LinkedHashSet<>(toPreserve.stream()
                .map(LessonOccurrenceEntity::getId).toList());
        balanceOccurrenceIds.addAll(endedPeriodOccurrences.stream()
                .map(LessonOccurrenceEntity::getId).toList());
        Map<Long, LessonRecordEntity> balanceRecords = new HashMap<>(recordByOccurrenceId);
        balanceRecords.putAll(latestRecordsForPackage(balanceOccurrenceIds, coursePackage.getId()));
        List<LessonOccurrenceEntity> preservedForBalance = new ArrayList<>(toPreserve);
        preservedForBalance.addAll(endedPeriodOccurrences);

        if (startsNewPeriod && validateRemainingAllocation) {
            validateNewPeriodAllocations(request.getSlots(), coursePackage, id,
                    preservedForBalance, balanceRecords);
        }

        // g) Delete classified occurrences FIRST (before slot FK cleanup)
        int confirmedCount = (int) toPreserve.stream().filter(o -> recordedOccIds.contains(o.getId())).count();
        if (!toDelete.isEmpty()) {
            int deletedCount = lessonOccurrenceMapper.softDeleteFutureUnprocessed(
                    toDelete.stream().map(LessonOccurrenceEntity::getId).toList(), AppTime.nowLocalDateTime());
            if (deletedCount != toDelete.size()) {
                throw new BusinessException("LESSON_CHANGED",
                        "A lesson has started or entered the deduction process. Refresh the schedule before editing it.");
            }
        }

        // 旧课次可能只保存 slot ID。修改旧时段前补齐老师和科目快照，避免历史记录随新时段显示变化。
        backfillPreservedOccurrenceDetails(toPreserve, existingSlotsById);

        RecurringScheduleRuleEntity targetRule = existingRule;
        if (startsNewPeriod) {
            targetRule = new RecurringScheduleRuleEntity();
            targetRule.setPackageId(coursePackage.getId());
            targetRule.setPrivateLessonGroupId(privateLessonGroupId);
            targetRule.setSupersedesRuleId(existingRule.getId());
            targetRule.setPrimaryTeacherId(null);
            targetRule.setDefaultClassroomId(classroom == null ? null : classroom.getId());
            targetRule.setRuleStatus(newStatus);
            targetRule.setStartDate(request.getStartDate());
            targetRule.setEndDate(request.getEndDate() != null ? request.getEndDate() : existingRule.getEndDate());
            targetRule.setNotes(request.getNotes());
            recurringScheduleRuleMapper.insert(targetRule);

            existingRule.setRuleStatus("ENDED");
            existingRule.setSupersededByRuleId(targetRule.getId());
            recurringScheduleRuleMapper.updateById(existingRule);
        } else {
            existingRule.setPrimaryTeacherId(null);
            existingRule.setPackageId(coursePackage.getId());
            existingRule.setPrivateLessonGroupId(privateLessonGroupId);
            existingRule.setDefaultClassroomId(classroom == null ? null : classroom.getId());
            existingRule.setRuleStatus(newStatus);
            existingRule.setStartDate(request.getStartDate());
            existingRule.setEndDate(request.getEndDate() != null ? request.getEndDate() : existingRule.getEndDate());
            existingRule.setNotes(request.getNotes());
            recurringScheduleRuleMapper.updateById(existingRule);
            recurringScheduleSlotMapper.delete(new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                    .eq(RecurringScheduleSlotEntity::getRuleId, id));
        }

        Long targetRuleId = targetRule.getId();
        List<RecurringScheduleSlotEntity> newSlots = request.getSlots().stream()
                .map(slotRequest -> toSlotEntity(targetRuleId, slotRequest))
                .toList();
        targetRule.setDefaultClassroomId(firstSlotClassroomId(newSlots));
        recurringScheduleRuleMapper.updateById(targetRule);
        newSlots.forEach(recurringScheduleSlotMapper::insert);
        if (startsNewPeriod) {
            saveStartingBalances(targetRule, coursePackage, privateLessonGroupId);
        }

        // i) Build and insert new occurrences only for an active package and rule.
        // A paused package may update its future slot configuration, but lesson generation
        // must wait for the explicit resume action so its restart date is respected.
        List<LessonOccurrenceEntity> newOccurrences = Collections.emptyList();
        if ("ACTIVE".equals(coursePackage.getPackageStatus()) && "ACTIVE".equals(targetRule.getRuleStatus())) {
            newOccurrences = buildOccurrences(targetRule, newSlots, coursePackage, student.getId());
            newOccurrences = removeOccurrencesOverlappingPreserved(newOccurrences, preservedForBalance);
            newOccurrences = capGeneratedOccurrencesToRuleAllocation(newOccurrences, toPreserve,
                    recordByOccurrenceId, newSlots, targetRule.getId());
            newOccurrences = capGeneratedOccurrencesToPackageBalance(newOccurrences,
                    preservedForBalance, balanceRecords, coursePackage);
        }

        // 普通未来课次会随规则重建；保留的未来例外若仍继承规则教室，也要同步到新槽位配置。
        List<LessonOccurrenceEntity> classroomUpdates = buildInheritedClassroomUpdates(
                toPreserve, recordByOccurrenceId, existingSlotsById, newSlots);
        ensureNoClassroomConflicts(classroomUpdates, toPreserve, newOccurrences, id);

        // j) Conflict check excluding this rule's preserved occurrences
        ensureNoConflictsExcludingPrivateLessonGroup(newOccurrences, student.getId(),
                id, targetRule.getPrivateLessonGroupId());

        for (LessonOccurrenceEntity update : classroomUpdates) {
            if (lessonOccurrenceMapper.updateInheritedClassroom(update.getId(), update.getPlannedClassroomId(),
                    update.getDeliveryMode(), AppTime.nowLocalDateTime()) != 1) {
                throw new BusinessException("LESSON_CHANGED",
                        "A lesson has started or entered the deduction process. Refresh the schedule before editing it.");
            }
        }
        newOccurrences.forEach(lessonOccurrenceMapper::insert);

        // k) Return result
        LocalDateTime firstStart = newOccurrences.stream().map(LessonOccurrenceEntity::getPlannedStartAt).min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime lastEnd = newOccurrences.stream().map(LessonOccurrenceEntity::getPlannedEndAt).max(LocalDateTime::compareTo).orElse(null);
        return new ScheduleRuleUpdateResultVO(
                targetRule.getId(), newSlots.size(), toPreserve.size(), toDelete.size(),
                newOccurrences.size(), confirmedCount, firstStart, lastEnd);
    }

    /** 判断是否只改老师、教室或课程名称；此类编辑不改变排课计划和小时分配。 */
    private boolean isCourseDetailsOnlyChange(RecurringScheduleRuleEntity rule,
                                              List<RecurringScheduleSlotEntity> currentSlots,
                                              RecurringScheduleRuleUpdateRequest request,
                                              Long privateLessonGroupId,
                                              String newStatus) {
        if (!Objects.equals(rule.getPrivateLessonGroupId(), privateLessonGroupId)
                || !Objects.equals(rule.getRuleStatus(), newStatus)
                || !Objects.equals(rule.getStartDate(), request.getStartDate())
                || !Objects.equals(rule.getEndDate(), request.getEndDate() == null
                        ? rule.getEndDate() : request.getEndDate())
                || currentSlots.size() != request.getSlots().size()) {
            return false;
        }
        Map<Long, RecurringScheduleSlotEntity> currentById = currentSlots.stream()
                .collect(Collectors.toMap(RecurringScheduleSlotEntity::getId, slot -> slot));
        if (request.getSlots().stream().map(RecurringScheduleSlotRequest::getSlotId)
                .distinct().count() != request.getSlots().size()) {
            return false;
        }
        boolean changed = !Objects.equals(rule.getNotes(), request.getNotes());
        for (RecurringScheduleSlotRequest requested : request.getSlots()) {
            if (requested.getSlotId() == null) return false;
            RecurringScheduleSlotEntity current = currentById.get(requested.getSlotId());
            if (current == null
                    || !Objects.equals(current.getWeekday(), requested.getWeekday())
                    || !Objects.equals(current.getStartTime(), requested.getStartTime())
                    || !Objects.equals(current.getEndTime(), requested.getEndTime())
                    || !Objects.equals(current.getPlannedUnits(), requested.getPlannedUnits())
                    || !Objects.equals(current.getAllocatedUnits(), requested.getAllocatedUnits())) {
                return false;
            }
            changed |= !Objects.equals(current.getTeacherId(), requested.getTeacherId())
                    || !Objects.equals(current.getDeliveryMode(), requested.getDeliveryMode())
                    || !Objects.equals(current.getClassroomId(), requested.getClassroomId())
                    || !Objects.equals(current.getSubjectName(), requested.getSubjectName());
        }
        return changed;
    }

    /** 只同步未来、未开始且未进入消课流程的课次，不删除或重建课次、不改变分配额。 */
    private ScheduleRuleUpdateResultVO updateCourseDetailsOnly(
            RecurringScheduleRuleEntity rule,
            List<RecurringScheduleSlotEntity> currentSlots,
            List<LessonOccurrenceEntity> occurrences,
            Map<Long, LessonRecordEntity> records,
            RecurringScheduleRuleUpdateRequest request,
            CoursePackageEntity coursePackage,
            Long studentId,
            Long privateLessonGroupId) {
        Map<Long, RecurringScheduleSlotEntity> slotsById = currentSlots.stream()
                .collect(Collectors.toMap(RecurringScheduleSlotEntity::getId, slot -> slot));
        backfillPreservedOccurrenceDetails(occurrences, slotsById);
        Map<Long, RecurringScheduleSlotRequest> requestedById = request.getSlots().stream()
                .collect(Collectors.toMap(RecurringScheduleSlotRequest::getSlotId, slot -> slot));
        LocalDateTime now = AppTime.nowLocalDateTime();
        List<LessonOccurrenceEntity> futureUpdates = occurrences.stream()
                .filter(occurrence -> "SCHEDULED".equals(occurrence.getPlanningStatus()))
                .filter(occurrence -> occurrence.getPlannedStartAt() != null
                        && !occurrence.getPlannedStartAt().isBefore(now))
                .filter(occurrence -> !records.containsKey(occurrence.getId()))
                .filter(occurrence -> requestedById.containsKey(occurrence.getScheduleSlotId()))
                .filter(occurrence -> {
                    RecurringScheduleSlotEntity current = slotsById.get(occurrence.getScheduleSlotId());
                    RecurringScheduleSlotRequest requested = requestedById.get(occurrence.getScheduleSlotId());
                    return !Objects.equals(current.getTeacherId(), requested.getTeacherId())
                            || !Objects.equals(current.getDeliveryMode(), requested.getDeliveryMode())
                            || !Objects.equals(current.getClassroomId(), requested.getClassroomId())
                            || !Objects.equals(current.getSubjectName(), requested.getSubjectName());
                })
                .map(occurrence -> {
                    RecurringScheduleSlotRequest requested = requestedById.get(occurrence.getScheduleSlotId());
                    LessonOccurrenceEntity candidate = new LessonOccurrenceEntity();
                    candidate.setId(occurrence.getId());
                    candidate.setScheduleSlotId(occurrence.getScheduleSlotId());
                    candidate.setStudentId(occurrence.getStudentId());
                    candidate.setPrivateLessonGroupId(privateLessonGroupId);
                    candidate.setPlannedTeacherId(requested.getTeacherId());
                    candidate.setPlannedClassroomId(Objects.equals(occurrence.getClassroomOverridden(), 1)
                            ? occurrence.getPlannedClassroomId() : requested.getClassroomId());
                    candidate.setDeliveryMode(Objects.equals(occurrence.getClassroomOverridden(), 1)
                            ? occurrence.getDeliveryMode() : requested.getDeliveryMode());
                    candidate.setPlannedStartAt(occurrence.getPlannedStartAt());
                    candidate.setPlannedEndAt(occurrence.getPlannedEndAt());
                    candidate.setPlannedUnits(occurrence.getPlannedUnits());
                    return candidate;
                }).toList();

        Map<Long, LessonOccurrenceEntity> originalByOccurrenceId = occurrences.stream()
                .collect(Collectors.toMap(LessonOccurrenceEntity::getId, occurrence -> occurrence));
        List<LessonOccurrenceEntity> resourceChanges = futureUpdates.stream().filter(candidate -> {
            RecurringScheduleSlotEntity current = slotsById.get(candidate.getScheduleSlotId());
            RecurringScheduleSlotRequest requested = requestedById.get(candidate.getScheduleSlotId());
            LessonOccurrenceEntity original = originalByOccurrenceId.get(candidate.getId());
            boolean classroomOverridden = Objects.equals(original.getClassroomOverridden(), 1);
            return !Objects.equals(current.getTeacherId(), requested.getTeacherId())
                    || (!classroomOverridden && (!Objects.equals(original.getPlannedClassroomId(),
                            requested.getClassroomId())
                            || !Objects.equals(original.getDeliveryMode(), requested.getDeliveryMode())));
        }).toList();
        ensureNoConflictsExcludingPrivateLessonGroup(resourceChanges, studentId, rule.getId(), privateLessonGroupId);

        for (RecurringScheduleSlotRequest requested : request.getSlots()) {
            RecurringScheduleSlotEntity current = slotsById.get(requested.getSlotId());
            if (!Objects.equals(current.getTeacherId(), requested.getTeacherId())
                    || !Objects.equals(current.getDeliveryMode(), requested.getDeliveryMode())
                    || !Objects.equals(current.getClassroomId(), requested.getClassroomId())
                    || !Objects.equals(current.getSubjectName(), requested.getSubjectName())) {
                LessonOccurrenceEntity auditOccurrence = futureUpdates.stream()
                        .filter(occurrence -> Objects.equals(occurrence.getScheduleSlotId(), current.getId()))
                        .findFirst()
                        .orElseGet(() -> occurrences.stream()
                                .filter(occurrence -> Objects.equals(occurrence.getScheduleSlotId(), current.getId()))
                                .findFirst().orElse(null));
                if (auditOccurrence != null) {
                    LessonAdjustmentLogEntity log = new LessonAdjustmentLogEntity();
                    log.setOccurrenceId(auditOccurrence.getId());
                    log.setAdjustmentType("SCHEDULE_DETAILS_UPDATE");
                    log.setBeforeSnapshot(jsonHelper.toJson(scheduleDetailsSnapshot(current.getTeacherId(),
                            current.getDeliveryMode(), current.getClassroomId(), current.getSubjectName(),
                            current.getWeekday(), current.getStartTime(), current.getEndTime())));
                    log.setAfterSnapshot(jsonHelper.toJson(scheduleDetailsSnapshot(requested.getTeacherId(),
                            requested.getDeliveryMode(), requested.getClassroomId(), requested.getSubjectName(),
                            requested.getWeekday(), requested.getStartTime(), requested.getEndTime())));
                    log.setReason("Recurring schedule details updated");
                    log.setAdjustedAt(now);
                    lessonAdjustmentLogMapper.insert(log);
                }
                recurringScheduleSlotMapper.updateCourseDetails(current.getId(), requested.getTeacherId(),
                        requested.getDeliveryMode(), requested.getClassroomId(), requested.getSubjectName());
            }
        }
        for (LessonOccurrenceEntity update : futureUpdates) {
            RecurringScheduleSlotRequest requested = requestedById.get(update.getScheduleSlotId());
            if (lessonOccurrenceMapper.updateRecurringDetails(update.getId(), requested.getTeacherId(),
                    requested.getSubjectName(), requested.getClassroomId(), requested.getDeliveryMode(),
                    AppTime.nowLocalDateTime()) != 1) {
                throw new BusinessException("LESSON_CHANGED",
                        "A lesson has started or entered the deduction process. Refresh the schedule before editing it.");
            }
        }

        Long defaultClassroomId = request.getSlots().stream()
                .map(RecurringScheduleSlotRequest::getClassroomId)
                .filter(Objects::nonNull).findFirst().orElse(null);
        recurringScheduleRuleMapper.updateCourseDetails(rule.getId(), defaultClassroomId, request.getNotes());
        int confirmedCount = (int) records.values().stream()
                .filter(record -> "CONFIRMED".equals(record.getConfirmStage())).count();
        return new ScheduleRuleUpdateResultVO(rule.getId(), currentSlots.size(), occurrences.size(), 0,
                0, confirmedCount, null, null);
    }

    /** 旧课次缺少独立字段时，先固化旧时段信息，再允许修改或替换时段。 */
    private void backfillPreservedOccurrenceDetails(List<LessonOccurrenceEntity> occurrences,
                                                     Map<Long, RecurringScheduleSlotEntity> slotsById) {
        for (LessonOccurrenceEntity occurrence : occurrences) {
            RecurringScheduleSlotEntity oldSlot = slotsById.get(occurrence.getScheduleSlotId());
            if (oldSlot == null) {
                continue;
            }
            LessonOccurrenceEntity update = new LessonOccurrenceEntity();
            update.setId(occurrence.getId());
            if (occurrence.getPlannedTeacherId() == null && oldSlot.getTeacherId() != null) {
                update.setPlannedTeacherId(oldSlot.getTeacherId());
            }
            if (!StringUtils.hasText(occurrence.getSubjectName()) && StringUtils.hasText(oldSlot.getSubjectName())) {
                update.setSubjectName(oldSlot.getSubjectName());
            }
            if (update.getPlannedTeacherId() != null || update.getSubjectName() != null) {
                lessonOccurrenceMapper.updateById(update);
            }
        }
    }

    /** 为签字表保留课程信息修改前后的可读快照，不改写已生成课程的历史信息。 */
    private Map<String, Object> scheduleDetailsSnapshot(Long teacherId, String deliveryMode, Long classroomId,
                                                          String subjectName, Integer weekday,
                                                          java.time.LocalTime startTime, java.time.LocalTime endTime) {
        TeacherEntity teacher = teacherId == null ? null : teacherMapper.selectById(teacherId);
        ClassroomEntity classroom = classroomId == null ? null : classroomMapper.selectById(classroomId);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("weekday", weekday);
        snapshot.put("startTime", startTime);
        snapshot.put("endTime", endTime);
        snapshot.put("subjectName", subjectName);
        snapshot.put("teacherName", teacher == null ? null : teacher.getTeacherName());
        snapshot.put("classroomName", "ONLINE".equals(deliveryMode)
                ? "Online" : classroom == null ? null : classroom.getRoomName());
        return snapshot;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshPackageSchedule(Long packageId) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectById(packageId);
        if (coursePackage == null || !"ACTIVE".equals(coursePackage.getPackageStatus())) {
            return;
        }
        List<RecurringScheduleRuleEntity> rules = recurringScheduleRuleMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                        .eq(RecurringScheduleRuleEntity::getPackageId, packageId)
                        .eq(RecurringScheduleRuleEntity::getRuleStatus, "ACTIVE"));
        if (rules.isEmpty()) {
            PrivateLessonGroupMemberEntity member = privateLessonGroupMemberMapper.selectOne(
                    new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                            .eq(PrivateLessonGroupMemberEntity::getPackageId, packageId)
                            .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                            .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                            .last("LIMIT 1"));
            if (member != null) {
                rules = recurringScheduleRuleMapper.selectList(
                        new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                                .eq(RecurringScheduleRuleEntity::getPrivateLessonGroupId, member.getPrivateLessonGroupId())
                                .eq(RecurringScheduleRuleEntity::getRuleStatus, "ACTIVE")
                                .orderByDesc(RecurringScheduleRuleEntity::getCreatedAt)
                                .orderByDesc(RecurringScheduleRuleEntity::getId));
                rules.stream().findFirst().ifPresent(rule -> ensureStartingBalance(rule, coursePackage));
                return;
            }
        }
        for (RecurringScheduleRuleEntity rule : rules) {
            ensureStartingBalance(rule, coursePackage);
            List<RecurringScheduleSlotEntity> slots = recurringScheduleSlotMapper.selectList(
                    new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                            .eq(RecurringScheduleSlotEntity::getRuleId, rule.getId())
                            .eq(RecurringScheduleSlotEntity::getSlotStatus, "ACTIVE")
                            .orderByAsc(RecurringScheduleSlotEntity::getWeekday)
                            .orderByAsc(RecurringScheduleSlotEntity::getStartTime));
            if (slots.isEmpty()) {
                continue;
            }
            RecurringScheduleRuleUpdateRequest request = new RecurringScheduleRuleUpdateRequest();
            request.setPrivateLessonGroupId(rule.getPrivateLessonGroupId());
            request.setDefaultClassroomId(rule.getDefaultClassroomId());
            request.setRuleStatus(rule.getRuleStatus());
            request.setStartDate(rule.getStartDate());
            request.setEndDate(rule.getEndDate());
            request.setNotes(rule.getNotes());
            request.setSlots(slots.stream().map(this::toSlotRequest).toList());
            updateRuleInternal(rule.getId(), request, false);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setPackageStartingHours(Long packageId, int startingUnits) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectById(packageId);
        if (coursePackage == null) {
            return;
        }
        List<PrivateLessonGroupMemberEntity> members = privateLessonGroupMemberMapper.selectList(
                new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .eq(PrivateLessonGroupMemberEntity::getPackageId, packageId)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0));
        Set<Long> groupIds = members.stream().map(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<RecurringScheduleRuleEntity> rules = recurringScheduleRuleMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                        .eq(RecurringScheduleRuleEntity::getRuleStatus, "ACTIVE")
                        .and(wrapper -> wrapper.eq(RecurringScheduleRuleEntity::getPackageId, packageId)
                                .or(!groupIds.isEmpty(), group -> group.in(
                                        RecurringScheduleRuleEntity::getPrivateLessonGroupId, groupIds))));
        for (RecurringScheduleRuleEntity rule : rules) {
            setStartingBalance(rule, coursePackage, startingUnits);
        }
    }

    private RecurringScheduleSlotRequest toSlotRequest(RecurringScheduleSlotEntity slot) {
        RecurringScheduleSlotRequest request = new RecurringScheduleSlotRequest();
        request.setTeacherId(slot.getTeacherId());
        request.setDeliveryMode(slot.getDeliveryMode());
        request.setClassroomId(slot.getClassroomId());
        request.setSubjectName(slot.getSubjectName());
        request.setWeekday(slot.getWeekday());
        request.setStartTime(slot.getStartTime());
        request.setEndTime(slot.getEndTime());
        request.setPlannedUnits(slot.getPlannedUnits());
        request.setAllocatedUnits(slot.getAllocatedUnits());
        return request;
    }

    /**
     * 找出需随规则更新教室的保留课次。已产生消课记录、已取消及明确单次改教室的课次不在此列。
     */
    private List<LessonOccurrenceEntity> buildInheritedClassroomUpdates(
            List<LessonOccurrenceEntity> preservedOccurrences,
            Map<Long, LessonRecordEntity> recordByOccurrenceId,
            Map<Long, RecurringScheduleSlotEntity> oldSlotsById,
            List<RecurringScheduleSlotEntity> newSlots) {
        LocalDateTime now = AppTime.nowLocalDateTime();
        List<LessonOccurrenceEntity> updates = new ArrayList<>();
        for (LessonOccurrenceEntity occurrence : preservedOccurrences) {
            if (occurrence.getPlannedStartAt() == null || occurrence.getPlannedStartAt().isBefore(now)
                    || "CANCELLED".equals(occurrence.getPlanningStatus())
                    || recordByOccurrenceId.containsKey(occurrence.getId())
                    || Objects.equals(occurrence.getClassroomOverridden(), 1)) {
                continue;
            }
            RecurringScheduleSlotEntity oldSlot = oldSlotsById.get(occurrence.getScheduleSlotId());
            RecurringScheduleSlotEntity newSlot = findMatchingUpdatedSlot(oldSlot, newSlots);
            if (newSlot == null || (Objects.equals(occurrence.getPlannedClassroomId(), newSlot.getClassroomId())
                    && Objects.equals(occurrence.getDeliveryMode(), newSlot.getDeliveryMode()))) {
                continue;
            }
            LessonOccurrenceEntity update = new LessonOccurrenceEntity();
            update.setId(occurrence.getId());
            update.setPlannedClassroomId(newSlot.getClassroomId());
            update.setDeliveryMode(newSlot.getDeliveryMode());
            update.setPlannedStartAt(occurrence.getPlannedStartAt());
            update.setPlannedEndAt(occurrence.getPlannedEndAt());
            updates.add(update);
        }
        return updates;
    }

    /** 仅在旧槽位与新槽位存在唯一业务匹配时同步，避免把重复时段误配到错误教室。 */
    private RecurringScheduleSlotEntity findMatchingUpdatedSlot(RecurringScheduleSlotEntity oldSlot,
                                                                 List<RecurringScheduleSlotEntity> newSlots) {
        if (oldSlot == null) {
            return null;
        }
        List<RecurringScheduleSlotEntity> matches = newSlots.stream()
                .filter(slot -> Objects.equals(slot.getTeacherId(), oldSlot.getTeacherId())
                        && Objects.equals(slot.getSubjectName(), oldSlot.getSubjectName())
                        && Objects.equals(slot.getWeekday(), oldSlot.getWeekday())
                        && Objects.equals(slot.getStartTime(), oldSlot.getStartTime())
                        && Objects.equals(slot.getEndTime(), oldSlot.getEndTime()))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    /** 更新教室前检查外部占用和本次规则内的新增/保留课次，避免同步制造教室冲突。 */
    private void ensureNoClassroomConflicts(List<LessonOccurrenceEntity> updates,
                                            List<LessonOccurrenceEntity> preservedOccurrences,
                                            List<LessonOccurrenceEntity> newOccurrences,
                                            Long ruleId) {
        if (updates.isEmpty()) {
            return;
        }
        Set<Long> classroomIds = updates.stream().map(LessonOccurrenceEntity::getPlannedClassroomId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (!classroomIds.isEmpty()) {
            LocalDateTime windowStart = updates.stream().map(LessonOccurrenceEntity::getPlannedStartAt)
                    .filter(Objects::nonNull).min(LocalDateTime::compareTo).orElseThrow();
            LocalDateTime windowEnd = updates.stream().map(LessonOccurrenceEntity::getPlannedEndAt)
                    .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElseThrow();
            List<LessonOccurrenceEntity> external = lessonOccurrenceMapper.selectList(
                    new LambdaQueryWrapper<LessonOccurrenceEntity>()
                            .in(LessonOccurrenceEntity::getPlannedClassroomId, classroomIds)
                            .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                            .and(wrapper -> wrapper.isNull(LessonOccurrenceEntity::getScheduleRuleId)
                                    .or().ne(LessonOccurrenceEntity::getScheduleRuleId, ruleId))
                            .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                            .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
            for (LessonOccurrenceEntity update : updates) {
                if (update.getPlannedClassroomId() == null) {
                    continue;
                }
                LessonOccurrenceEntity conflict = firstOverlap(update, external.stream()
                        .filter(item -> Objects.equals(item.getPlannedClassroomId(), update.getPlannedClassroomId()))
                        .toList());
                if (conflict != null) {
                    throw new BusinessException("SCHEDULE_CONFLICT",
                            "Classroom conflict at " + update.getPlannedStartAt());
                }
            }
        }

        Set<Long> updateIds = updates.stream().map(LessonOccurrenceEntity::getId).collect(Collectors.toSet());
        List<LessonOccurrenceEntity> sameRuleOccurrences = new ArrayList<>();
        sameRuleOccurrences.addAll(updates);
        sameRuleOccurrences.addAll(newOccurrences);
        sameRuleOccurrences.addAll(preservedOccurrences.stream()
                .filter(item -> !updateIds.contains(item.getId())
                        && !"CANCELLED".equals(item.getPlanningStatus()))
                .toList());
        for (int i = 0; i < sameRuleOccurrences.size(); i++) {
            LessonOccurrenceEntity left = sameRuleOccurrences.get(i);
            if (left.getPlannedClassroomId() == null || left.getPlannedStartAt() == null
                    || left.getPlannedEndAt() == null) {
                continue;
            }
            for (int j = i + 1; j < sameRuleOccurrences.size(); j++) {
                LessonOccurrenceEntity right = sameRuleOccurrences.get(j);
                if ((!updateIds.contains(left.getId()) && !updateIds.contains(right.getId()))
                        || !Objects.equals(left.getPlannedClassroomId(), right.getPlannedClassroomId())
                        || right.getPlannedStartAt() == null || right.getPlannedEndAt() == null) {
                    continue;
                }
                if (left.getPlannedStartAt().isBefore(right.getPlannedEndAt())
                        && left.getPlannedEndAt().isAfter(right.getPlannedStartAt())) {
                    throw new BusinessException("SCHEDULE_CONFLICT",
                            "Classroom conflict at " + left.getPlannedStartAt());
                }
            }
        }
    }

    private void validateUpdateRuleDates(RecurringScheduleRuleUpdateRequest request) {
        if (request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("INVALID_RULE_DATE_RANGE", "Rule end date cannot be earlier than the start date.");
        }
    }

    /** 只有排课周期定义发生变化时才新建版本；备注或状态调整不应重置 Starting Hours。 */
    private boolean hasScheduleDefinitionChanged(RecurringScheduleRuleEntity rule,
                                                 List<RecurringScheduleSlotEntity> currentSlots,
                                                 RecurringScheduleRuleUpdateRequest request,
                                                 Long privateLessonGroupId) {
        LocalDate effectiveEndDate = request.getEndDate() == null ? rule.getEndDate() : request.getEndDate();
        if (!Objects.equals(rule.getPrivateLessonGroupId(), privateLessonGroupId)
                || !Objects.equals(rule.getStartDate(), request.getStartDate())
                || !Objects.equals(rule.getEndDate(), effectiveEndDate)
                || currentSlots.size() != request.getSlots().size()) {
            return true;
        }
        List<RecurringScheduleSlotEntity> sortedCurrent = currentSlots.stream()
                .sorted(Comparator.comparing(RecurringScheduleSlotEntity::getWeekday)
                        .thenComparing(RecurringScheduleSlotEntity::getStartTime)
                        .thenComparing(RecurringScheduleSlotEntity::getEndTime))
                .toList();
        List<RecurringScheduleSlotRequest> sortedRequested = request.getSlots().stream()
                .sorted(Comparator.comparing(RecurringScheduleSlotRequest::getWeekday)
                        .thenComparing(RecurringScheduleSlotRequest::getStartTime)
                        .thenComparing(RecurringScheduleSlotRequest::getEndTime))
                .toList();
        for (int index = 0; index < sortedCurrent.size(); index++) {
            RecurringScheduleSlotEntity current = sortedCurrent.get(index);
            RecurringScheduleSlotRequest requested = sortedRequested.get(index);
            if (!Objects.equals(current.getTeacherId(), requested.getTeacherId())
                    || !Objects.equals(current.getDeliveryMode(), requested.getDeliveryMode())
                    || !Objects.equals(current.getClassroomId(), requested.getClassroomId())
                    || !Objects.equals(current.getSubjectName(), requested.getSubjectName())
                    || !Objects.equals(current.getWeekday(), requested.getWeekday())
                    || !Objects.equals(current.getStartTime(), requested.getStartTime())
                    || !Objects.equals(current.getEndTime(), requested.getEndTime())
                    || !Objects.equals(current.getPlannedUnits(), requested.getPlannedUnits())
                    || !Objects.equals(current.getAllocatedUnits(), requested.getAllocatedUnits())) {
                return true;
            }
        }
        return false;
    }

    private TeacherEntity resolveTeacherById(Long teacherId) {
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        if (teacher == null || !"ACTIVE".equalsIgnoreCase(teacher.getStatus())) {
            throw new BusinessException("TEACHER_NOT_AVAILABLE", "The selected teacher does not exist or is not active.");
        }
        return teacher;
    }

    /** 排课冲突按学生实际参加的课次查找，覆盖本人课包、其他共享私教组和有效班课。 */
    private List<LessonOccurrenceEntity> listStudentOccupiedOccurrences(Long studentId,
                                                                         LocalDateTime windowStart,
                                                                         LocalDateTime windowEnd,
                                                                         Long excludeRuleId) {
        List<Long> privateGroupIds = privateLessonGroupMemberMapper.selectList(
                        new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                                .eq(PrivateLessonGroupMemberEntity::getStudentId, studentId)
                                .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                                .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0))
                .stream().map(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId)
                .filter(Objects::nonNull).distinct().toList();
        List<ClassEnrollmentEntity> enrollments = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getStudentId, studentId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0));
        List<Long> classIds = enrollments.stream().map(ClassEnrollmentEntity::getClassId)
                .filter(Objects::nonNull).distinct().toList();
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .and(nested -> nested.eq(LessonOccurrenceEntity::getStudentId, studentId)
                                .or(!privateGroupIds.isEmpty(), group -> group.in(
                                        LessonOccurrenceEntity::getPrivateLessonGroupId, privateGroupIds))
                                .or(!classIds.isEmpty(), group -> group.in(
                                        LessonOccurrenceEntity::getClassGroupId, classIds)))
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .ne(excludeRuleId != null, LessonOccurrenceEntity::getScheduleRuleId, excludeRuleId)
                        .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                        .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        Set<Long> slotIds = occurrences.stream()
                .filter(occurrence -> occurrence.getClassGroupId() != null)
                .map(LessonOccurrenceEntity::getScheduleSlotId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ClassGroupScheduleSlotEntity> slots = slotIds.isEmpty() ? Map.of()
                : classGroupScheduleSlotMapper.selectBatchIds(slotIds).stream()
                        .collect(Collectors.toMap(ClassGroupScheduleSlotEntity::getId, slot -> slot));
        Map<Long, List<ClassEnrollmentEntity>> enrollmentsByClass = enrollments.stream()
                .filter(enrollment -> enrollment.getClassId() != null)
                .collect(Collectors.groupingBy(ClassEnrollmentEntity::getClassId));
        return occurrences.stream()
                .filter(occurrence -> occurrence.getClassGroupId() == null
                        || classOccurrenceVisibleToStudent(occurrence,
                                enrollmentsByClass.get(occurrence.getClassGroupId()), slots))
                .toList();
    }

    /** 班课占用须符合学生报名日期和 AM/PM 选项，避免因同班其他时段产生误报。 */
    private boolean classOccurrenceVisibleToStudent(LessonOccurrenceEntity occurrence,
                                                    List<ClassEnrollmentEntity> enrollments,
                                                    Map<Long, ClassGroupScheduleSlotEntity> slots) {
        if (enrollments == null || occurrence.getPlannedStartAt() == null) {
            return false;
        }
        LocalDate lessonDate = occurrence.getPlannedStartAt().toLocalDate();
        ClassGroupScheduleSlotEntity slot = occurrence.getScheduleSlotId() == null
                ? null : slots.get(occurrence.getScheduleSlotId());
        return enrollments.stream().anyMatch(enrollment ->
                (enrollment.getCreatedAt() == null || !lessonDate.isBefore(enrollment.getCreatedAt().toLocalDate()))
                        && (enrollment.getEnrollmentDate() == null || !lessonDate.isBefore(enrollment.getEnrollmentDate()))
                        && (enrollment.getExpiryDate() == null || !lessonDate.isAfter(enrollment.getExpiryDate()))
                        && attendanceMatches(enrollment.getAttendanceOption(), slot));
    }

    private boolean attendanceMatches(String attendanceOption, ClassGroupScheduleSlotEntity slot) {
        String raw = slot == null ? null : slot.getAttendanceOptions();
        if (!StringUtils.hasText(raw)) {
            return true;
        }
        Set<String> segments = Arrays.stream(raw.toUpperCase(Locale.ROOT).split(","))
                .map(String::trim).filter(StringUtils::hasText).collect(Collectors.toSet());
        if (segments.isEmpty() || segments.contains("ALL")) {
            return true;
        }
        String attendance = StringUtils.hasText(attendanceOption)
                ? attendanceOption.trim().toUpperCase(Locale.ROOT) : "FULL_DAY";
        if ("FULL_DAY".equals(attendance)) {
            return segments.contains("FULL_DAY") || segments.contains("MORNING") || segments.contains("AFTERNOON");
        }
        return segments.contains(attendance);
    }

    private void ensureNoConflictsExcludingRule(List<LessonOccurrenceEntity> candidates,
                                                Long studentId, Long excludeRuleId) {
        if (candidates.isEmpty()) {
            return;
        }
        LocalDateTime windowStart = candidates.stream().map(LessonOccurrenceEntity::getPlannedStartAt).min(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime windowEnd = candidates.stream().map(LessonOccurrenceEntity::getPlannedEndAt).max(LocalDateTime::compareTo).orElseThrow();

        List<LessonOccurrenceEntity> studentOccurrences = listStudentOccupiedOccurrences(
                studentId, windowStart, windowEnd, excludeRuleId);
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
        Set<Long> classroomIds = candidates.stream().map(LessonOccurrenceEntity::getPlannedClassroomId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<LessonOccurrenceEntity> classroomOccurrences = classroomIds.isEmpty() ? Collections.emptyList() : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getPlannedClassroomId, classroomIds)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ne(LessonOccurrenceEntity::getScheduleRuleId, excludeRuleId)
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));

        List<String> conflicts = new ArrayList<>();
        List<ScheduleConflictDetailVO> details = new ArrayList<>();
        for (LessonOccurrenceEntity candidate : candidates) {
            LessonOccurrenceEntity studentConflict = firstOverlap(candidate, studentOccurrences);
            if (studentConflict != null) {
                conflicts.add(resolveStudentName(studentId) + " has another lesson at " + candidate.getPlannedStartAt());
                details.add(buildConflictDetail("STUDENT", studentId, resolveStudentName(studentId), candidate, studentConflict));
            }
            Long candidateTeacherId = candidate.getPlannedTeacherId();
            LessonOccurrenceEntity teacherConflict = firstOverlap(candidate, teacherOccurrences.stream()
                    .filter(existing -> Objects.equals(existing.getPlannedTeacherId(), candidateTeacherId))
                    .toList());
            if (candidateTeacherId != null && teacherConflict != null) {
                conflicts.add("Teacher conflict at " + candidate.getPlannedStartAt());
                details.add(buildConflictDetail("TEACHER", candidateTeacherId,
                        resolveTeacherName(candidateTeacherId), candidate, teacherConflict));
            }
            Long candidateClassroomId = candidate.getPlannedClassroomId();
            LessonOccurrenceEntity classroomConflict = candidateClassroomId == null ? null : firstOverlap(candidate, classroomOccurrences.stream()
                    .filter(existing -> Objects.equals(existing.getPlannedClassroomId(), candidateClassroomId)).toList());
            if (candidateClassroomId != null && classroomConflict != null) {
                conflicts.add("Classroom conflict at " + candidate.getPlannedStartAt());
                details.add(buildConflictDetail("CLASSROOM", candidateClassroomId, resolveClassroomName(candidateClassroomId), candidate, classroomConflict));
            }
            if (conflicts.size() >= 3) {
                break;
            }
        }
        if (!conflicts.isEmpty()) {
            throw new BusinessException("SCHEDULE_CONFLICT", String.join("; ", conflicts), details);
        }
    }

    /** 共享私教课只生成一条 occurrence，但每个成员都必须通过学生冲突校验。 */
    private void ensureNoConflictsForPrivateLessonGroup(List<LessonOccurrenceEntity> candidates,
                                                        Long primaryStudentId,
                                                        Long privateLessonGroupId) {
        ensureNoConflicts(candidates, primaryStudentId);
        if (privateLessonGroupId == null) {
            return;
        }
        for (PrivateLessonGroupMemberEntity member : activeGroupMembers(privateLessonGroupId)) {
            if (Objects.equals(member.getStudentId(), primaryStudentId)) {
                continue;
            }
            ensureNoConflicts(copyCandidatesForStudent(candidates, member.getStudentId()),
                    member.getStudentId());
        }
    }

    private void ensureNoConflictsExcludingPrivateLessonGroup(List<LessonOccurrenceEntity> candidates,
                                                              Long primaryStudentId,
                                                              Long excludeRuleId,
                                                              Long privateLessonGroupId) {
        ensureNoConflictsExcludingRule(candidates, primaryStudentId, excludeRuleId);
        if (privateLessonGroupId == null) {
            return;
        }
        for (PrivateLessonGroupMemberEntity member : activeGroupMembers(privateLessonGroupId)) {
            if (Objects.equals(member.getStudentId(), primaryStudentId)) {
                continue;
            }
            ensureNoConflictsExcludingRule(copyCandidatesForStudent(candidates, member.getStudentId()),
                    member.getStudentId(), excludeRuleId);
        }
    }

    private List<LessonOccurrenceEntity> copyCandidatesForStudent(List<LessonOccurrenceEntity> candidates,
                                                                  Long studentId) {
        return candidates.stream().map(candidate -> {
            LessonOccurrenceEntity copy = new LessonOccurrenceEntity();
            copy.setId(candidate.getId());
            copy.setStudentId(studentId);
            copy.setPrivateLessonGroupId(candidate.getPrivateLessonGroupId());
            copy.setPlannedTeacherId(candidate.getPlannedTeacherId());
            copy.setDeliveryMode(candidate.getDeliveryMode());
            copy.setPlannedClassroomId(candidate.getPlannedClassroomId());
            copy.setPlannedStartAt(candidate.getPlannedStartAt());
            copy.setPlannedEndAt(candidate.getPlannedEndAt());
            copy.setPlannedUnits(candidate.getPlannedUnits());
            return copy;
        }).toList();
    }

    private Long resolvePrivateLessonGroupId(Long requestedGroupId, Long packageId) {
        if (requestedGroupId != null) {
            requireActivePrivateLessonGroup(requestedGroupId, packageId);
            return requestedGroupId;
        }
        PrivateLessonGroupMemberEntity member = privateLessonGroupMemberMapper.selectOne(
                new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .eq(PrivateLessonGroupMemberEntity::getPackageId, packageId)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                        .last("LIMIT 1"));
        return member == null ? null : member.getPrivateLessonGroupId();
    }

    private void requireActivePrivateLessonGroup(Long groupId, Long packageId) {
        PrivateLessonGroupEntity group = privateLessonGroupMapper.selectById(groupId);
        if (group == null || !"ACTIVE".equals(group.getGroupStatus())) {
            throw new BusinessException("PRIVATE_LESSON_GROUP_NOT_FOUND", "Private lesson group not found or inactive.");
        }
        Long memberCount = privateLessonGroupMemberMapper.selectCount(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                .eq(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, groupId)
                .eq(PrivateLessonGroupMemberEntity::getPackageId, packageId)
                .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0));
        if (memberCount == null || memberCount == 0) {
            throw new BusinessException("PRIVATE_LESSON_GROUP_PACKAGE_MISMATCH",
                    "The selected package does not belong to this private lesson group.");
        }
    }

    private CoursePackageEntity primaryPackageForPrivateGroup(CoursePackageEntity coursePackage,
                                                              Long privateLessonGroupId) {
        if (privateLessonGroupId == null) {
            return coursePackage;
        }
        PrivateLessonGroupMemberEntity primaryMember = activeGroupMembers(privateLessonGroupId).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("PRIVATE_LESSON_GROUP_EMPTY",
                        "This private lesson group has no active students."));
        CoursePackageEntity primaryPackage = coursePackageMapper.selectById(primaryMember.getPackageId());
        if (primaryPackage == null || !"ACTIVE".equals(primaryPackage.getPackageStatus())) {
            throw new BusinessException("PRIVATE_LESSON_MEMBER_PACKAGE_INVALID",
                    "The primary private lesson package is missing or inactive.");
        }
        return primaryPackage;
    }

    private List<PrivateLessonGroupMemberEntity> activeGroupMembers(Long groupId) {
        return privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                .eq(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, groupId)
                .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                .orderByAsc(PrivateLessonGroupMemberEntity::getParticipantIndex));
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
                if (!ScheduleSlotTime.isValidRange(slot.getStartTime(), slot.getEndTime())) {
                    throw new BusinessException("INVALID_SLOT_TIME_RANGE", "Slot end time must be later than the start time.");
                }
                long minutes = ScheduleSlotTime.durationMinutes(slot.getStartTime(), slot.getEndTime());
                if (minutes != slot.getPlannedUnits() * 30L) {
                    throw new BusinessException("SLOT_UNITS_MISMATCH", "Slot duration must exactly match planned units at 0.5h per unit.");
                }
                if (previous != null && slot.getStartTime().toSecondOfDay() / 60
                        < ScheduleSlotTime.endMinutes(previous.getStartTime(), previous.getEndTime())) {
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

    /** 新课包或同一周期的轻量修改按本规则累计额校验；周期换版使用当前余额校验。 */
    private void validateSlotAllocations(List<RecurringScheduleSlotRequest> slots, CoursePackageEntity coursePackage,
                                         Long editingRuleId) {
        int allocatedSum = 0;
        for (RecurringScheduleSlotRequest slot : slots) {
            if (slot.getTeacherId() == null || !StringUtils.hasText(slot.getSubjectName())
                    || slot.getAllocatedUnits() == null || slot.getAllocatedUnits() <= 0) {
                throw new BusinessException("INCOMPLETE_SLOT_ALLOCATION",
                        "Each schedule slot requires a teacher, course/subject, and schedule total hours.");
            }
            allocatedSum += slot.getAllocatedUnits();
        }
        RuleDeductionUnits existing = editingRuleId == null
                ? new RuleDeductionUnits(0, 0) : ruleDeductionUnits(editingRuleId, coursePackage.getId());
        int availableUnits = Math.max(0, allocatableUnits(coursePackage)
                - pendingDeductionUnits(coursePackage.getId())
                - otherRuleReservedUnits(coursePackage.getId(), editingRuleId)
                + existing.confirmed() + existing.pending());
        if (allocatedSum > availableUnits) {
            throw new BusinessException("SLOT_ALLOCATION_EXCEEDS_PACKAGE",
                    "Allocated hours exceed the package's remaining hours after other schedules are accounted for.");
        }
        if (allocatedSum < existing.confirmed() + existing.pending()) {
            throw new BusinessException("SLOT_ALLOCATION_BELOW_USED",
                    "Schedule total hours cannot be less than lessons already deducted or pending for this schedule.");
        }
    }

    private record RuleDeductionUnits(int confirmed, int pending) {}

    /** 同一课包其他仍生效规则的未使用分配额，不能再次分给新规则。 */
    private int otherRuleReservedUnits(Long packageId, Long editingRuleId) {
        List<RecurringScheduleRuleEntity> rules = recurringScheduleRuleMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                        .eq(RecurringScheduleRuleEntity::getPackageId, packageId)
                        .in(RecurringScheduleRuleEntity::getRuleStatus, "ACTIVE", "PAUSED"));
        int reserved = 0;
        for (RecurringScheduleRuleEntity rule : rules) {
            if (Objects.equals(rule.getId(), editingRuleId)) {
                continue;
            }
            int allocated = recurringScheduleSlotMapper.selectList(
                    new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                            .eq(RecurringScheduleSlotEntity::getRuleId, rule.getId())
                            .eq(RecurringScheduleSlotEntity::getSlotStatus, "ACTIVE"))
                    .stream().mapToInt(slot -> slot.getAllocatedUnits() == null
                            ? 0 : Math.max(0, slot.getAllocatedUnits())).sum();
            RuleDeductionUnits used = ruleDeductionUnits(rule.getId(), packageId);
            reserved += Math.max(0, allocated - used.confirmed() - used.pending());
        }
        return reserved;
    }

    /** 按课次所属规则统计实际扣课，避免把其他排课或报名时录入的已用课时算进本规则。 */
    private RuleDeductionUnits ruleDeductionUnits(Long ruleId, Long packageId) {
        List<Long> occurrenceIds = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .select(LessonOccurrenceEntity::getId)
                        .eq(LessonOccurrenceEntity::getScheduleRuleId, ruleId))
                .stream().map(LessonOccurrenceEntity::getId).toList();
        if (occurrenceIds.isEmpty()) {
            return new RuleDeductionUnits(0, 0);
        }
        int confirmed = 0;
        int pending = 0;
        for (LessonRecordEntity record : latestRecordsForPackage(occurrenceIds, packageId).values()) {
            if (!Objects.equals(record.getShouldDeduct(), 1)
                    || (!"CONFIRMED".equals(record.getConfirmStage())
                    && !"PENDING_ADMIN".equals(record.getConfirmStage()))) {
                continue;
            }
            int units = record.getDeductedUnits() == null ? 0 : Math.max(0, record.getDeductedUnits());
            if ("CONFIRMED".equals(record.getConfirmStage())) {
                confirmed += units;
            } else {
                pending += units;
            }
        }
        return new RuleDeductionUnits(confirmed, pending);
    }

    /** 待管理员确认且将扣除的课时先占用余额，避免被新的时段重复分配。 */
    private int pendingDeductionUnits(Long packageId) {
        return lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                        .select(LessonRecordEntity::getDeductedUnits)
                        .eq(LessonRecordEntity::getPackageId, packageId)
                        .eq(LessonRecordEntity::getConfirmStage, "PENDING_ADMIN")
                        .eq(LessonRecordEntity::getShouldDeduct, 1)
                        .eq(LessonRecordEntity::getIsDeleted, 0))
                .stream()
                .mapToInt(record -> record.getDeductedUnits() == null ? 0 : Math.max(0, record.getDeductedUnits()))
                .sum();
    }

    /** 新排课周期只允许分配当前可用余额，旧周期保留的待确认与例外课次继续占用原余额。 */
    private void validateNewPeriodAllocations(List<RecurringScheduleSlotRequest> slots,
                                              CoursePackageEntity coursePackage,
                                              Long previousRuleId,
                                              List<LessonOccurrenceEntity> preservedOccurrences,
                                              Map<Long, LessonRecordEntity> recordByOccurrenceId) {
        int allocated = 0;
        for (RecurringScheduleSlotRequest slot : slots) {
            if (slot.getTeacherId() == null || !StringUtils.hasText(slot.getSubjectName())
                    || slot.getAllocatedUnits() == null || slot.getAllocatedUnits() <= 0) {
                throw new BusinessException("INCOMPLETE_SLOT_ALLOCATION",
                        "Each schedule slot requires a teacher, course/subject, and schedule total hours.");
            }
            allocated += slot.getAllocatedUnits();
        }
        int available = Math.max(0, allocatableUnits(coursePackage)
                - pendingDeductionUnits(coursePackage.getId())
                - otherRuleReservedUnits(coursePackage.getId(), previousRuleId)
                - preservedNonPendingUnits(preservedOccurrences, recordByOccurrenceId,
                        AppTime.nowLocalDateTime()));
        if (allocated > available) {
            throw new BusinessException("SLOT_ALLOCATION_EXCEEDS_PACKAGE",
                    "Allocated hours exceed the current package balance after pending lessons and preserved schedule exceptions are accounted for.");
        }
    }

    /** 周期快照中的 Pending 已从可分配余额扣除，因此这里只返回其它保留课次。 */
    private int preservedNonPendingUnits(List<LessonOccurrenceEntity> occurrences,
                                         Map<Long, LessonRecordEntity> recordByOccurrenceId,
                                         LocalDateTime now) {
        int reserved = 0;
        for (LessonOccurrenceEntity occurrence : occurrences) {
            if (!isPreservedByScheduleEdit(occurrence, recordByOccurrenceId, now)
                    || "CANCELLED".equals(occurrence.getPlanningStatus())) {
                continue;
            }
            LessonRecordEntity record = recordByOccurrenceId.get(occurrence.getId());
            if (record != null && ("CONFIRMED".equals(record.getConfirmStage())
                    || "PENDING_ADMIN".equals(record.getConfirmStage()))) {
                continue;
            }
            reserved += Math.max(0, safeUnits(occurrence.getPlannedUnits()));
        }
        return reserved;
    }

    private boolean isPreservedByScheduleEdit(LessonOccurrenceEntity occurrence,
                                              Map<Long, LessonRecordEntity> recordByOccurrenceId,
                                              LocalDateTime now) {
        return recordByOccurrenceId.containsKey(occurrence.getId())
                || "ADJUSTED".equals(occurrence.getPlanningStatus())
                || "CANCELLED".equals(occurrence.getPlanningStatus())
                || occurrence.getPlannedStartAt() == null
                || occurrence.getPlannedStartAt().isBefore(now);
    }

    /** 默认把旧周期未使用且未被保留课次占用的分配额带入新周期。 */
    private int rolloverAllocatedUnits(RecurringScheduleSlotEntity slot,
                                       List<LessonOccurrenceEntity> occurrences,
                                       Map<Long, LessonRecordEntity> recordByOccurrenceId,
                                       LocalDateTime now) {
        int usedOrReserved = 0;
        for (LessonOccurrenceEntity occurrence : occurrences) {
            if (!Objects.equals(slot.getId(), occurrence.getScheduleSlotId())
                    || "CANCELLED".equals(occurrence.getPlanningStatus())) {
                continue;
            }
            LessonRecordEntity record = recordByOccurrenceId.get(occurrence.getId());
            if (record != null && "CONFIRMED".equals(record.getConfirmStage())) {
                usedOrReserved += Objects.equals(record.getShouldDeduct(), 1)
                        ? Math.max(0, safeUnits(record.getDeductedUnits())) : 0;
            } else if (record != null && "PENDING_ADMIN".equals(record.getConfirmStage())) {
                usedOrReserved += Objects.equals(record.getShouldDeduct(), 1)
                        ? Math.max(0, safeUnits(record.getDeductedUnits())) : 0;
            } else if (isPreservedByScheduleEdit(occurrence, recordByOccurrenceId, now)) {
                usedOrReserved += Math.max(0, safeUnits(occurrence.getPlannedUnits()));
            }
        }
        return Math.max(0, safeUnits(slot.getAllocatedUnits()) - usedOrReserved);
    }

    private int packageStartingUnits(CoursePackageEntity coursePackage) {
        return Math.max(0, safeUnits(coursePackage.getTotalUnits())
                - safeUnits(coursePackage.getOpeningUsedUnits()));
    }

    private int safeUnits(Integer units) {
        return units == null ? 0 : units;
    }

    /** 规则版本保存课包的 Starting 快照，不通过排课操作重设课包起始值。 */
    private void saveStartingBalances(RecurringScheduleRuleEntity rule,
                                      CoursePackageEntity primaryPackage,
                                      Long privateLessonGroupId) {
        List<CoursePackageEntity> packages;
        if (privateLessonGroupId == null) {
            packages = List.of(primaryPackage);
        } else {
            packages = activeGroupMembers(privateLessonGroupId).stream()
                    .map(member -> coursePackageMapper.selectById(member.getPackageId()))
                    .filter(Objects::nonNull)
                    .toList();
        }
        for (CoursePackageEntity coursePackage : packages) {
            saveStartingBalance(rule, coursePackage);
        }
    }

    private void saveStartingBalance(RecurringScheduleRuleEntity rule, CoursePackageEntity coursePackage) {
        ensureStartingBalance(rule, coursePackage);
    }

    /** 普通刷新只补齐缺失快照，不能用当前余额覆盖周期起始值。 */
    private void ensureStartingBalance(RecurringScheduleRuleEntity rule, CoursePackageEntity coursePackage) {
        upsertStartingBalance(rule, coursePackage, null);
    }

    private void setStartingBalance(RecurringScheduleRuleEntity rule, CoursePackageEntity coursePackage,
                                    int startingUnits) {
        upsertStartingBalance(rule, coursePackage, startingUnits);
    }

    private void upsertStartingBalance(RecurringScheduleRuleEntity rule, CoursePackageEntity coursePackage,
                                       Integer startingUnits) {
        RecurringScheduleStartingBalanceEntity balance = startingBalanceMapper.selectOne(
                new LambdaQueryWrapper<RecurringScheduleStartingBalanceEntity>()
                        .eq(RecurringScheduleStartingBalanceEntity::getScheduleRuleId, rule.getId())
                        .eq(RecurringScheduleStartingBalanceEntity::getPackageId, coursePackage.getId())
                        .last("LIMIT 1"));
        if (balance == null) {
            balance = new RecurringScheduleStartingBalanceEntity();
            balance.setScheduleRuleId(rule.getId());
            balance.setPackageId(coursePackage.getId());
            balance.setStartingUnits(startingUnits == null
                    ? packageStartingUnits(coursePackage)
                    : Math.max(0, startingUnits));
            startingBalanceMapper.insert(balance);
            return;
        }
        if (startingUnits != null) {
            balance.setStartingUnits(Math.max(0, startingUnits));
            startingBalanceMapper.updateById(balance);
        }
    }

    /** 课时包当前可用于新生成课次的余额，单位为半小时。 */
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

    /** 保存线上/线下状态；线下课允许暂不分配教室，旧客户端仍可用规则默认教室补齐。 */
    private void normalizeSlotClassrooms(List<RecurringScheduleSlotRequest> slots, Long legacyDefaultClassroomId) {
        for (RecurringScheduleSlotRequest slot : slots) {
            String mode = slot.getDeliveryMode();
            if ("ONLINE".equalsIgnoreCase(mode)) {
                if (slot.getClassroomId() != null) {
                    throw new BusinessException("INVALID_SLOT_CLASSROOM", "Online slots cannot have a classroom.");
                }
                slot.setDeliveryMode("ONLINE");
                continue;
            }
            if (mode != null && !"IN_PERSON".equalsIgnoreCase(mode)) {
                throw new BusinessException("INVALID_DELIVERY_MODE", "Delivery mode must be in-person or online.");
            }
            Long classroomId = slot.getClassroomId() != null ? slot.getClassroomId() : legacyDefaultClassroomId;
            if (classroomId == null) {
                slot.setDeliveryMode("IN_PERSON");
                slot.setClassroomId(null);
                continue;
            }
            ClassroomEntity classroom = resolveClassroom(classroomId);
            slot.setDeliveryMode("IN_PERSON");
            slot.setClassroomId(classroom.getId());
        }
    }

    private RecurringScheduleSlotEntity toSlotEntity(Long ruleId, RecurringScheduleSlotRequest request) {
        RecurringScheduleSlotEntity entity = new RecurringScheduleSlotEntity();
        entity.setRuleId(ruleId);
        entity.setTeacherId(request.getTeacherId());
        entity.setDeliveryMode(request.getDeliveryMode());
        entity.setClassroomId(request.getClassroomId());
        entity.setSubjectName(request.getSubjectName());
        entity.setWeekday(request.getWeekday());
        entity.setStartTime(request.getStartTime());
        entity.setEndTime(request.getEndTime());
        entity.setPlannedUnits(request.getPlannedUnits());
        entity.setAllocatedUnits(request.getAllocatedUnits());
        entity.setSlotStatus("ACTIVE");
        return entity;
    }

    private Long firstSlotClassroomId(List<RecurringScheduleSlotEntity> slots) {
        return slots.stream().map(RecurringScheduleSlotEntity::getClassroomId)
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    /** 按时段分配生成课次，不设固定日历上限；调用方随后按课包余额截断。 */
    private List<LessonOccurrenceEntity> buildOccurrences(RecurringScheduleRuleEntity rule,
                                                          List<RecurringScheduleSlotEntity> slots,
                                                          CoursePackageEntity coursePackage,
                                                          Long studentId) {
        LocalDateTime now = AppTime.nowLocalDateTime();
        LocalDate generationStart = Collections.max(List.of(rule.getStartDate(), now.toLocalDate()));
        LocalDate generationEnd = rule.getEndDate();
        if (generationEnd != null && generationEnd.isBefore(generationStart)) {
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
            for (LocalDate date = generationStart;
                 remaining > 0 && (generationEnd == null || !date.isAfter(generationEnd));
                 date = date.plusDays(1)) {
                if (!Objects.equals(slot.getWeekday(), toWeekday(date.getDayOfWeek()))) {
                    continue;
                }
                int thisUnits = Math.min(plannedUnits, remaining);
                LocalDateTime start = LocalDateTime.of(date, slot.getStartTime());
                // 当天已过去的时段属于旧排课历史，改课时不能再次生成并占用新周期课时。
                if (start.isBefore(now)) {
                    continue;
                }
                LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
                occurrence.setLessonNo(BusinessNoGenerator.generateLessonOccurrenceNo());
                occurrence.setPackageId(coursePackage.getId());
                occurrence.setPrivateLessonGroupId(rule.getPrivateLessonGroupId());
                occurrence.setStudentId(studentId);
                occurrence.setScheduleRuleId(rule.getId());
                occurrence.setScheduleSlotId(slot.getId());
                occurrence.setOccurrenceType("REGULAR");
                occurrence.setPlannedTeacherId(slot.getTeacherId());
                // 课次保存课程名称快照，后续改排课或删除旧时段也能还原历史签字表。
                occurrence.setSubjectName(slot.getSubjectName());
                boolean legacySlot = slot.getDeliveryMode() == null;
                Long classroomId = slot.getClassroomId() != null ? slot.getClassroomId()
                        : (legacySlot ? rule.getDefaultClassroomId() : null);
                occurrence.setDeliveryMode(legacySlot && classroomId != null ? "IN_PERSON" : slot.getDeliveryMode());
                occurrence.setPlannedClassroomId(classroomId);
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
                    "Each schedule slot requires a teacher, course/subject, and schedule total hours. Update this schedule before generating more lessons.");
        }
    }

    private List<LessonOccurrenceEntity> listActivePackageOccurrences(Long packageId) {
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getPackageId, packageId)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));
    }

    /** 旧周期中保留的未确认例外仍占余额；避免多次换表后新规则重复分配这些课次。 */
    private List<LessonOccurrenceEntity> loadEndedPeriodOccurrences(Long packageId, Long currentRuleId) {
        List<LessonOccurrenceEntity> packageOccurrences = listActivePackageOccurrences(packageId);
        Set<Long> previousRuleIds = packageOccurrences.stream()
                .map(LessonOccurrenceEntity::getScheduleRuleId)
                .filter(Objects::nonNull)
                .filter(ruleId -> !Objects.equals(ruleId, currentRuleId))
                .collect(Collectors.toSet());
        if (previousRuleIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> endedRuleIds = recurringScheduleRuleMapper.selectBatchIds(previousRuleIds).stream()
                .filter(rule -> "ENDED".equalsIgnoreCase(rule.getRuleStatus()))
                .map(RecurringScheduleRuleEntity::getId)
                .collect(Collectors.toSet());
        if (endedRuleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return packageOccurrences.stream()
                .filter(occurrence -> endedRuleIds.contains(occurrence.getScheduleRuleId()))
                .toList();
    }

    private Map<Long, LessonRecordEntity> listActiveRecordsByOccurrenceId(
            List<LessonOccurrenceEntity> occurrences, Long packageId) {
        if (occurrences.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> occurrenceIds = occurrences.stream()
                .map(LessonOccurrenceEntity::getId)
                .collect(Collectors.toSet());
        return latestRecordsForPackage(occurrenceIds, packageId);
    }

    /** 同一课次多次提交时只取当前有效记录，避免重复计算历史确认或待确认课时。 */
    private Map<Long, LessonRecordEntity> latestRecordsForPackage(
            Collection<Long> occurrenceIds, Long packageId) {
        if (occurrenceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, LessonRecordEntity> latest = new LinkedHashMap<>();
        for (LessonRecordEntity record : lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                .in(LessonRecordEntity::getOccurrenceId, occurrenceIds)
                .eq(LessonRecordEntity::getPackageId, packageId)
                .eq(LessonRecordEntity::getIsDeleted, 0)
                .orderByDesc(LessonRecordEntity::getUpdatedAt)
                .orderByDesc(LessonRecordEntity::getId))) {
            latest.putIfAbsent(record.getOccurrenceId(), record);
        }
        latest.values().removeIf(record -> "REJECTED".equals(record.getConfirmStage()));
        return latest;
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
     * 时段 allocatedUnits 是累计分配额。已保留课次（包括已消课）占用过额度，
     * 编辑和自动续排都只能生成余额部分，不能从完整分配额重新开始。
     */
    private List<LessonOccurrenceEntity> capGeneratedOccurrencesToRuleAllocation(
            List<LessonOccurrenceEntity> candidates, List<LessonOccurrenceEntity> preservedOccurrences,
            Map<Long, LessonRecordEntity> recordByOccurrenceId,
            List<RecurringScheduleSlotEntity> slots, Long ruleId) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        Map<String, Long> slotIdByTime = slots.stream().collect(Collectors.toMap(
                slot -> slot.getWeekday() + ":" + slot.getStartTime(),
                RecurringScheduleSlotEntity::getId));
        Map<Long, Integer> remainingBySlot = new HashMap<>();
        for (RecurringScheduleSlotEntity slot : slots) {
            remainingBySlot.put(slot.getId(), Math.max(0, slot.getAllocatedUnits()));
        }
        int totalRemaining = remainingBySlot.values().stream().mapToInt(Integer::intValue).sum();
        for (LessonOccurrenceEntity preserved : preservedOccurrences) {
            if (!Objects.equals(preserved.getScheduleRuleId(), ruleId)
                    || "CANCELLED".equals(preserved.getPlanningStatus())) {
                continue;
            }
            int units = reservedAllocationUnits(preserved, recordByOccurrenceId.get(preserved.getId()));
            totalRemaining -= units;
            String timeKey = toWeekday(preserved.getPlannedStartAt().getDayOfWeek())
                    + ":" + preserved.getPlannedStartAt().toLocalTime();
            Long slotId = slotIdByTime.get(timeKey);
            if (slotId != null) {
                remainingBySlot.merge(slotId, -units, Integer::sum);
            }
        }
        List<LessonOccurrenceEntity> capped = new ArrayList<>();
        for (LessonOccurrenceEntity candidate : candidates) {
            Long slotId = candidate.getScheduleSlotId();
            int remaining = Math.min(Math.max(0, totalRemaining),
                    Math.max(0, remainingBySlot.getOrDefault(slotId, 0)));
            if (remaining == 0) {
                continue;
            }
            int units = Math.min(candidate.getPlannedUnits(), remaining);
            if (units < candidate.getPlannedUnits()) {
                updateCandidatePlannedUnits(candidate, units);
            }
            capped.add(candidate);
            totalRemaining -= units;
            remainingBySlot.put(slotId, remainingBySlot.get(slotId) - units);
        }
        return capped;
    }

    private int reservedAllocationUnits(LessonOccurrenceEntity occurrence, LessonRecordEntity record) {
        if (record != null && ("CONFIRMED".equals(record.getConfirmStage())
                || "PENDING_ADMIN".equals(record.getConfirmStage()))) {
            return Objects.equals(record.getShouldDeduct(), 1)
                    ? Math.max(0, record.getDeductedUnits() == null ? 0 : record.getDeductedUnits()) : 0;
        }
        return Math.max(0, occurrence.getPlannedUnits() == null ? 0 : occurrence.getPlannedUnits());
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
        int reservedPlannedUnits = PrivateLessonAllocation.reservedUnits(
                preservedOccurrences, recordByOccurrenceId);
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

    private void updateCandidatePlannedUnits(LessonOccurrenceEntity occurrence, int plannedUnits) {
        occurrence.setPlannedUnits(plannedUnits);
        occurrence.setPlannedEndAt(occurrence.getPlannedStartAt().plusMinutes(plannedUnits * 30L));
    }

    /**
     * 冲突检测一次性拉取时间窗口内相关课次，避免按课次逐条扫库。
     */
    private void ensureNoConflicts(List<LessonOccurrenceEntity> generatedOccurrences,
                                   Long studentId) {
        if (generatedOccurrences.isEmpty()) {
            return;
        }
        LocalDateTime windowStart = generatedOccurrences.stream().map(LessonOccurrenceEntity::getPlannedStartAt).min(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime windowEnd = generatedOccurrences.stream().map(LessonOccurrenceEntity::getPlannedEndAt).max(LocalDateTime::compareTo).orElseThrow();

        List<LessonOccurrenceEntity> studentOccurrences = listStudentOccupiedOccurrences(
                studentId, windowStart, windowEnd, null);
        Set<Long> teacherIds = generatedOccurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<LessonOccurrenceEntity> teacherOccurrences = teacherIds.isEmpty() ? Collections.emptyList() : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getPlannedTeacherId, teacherIds)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        Set<Long> classroomIds = generatedOccurrences.stream().map(LessonOccurrenceEntity::getPlannedClassroomId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<LessonOccurrenceEntity> classroomOccurrences = classroomIds.isEmpty() ? Collections.emptyList() : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getPlannedClassroomId, classroomIds)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));

        List<String> conflicts = new ArrayList<>();
        List<ScheduleConflictDetailVO> details = new ArrayList<>();
        for (LessonOccurrenceEntity candidate : generatedOccurrences) {
            LessonOccurrenceEntity studentConflict = firstOverlap(candidate, studentOccurrences);
            if (studentConflict != null) {
                conflicts.add(resolveStudentName(studentId) + " has another lesson at " + candidate.getPlannedStartAt());
                details.add(buildConflictDetail("STUDENT", studentId, resolveStudentName(studentId), candidate, studentConflict));
            }
            Long candidateTeacherId = candidate.getPlannedTeacherId();
            LessonOccurrenceEntity teacherConflict = firstOverlap(candidate, teacherOccurrences.stream()
                    .filter(existing -> Objects.equals(existing.getPlannedTeacherId(), candidateTeacherId))
                    .toList());
            if (candidateTeacherId != null && teacherConflict != null) {
                conflicts.add("Teacher conflict at " + candidate.getPlannedStartAt());
                details.add(buildConflictDetail("TEACHER", candidateTeacherId,
                        resolveTeacherName(candidateTeacherId), candidate, teacherConflict));
            }
            Long candidateClassroomId = candidate.getPlannedClassroomId();
            LessonOccurrenceEntity classroomConflict = candidateClassroomId == null ? null : firstOverlap(candidate, classroomOccurrences.stream()
                    .filter(existing -> Objects.equals(existing.getPlannedClassroomId(), candidateClassroomId)).toList());
            if (candidateClassroomId != null && classroomConflict != null) {
                conflicts.add("Classroom conflict at " + candidate.getPlannedStartAt());
                details.add(buildConflictDetail("CLASSROOM", candidateClassroomId, resolveClassroomName(candidateClassroomId), candidate, classroomConflict));
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
                                                         LessonOccurrenceEntity candidate,
                                                         LessonOccurrenceEntity existing) {
        return ScheduleConflictDetailVO.builder()
                .resourceType(resourceType)
                .resourceId(resourceId)
                .resourceName(resourceName)
                .requestedStartAt(candidate.getPlannedStartAt())
                .requestedEndAt(candidate.getPlannedEndAt())
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

    /** 教室筛选同时覆盖逐时段教室与旧规则默认教室，供规则列表分页使用。 */
    private List<Long> findMatchedRuleIdsByClassroom(Long classroomId) {
        Set<Long> ruleIds = recurringScheduleSlotMapper.selectList(new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                        .eq(RecurringScheduleSlotEntity::getClassroomId, classroomId))
                .stream()
                .map(RecurringScheduleSlotEntity::getRuleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        recurringScheduleRuleMapper.selectList(new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                        .eq(RecurringScheduleRuleEntity::getDefaultClassroomId, classroomId))
                .stream()
                .map(RecurringScheduleRuleEntity::getId)
                .filter(Objects::nonNull)
                .forEach(ruleIds::add);
        return List.copyOf(ruleIds);
    }

    private RecurringScheduleSlotVO toSlotVO(RecurringScheduleSlotEntity slot, int rolloverAllocatedUnits) {
        return RecurringScheduleSlotVO.builder()
                .id(slot.getId())
                .teacherId(slot.getTeacherId())
                .deliveryMode(slot.getDeliveryMode())
                .classroomId(slot.getClassroomId())
                .subjectName(slot.getSubjectName())
                .weekday(slot.getWeekday())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .plannedUnits(slot.getPlannedUnits())
                .allocatedUnits(slot.getAllocatedUnits())
                .rolloverAllocatedUnits(rolloverAllocatedUnits)
                .slotStatus(slot.getSlotStatus())
                .build();
    }
}
