package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
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
import com.classdeduction.system.modules.schedule.util.PrivateSchedulePeriod;
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
    private final CourseProductMapper courseProductMapper;

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
        // 同一主课包的排课创建串行执行，避免并发请求同时通过分配额度校验。
        CoursePackageEntity lockedPackage = coursePackageMapper.selectByIdForUpdate(coursePackage.getId());
        if (lockedPackage == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        if (!"ACTIVE".equalsIgnoreCase(lockedPackage.getPackageStatus())) {
            throw new BusinessException("PACKAGE_NOT_ACTIVE", "Only active course packages can be scheduled.");
        }
        coursePackage = lockedPackage;
        // 创建与改排课共用主课包锁；已有生效规则必须走更新入口，避免同一课组出现两个当前周期。
        Long activeRuleCount = recurringScheduleRuleMapper.selectCount(
                new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                        .eq(RecurringScheduleRuleEntity::getPackageId, coursePackage.getId())
                        .eq(RecurringScheduleRuleEntity::getRuleStatus, "ACTIVE"));
        if (activeRuleCount != null && activeRuleCount > 0) {
            throw new BusinessException("SCHEDULE_ALREADY_EXISTS",
                    "This package already has an active schedule. Edit the existing schedule instead.");
        }
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

        List<LessonOccurrenceEntity> existingOccurrences = listActivePackageOccurrences(coursePackage.getId());
        Map<Long, LessonRecordEntity> recordByOccurrenceId = listActiveRecordsByOccurrenceId(existingOccurrences, coursePackage.getId());
        List<LessonOccurrenceEntity> generatedOccurrences = buildOccurrences(
                rule, slots, coursePackage, student.getId(), existingOccurrences);
        generatedOccurrences = removeOccurrencesOverlappingPreserved(generatedOccurrences, existingOccurrences);
        generatedOccurrences = capGeneratedOccurrencesToRuleAllocation(generatedOccurrences, existingOccurrences,
                recordByOccurrenceId, slots, rule);
        generatedOccurrences = capGeneratedOccurrencesToPackageBalance(generatedOccurrences, existingOccurrences, recordByOccurrenceId, coursePackage);
        ensureAllocatedHoursWereGenerated(slots, generatedOccurrences);
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
        return renewOneRule(rule, null);
    }

    private int renewOneRule(RecurringScheduleRuleEntity rule, Set<Long> selectedSlotIds) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectById(rule.getPackageId());
        if (coursePackage == null || !"ACTIVE".equals(coursePackage.getPackageStatus())) {
            return 0;
        }
        if (coursePackage.getCourseProductId() != null) {
            // 与归档共用 Product 行锁，避免归档确认后续排任务又写入未来课次。
            CourseProductEntity product = courseProductMapper.selectByIdForUpdate(coursePackage.getCourseProductId());
            if (product == null || product.getArchivedAt() != null) return 0;
        }
        coursePackage = primaryPackageForPrivateGroup(coursePackage, rule.getPrivateLessonGroupId());
        List<RecurringScheduleSlotEntity> slots = recurringScheduleSlotMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                        .eq(RecurringScheduleSlotEntity::getRuleId, rule.getId())
                        .eq(RecurringScheduleSlotEntity::getSlotStatus, "ACTIVE")
                        .in(selectedSlotIds != null, RecurringScheduleSlotEntity::getId,
                                selectedSlotIds == null ? Collections.emptySet() : selectedSlotIds));
        if (slots.isEmpty()) {
            return 0;
        }

        LocalDateTime now = AppTime.nowLocalDateTime();
        List<LessonOccurrenceEntity> activeOccurrences = listActivePackageOccurrences(coursePackage.getId());
        List<LessonOccurrenceEntity> ruleOccurrences = activeOccurrences.stream()
                .filter(occurrence -> Objects.equals(rule.getId(), occurrence.getScheduleRuleId()))
                .toList();
        List<LessonOccurrenceEntity> allRuleOccurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getScheduleRuleId, rule.getId())
                        .eq(LessonOccurrenceEntity::getIsDeleted, 0));
        Map<Long, LessonRecordEntity> recordByOccurrenceId =
                listActiveRecordsByOccurrenceId(activeOccurrences, coursePackage.getId());

        // 先收回超出 slot 分配额的未来普通课；历史、待处理、有记录及补课均属于固定课次。
        List<AllocationCorrection> corrections = trimFutureSlotSurplus(
                rule, slots, ruleOccurrences, recordByOccurrenceId, now);

        List<LessonOccurrenceEntity> candidates = buildOccurrences(
                rule, slots, coursePackage, coursePackage.getStudentId(), activeOccurrences);
        Set<String> existingSlotAnchors = allRuleOccurrences.stream()
                .filter(occurrence -> Objects.equals(rule.getId(), occurrence.getScheduleRuleId()))
                .filter(occurrence -> occurrence.getScheduleSlotId() != null)
                .map(occurrence -> occurrence.getScheduleSlotId() + ":"
                        + (occurrence.getAllocationAnchorDate() != null
                        ? occurrence.getAllocationAnchorDate()
                        : occurrence.getPlannedStartAt().toLocalDate()))
                .collect(Collectors.toSet());
        List<LessonOccurrenceEntity> toInsert = candidates.stream()
                .filter(occurrence -> !existingSlotAnchors.contains(occurrence.getScheduleSlotId() + ":"
                        + occurrence.getAllocationAnchorDate()))
                .toList();
        toInsert = removeOccurrencesOverlappingPreserved(toInsert, activeOccurrences);
        toInsert = capGeneratedOccurrencesToRuleAllocation(toInsert, activeOccurrences,
                recordByOccurrenceId, slots, rule);
        toInsert = capGeneratedOccurrencesToPackageBalance(toInsert, activeOccurrences,
                recordByOccurrenceId, coursePackage);
        if (!toInsert.isEmpty()) {
            ensureNoConflictsExcludingPrivateLessonGroup(toInsert, coursePackage.getStudentId(),
                    rule.getId(), rule.getPrivateLessonGroupId());
        }
        for (AllocationCorrection correction : corrections) {
            int updated = "CANCELLED".equals(correction.after().getPlanningStatus())
                    ? lessonOccurrenceMapper.softDeleteAllocationTrimmedOccurrence(
                            correction.after().getId(), now)
                    : lessonOccurrenceMapper.updateById(correction.after());
            if (updated != 1) {
                throw new BusinessException("SCHEDULE_CHANGED", "A lesson changed while reconciling its schedule.");
            }
            insertAllocationReconciliationLog(correction.before(), correction.after());
        }
        toInsert.forEach(lessonOccurrenceMapper::insert);
        updatePackageEstimatedEndDate(coursePackage, rule.getPrivateLessonGroupId());
        return toInsert.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcilePackageSchedule(Long packageId) {
        CoursePackageEntity coursePackage = coursePackageMapper.selectById(packageId);
        if (coursePackage == null || !"ACTIVE".equals(coursePackage.getPackageStatus())) {
            return;
        }
        PrivateLessonGroupMemberEntity groupMember = privateLessonGroupMemberMapper.selectOne(
                new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .eq(PrivateLessonGroupMemberEntity::getPackageId, packageId)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                        .last("LIMIT 1"));
        Long groupId = groupMember == null ? null : groupMember.getPrivateLessonGroupId();
        coursePackage = primaryPackageForPrivateGroup(coursePackage, groupId);
        Long targetPackageId = coursePackage.getId();
        List<RecurringScheduleRuleEntity> rules = recurringScheduleRuleMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                        .eq(RecurringScheduleRuleEntity::getRuleStatus, "ACTIVE")
                        .eq(groupId == null, RecurringScheduleRuleEntity::getPackageId, targetPackageId)
                        .eq(groupId != null, RecurringScheduleRuleEntity::getPrivateLessonGroupId, groupId)
                        .orderByDesc(RecurringScheduleRuleEntity::getCreatedAt)
                        .orderByDesc(RecurringScheduleRuleEntity::getId));
        if (!rules.isEmpty()) {
            // 共享私教组只有一条公共排课规则，避免通过组员课包重复校准。
            try {
                renewOneRule(rules.get(0));
            } catch (BusinessException ex) {
                if (!"SCHEDULE_CONFLICT".equals(ex.getCode())) {
                    throw ex;
                }
                // 生成候选课次发现外部冲突时保留原排课，不回滚已确认的消课。
                log.warn("Could not reconcile schedule for package {}: {}", packageId, ex.getMessage());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcileScheduleSlot(Long packageId, Long scheduleSlotId) {
        if (packageId == null || scheduleSlotId == null) {
            throw new BusinessException("LESSON_SLOT_REQUIRED", "This lesson is not linked to a recurring schedule slot.");
        }
        RecurringScheduleSlotEntity slot = recurringScheduleSlotMapper.selectById(scheduleSlotId);
        if (slot == null || !"ACTIVE".equals(slot.getSlotStatus())) {
            throw new BusinessException("SCHEDULE_SLOT_NOT_FOUND", "The lesson's original recurring schedule slot is no longer active.");
        }
        RecurringScheduleRuleEntity rule = recurringScheduleRuleMapper.selectById(slot.getRuleId());
        if (rule == null || !"ACTIVE".equals(rule.getRuleStatus())) {
            throw new BusinessException("SCHEDULE_RULE_NOT_ACTIVE", "The lesson's recurring schedule is no longer active.");
        }
        CoursePackageEntity sourcePackage = coursePackageMapper.selectById(packageId);
        if (sourcePackage == null) {
            throw new BusinessException("COURSE_PACKAGE_NOT_FOUND", "Course package not found.");
        }
        CoursePackageEntity rulePackage = coursePackageMapper.selectById(rule.getPackageId());
        boolean samePackage = Objects.equals(rulePackage == null ? null : rulePackage.getId(), sourcePackage.getId());
        boolean sharedMember = rule.getPrivateLessonGroupId() != null
                && privateLessonGroupMemberMapper.selectCount(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                .eq(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, rule.getPrivateLessonGroupId())
                .eq(PrivateLessonGroupMemberEntity::getPackageId, packageId)
                .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)) > 0;
        if (rulePackage == null || (!samePackage && !sharedMember)) {
            throw new BusinessException("SCHEDULE_SLOT_PACKAGE_MISMATCH", "The lesson slot does not belong to this course package.");
        }
        renewOneRule(rule, Set.of(scheduleSlotId));
    }

    private List<AllocationCorrection> trimFutureSlotSurplus(RecurringScheduleRuleEntity rule,
                                                             List<RecurringScheduleSlotEntity> slots,
                                                             List<LessonOccurrenceEntity> occurrences,
                                                             Map<Long, LessonRecordEntity> recordsByOccurrenceId,
                                                             LocalDateTime now) {
        List<AllocationCorrection> corrections = new ArrayList<>();
        LocalDateTime periodStart = PrivateSchedulePeriod.startAt(rule);
        Map<Long, List<LessonOccurrenceEntity>> bySlot = occurrences.stream()
                .filter(occurrence -> occurrence.getScheduleSlotId() != null)
                .filter(occurrence -> PrivateSchedulePeriod.contains(occurrence,
                        recordsByOccurrenceId.get(occurrence.getId()), rule, periodStart))
                .collect(Collectors.groupingBy(LessonOccurrenceEntity::getScheduleSlotId));
        for (RecurringScheduleSlotEntity slot : slots) {
            List<LessonOccurrenceEntity> slotOccurrences = bySlot.getOrDefault(slot.getId(), List.of());
            int accounted = slotOccurrences.stream()
                    .filter(occurrence -> !"CANCELLED".equals(occurrence.getPlanningStatus()))
                    .mapToInt(occurrence -> reservedAllocationUnits(occurrence,
                            recordsByOccurrenceId.get(occurrence.getId())))
                    .sum();
            int surplus = accounted - Math.max(0, slot.getAllocatedUnits() == null ? 0 : slot.getAllocatedUnits());
            if (surplus <= 0) {
                continue;
            }
            List<LessonOccurrenceEntity> mutable = slotOccurrences.stream()
                    .filter(occurrence -> "REGULAR".equals(occurrence.getOccurrenceType()))
                    .filter(occurrence -> "SCHEDULED".equals(occurrence.getPlanningStatus()))
                    .filter(occurrence -> occurrence.getPlannedStartAt() != null
                            && !occurrence.getPlannedStartAt().isBefore(now))
                    .filter(occurrence -> recordsByOccurrenceId.get(occurrence.getId()) == null)
                    .sorted(Comparator.comparing(LessonOccurrenceEntity::getPlannedStartAt).reversed())
                    .toList();
            for (LessonOccurrenceEntity occurrence : mutable) {
                if (surplus <= 0) break;
                LessonOccurrenceEntity before = copyOccurrenceForAudit(occurrence);
                int plannedUnits = occurrence.getPlannedUnits() == null ? 0 : occurrence.getPlannedUnits();
                if (plannedUnits <= surplus) {
                    occurrence.setPlanningStatus("CANCELLED");
                    surplus -= plannedUnits;
                } else {
                    updateCandidatePlannedUnits(occurrence, plannedUnits - surplus);
                    surplus = 0;
                }
                corrections.add(new AllocationCorrection(before, occurrence));
            }
        }
        return corrections;
    }

    private record AllocationCorrection(LessonOccurrenceEntity before, LessonOccurrenceEntity after) { }

    private LessonOccurrenceEntity copyOccurrenceForAudit(LessonOccurrenceEntity occurrence) {
        LessonOccurrenceEntity copy = new LessonOccurrenceEntity();
        copy.setId(occurrence.getId());
        copy.setPlannedStartAt(occurrence.getPlannedStartAt());
        copy.setPlannedEndAt(occurrence.getPlannedEndAt());
        copy.setPlannedUnits(occurrence.getPlannedUnits());
        copy.setPlanningStatus(occurrence.getPlanningStatus());
        copy.setScheduleSlotId(occurrence.getScheduleSlotId());
        copy.setPlannedTeacherId(occurrence.getPlannedTeacherId());
        return copy;
    }

    private void insertAllocationReconciliationLog(LessonOccurrenceEntity before, LessonOccurrenceEntity after) {
        Map<String, Object> beforeSnapshot = new LinkedHashMap<>();
        beforeSnapshot.put("plannedStartAt", before.getPlannedStartAt());
        beforeSnapshot.put("plannedEndAt", before.getPlannedEndAt());
        beforeSnapshot.put("plannedUnits", before.getPlannedUnits());
        beforeSnapshot.put("planningStatus", before.getPlanningStatus());
        Map<String, Object> afterSnapshot = new LinkedHashMap<>();
        afterSnapshot.put("plannedStartAt", after.getPlannedStartAt());
        afterSnapshot.put("plannedEndAt", after.getPlannedEndAt());
        afterSnapshot.put("plannedUnits", after.getPlannedUnits());
        afterSnapshot.put("planningStatus", after.getPlanningStatus());
        LessonAdjustmentLogEntity adjustmentLog = new LessonAdjustmentLogEntity();
        adjustmentLog.setOccurrenceId(after.getId());
        adjustmentLog.setAdjustmentType("ALLOCATION_RECONCILIATION");
        adjustmentLog.setBeforeSnapshot(jsonHelper.toJson(beforeSnapshot));
        adjustmentLog.setAfterSnapshot(jsonHelper.toJson(afterSnapshot));
        adjustmentLog.setReason("Automatically aligned an unprocessed future lesson with its saved teacher allocation.");
        adjustmentLog.setAdjustedAt(AppTime.nowLocalDateTime());
        lessonAdjustmentLogMapper.insert(adjustmentLog);
    }

    private void updatePackageEstimatedEndDate(CoursePackageEntity coursePackage, Long privateLessonGroupId) {
        LocalDate latestEndDate = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getPackageId, coursePackage.getId())
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .eq(LessonOccurrenceEntity::getIsDeleted, 0))
                .stream()
                .map(LessonOccurrenceEntity::getPlannedEndAt)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (latestEndDate == null) return;
        List<CoursePackageEntity> packages = privateLessonGroupId == null
                ? List.of(coursePackage)
                : activeGroupMembers(privateLessonGroupId).stream()
                .map(PrivateLessonGroupMemberEntity::getPackageId)
                .map(coursePackageMapper::selectById)
                .filter(Objects::nonNull)
                .toList();
        for (CoursePackageEntity item : packages) {
            if (!Objects.equals(item.getEstimatedEndDate(), latestEndDate)) {
                item.setEstimatedEndDate(latestEndDate);
                coursePackageMapper.updateById(item);
            }
        }
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
        if (allowNewPeriod && isSamePeriodAllocationChange(existingRule, existingSlots, request,
                privateLessonGroupId, newStatus)) {
            Set<Long> occurrenceIds = allOccurrences.stream()
                    .map(LessonOccurrenceEntity::getId).collect(Collectors.toSet());
            Map<Long, LessonRecordEntity> records = latestRecordsForPackage(occurrenceIds, coursePackage.getId());
            ScheduleRuleUpdateResultVO detailsResult = updateCourseDetailsOnly(existingRule, existingSlots,
                    allOccurrences, records, request, coursePackage, student.getId(), privateLessonGroupId);
            return updateAllocationWithinPeriod(existingRule, existingSlots, allOccurrences, records,
                    request, coursePackage, detailsResult);
        }
        boolean startsNewPeriod = allowNewPeriod
                && !"ENDED".equals(newStatus)
                && hasScheduleDefinitionChanged(existingRule, existingSlots, request, privateLessonGroupId);
        // 暂停、恢复等状态变更不改变排课分配，必须沿用原课位及周期边界。
        boolean reuseExistingSlots = !startsNewPeriod
                && hasUnchangedSlotDetails(existingRule, existingSlots, request.getSlots());
        if (!startsNewPeriod && !reuseExistingSlots && !existingSlots.isEmpty()) {
            freezeLegacyAllocationPeriod(existingRule);
        }
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

        boolean teacherHandoffOnly = startsNewPeriod && isTeacherHandoffOnly(request.getSlots(), existingSlots);
        Map<Long, Long> handoffTeacherByLineage = startsNewPeriod
                ? teacherHandoffTargetsByLineage(request.getSlots(), existingSlots)
                : Collections.emptyMap();
        Map<Long, RecurringScheduleSlotEntity> handoffSlotsById = Collections.emptyMap();
        List<LessonOccurrenceEntity> futureHandoffOccurrences = Collections.emptyList();
        Map<Long, Integer> futureHandoffUnitsBySlot = Collections.emptyMap();
        if (startsNewPeriod) {
            List<LessonOccurrenceEntity> handoffCandidates = mergeOccurrences(toPreserve, endedPeriodOccurrences);
            handoffSlotsById = loadOccurrenceSlots(handoffCandidates);
            Map<Long, LessonRecordEntity> handoffRecords = new HashMap<>(recordByOccurrenceId);
            handoffRecords.putAll(latestRecordsForPackage(
                    handoffCandidates.stream().map(LessonOccurrenceEntity::getId).toList(), coursePackage.getId()));
            futureHandoffOccurrences = futureOccurrencesForTeacherHandoff(
                    handoffCandidates, handoffSlotsById, handoffRecords,
                    handoffTeacherByLineage, AppTime.nowLocalDateTime());
            futureHandoffUnitsBySlot = futureHandoffUnitsBySourceSlot(
                    existingSlots, futureHandoffOccurrences, handoffSlotsById,
                    handoffTeacherByLineage);
            applyImplicitTeacherHandoffs(request.getSlots(), existingSlotsById, allOccurrences,
                    recordByOccurrenceId, AppTime.nowLocalDateTime(), teacherHandoffOnly,
                    futureHandoffUnitsBySlot);
        }
        if (startsNewPeriod && validateRemainingAllocation) {
            validateNewPeriodAllocations(request.getSlots(), coursePackage, id,
                    preservedForBalance, balanceRecords, teacherHandoffOnly,
                    futureHandoffUnitsBySlot.values().stream().mapToInt(Integer::intValue).sum());
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
            if (!reuseExistingSlots) {
                recurringScheduleSlotMapper.delete(new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                        .eq(RecurringScheduleSlotEntity::getRuleId, id));
            }
        }

        Long targetRuleId = targetRule.getId();
        List<RecurringScheduleSlotEntity> newSlots = reuseExistingSlots
                ? existingSlots
                : request.getSlots().stream()
                        .map(slotRequest -> toSlotEntity(targetRuleId, slotRequest, existingSlotsById)).toList();
        if (!reuseExistingSlots) {
            targetRule.setDefaultClassroomId(firstSlotClassroomId(newSlots));
            recurringScheduleRuleMapper.updateById(targetRule);
            newSlots.forEach(recurringScheduleSlotMapper::insert);
        }
        if (startsNewPeriod) {
            saveStartingBalances(targetRule, coursePackage, privateLessonGroupId);
        }

        List<LessonOccurrenceEntity> classroomUpdates = buildInheritedClassroomUpdates(
                toPreserve, recordByOccurrenceId, existingSlotsById, newSlots);

        Map<Long, RecurringScheduleSlotEntity> handoffTargetSlotsByOccurrence = new HashMap<>();
        Map<Long, RecurringScheduleSlotEntity> handoffSourceSlotsByOccurrence = new HashMap<>();
        Map<Long, Long> handoffExpectedTeachersByOccurrence = new HashMap<>();
        if (!futureHandoffOccurrences.isEmpty()) {
            Map<Long, RecurringScheduleSlotEntity> targetSlotsByLineage = newSlots.stream()
                    .collect(Collectors.toMap(this::slotLineageId, slot -> slot, (left, right) -> left));
            for (LessonOccurrenceEntity occurrence : futureHandoffOccurrences) {
                RecurringScheduleSlotEntity source = handoffSlotsById.get(occurrence.getScheduleSlotId());
                RecurringScheduleSlotEntity target = targetSlotsByLineage.get(slotLineageId(source));
                if (target == null) {
                    throw new BusinessException("SCHEDULE_HANDOFF_SLOT_MISSING",
                            "The updated schedule no longer contains the slot for a future lesson being handed off.");
                }
                handoffTargetSlotsByOccurrence.put(occurrence.getId(), target);
                handoffSourceSlotsByOccurrence.put(occurrence.getId(), source);
                handoffExpectedTeachersByOccurrence.put(occurrence.getId(),
                        occurrence.getPlannedTeacherId() == null && source != null
                                ? source.getTeacherId() : occurrence.getPlannedTeacherId());
                occurrence.setScheduleRuleId(targetRuleId);
                occurrence.setScheduleSlotId(target.getId());
                occurrence.setAllocationAnchorDate(occurrence.getPlannedStartAt().toLocalDate());
                occurrence.setPlannedTeacherId(target.getTeacherId());
                occurrence.setAllocationOwnerTeacherId(target.getTeacherId());
            }
        }

        // i) Build and insert new occurrences only for an active package and rule.
        // A paused package may update its future slot configuration, but lesson generation
        // must wait for the explicit resume action so its restart date is respected.
        List<LessonOccurrenceEntity> newOccurrences = Collections.emptyList();
        if ("ACTIVE".equals(coursePackage.getPackageStatus()) && "ACTIVE".equals(targetRule.getRuleStatus())) {
            newOccurrences = buildOccurrences(
                    targetRule, newSlots, coursePackage, student.getId(), preservedForBalance);
            newOccurrences = removeOccurrencesOverlappingPreserved(newOccurrences, preservedForBalance);
            newOccurrences = capGeneratedOccurrencesToRuleAllocation(newOccurrences, toPreserve,
                    recordByOccurrenceId, newSlots, targetRule);
            newOccurrences = capGeneratedOccurrencesToPackageBalance(newOccurrences,
                    preservedForBalance, balanceRecords, coursePackage);
            if (allowNewPeriod) {
                List<LessonOccurrenceEntity> scheduledOccurrences = new ArrayList<>(preservedForBalance);
                scheduledOccurrences.addAll(newOccurrences);
                ensureCurrentPeriodAllocationIsCovered(newSlots, scheduledOccurrences,
                        balanceRecords, targetRule);
            }
        }

        // 普通未来课次会随规则重建；保留的未来例外若仍继承规则教室，也要同步到新槽位配置。
        ensureNoClassroomConflicts(classroomUpdates, toPreserve, newOccurrences, id);

        // j) Conflict check excluding this rule's preserved occurrences
        List<LessonOccurrenceEntity> conflictCandidates = new ArrayList<>(newOccurrences);
        conflictCandidates.addAll(futureHandoffOccurrences);
        ensureNoConflictsExcludingPrivateLessonGroup(conflictCandidates, student.getId(),
                id, targetRule.getPrivateLessonGroupId(), futureHandoffOccurrences.stream()
                        .map(LessonOccurrenceEntity::getId).collect(Collectors.toSet()));

        for (LessonOccurrenceEntity update : classroomUpdates) {
            if (lessonOccurrenceMapper.updateInheritedClassroom(update.getId(), update.getPlannedClassroomId(),
                    update.getDeliveryMode(), AppTime.nowLocalDateTime()) != 1) {
                throw new BusinessException("LESSON_CHANGED",
                        "A lesson has started or entered the deduction process. Refresh the schedule before editing it.");
            }
        }
        for (LessonOccurrenceEntity occurrence : futureHandoffOccurrences) {
            RecurringScheduleSlotEntity targetSlot = handoffTargetSlotsByOccurrence.get(occurrence.getId());
            RecurringScheduleSlotEntity sourceSlot = handoffSourceSlotsByOccurrence.get(occurrence.getId());
            Long expectedTeacherId = handoffExpectedTeachersByOccurrence.get(occurrence.getId());
            if (targetSlot == null || lessonOccurrenceMapper.handoffFutureOccurrence(
                    occurrence.getId(), sourceSlot == null ? null : sourceSlot.getRuleId(),
                    sourceSlot == null ? null : sourceSlot.getId(), targetRuleId, targetSlot.getId(),
                    occurrence.getAllocationAnchorDate(),
                    expectedTeacherId, targetSlot.getTeacherId(),
                    AppTime.nowLocalDateTime()) != 1) {
                throw new BusinessException("LESSON_CHANGED",
                        "A future lesson changed while its teacher was being handed off. Refresh the schedule and try again.");
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

    /** 课程名称、教室和备注仅更新未来课次；更换老师必须建立新排课版本。 */
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
            if (!Objects.equals(current.getTeacherId(), requested.getTeacherId())) {
                return false;
            }
        }
        // 无变化的保存也走原位更新，避免删除并重建同一版本的未来课次。
        return true;
    }

    /** 状态变更时核对课位快照，完全相同时复用 ID，避免重建课位被误判为新周期。 */
    private boolean hasUnchangedSlotDetails(RecurringScheduleRuleEntity rule,
                                            List<RecurringScheduleSlotEntity> currentSlots,
                                            List<RecurringScheduleSlotRequest> requestedSlots) {
        if (currentSlots.size() != requestedSlots.size()) {
            return false;
        }
        Map<Long, RecurringScheduleSlotEntity> currentById = currentSlots.stream()
                .collect(Collectors.toMap(RecurringScheduleSlotEntity::getId, slot -> slot));
        if (requestedSlots.stream().map(RecurringScheduleSlotRequest::getSlotId)
                .distinct().count() != requestedSlots.size()) {
            return false;
        }
        for (RecurringScheduleSlotRequest requested : requestedSlots) {
            RecurringScheduleSlotEntity current = currentById.get(requested.getSlotId());
            if (current == null) {
                return false;
            }
            String currentMode = current.getDeliveryMode() == null ? "IN_PERSON" : current.getDeliveryMode();
            Long currentClassroomId = "ONLINE".equals(currentMode) ? null
                    : current.getClassroomId() != null ? current.getClassroomId() : rule.getDefaultClassroomId();
            if (!Objects.equals(current.getTeacherId(), requested.getTeacherId())
                    || !Objects.equals(currentMode, requested.getDeliveryMode())
                    || !Objects.equals(currentClassroomId, requested.getClassroomId())
                    || !Objects.equals(current.getSubjectName(), requested.getSubjectName())
                    || !Objects.equals(current.getWeekday(), requested.getWeekday())
                    || !Objects.equals(current.getStartTime(), requested.getStartTime())
                    || !Objects.equals(current.getEndTime(), requested.getEndTime())
                    || !Objects.equals(current.getPlannedUnits(), requested.getPlannedUnits())
                    || !Objects.equals(current.getAllocatedUnits(), requested.getAllocatedUnits())) {
                return false;
            }
        }
        return true;
    }

    /** 仅改变已有固定课位分配额时沿用周期，不重建规则或改写课次时间线。 */
    private boolean isSamePeriodAllocationChange(RecurringScheduleRuleEntity rule,
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
        boolean allocationChanged = false;
        for (RecurringScheduleSlotRequest requested : request.getSlots()) {
            RecurringScheduleSlotEntity current = currentById.get(requested.getSlotId());
            if (current == null) return false;
            if (!Objects.equals(current.getTeacherId(), requested.getTeacherId())
                    || !Objects.equals(current.getWeekday(), requested.getWeekday())
                    || !Objects.equals(current.getStartTime(), requested.getStartTime())
                    || !Objects.equals(current.getEndTime(), requested.getEndTime())
                    || !Objects.equals(current.getPlannedUnits(), requested.getPlannedUnits())) {
                return false;
            }
            allocationChanged |= safeUnits(current.getAllocatedUnits())
                    != safeUnits(requested.getAllocatedUnits());
        }
        return allocationChanged;
    }

    /** 同周期额度调整只追加或安全回收末尾普通课，不触碰历史、例外课和有记录课次。 */
    private ScheduleRuleUpdateResultVO updateAllocationWithinPeriod(
            RecurringScheduleRuleEntity rule,
            List<RecurringScheduleSlotEntity> currentSlots,
            List<LessonOccurrenceEntity> occurrences,
            Map<Long, LessonRecordEntity> recordsByOccurrenceId,
            RecurringScheduleRuleUpdateRequest request,
            CoursePackageEntity coursePackage,
            ScheduleRuleUpdateResultVO detailsResult) {
        validateSlotAllocations(request.getSlots(), coursePackage, rule.getId());
        Map<Long, RecurringScheduleSlotRequest> requestedById = request.getSlots().stream()
                .collect(Collectors.toMap(RecurringScheduleSlotRequest::getSlotId, slot -> slot));
        LocalDateTime now = AppTime.nowLocalDateTime();
        LocalDateTime periodStart = PrivateSchedulePeriod.startAt(rule);

        for (RecurringScheduleSlotEntity current : currentSlots) {
            int nextUnits = safeUnits(requestedById.get(current.getId()).getAllocatedUnits());
            if (nextUnits >= safeUnits(current.getAllocatedUnits())) continue;
            List<LessonOccurrenceEntity> slotOccurrences = occurrences.stream()
                    .filter(occurrence -> Objects.equals(current.getId(), occurrence.getScheduleSlotId()))
                    .filter(occurrence -> PrivateSchedulePeriod.contains(occurrence,
                            recordsByOccurrenceId.get(occurrence.getId()), rule, periodStart))
                    .filter(occurrence -> !"CANCELLED".equals(occurrence.getPlanningStatus()))
                    .toList();
            int accountedUnits = slotOccurrences.stream().mapToInt(occurrence -> reservedAllocationUnits(
                    occurrence, recordsByOccurrenceId.get(occurrence.getId()))).sum();
            int excess = accountedUnits - nextUnits;
            if (excess <= 0) continue;
            int removableUnits = slotOccurrences.stream()
                    .filter(occurrence -> "REGULAR".equals(occurrence.getOccurrenceType()))
                    .filter(occurrence -> "SCHEDULED".equals(occurrence.getPlanningStatus()))
                    .filter(occurrence -> occurrence.getPlannedStartAt() != null
                            && !occurrence.getPlannedStartAt().isBefore(now))
                    .filter(occurrence -> !recordsByOccurrenceId.containsKey(occurrence.getId()))
                    .mapToInt(occurrence -> Math.max(0, safeUnits(occurrence.getPlannedUnits())))
                    .sum();
            if (removableUnits < excess) {
                throw new BusinessException("SLOT_ALLOCATION_BELOW_RESERVED",
                        "This allocation cannot be reduced by that amount because the difference is reserved by started, adjusted, or processed lessons.");
            }
        }

        for (RecurringScheduleSlotEntity current : currentSlots) {
            int oldUnits = safeUnits(current.getAllocatedUnits());
            int nextUnits = safeUnits(requestedById.get(current.getId()).getAllocatedUnits());
            if (oldUnits == nextUnits) continue;
            if (recurringScheduleSlotMapper.updateAllocatedUnits(current.getId(), rule.getId(),
                    oldUnits, nextUnits) != 1) {
                throw new BusinessException("SCHEDULE_CHANGED",
                        "The schedule allocation changed while you were editing it. Refresh and try again.");
            }
            current.setAllocatedUnits(nextUnits);
        }
        recurringScheduleRuleMapper.updateCourseDetails(rule.getId(), firstSlotClassroomId(currentSlots),
                request.getNotes());

        int generatedCount = renewOneRule(rule);
        if ("ACTIVE".equals(coursePackage.getPackageStatus()) && "ACTIVE".equals(rule.getRuleStatus())) {
            List<LessonOccurrenceEntity> activeOccurrences = listActivePackageOccurrences(coursePackage.getId());
            Map<Long, LessonRecordEntity> activeRecords =
                    listActiveRecordsByOccurrenceId(activeOccurrences, coursePackage.getId());
            ensureCurrentPeriodAllocationIsCovered(currentSlots, activeOccurrences, activeRecords, rule);
        }
        int confirmedCount = (int) recordsByOccurrenceId.values().stream()
                .filter(record -> "CONFIRMED".equals(record.getConfirmStage())).count();
        return new ScheduleRuleUpdateResultVO(rule.getId(), currentSlots.size(),
                detailsResult.preservedOccurrenceCount(),
                0, generatedCount, confirmedCount, null, null);
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
            if (!StringUtils.hasText(occurrence.getSubjectName())
                    && StringUtils.hasText(oldSlot.getSubjectName())
                    && isSlotSubjectSnapshotSafe(occurrence, oldSlot)) {
                update.setSubjectName(oldSlot.getSubjectName());
            }
            if (update.getPlannedTeacherId() != null || update.getSubjectName() != null) {
                lessonOccurrenceMapper.updateById(update);
            }
        }
    }

    /** 只有时段最后修改时间不晚于上课时间，才可将当前时段名称视作该课次的历史快照。 */
    private boolean isSlotSubjectSnapshotSafe(LessonOccurrenceEntity occurrence,
                                              RecurringScheduleSlotEntity slot) {
        LocalDateTime lessonStartAt = occurrence.getPlannedStartAt();
        LocalDateTime slotUpdatedAt = slot.getUpdatedAt();
        if (Integer.valueOf(1).equals(slot.getIsDeleted())
                && (lessonStartAt == null || slotUpdatedAt == null)) {
            return true;
        }
        return lessonStartAt != null && slotUpdatedAt != null
                && !slotUpdatedAt.isAfter(lessonStartAt);
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
            freezeLegacyAllocationPeriod(rule);
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
        reconcilePackageSchedule(packageId);
    }

    /** 同周期重建旧课位前固定周期起点；课位创建时间不作为排课变更证据。 */
    private void freezeLegacyAllocationPeriod(RecurringScheduleRuleEntity rule) {
        if (rule.getSupersedesRuleId() != null || rule.getAllocationPeriodStartedAt() != null) {
            return;
        }
        // 新规则的创建时间就是周期起点；旧规则缺少可靠历史边界时不从课位写入时间猜测。
        LocalDateTime periodStart = rule.getCreatedAt();
        if (periodStart == null) {
            throw new BusinessException("SCHEDULE_PERIOD_START_MISSING",
                    "Cannot refresh this schedule because its allocation period is unknown.");
        }
        RecurringScheduleRuleEntity patch = new RecurringScheduleRuleEntity();
        patch.setId(rule.getId());
        patch.setAllocationPeriodStartedAt(periodStart);
        if (recurringScheduleRuleMapper.updateById(patch) != 1) {
            throw new BusinessException("SCHEDULE_CHANGED", "The schedule changed. Please retry.");
        }
        rule.setAllocationPeriodStartedAt(periodStart);
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
        ensureNoConflictsExcludingRule(candidates, studentId, excludeRuleId, Collections.emptySet());
    }

    private void ensureNoConflictsExcludingRule(List<LessonOccurrenceEntity> candidates,
                                                Long studentId, Long excludeRuleId,
                                                Set<Long> excludeOccurrenceIds) {
        if (candidates.isEmpty()) {
            return;
        }
        LocalDateTime windowStart = candidates.stream().map(LessonOccurrenceEntity::getPlannedStartAt).min(LocalDateTime::compareTo).orElseThrow();
        LocalDateTime windowEnd = candidates.stream().map(LessonOccurrenceEntity::getPlannedEndAt).max(LocalDateTime::compareTo).orElseThrow();

        List<LessonOccurrenceEntity> studentOccurrences = listStudentOccupiedOccurrences(
                studentId, windowStart, windowEnd, excludeRuleId).stream()
                .filter(occurrence -> !excludeOccurrenceIds.contains(occurrence.getId())).toList();
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
        teacherOccurrences = teacherOccurrences.stream()
                .filter(occurrence -> !excludeOccurrenceIds.contains(occurrence.getId())).toList();
        Set<Long> classroomIds = candidates.stream().map(LessonOccurrenceEntity::getPlannedClassroomId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<LessonOccurrenceEntity> classroomOccurrences = classroomIds.isEmpty() ? Collections.emptyList() : lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getPlannedClassroomId, classroomIds)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ne(LessonOccurrenceEntity::getScheduleRuleId, excludeRuleId)
                .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                .gt(LessonOccurrenceEntity::getPlannedEndAt, windowStart));
        classroomOccurrences = classroomOccurrences.stream()
                .filter(occurrence -> !excludeOccurrenceIds.contains(occurrence.getId())).toList();

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
        ensureNoConflictsExcludingPrivateLessonGroup(candidates, primaryStudentId,
                excludeRuleId, privateLessonGroupId, Collections.emptySet());
    }

    private void ensureNoConflictsExcludingPrivateLessonGroup(List<LessonOccurrenceEntity> candidates,
                                                              Long primaryStudentId,
                                                              Long excludeRuleId,
                                                              Long privateLessonGroupId,
                                                              Set<Long> excludeOccurrenceIds) {
        ensureNoConflictsExcludingRule(candidates, primaryStudentId, excludeRuleId, excludeOccurrenceIds);
        if (privateLessonGroupId == null) {
            return;
        }
        for (PrivateLessonGroupMemberEntity member : activeGroupMembers(privateLessonGroupId)) {
            if (Objects.equals(member.getStudentId(), primaryStudentId)) {
                continue;
            }
            ensureNoConflictsExcludingRule(copyCandidatesForStudent(candidates, member.getStudentId()),
                    member.getStudentId(), excludeRuleId, excludeOccurrenceIds);
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
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .select(LessonOccurrenceEntity::getId, LessonOccurrenceEntity::getPlannedUnits)
                        .eq(LessonOccurrenceEntity::getScheduleRuleId, ruleId));
        List<Long> occurrenceIds = occurrences.stream().map(LessonOccurrenceEntity::getId).toList();
        if (occurrenceIds.isEmpty()) {
            return new RuleDeductionUnits(0, 0);
        }
        int confirmed = 0;
        int pending = 0;
        Map<Long, LessonRecordEntity> records = latestRecordsForPackage(occurrenceIds, packageId);
        for (LessonOccurrenceEntity occurrence : occurrences) {
            LessonRecordEntity record = records.get(occurrence.getId());
            if (record == null) {
                continue;
            }
            if ("CONFIRMED".equals(record.getConfirmStage())) {
                if (Objects.equals(record.getShouldDeduct(), 1)) {
                    confirmed += Math.max(0, safeUnits(record.getDeductedUnits()));
                }
            } else if ("PENDING_ADMIN".equals(record.getConfirmStage())) {
                // 老师意见尚未生效，按原计划课时预留，不按拟议的扣课或请假结果计算。
                pending += Math.max(0, safeUnits(occurrence.getPlannedUnits()));
            }
        }
        return new RuleDeductionUnits(confirmed, pending);
    }

    /** 待管理员确认的课次仍占用原计划课时，避免老师的请假意见提前释放余额。 */
    private int pendingDeductionUnits(Long packageId) {
        List<Long> occurrenceIds = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                        .select(LessonRecordEntity::getOccurrenceId)
                        .eq(LessonRecordEntity::getPackageId, packageId)
                        .eq(LessonRecordEntity::getConfirmStage, "PENDING_ADMIN")
                        .eq(LessonRecordEntity::getIsDeleted, 0))
                .stream().map(LessonRecordEntity::getOccurrenceId).filter(Objects::nonNull).distinct().toList();
        if (occurrenceIds.isEmpty()) {
            return 0;
        }
        Map<Long, LessonRecordEntity> latestRecords = latestRecordsForPackage(occurrenceIds, packageId);
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .select(LessonOccurrenceEntity::getId, LessonOccurrenceEntity::getPlannedUnits)
                        .in(LessonOccurrenceEntity::getId, occurrenceIds))
                .stream()
                .filter(occurrence -> {
                    LessonRecordEntity record = latestRecords.get(occurrence.getId());
                    return record != null && "PENDING_ADMIN".equals(record.getConfirmStage());
                })
                .mapToInt(occurrence -> Math.max(0, safeUnits(occurrence.getPlannedUnits())))
                .sum();
    }

    /** 新排课周期只允许分配当前可用余额，旧周期保留的待确认与例外课次继续占用原余额。 */
    private void validateNewPeriodAllocations(List<RecurringScheduleSlotRequest> slots,
                                              CoursePackageEntity coursePackage,
                                              Long previousRuleId,
                                              List<LessonOccurrenceEntity> preservedOccurrences,
                                              Map<Long, LessonRecordEntity> recordByOccurrenceId,
                                              boolean allowEmptySlotAllocations,
                                              int transferredFutureUnits) {
        int allocated = 0;
        for (RecurringScheduleSlotRequest slot : slots) {
            if (slot.getTeacherId() == null || !StringUtils.hasText(slot.getSubjectName())
                    || slot.getAllocatedUnits() == null || slot.getAllocatedUnits() < 0
                    || (slot.getAllocatedUnits() == 0 && !allowEmptySlotAllocations)) {
                throw new BusinessException("INCOMPLETE_SLOT_ALLOCATION",
                        "Each schedule slot requires a teacher, course/subject, and schedule total hours.");
            }
            allocated += slot.getAllocatedUnits();
        }
        int available = Math.max(0, allocatableUnits(coursePackage)
                - pendingDeductionUnits(coursePackage.getId())
                - otherRuleReservedUnits(coursePackage.getId(), previousRuleId)
                - preservedNonPendingUnits(preservedOccurrences, recordByOccurrenceId,
                        AppTime.nowLocalDateTime())
                + transferredFutureUnits);
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
                usedOrReserved += Math.max(0, safeUnits(occurrence.getPlannedUnits()));
            } else if (isPreservedByScheduleEdit(occurrence, recordByOccurrenceId, now)) {
                usedOrReserved += Math.max(0, safeUnits(occurrence.getPlannedUnits()));
            }
        }
        return Math.max(0, safeUnits(slot.getAllocatedUnits()) - usedOrReserved);
    }

    /** 只换老师且请求仍带着旧分配值时，将旧 slot 尚未使用、未被保留的额度自动转给新老师。 */
    private void applyImplicitTeacherHandoffs(List<RecurringScheduleSlotRequest> requestedSlots,
                                              Map<Long, RecurringScheduleSlotEntity> previousSlotsById,
                                              List<LessonOccurrenceEntity> previousOccurrences,
                                              Map<Long, LessonRecordEntity> recordsByOccurrenceId,
                                              LocalDateTime changedAt,
                                              boolean teacherHandoffOnly,
                                              Map<Long, Integer> futureHandoffUnitsBySlot) {
        for (RecurringScheduleSlotRequest requested : requestedSlots) {
            if (requested.getSlotId() == null) continue;
            RecurringScheduleSlotEntity previous = previousSlotsById.get(requested.getSlotId());
            boolean teacherChanged = previous != null
                    && !Objects.equals(previous.getTeacherId(), requested.getTeacherId());
            if (previous == null || (!teacherChanged && !teacherHandoffOnly)) continue;
            int rolloverUnits = rolloverAllocatedUnits(previous, previousOccurrences,
                    recordsByOccurrenceId, changedAt);
            int transferableUnits = rolloverUnits
                    + futureHandoffUnitsBySlot.getOrDefault(previous.getId(), 0);
            int requestedUnits = safeUnits(requested.getAllocatedUnits());
            int futureHandoffUnits = futureHandoffUnitsBySlot.getOrDefault(previous.getId(), 0);
            if (teacherChanged && requestedUnits < futureHandoffUnits) {
                throw new BusinessException("SLOT_ALLOCATION_BELOW_HANDOFF",
                        "The new teacher allocation must cover future adjusted lessons being handed off.");
            }
            boolean allocationWasAutoFilled = requestedUnits == safeUnits(previous.getAllocatedUnits())
                    || requestedUnits == rolloverUnits;
            if (teacherChanged && allocationWasAutoFilled) {
                requested.setAllocatedUnits(transferableUnits);
            }
            if (transferableUnits <= 0 && !teacherHandoffOnly) {
                throw new BusinessException("NO_TRANSFERABLE_SLOT_ALLOCATION",
                        "This slot has no future unreserved hours to transfer. Remove the slot or set a valid new allocation.");
            }
        }
    }

    /** 只把明确变更老师的 slot lineage 映射到新老师，供保留未来课次与额度一起交接。 */
    private Map<Long, Long> teacherHandoffTargetsByLineage(
            List<RecurringScheduleSlotRequest> requestedSlots,
            List<RecurringScheduleSlotEntity> existingSlots) {
        Map<Long, RecurringScheduleSlotEntity> existingById = existingSlots.stream()
                .collect(Collectors.toMap(RecurringScheduleSlotEntity::getId, slot -> slot));
        Map<Long, Long> targets = new HashMap<>();
        for (RecurringScheduleSlotRequest request : requestedSlots) {
            RecurringScheduleSlotEntity existing = request.getSlotId() == null
                    ? null : existingById.get(request.getSlotId());
            if (existing == null || Objects.equals(existing.getTeacherId(), request.getTeacherId())) continue;
            targets.put(slotLineageId(existing), request.getTeacherId());
        }
        return targets;
    }

    /** 合并当前周期将保留的课次和旧周期保留课次，按课次 ID 去重。 */
    private List<LessonOccurrenceEntity> mergeOccurrences(List<LessonOccurrenceEntity> first,
                                                          List<LessonOccurrenceEntity> second) {
        Map<Long, LessonOccurrenceEntity> unique = new LinkedHashMap<>();
        first.stream().filter(item -> item.getId() != null)
                .forEach(item -> unique.put(item.getId(), item));
        second.stream().filter(item -> item.getId() != null)
                .forEach(item -> unique.putIfAbsent(item.getId(), item));
        return new ArrayList<>(unique.values());
    }

    /** 批量取课次来源 slot，包含已软删除的旧周期 slot 以恢复 lineage。 */
    private Map<Long, RecurringScheduleSlotEntity> loadOccurrenceSlots(List<LessonOccurrenceEntity> occurrences) {
        List<Long> slotIds = occurrences.stream()
                .map(LessonOccurrenceEntity::getScheduleSlotId)
                .filter(Objects::nonNull).distinct().toList();
        if (slotIds.isEmpty()) return Collections.emptyMap();
        return recurringScheduleSlotMapper.selectIncludingDeletedByIds(slotIds).stream()
                .collect(Collectors.toMap(RecurringScheduleSlotEntity::getId, slot -> slot, (left, right) -> left));
    }

    /** 只交接未来未取消、且没有消课记录的课次；历史记录快照保持原值。 */
    private List<LessonOccurrenceEntity> futureOccurrencesForTeacherHandoff(
            List<LessonOccurrenceEntity> candidates,
            Map<Long, RecurringScheduleSlotEntity> slotsById,
            Map<Long, LessonRecordEntity> recordsByOccurrenceId,
            Map<Long, Long> teacherByLineage,
            LocalDateTime effectiveAt) {
        List<LessonOccurrenceEntity> transferable = new ArrayList<>();
        for (LessonOccurrenceEntity occurrence : candidates) {
            if (occurrence.getPlannedStartAt() == null
                    || occurrence.getPlannedStartAt().isBefore(effectiveAt)
                    || "CANCELLED".equals(occurrence.getPlanningStatus())) continue;
            RecurringScheduleSlotEntity source = slotsById.get(occurrence.getScheduleSlotId());
            if (!teacherByLineage.containsKey(slotLineageId(source))) continue;
            if (recordsByOccurrenceId.containsKey(occurrence.getId())) {
                throw new BusinessException("FUTURE_HANDOFF_HAS_LESSON_RECORD",
                        "A future lesson in this slot already has a lesson record. Resolve that record before changing the recurring teacher.");
            }
            transferable.add(occurrence);
        }
        return transferable;
    }

    /** 未来例外课次随 slot 一起交接，其预留课时也计入新老师分配。 */
    private Map<Long, Integer> futureHandoffUnitsBySourceSlot(
            List<RecurringScheduleSlotEntity> existingSlots,
            List<LessonOccurrenceEntity> futureOccurrences,
            Map<Long, RecurringScheduleSlotEntity> occurrenceSlotsById,
            Map<Long, Long> teacherByLineage) {
        Map<Long, Integer> unitsByLineage = new HashMap<>();
        for (LessonOccurrenceEntity occurrence : futureOccurrences) {
            RecurringScheduleSlotEntity source = occurrenceSlotsById.get(occurrence.getScheduleSlotId());
            Long lineageId = slotLineageId(source);
            if (lineageId != null) {
                unitsByLineage.merge(lineageId, Math.max(0, safeUnits(occurrence.getPlannedUnits())), Integer::sum);
            }
        }
        Map<Long, Integer> unitsBySlot = new HashMap<>();
        for (RecurringScheduleSlotEntity existing : existingSlots) {
            Long lineageId = slotLineageId(existing);
            if (teacherByLineage.containsKey(lineageId)) {
                unitsBySlot.put(existing.getId(), unitsByLineage.getOrDefault(lineageId, 0));
            }
        }
        return unitsBySlot;
    }

    private Long slotLineageId(RecurringScheduleSlotEntity slot) {
        return slot == null ? null : slot.getSlotLineageId() == null ? slot.getId() : slot.getSlotLineageId();
    }

    /** 检查本次是否只更换既有周课位的老师，供服务端安全推导未手动改写的各 slot 新额度。 */
    private boolean isTeacherHandoffOnly(List<RecurringScheduleSlotRequest> requestedSlots,
                                         List<RecurringScheduleSlotEntity> previousSlots) {
        if (requestedSlots.size() != previousSlots.size()) return false;
        Map<Long, RecurringScheduleSlotEntity> previousSlotsById = previousSlots.stream()
                .collect(Collectors.toMap(RecurringScheduleSlotEntity::getId, slot -> slot));
        boolean teacherChanged = false;
        for (RecurringScheduleSlotRequest requested : requestedSlots) {
            RecurringScheduleSlotEntity previous = requested.getSlotId() == null
                    ? null : previousSlotsById.get(requested.getSlotId());
            if (previous == null
                    || !Objects.equals(previous.getWeekday(), requested.getWeekday())
                    || !Objects.equals(previous.getStartTime(), requested.getStartTime())
                    || !Objects.equals(previous.getEndTime(), requested.getEndTime())
                    || !Objects.equals(previous.getPlannedUnits(), requested.getPlannedUnits())) {
                return false;
            }
            teacherChanged |= !Objects.equals(previous.getTeacherId(), requested.getTeacherId());
        }
        return teacherChanged;
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
        return toSlotEntity(ruleId, request, Collections.emptyMap());
    }

    /** 新规则重建 slot 时延续请求中原 slot 的 lineage；没有来源的 slot 建立独立 lineage。 */
    private RecurringScheduleSlotEntity toSlotEntity(Long ruleId, RecurringScheduleSlotRequest request,
                                                     Map<Long, RecurringScheduleSlotEntity> previousSlotsById) {
        RecurringScheduleSlotEntity entity = new RecurringScheduleSlotEntity();
        entity.setRuleId(ruleId);
        RecurringScheduleSlotEntity previousSlot = request.getSlotId() == null
                ? null : previousSlotsById.get(request.getSlotId());
        entity.setSlotLineageId(previousSlot == null
                ? IdWorker.getId()
                : previousSlot.getSlotLineageId() == null ? previousSlot.getId() : previousSlot.getSlotLineageId());
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
                                                          Long studentId,
                                                          List<LessonOccurrenceEntity> preservedOccurrences) {
        LocalDateTime now = AppTime.nowLocalDateTime();
        LocalDate generationStart = Collections.max(List.of(rule.getStartDate(), now.toLocalDate()));
        LocalDate generationEnd = rule.getEndDate();
        if (generationEnd != null && generationEnd.isBefore(generationStart)) {
            return Collections.emptyList();
        }

        // 逐时段独立生成：每个 slot 用自己的 allocatedUnits 排满，最后一次课不足整次时只排余数。
        List<LessonOccurrenceEntity> occurrences = new ArrayList<>();
        List<LessonOccurrenceEntity> activePreserved = preservedOccurrences.stream()
                .filter(occurrence -> !"CANCELLED".equals(occurrence.getPlanningStatus()))
                .toList();
        for (RecurringScheduleSlotEntity slot : slots) {
            if (!"ACTIVE".equals(slot.getSlotStatus())) {
                continue;
            }
            if (Objects.equals(slot.getAllocatedUnits(), 0)) {
                continue;
            }
            requireCompleteSlotAllocation(slot);
            int plannedUnits = slot.getPlannedUnits() == null ? 0 : slot.getPlannedUnits();
            if (plannedUnits <= 0) {
                continue;
            }
            int remaining = slot.getAllocatedUnits();
            int overlapSkipped = 0;
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
                occurrence.setAllocationOwnerTeacherId(slot.getTeacherId());
                occurrence.setAllocationAnchorDate(date);
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
                // 与保留课次冲突时只跳过该日期，不消耗分配额，继续向后寻找可排日期。
                if (hasOverlap(occurrence, activePreserved)) {
                    overlapSkipped++;
                    continue;
                }
                occurrences.add(occurrence);
                remaining -= thisUnits;
            }
            if (remaining > 0 && overlapSkipped > 0 && generationEnd != null) {
                throw new BusinessException("SCHEDULE_CONFLICT",
                        "The schedule end date does not leave enough non-overlapping dates for the allocated hours.");
            }
        }
        occurrences.sort(Comparator.comparing(LessonOccurrenceEntity::getPlannedStartAt));
        return occurrences;
    }

    private void requireCompleteSlotAllocation(RecurringScheduleSlotEntity slot) {
        if (slot.getTeacherId() == null || !StringUtils.hasText(slot.getSubjectName())
                || slot.getAllocatedUnits() == null || slot.getAllocatedUnits() < 0) {
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

    /** 本周期课次才占用本周期课位额度；旧规则复用 ID 的历史课次只通过课包余额校验。 */
    private List<LessonOccurrenceEntity> capGeneratedOccurrencesToRuleAllocation(
            List<LessonOccurrenceEntity> candidates, List<LessonOccurrenceEntity> preservedOccurrences,
            Map<Long, LessonRecordEntity> recordByOccurrenceId,
            List<RecurringScheduleSlotEntity> slots, RecurringScheduleRuleEntity rule) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        LocalDateTime periodStart = PrivateSchedulePeriod.startAt(rule);
        Map<Long, Integer> remainingBySlot = new HashMap<>();
        for (RecurringScheduleSlotEntity slot : slots) {
            remainingBySlot.put(slot.getId(), Math.max(0, slot.getAllocatedUnits()));
        }
        int totalRemaining = remainingBySlot.values().stream().mapToInt(Integer::intValue).sum();
        for (LessonOccurrenceEntity preserved : preservedOccurrences) {
            if (!Objects.equals(preserved.getScheduleRuleId(), rule.getId())
                    || "CANCELLED".equals(preserved.getPlanningStatus())
                    || !PrivateSchedulePeriod.contains(preserved,
                            recordByOccurrenceId.get(preserved.getId()), rule, periodStart)) {
                continue;
            }
            int units = reservedAllocationUnits(preserved, recordByOccurrenceId.get(preserved.getId()));
            if (preserved.getScheduleSlotId() == null
                    || !remainingBySlot.containsKey(preserved.getScheduleSlotId())) {
                continue;
            }
            totalRemaining -= units;
            Long slotId = preserved.getScheduleSlotId();
            remainingBySlot.merge(slotId, -units, Integer::sum);
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
        // 待管理员确认的老师意见不能释放排课额度，尤其不能提前把 Formal Leave 当作已批准。
        if (record != null && "CONFIRMED".equals(record.getConfirmStage())) {
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

    /** 新建或切换排课周期时，防止额度已保存但课次被课包余额或日期范围静默截短。 */
    private void ensureAllocatedHoursWereGenerated(List<RecurringScheduleSlotEntity> slots,
                                                    List<LessonOccurrenceEntity> generatedOccurrences) {
        int allocatedUnits = slots.stream()
                .filter(slot -> "ACTIVE".equals(slot.getSlotStatus()))
                .mapToInt(slot -> Math.max(0, slot.getAllocatedUnits() == null ? 0 : slot.getAllocatedUnits()))
                .sum();
        int generatedUnits = generatedOccurrences.stream()
                .mapToInt(occurrence -> Math.max(0,
                        occurrence.getPlannedUnits() == null ? 0 : occurrence.getPlannedUnits()))
                .sum();
        if (generatedUnits != allocatedUnits) {
            throw new BusinessException("SCHEDULE_ALLOCATION_NOT_FULLY_GENERATED",
                    "Generated lesson hours do not match the allocated hours. Check the package balance, schedule date range, and conflicting lessons; no changes were saved.");
        }
    }

    /** 手动保存排课后，核对本周期每个课位的分配额都由保留课次和新课次覆盖。 */
    private void ensureCurrentPeriodAllocationIsCovered(
            List<RecurringScheduleSlotEntity> slots,
            List<LessonOccurrenceEntity> scheduledOccurrences,
            Map<Long, LessonRecordEntity> recordByOccurrenceId,
            RecurringScheduleRuleEntity rule) {
        LocalDateTime periodStart = PrivateSchedulePeriod.startAt(rule);
        Map<Long, Integer> accountedUnitsBySlot = new HashMap<>();
        for (LessonOccurrenceEntity occurrence : scheduledOccurrences) {
            if (Objects.equals(occurrence.getIsDeleted(), 1)
                    || "CANCELLED".equals(occurrence.getPlanningStatus())
                    || !Objects.equals(rule.getId(), occurrence.getScheduleRuleId())
                    || occurrence.getScheduleSlotId() == null
                    || !PrivateSchedulePeriod.contains(occurrence,
                            recordByOccurrenceId.get(occurrence.getId()), rule, periodStart)) {
                continue;
            }
            accountedUnitsBySlot.merge(occurrence.getScheduleSlotId(),
                    reservedAllocationUnits(occurrence, recordByOccurrenceId.get(occurrence.getId())),
                    Integer::sum);
        }

        for (RecurringScheduleSlotEntity slot : slots) {
            if (!"ACTIVE".equals(slot.getSlotStatus())) continue;
            int allocatedUnits = Math.max(0, safeUnits(slot.getAllocatedUnits()));
            int accountedUnits = accountedUnitsBySlot.getOrDefault(slot.getId(), 0);
            if (accountedUnits != allocatedUnits) {
                throw new BusinessException("SCHEDULE_ALLOCATION_NOT_FULLY_GENERATED",
                        "Generated lesson hours do not match the allocated hours after preserving existing lessons. "
                                + "Resolve pending lessons or adjust the allocation; no changes were saved.");
            }
        }
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
