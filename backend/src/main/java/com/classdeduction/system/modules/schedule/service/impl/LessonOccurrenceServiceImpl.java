package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.schedule.dto.ClassroomOccupancyPageQuery;
import com.classdeduction.system.modules.schedule.dto.LessonOccurrencePageQuery;
import com.classdeduction.system.modules.schedule.dto.OneOnOneDeductionQuery;
import com.classdeduction.system.modules.schedule.dto.PendingClassroomBackfillRequest;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.entity.LessonStatusRuleEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonStatusRuleMapper;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.service.LessonOccurrenceService;
import com.classdeduction.system.modules.schedule.vo.ClassroomOccupancyVO;
import com.classdeduction.system.modules.schedule.vo.LessonOccurrenceVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOneLessonVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOnePackageVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOneScheduleChangeVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOneParticipantVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOneStudentVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOneTeacherVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOneTeacherHoursVO;
import com.classdeduction.system.modules.schedule.vo.PendingClassroomAssignmentVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleConflictDetailVO;
import com.classdeduction.system.modules.schedule.vo.StudentScheduleSlotVO;
import com.classdeduction.system.modules.schedule.util.PrivateLessonAllocation;
import com.classdeduction.system.modules.schedule.util.PrivateSchedulePeriod;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 课次查询服务：负责课次列表与教室占用视图。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LessonOccurrenceServiceImpl implements LessonOccurrenceService {

    private static final int DEFAULT_OCCURRENCE_WINDOW_DAYS = 30;
    private static final int DEFAULT_OCCUPANCY_WINDOW_DAYS = 7;
    private static final int MAX_QUERY_WINDOW_DAYS = 180;

    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final LessonStatusRuleMapper lessonStatusRuleMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;
    private final PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseProductMapper courseProductMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final RecurringScheduleSlotMapper recurringScheduleSlotMapper;
    private final RecurringScheduleRuleMapper recurringScheduleRuleMapper;

    @Override
    public PageResponse<LessonOccurrenceVO> pageOccurrences(LessonOccurrencePageQuery query) {
        return pageOccurrences(query, null);
    }

    @Override
    public PageResponse<LessonOccurrenceVO> pageOccurrences(LessonOccurrencePageQuery query, CurrentUserResponse currentUser) {
        applyScheduleScope(query, currentUser);
        // 课包 ID 是精确范围；未传日期时返回该课包全部历史与未来课次，供临时调课计算整包占用。
        boolean packageWideQuery = query.getPackageId() != null
                && query.getStartAt() == null
                && query.getEndAt() == null;
        Integer maxDays = query.getPackageId() != null ? null : MAX_QUERY_WINDOW_DAYS;
        LocalDateTime[] window = packageWideQuery
                ? null
                : normalizeWindow(query.getStartAt(), query.getEndAt(), DEFAULT_OCCURRENCE_WINDOW_DAYS, maxDays);
        Page<LessonOccurrenceEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        List<Long> matchedPackageIds = findMatchedPackageIds(query.getKeyword());
        List<Long> matchedStudentIds = findMatchedStudentIds(query.getKeyword());

        // Build planning status condition: support comma-separated multi-value IN
        List<String> planningStatuses = null;
        String singleStatus = null;
        if (StringUtils.hasText(query.getPlanningStatus())) {
            if (query.getPlanningStatus().contains(",")) {
                planningStatuses = java.util.Arrays.stream(query.getPlanningStatus().split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
            } else {
                singleStatus = query.getPlanningStatus();
            }
        }

        // 按班课查询时验证班课存在且未删除（@TableLogic 已过滤 is_deleted=1）
        if (query.getClassGroupId() != null) {
            ClassGroupEntity cls = classGroupMapper.selectById(query.getClassGroupId());
            if (cls == null) {
                return PageResponse.empty(query.getPageNum(), query.getPageSize());
            }
        }

        // 当按 studentId 查询时，同时匹配该学生的班课课次（班课 lesson_occurrence 没有 studentId）
        LambdaQueryWrapper<LessonOccurrenceEntity> wrapper = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .and(StringUtils.hasText(query.getKeyword()), nested -> nested
                        .like(LessonOccurrenceEntity::getLessonNo, query.getKeyword())
                        .or(!matchedPackageIds.isEmpty(), inner -> inner.in(LessonOccurrenceEntity::getPackageId, matchedPackageIds))
                        .or(!matchedStudentIds.isEmpty(), inner -> inner.in(LessonOccurrenceEntity::getStudentId, matchedStudentIds)))
                .eq(query.getPackageId() != null, LessonOccurrenceEntity::getPackageId, query.getPackageId())
                .and(query.getStudentId() != null, studentWrapper -> {
                    studentWrapper.eq(LessonOccurrenceEntity::getStudentId, query.getStudentId());
                    List<Long> privateGroupIds = privateLessonGroupMemberMapper.selectList(
                                    new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                                            .eq(PrivateLessonGroupMemberEntity::getStudentId, query.getStudentId())
                                            .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                                            .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0))
                            .stream().map(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId)
                            .filter(Objects::nonNull).toList();
                    if (!privateGroupIds.isEmpty()) {
                        studentWrapper.or().in(LessonOccurrenceEntity::getPrivateLessonGroupId, privateGroupIds);
                    }
                    List<Long> enrolledClassGroupIds = classEnrollmentMapper.selectList(
                                    new LambdaQueryWrapper<ClassEnrollmentEntity>()
                                            .eq(ClassEnrollmentEntity::getStudentId, query.getStudentId())
                                            .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                                            .eq(ClassEnrollmentEntity::getIsDeleted, 0))
                            .stream().map(ClassEnrollmentEntity::getClassId)
                            .filter(Objects::nonNull).toList();
                    if (!enrolledClassGroupIds.isEmpty()) {
                        studentWrapper.or().in(LessonOccurrenceEntity::getClassGroupId, enrolledClassGroupIds);
                    }
                })
                .eq(query.getTeacherId() != null, LessonOccurrenceEntity::getPlannedTeacherId, query.getTeacherId())
                .eq(query.getClassroomId() != null, LessonOccurrenceEntity::getPlannedClassroomId, query.getClassroomId())
                .eq(query.getClassGroupId() != null, LessonOccurrenceEntity::getClassGroupId, query.getClassGroupId())
                .isNull(LessonOccurrenceEntity::getCourseHolidayId)
                .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt);

        if (window != null) {
            wrapper.ge(LessonOccurrenceEntity::getPlannedStartAt, window[0])
                    .lt(LessonOccurrenceEntity::getPlannedStartAt, window[1]);
        }

        if (singleStatus != null) {
            wrapper.eq(LessonOccurrenceEntity::getPlanningStatus, singleStatus);
        } else if (planningStatuses != null && !planningStatuses.isEmpty()) {
            wrapper.in(LessonOccurrenceEntity::getPlanningStatus, planningStatuses);
        }

        if (query.getStudentId() != null) {
            List<LessonOccurrenceEntity> visibleRecords = filterStudentClassOccurrences(
                    lessonOccurrenceMapper.selectList(wrapper), query.getStudentId());
            long fromIndex = Math.max((query.getPageNum() - 1) * query.getPageSize(), 0);
            if (fromIndex >= visibleRecords.size()) {
                return PageResponse.empty(query.getPageNum(), query.getPageSize());
            }
            long toIndex = Math.min(fromIndex + query.getPageSize(), visibleRecords.size());
            return PageResponse.of(
                    toOccurrenceVOs(visibleRecords.subList((int) fromIndex, (int) toIndex)),
                    visibleRecords.size(),
                    query.getPageNum(),
                    query.getPageSize());
        }

        IPage<LessonOccurrenceEntity> result = lessonOccurrenceMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }
        return PageResponse.of(toOccurrenceVOs(result.getRecords()), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public LessonOccurrenceVO getOccurrence(Long id) {
        return getOccurrence(id, null);
    }

    @Override
    public LessonOccurrenceVO getOccurrence(Long id, CurrentUserResponse currentUser) {
        LessonOccurrenceEntity entity = lessonOccurrenceMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("NOT_FOUND", "Lesson occurrence not found.");
        }
        validateScheduleDetailScope(entity, currentUser);
        List<LessonOccurrenceVO> vos = toOccurrenceVOs(List.of(entity));
        return vos.get(0);
    }

    @Override
    public PageResponse<ClassroomOccupancyVO> pageClassroomOccupancy(ClassroomOccupancyPageQuery query) {
        LocalDateTime[] window = normalizeWindow(query.getStartAt(), query.getEndAt(), DEFAULT_OCCUPANCY_WINDOW_DAYS);
        Page<LessonOccurrenceEntity> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<LessonOccurrenceEntity> wrapper = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .isNotNull(LessonOccurrenceEntity::getPlannedClassroomId)
                .eq(query.getClassroomId() != null, LessonOccurrenceEntity::getPlannedClassroomId, query.getClassroomId())
                .eq(query.getTeacherId() != null, LessonOccurrenceEntity::getPlannedTeacherId, query.getTeacherId())
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ge(LessonOccurrenceEntity::getPlannedStartAt, window[0])
                .lt(LessonOccurrenceEntity::getPlannedStartAt, window[1])
                .orderByAsc(LessonOccurrenceEntity::getPlannedClassroomId)
                .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt);
        IPage<LessonOccurrenceEntity> result = lessonOccurrenceMapper.selectPage(page, wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResponse.empty(query.getPageNum(), query.getPageSize());
        }

        Map<Long, CoursePackageEntity> packageMap = listPackagesByIds(result.getRecords().stream().map(LessonOccurrenceEntity::getPackageId).collect(Collectors.toSet()));
        Map<Long, StudentEntity> studentMap = listStudentsByIds(result.getRecords().stream().map(LessonOccurrenceEntity::getStudentId).collect(Collectors.toSet()));
        Map<Long, TeacherEntity> teacherMap = listTeachersByIds(result.getRecords().stream().map(LessonOccurrenceEntity::getPlannedTeacherId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, ClassroomEntity> classroomMap = listClassroomsByIds(result.getRecords().stream().map(LessonOccurrenceEntity::getPlannedClassroomId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, ClassGroupEntity> classGroupMap = listClassGroupsByIds(result.getRecords().stream().map(LessonOccurrenceEntity::getClassGroupId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, RecurringScheduleSlotEntity> recurringSlotMap = loadRecurringSlotMap(result.getRecords());
        Map<Long, ClassGroupScheduleSlotEntity> classSlotMap = loadClassSlotMap(result.getRecords());

        List<ClassroomOccupancyVO> records = result.getRecords().stream()
                .map(item -> {
                    CoursePackageEntity coursePackage = packageMap.get(item.getPackageId());
                    StudentEntity student = studentMap.get(item.getStudentId());
                    TeacherEntity teacher = teacherMap.get(item.getPlannedTeacherId());
                    ClassroomEntity classroom = classroomMap.get(item.getPlannedClassroomId());
                    ClassGroupEntity classGroup = classGroupMap.get(item.getClassGroupId());
                    return ClassroomOccupancyVO.builder()
                            .occurrenceId(item.getId())
                            .lessonNo(item.getLessonNo())
                            .classroomId(item.getPlannedClassroomId())
                            .classroomName(classroom == null ? null : classroom.getRoomName())
                            .teacherId(item.getPlannedTeacherId())
                            .teacherName(teacher == null ? null : teacher.getTeacherName())
                            .packageId(item.getPackageId())
                            .packageNo(coursePackage == null ? null : coursePackage.getPackageNo())
                            .packageName(coursePackage == null ? null : coursePackage.getPackageName())
                            .studentId(item.getStudentId())
                            .studentName(student == null ? null : student.getStudentName())
                            .plannedStartAt(item.getPlannedStartAt())
                            .plannedEndAt(item.getPlannedEndAt())
                            .plannedUnits(item.getPlannedUnits())
                            .planningStatus(item.getPlanningStatus())
                            .classGroupId(item.getClassGroupId())
                            .className(classGroup == null ? null : classGroup.getClassName())
                            .subjectName(resolveOccurrenceSubjectName(item, recurringSlotMap, classSlotMap))
                            .build();
                })
                .toList();
        return PageResponse.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<PendingClassroomAssignmentVO> listPendingClassroomAssignments() {
        LocalDateTime now = AppTime.nowLocalDateTime();
        LocalDateTime windowEnd = now.plusDays(MAX_QUERY_WINDOW_DAYS);

        List<ClassGroupEntity> pendingClasses = classGroupMapper.selectList(
                new LambdaQueryWrapper<ClassGroupEntity>()
                        .eq(ClassGroupEntity::getStatus, "ACTIVE")
                        .isNull(ClassGroupEntity::getClassroomId)
                        .orderByAsc(ClassGroupEntity::getClassName));
        Set<Long> pendingClassIds = pendingClasses.stream()
                .map(ClassGroupEntity::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, LessonOccurrenceEntity> nextOccurrenceByClass = loadNextPendingClassOccurrence(pendingClassIds, now, windowEnd);
        List<LessonOccurrenceEntity> pendingOccurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .isNull(LessonOccurrenceEntity::getPlannedClassroomId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .ge(LessonOccurrenceEntity::getPlannedEndAt, now)
                        .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                        .and(!pendingClassIds.isEmpty(), nested -> nested
                                .isNull(LessonOccurrenceEntity::getClassGroupId)
                                .or()
                                .notIn(LessonOccurrenceEntity::getClassGroupId, pendingClassIds))
                        .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));

        List<LessonOccurrenceEntity> occurrenceContext = new ArrayList<>(nextOccurrenceByClass.values());
        occurrenceContext.addAll(pendingOccurrences);
        PendingContext context = loadPendingContext(occurrenceContext, pendingClasses);

        List<PendingClassroomAssignmentVO> result = new ArrayList<>();
        for (ClassGroupEntity classGroup : pendingClasses) {
            result.add(toPendingClassGroupVO(classGroup, nextOccurrenceByClass.get(classGroup.getId()), context));
        }
        for (LessonOccurrenceEntity occurrence : pendingOccurrences) {
            result.add(toPendingOccurrenceVO(occurrence, context));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public PendingClassroomAssignmentVO backfillPendingClassroom(PendingClassroomBackfillRequest request) {
        String targetType = request.getTargetType() == null ? "" : request.getTargetType().trim().toUpperCase(Locale.ROOT);
        if ("CLASS_GROUP".equals(targetType)) {
            // 班级补录和课表编辑统一先锁班级，避免出现“教室→班级”与“班级→教室”死锁。
            ClassGroupEntity classGroup = requireActiveClassGroupForUpdate(request.getTargetId());
            ClassroomEntity classroom = requireActiveClassroomForUpdate(request.getClassroomId());
            return backfillClassGroupClassroom(classGroup, classroom);
        }
        if ("OCCURRENCE".equals(targetType)) {
            return backfillOccurrenceClassroom(request.getTargetId(), request.getClassroomId());
        }
        throw new BusinessException("INVALID_TARGET_TYPE", "Target type must be CLASS_GROUP or OCCURRENCE.");
    }

    private PendingClassroomAssignmentVO backfillClassGroupClassroom(ClassGroupEntity classGroup, ClassroomEntity classroom) {
        if (classGroup == null || !"ACTIVE".equals(classGroup.getStatus())) {
            throw new BusinessException("CLASS_GROUP_NOT_FOUND", "Active class group not found.");
        }
        Long classGroupId = classGroup.getId();

        LocalDateTime now = AppTime.nowLocalDateTime();
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classGroupId)
                        .isNull(LessonOccurrenceEntity::getPlannedClassroomId)
                        .and(wrapper -> wrapper.ne(LessonOccurrenceEntity::getDeliveryMode, "ONLINE")
                                .or().isNull(LessonOccurrenceEntity::getDeliveryMode))
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .ge(LessonOccurrenceEntity::getPlannedEndAt, now)
                        .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));
        Set<Long> occurrenceIds = occurrences.stream()
                .map(LessonOccurrenceEntity::getId)
                .collect(Collectors.toSet());
        for (LessonOccurrenceEntity occurrence : occurrences) {
            occurrence.setPlannedClassroomId(classroom.getId());
            occurrence.setDeliveryMode("IN_PERSON");
        }
        ensureNoInternalClassroomConflicts(occurrences);
        ensureNoClassroomBackfillConflicts(occurrences, occurrenceIds);

        classGroup.setClassroomId(classroom.getId());
        classGroupMapper.updateById(classGroup);
        for (LessonOccurrenceEntity occurrence : occurrences) {
            occurrence.setConflictChecked(1);
            requireOccurrenceUpdate(lessonOccurrenceMapper.updateById(occurrence));
        }

        LessonOccurrenceEntity nextOccurrence = occurrences.isEmpty() ? null : occurrences.get(0);
        PendingContext context = loadPendingContext(nextOccurrence == null ? List.of() : List.of(nextOccurrence), List.of(classGroup));
        return toPendingClassGroupVO(classGroup, nextOccurrence, context);
    }

    private PendingClassroomAssignmentVO backfillOccurrenceClassroom(Long occurrenceId, Long classroomId) {
        LessonOccurrenceEntity initial = lessonOccurrenceMapper.selectById(occurrenceId);
        if (initial == null) {
            throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "Lesson occurrence not found.");
        }
        boolean groupClassOccurrence = initial.getClassGroupId() != null;
        if (groupClassOccurrence) {
            // 班课 occurrence 修改与班级课表/假期重算共用班级行锁；READ_COMMITTED 确保锁后校验读取最新状态。
            requireActiveClassGroupForUpdate(initial.getClassGroupId());
        }
        // 单独课次没有班级行，继续按教室→课次获取锁；班课则按班级→课次→教室。
        ClassroomEntity classroom = groupClassOccurrence ? null : requireActiveClassroomForUpdate(classroomId);
        LessonOccurrenceEntity occurrence = lessonOccurrenceMapper.selectByIdForUpdate(occurrenceId);
        if (occurrence == null || !Objects.equals(occurrence.getClassGroupId(), initial.getClassGroupId())) {
            throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "Lesson occurrence not found.");
        }
        if ("CANCELLED".equals(occurrence.getPlanningStatus())) {
            throw new BusinessException("LESSON_OCCURRENCE_CANCELLED", "Cancelled lesson occurrences cannot be backfilled.");
        }
        if ("ONLINE".equals(occurrence.getDeliveryMode())) {
            throw new BusinessException("ONLINE_CLASSROOM_NOT_REQUIRED", "Online lessons do not need a classroom assignment.");
        }
        if (occurrence.getPlannedClassroomId() != null) {
            throw new BusinessException("CLASSROOM_ALREADY_ASSIGNED", "This lesson occurrence already has a classroom.");
        }

        if (groupClassOccurrence) {
            classroom = requireActiveClassroomForUpdate(classroomId);
        }
        occurrence.setPlannedClassroomId(classroom.getId());
        occurrence.setDeliveryMode("IN_PERSON");
        ensureNoClassroomBackfillConflicts(List.of(occurrence), Set.of(occurrence.getId()));
        occurrence.setConflictChecked(1);
        requireOccurrenceUpdate(lessonOccurrenceMapper.updateById(occurrence));

        PendingContext context = loadPendingContext(List.of(occurrence), List.of());
        return toPendingOccurrenceVO(occurrence, context);
    }

    private ClassGroupEntity requireActiveClassGroupForUpdate(Long classGroupId) {
        ClassGroupEntity classGroup = classGroupMapper.selectByIdForUpdate(classGroupId);
        if (classGroup == null || !"ACTIVE".equals(classGroup.getStatus())) {
            throw new BusinessException("CLASS_GROUP_NOT_FOUND", "Active class group not found.");
        }
        return classGroup;
    }

    private ClassroomEntity requireActiveClassroomForUpdate(Long classroomId) {
        ClassroomEntity classroom = classroomMapper.selectByIdForUpdate(classroomId);
        if (classroom == null || !"ACTIVE".equalsIgnoreCase(classroom.getStatus())) {
            throw new BusinessException("CLASSROOM_NOT_AVAILABLE", "The selected classroom does not exist or is not active.");
        }
        return classroom;
    }

    private void requireOccurrenceUpdate(int updated) {
        if (updated != 1) {
            throw new BusinessException("OCCURRENCE_CONCURRENT_UPDATE",
                    "The lesson occurrence was updated by another request. Please retry.");
        }
    }

    private Map<Long, LessonOccurrenceEntity> loadNextPendingClassOccurrence(Set<Long> classGroupIds,
                                                                             LocalDateTime now,
                                                                             LocalDateTime windowEnd) {
        if (classGroupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .in(LessonOccurrenceEntity::getClassGroupId, classGroupIds)
                        .isNull(LessonOccurrenceEntity::getPlannedClassroomId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .ge(LessonOccurrenceEntity::getPlannedEndAt, now)
                        .lt(LessonOccurrenceEntity::getPlannedStartAt, windowEnd)
                        .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));
        Map<Long, LessonOccurrenceEntity> result = new HashMap<>();
        for (LessonOccurrenceEntity occurrence : occurrences) {
            result.putIfAbsent(occurrence.getClassGroupId(), occurrence);
        }
        return result;
    }

    private PendingContext loadPendingContext(List<LessonOccurrenceEntity> occurrences, List<ClassGroupEntity> classGroups) {
        Set<Long> packageIds = occurrences.stream()
                .map(LessonOccurrenceEntity::getPackageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> studentIds = occurrences.stream()
                .map(LessonOccurrenceEntity::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> teacherIds = new LinkedHashSet<>();
        occurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedTeacherId)
                .filter(Objects::nonNull)
                .forEach(teacherIds::add);
        classGroups.stream()
                .map(ClassGroupEntity::getTeacherId)
                .filter(Objects::nonNull)
                .forEach(teacherIds::add);
        Set<Long> classGroupIds = new LinkedHashSet<>();
        occurrences.stream()
                .map(LessonOccurrenceEntity::getClassGroupId)
                .filter(Objects::nonNull)
                .forEach(classGroupIds::add);
        Map<Long, ClassGroupEntity> classGroupMap = classGroups.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(ClassGroupEntity::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        classGroups.stream()
                .map(ClassGroupEntity::getId)
                .filter(Objects::nonNull)
                .forEach(classGroupIds::remove);
        Map<Long, CoursePackageEntity> packageMap = listPackagesByIds(packageIds);
        classGroupMap.putAll(listClassGroupsByIds(classGroupIds));
        Set<Long> productIds = collectCourseProductIds(packageMap.values(), classGroupMap.values());
        return new PendingContext(
                packageMap,
                listStudentsByIds(studentIds),
                listTeachersByIds(teacherIds),
                classGroupMap,
                listCourseProductsByIds(productIds)
        );
    }

    private PendingClassroomAssignmentVO toPendingClassGroupVO(ClassGroupEntity classGroup,
                                                               LessonOccurrenceEntity nextOccurrence,
                                                               PendingContext context) {
        TeacherEntity teacher = classGroup.getTeacherId() == null ? null : context.teacherMap().get(classGroup.getTeacherId());
        CourseProductEntity product = classGroup.getProductId() == null ? null : context.courseProductMap().get(classGroup.getProductId());
        return PendingClassroomAssignmentVO.builder()
                .targetType("CLASS_GROUP")
                .targetId(classGroup.getId())
                .occurrenceId(nextOccurrence == null ? null : nextOccurrence.getId())
                .lessonNo(nextOccurrence == null ? null : nextOccurrence.getLessonNo())
                .classGroupId(classGroup.getId())
                .productId(classGroup.getProductId())
                .productName(product == null ? null : product.getProductName())
                .className(classGroup.getClassName())
                .gradeBand(classGroup.getGradeBand())
                .actualClass(resolveActualClassLabel(classGroup))
                .teacherId(classGroup.getTeacherId())
                .teacherName(teacher == null ? null : teacher.getTeacherName())
                .plannedStartAt(nextOccurrence == null ? null : nextOccurrence.getPlannedStartAt())
                .plannedEndAt(nextOccurrence == null ? null : nextOccurrence.getPlannedEndAt())
                .plannedUnits(nextOccurrence == null ? null : nextOccurrence.getPlannedUnits())
                .scheduleDesc(classGroup.getScheduleDesc())
                .build();
    }

    private PendingClassroomAssignmentVO toPendingOccurrenceVO(LessonOccurrenceEntity occurrence, PendingContext context) {
        CoursePackageEntity coursePackage = occurrence.getPackageId() == null ? null : context.packageMap().get(occurrence.getPackageId());
        StudentEntity student = occurrence.getStudentId() == null ? null : context.studentMap().get(occurrence.getStudentId());
        TeacherEntity teacher = occurrence.getPlannedTeacherId() == null ? null : context.teacherMap().get(occurrence.getPlannedTeacherId());
        ClassGroupEntity classGroup = occurrence.getClassGroupId() == null ? null : context.classGroupMap().get(occurrence.getClassGroupId());
        CourseProductEntity product = resolvePendingProduct(coursePackage, classGroup, context.courseProductMap());
        return PendingClassroomAssignmentVO.builder()
                .targetType("OCCURRENCE")
                .targetId(occurrence.getId())
                .occurrenceId(occurrence.getId())
                .lessonNo(occurrence.getLessonNo())
                .classGroupId(occurrence.getClassGroupId())
                .productId(product == null ? null : product.getId())
                .productName(product == null ? null : product.getProductName())
                .className(classGroup == null ? null : classGroup.getClassName())
                .gradeBand(classGroup == null ? null : classGroup.getGradeBand())
                .actualClass(resolveActualClassLabel(classGroup))
                .packageId(occurrence.getPackageId())
                .packageName(coursePackage == null ? null : coursePackage.getPackageName())
                .studentId(occurrence.getStudentId())
                .studentName(student == null ? null : student.getStudentName())
                .teacherId(occurrence.getPlannedTeacherId())
                .teacherName(teacher == null ? null : teacher.getTeacherName())
                .plannedStartAt(occurrence.getPlannedStartAt())
                .plannedEndAt(occurrence.getPlannedEndAt())
                .plannedUnits(occurrence.getPlannedUnits())
                .scheduleDesc(classGroup == null ? null : classGroup.getScheduleDesc())
                .build();
    }

    private CourseProductEntity resolvePendingProduct(CoursePackageEntity coursePackage,
                                                      ClassGroupEntity classGroup,
                                                      Map<Long, CourseProductEntity> courseProductMap) {
        Long productId = classGroup != null && classGroup.getProductId() != null
                ? classGroup.getProductId()
                : coursePackage == null ? null : coursePackage.getCourseProductId();
        return productId == null ? null : courseProductMap.get(productId);
    }

    private String resolveActualClassLabel(ClassGroupEntity classGroup) {
        if (classGroup == null) {
            return null;
        }
        return StringUtils.hasText(classGroup.getActualClass())
                ? classGroup.getActualClass()
                : classGroup.getGradeBand();
    }

    private void ensureNoInternalClassroomConflicts(List<LessonOccurrenceEntity> occurrences) {
        for (int i = 0; i < occurrences.size(); i++) {
            LessonOccurrenceEntity left = occurrences.get(i);
            for (int j = i + 1; j < occurrences.size(); j++) {
                LessonOccurrenceEntity right = occurrences.get(j);
                if (overlaps(left, right)) {
                    throw new BusinessException("SCHEDULE_CONFLICT",
                            "Pending lessons overlap and cannot be assigned to the same classroom together.",
                            List.of(toClassroomConflictDetail(left, right)));
                }
            }
        }
    }

    private void ensureNoClassroomBackfillConflicts(List<LessonOccurrenceEntity> occurrences, Set<Long> ignoredOccurrenceIds) {
        for (LessonOccurrenceEntity occurrence : occurrences) {
            List<LessonOccurrenceEntity> conflicts = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                    .eq(LessonOccurrenceEntity::getPlannedClassroomId, occurrence.getPlannedClassroomId())
                    .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                    .notIn(ignoredOccurrenceIds != null && !ignoredOccurrenceIds.isEmpty(), LessonOccurrenceEntity::getId, ignoredOccurrenceIds)
                    .lt(LessonOccurrenceEntity::getPlannedStartAt, occurrence.getPlannedEndAt())
                    .gt(LessonOccurrenceEntity::getPlannedEndAt, occurrence.getPlannedStartAt()));
            if (conflicts != null && !conflicts.isEmpty()) {
                throw new BusinessException("SCHEDULE_CONFLICT", "Classroom conflict detected for the selected room.",
                        List.of(toClassroomConflictDetail(occurrence, conflicts.get(0))));
            }
        }
    }

    private ScheduleConflictDetailVO toClassroomConflictDetail(LessonOccurrenceEntity candidate,
                                                               LessonOccurrenceEntity existing) {
        Long classroomId = candidate.getPlannedClassroomId();
        ClassroomEntity classroom = classroomId == null ? null : classroomMapper.selectById(classroomId);
        return ScheduleConflictDetailVO.builder()
                .resourceType("CLASSROOM")
                .resourceId(classroomId)
                .resourceName(classroom == null ? null : classroom.getRoomName())
                .requestedStartAt(candidate.getPlannedStartAt())
                .requestedEndAt(candidate.getPlannedEndAt())
                .conflictStartAt(existing.getPlannedStartAt())
                .conflictEndAt(existing.getPlannedEndAt())
                .existingOccurrenceId(existing.getId())
                .existingLessonNo(existing.getLessonNo())
                .build();
    }

    private boolean overlaps(LessonOccurrenceEntity left, LessonOccurrenceEntity right) {
        return left.getPlannedStartAt().isBefore(right.getPlannedEndAt())
                && left.getPlannedEndAt().isAfter(right.getPlannedStartAt());
    }

    private record PendingContext(
            Map<Long, CoursePackageEntity> packageMap,
            Map<Long, StudentEntity> studentMap,
            Map<Long, TeacherEntity> teacherMap,
            Map<Long, ClassGroupEntity> classGroupMap,
            Map<Long, CourseProductEntity> courseProductMap
    ) {
    }

    @Override
    public List<StudentScheduleSlotVO> getActiveStudentSchedule(Long studentId) {
        LocalDateTime now = AppTime.nowLocalDateTime();
        LocalDateTime futureWindowEnd = now.plusMonths(6);

        LambdaQueryWrapper<LessonOccurrenceEntity> wrapper = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .and(scope -> scope.eq(LessonOccurrenceEntity::getStudentId, studentId)
                        .or(privateGroupMemberMapperHasGroup(studentId),
                                group -> group.in(LessonOccurrenceEntity::getPrivateLessonGroupId,
                                        privateGroupIdsForStudent(studentId))))
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .ge(LessonOccurrenceEntity::getPlannedStartAt, now)
                .le(LessonOccurrenceEntity::getPlannedStartAt, futureWindowEnd)
                .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt);

        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(wrapper);
        if (occurrences.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, CoursePackageEntity> packageMap = listPackagesByIds(
                occurrences.stream().map(LessonOccurrenceEntity::getPackageId).collect(Collectors.toSet()));

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        return occurrences.stream()
                .map(item -> {
                    CoursePackageEntity cp = packageMap.get(item.getPackageId());
                    DayOfWeek dow = item.getPlannedStartAt().getDayOfWeek();
                    int weekday = dow.getValue(); // 1=Mon..7=Sun
                    return StudentScheduleSlotVO.builder()
                            .occurrenceId(item.getId())
                            .weekday(weekday)
                            .startTime(item.getPlannedStartAt().format(timeFmt))
                            .endTime(item.getPlannedEndAt().format(timeFmt))
                            .plannedUnits(item.getPlannedUnits())
                            .courseLabel(cp == null ? item.getLessonNo() : cp.getPackageName())
                            .packageId(item.getPackageId())
                            .planningStatus(item.getPlanningStatus())
                            .build();
                })
                .toList();
    }

    @Override
    public List<OneOnOneTeacherVO> listOneOnOneDeductions(OneOnOneDeductionQuery query, CurrentUserResponse currentUser) {
        OneOnOneDeductionQuery safeQuery = query == null ? new OneOnOneDeductionQuery() : query;
        Long teacherId = resolveOneOnOneTeacherFilter(safeQuery, currentUser);
        Long studentId = resolveOneOnOneStudentFilter(safeQuery, currentUser);
        if (isTeacherUser(currentUser) && teacherId == null) {
            return Collections.emptyList();
        }
        if (isStudentUser(currentUser) && studentId == null) {
            return Collections.emptyList();
        }

        List<Long> teacherPackageIds = teacherId == null ? Collections.emptyList() : findOneOnOnePackageIdsByTeacher(teacherId);
        if (teacherId != null && teacherPackageIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> matchedStudentIds = findMatchedStudentIds(safeQuery.getKeyword());
        List<Long> matchedGroupIds = matchedStudentIds.isEmpty() ? Collections.emptyList()
                : privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .in(PrivateLessonGroupMemberEntity::getStudentId, matchedStudentIds)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0))
                .stream().map(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId)
                .filter(Objects::nonNull).distinct().toList();
        List<Long> matchedPrimaryPackageIds = matchedGroupIds.isEmpty() ? Collections.emptyList()
                : privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .in(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, matchedGroupIds)
                        .eq(PrivateLessonGroupMemberEntity::getParticipantIndex, 1)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0))
                .stream().map(PrivateLessonGroupMemberEntity::getPackageId)
                .filter(Objects::nonNull).distinct().toList();

        LambdaQueryWrapper<CoursePackageEntity> wrapper = new LambdaQueryWrapper<CoursePackageEntity>()
                .and(teachingMode -> teachingMode
                        .eq(CoursePackageEntity::getTeachingMode, "ONE_ON_ONE")
                        .or()
                        .isNull(CoursePackageEntity::getTeachingMode))
                // 1 对 1 消课页同时承担待处理和历史查询；已完成/暂停/关闭课包只要有课次记录也必须可追溯。
                .in(CoursePackageEntity::getPackageStatus, "ACTIVE", "PAUSED", "COMPLETED", "CLOSED")
                .in(teacherId != null, CoursePackageEntity::getId, teacherPackageIds)
                .eq(studentId != null, CoursePackageEntity::getStudentId, studentId)
                .eq(safeQuery.getPackageId() != null, CoursePackageEntity::getId, safeQuery.getPackageId())
                .and(StringUtils.hasText(safeQuery.getKeyword()), keyword -> keyword
                        .like(CoursePackageEntity::getPackageNo, safeQuery.getKeyword())
                        .or()
                        .like(CoursePackageEntity::getPackageName, safeQuery.getKeyword())
                        .or(!matchedStudentIds.isEmpty(), nested -> nested.in(CoursePackageEntity::getStudentId, matchedStudentIds))
                        .or(!matchedPrimaryPackageIds.isEmpty(), nested -> nested.in(CoursePackageEntity::getId, matchedPrimaryPackageIds)))
                .orderByDesc(CoursePackageEntity::getCreatedAt);
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(wrapper);
        if (packages.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, CourseProductEntity> courseProductMap = listCourseProductsByIds(packages.stream()
                .map(CoursePackageEntity::getCourseProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        packages = packages.stream()
                .filter(coursePackage -> isOneOnOnePackage(coursePackage, courseProductMap.get(coursePackage.getCourseProductId())))
                .toList();
        if (packages.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> packageIds = packages.stream().map(CoursePackageEntity::getId).toList();
        Map<Long, List<PrivateLessonGroupMemberEntity>> groupMembersByPackage = loadPrivateGroupMembersByPackage(packageIds);
        // 管理员视图按私教课组展示一次；学生视图仍保留当前学生自己的课包入口。
        if (studentId == null) {
            Map<Long, List<PrivateLessonGroupMemberEntity>> groupMembersForFilter = groupMembersByPackage;
            packages = packages.stream()
                    .filter(coursePackage -> isPrimaryPrivateLessonPackage(coursePackage, groupMembersForFilter))
                    .toList();
            packageIds = packages.stream().map(CoursePackageEntity::getId).toList();
            groupMembersByPackage = loadPrivateGroupMembersByPackage(packageIds);
        }
        Map<Long, List<LessonOccurrenceEntity>> occurrenceMap = loadOneOnOneOccurrenceMap(packageIds, safeQuery.getStartAt(), safeQuery.getEndAt());
        // 课包余额摘要始终覆盖完整课表；日期窗口只限制下方课次列表。
        Map<Long, List<LessonOccurrenceEntity>> allocationOccurrenceMap = safeQuery.getStartAt() == null
                && safeQuery.getEndAt() == null ? occurrenceMap : loadOneOnOneOccurrenceMap(packageIds, null, null);
        Set<Long> packagesWithOccurrences = occurrenceMap.keySet();
        packages = packages.stream()
                .filter(coursePackage -> "ACTIVE".equals(coursePackage.getPackageStatus())
                        || packagesWithOccurrences.contains(coursePackage.getId()))
                .toList();
        if (packages.isEmpty()) {
            return Collections.emptyList();
        }
        packageIds = packages.stream().map(CoursePackageEntity::getId).toList();
        Map<Long, List<PrivateLessonGroupMemberEntity>> groupMembersForView = loadPrivateGroupMembersByPackage(packageIds);
        Map<Long, List<OneOnOneScheduleChangeVO>> scheduleChangesByPackage =
                loadOneOnOneScheduleChanges(packageIds, groupMembersForView);
        List<CoursePackageEntity> packagesForParticipantContext = new ArrayList<>(packages);
        Set<Long> relatedPackageIds = groupMembersForView.values().stream()
                .flatMap(List::stream)
                .map(PrivateLessonGroupMemberEntity::getPackageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> loadedPackageIds = packagesForParticipantContext.stream()
                .map(CoursePackageEntity::getId)
                .collect(Collectors.toSet());
        relatedPackageIds.removeAll(loadedPackageIds);
        if (!relatedPackageIds.isEmpty()) {
            packagesForParticipantContext.addAll(coursePackageMapper.selectBatchIds(relatedPackageIds));
        }
        Set<Long> occurrenceIds = allocationOccurrenceMap.values().stream()
                .flatMap(List::stream)
                .map(LessonOccurrenceEntity::getId)
                .collect(Collectors.toSet());
        LatestRecordIndex recordIndex = loadLatestRecordIndex(occurrenceIds);
        Collection<LessonRecordEntity> allRecords = recordIndex.allRecords();
        Map<Long, Integer> scheduledUnitsByPackage = allocationOccurrenceMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> PrivateLessonAllocation.reservedUnits(entry.getValue(),
                                recordIndex.forPackage(entry.getKey(), entry.getValue()))));
        Map<Long, Integer> unresolvedPastByPackage = allocationOccurrenceMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> PrivateLessonAllocation.unresolvedPastUnits(entry.getValue(),
                                recordIndex.forPackage(entry.getKey(), entry.getValue()),
                                AppTime.nowLocalDateTime())));
        PackageHoursContext hoursContext = loadPackageHoursContext(
                packageIds, scheduledUnitsByPackage, unresolvedPastByPackage,
                allocationOccurrenceMap, recordIndex);

        Set<Long> studentIds = new LinkedHashSet<>(packages.stream()
                .map(CoursePackageEntity::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        groupMembersForView.values().stream().flatMap(List::stream)
                .map(PrivateLessonGroupMemberEntity::getStudentId)
                .filter(Objects::nonNull)
                .forEach(studentIds::add);
        Map<Long, StudentEntity> studentMap = listStudentsByIds(studentIds);
        Map<Long, TeacherEntity> teacherMap = loadOneOnOneTeachers(occurrenceMap, allRecords, hoursContext);
        Map<Long, ClassroomEntity> classroomMap = loadOneOnOneClassrooms(occurrenceMap, allRecords);
        Map<Long, EnrollmentOrderItemEntity> orderItemMap = listEnrollmentOrderItemsByIds(packages.stream()
                .map(CoursePackageEntity::getEnrollmentOrderItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<String, String> statusNameMap = loadStatusNames(allRecords);

        LinkedHashMap<Long, LinkedHashMap<Long, List<CoursePackageEntity>>> grouped = new LinkedHashMap<>();
        for (CoursePackageEntity coursePackage : packages) {
            for (Long groupTeacherId : resolveOneOnOneGroupTeacherIds(
                    coursePackage, occurrenceMap, teacherId, hoursContext)) {
                grouped.computeIfAbsent(groupTeacherId, key -> new LinkedHashMap<>())
                        .computeIfAbsent(coursePackage.getStudentId(), key -> new ArrayList<>())
                        .add(coursePackage);
            }
        }

        List<CoursePackageEntity> resolvedPackages = packagesForParticipantContext;
        return grouped.entrySet().stream()
                .map(teacherEntry -> toOneOnOneTeacherVO(teacherEntry.getKey(), teacherEntry.getValue(),
                        studentMap, teacherMap, classroomMap, courseProductMap, orderItemMap,
                        occurrenceMap, recordIndex, hoursContext, statusNameMap,
                        groupMembersForView, resolvedPackages, scheduleChangesByPackage))
                .toList();
    }

    /** 一次读取排课版本，按课包与共享私教组分发，避免逐个学生查询。 */
    private Map<Long, List<OneOnOneScheduleChangeVO>> loadOneOnOneScheduleChanges(
            List<Long> packageIds,
            Map<Long, List<PrivateLessonGroupMemberEntity>> groupMembersByPackage) {
        Set<Long> packageIdSet = new HashSet<>(packageIds);
        Map<Long, Set<Long>> packageIdsByGroup = new HashMap<>();
        groupMembersByPackage.forEach((packageId, members) -> members.stream()
                .map(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId)
                .filter(Objects::nonNull)
                .forEach(groupId -> packageIdsByGroup.computeIfAbsent(groupId, ignored -> new HashSet<>())
                        .add(packageId)));
        List<RecurringScheduleRuleEntity> rules = recurringScheduleRuleMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                        .and(wrapper -> wrapper.in(RecurringScheduleRuleEntity::getPackageId, packageIds)
                                .or(!packageIdsByGroup.isEmpty(), group -> group.in(
                                        RecurringScheduleRuleEntity::getPrivateLessonGroupId,
                                        packageIdsByGroup.keySet())))
                        .eq(RecurringScheduleRuleEntity::getIsDeleted, 0)
                        .orderByDesc(RecurringScheduleRuleEntity::getCreatedAt)
                        .orderByDesc(RecurringScheduleRuleEntity::getId));
        Map<Long, RecurringScheduleRuleEntity> currentRulesByPackage = new HashMap<>();
        for (RecurringScheduleRuleEntity rule : rules) {
            if (!"ACTIVE".equals(rule.getRuleStatus())) {
                continue;
            }
            for (Long affectedPackageId : scheduleChangeAffectedPackageIds(rule, packageIdSet,
                    packageIdsByGroup)) {
                currentRulesByPackage.putIfAbsent(affectedPackageId, rule);
            }
        }
        Set<Long> currentRuleIds = currentRulesByPackage.values().stream()
                .map(RecurringScheduleRuleEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, LocalDateTime> currentSlotStartsByRule = new HashMap<>();
        if (!currentRuleIds.isEmpty()) {
            List<RecurringScheduleSlotEntity> currentSlots = recurringScheduleSlotMapper.selectList(
                    new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                            .in(RecurringScheduleSlotEntity::getRuleId, currentRuleIds)
                            .eq(RecurringScheduleSlotEntity::getSlotStatus, "ACTIVE")
                            .eq(RecurringScheduleSlotEntity::getIsDeleted, 0));
            for (RecurringScheduleSlotEntity slot : currentSlots) {
                if (slot.getCreatedAt() != null) {
                    currentSlotStartsByRule.merge(slot.getRuleId(), slot.getCreatedAt(),
                            (previous, createdAt) -> previous.isAfter(createdAt) ? previous : createdAt);
                }
            }
        }
        Map<Long, Map<Long, OneOnOneScheduleChangeVO>> changesByPackage = new HashMap<>();
        for (RecurringScheduleRuleEntity rule : rules) {
            if (rule.getId() == null) {
                continue;
            }
            if (rule.getSupersedesRuleId() != null) {
                OneOnOneScheduleChangeVO change = new OneOnOneScheduleChangeVO(
                        rule.getId(), rule.getCreatedAt(), false);
                for (Long affectedPackageId : scheduleChangeAffectedPackageIds(rule, packageIdSet,
                        packageIdsByGroup)) {
                    changesByPackage.computeIfAbsent(affectedPackageId, ignored -> new LinkedHashMap<>())
                            .putIfAbsent(rule.getId(), change);
                }
            }

            // 老数据曾原位替换课位而复用规则 ID；用当前有效课位快照时间标出同周期余额统计的边界。
            if (rule.getSupersedesRuleId() == null && "ACTIVE".equals(rule.getRuleStatus())) {
                LocalDateTime slotPeriodStart = rule.getAllocationPeriodStartedAt() == null
                        ? currentSlotStartsByRule.get(rule.getId()) : rule.getAllocationPeriodStartedAt();
                if (slotPeriodStart != null && rule.getCreatedAt() != null
                        && slotPeriodStart.isAfter(rule.getCreatedAt())) {
                    OneOnOneScheduleChangeVO change = new OneOnOneScheduleChangeVO(
                            rule.getId(), slotPeriodStart, true);
                    for (Long affectedPackageId : scheduleChangeAffectedPackageIds(rule, packageIdSet,
                            packageIdsByGroup)) {
                        changesByPackage.computeIfAbsent(affectedPackageId, ignored -> new LinkedHashMap<>())
                                .putIfAbsent(rule.getId(), change);
                    }
                }
            }
        }
        Comparator<OneOnOneScheduleChangeVO> byTime = Comparator
                .comparing(OneOnOneScheduleChangeVO::changedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(OneOnOneScheduleChangeVO::ruleId);
        return changesByPackage.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().values().stream().sorted(byTime).toList()));
    }

    /** 将排课版本映射到直接课包及同一私教组的成员课包。 */
    private Set<Long> scheduleChangeAffectedPackageIds(RecurringScheduleRuleEntity rule,
                                                        Set<Long> packageIds,
                                                        Map<Long, Set<Long>> packageIdsByGroup) {
        Set<Long> affectedPackageIds = new HashSet<>(packageIdsByGroup.getOrDefault(
                rule.getPrivateLessonGroupId(), Collections.emptySet()));
        if (packageIds.contains(rule.getPackageId())) {
            affectedPackageIds.add(rule.getPackageId());
        }
        return affectedPackageIds;
    }

    private boolean isOneOnOnePackage(CoursePackageEntity coursePackage, CourseProductEntity courseProduct) {
        if (courseProduct != null) {
            return "ONE_ON_ONE".equals(courseProduct.getTeachingMode());
        }
        // 无法取到产品时才回退到课包字段；正常路径以产品模式为准，避免班课学生混入 1 对 1 排课列表。
        return "ONE_ON_ONE".equals(coursePackage.getTeachingMode());
    }

    private boolean isPrimaryPrivateLessonPackage(CoursePackageEntity coursePackage,
                                                   Map<Long, List<PrivateLessonGroupMemberEntity>> membersByPackage) {
        List<PrivateLessonGroupMemberEntity> members = membersByPackage.getOrDefault(
                coursePackage.getId(), Collections.emptyList());
        if (members.isEmpty()) {
            return true;
        }
        return members.stream().anyMatch(member -> Integer.valueOf(1).equals(member.getParticipantIndex())
                && Objects.equals(member.getPackageId(), coursePackage.getId()));
    }

    private List<LessonOccurrenceVO> toOccurrenceVOs(List<LessonOccurrenceEntity> occurrences) {
        Set<Long> occurrenceIds = occurrences.stream().map(LessonOccurrenceEntity::getId).collect(Collectors.toSet());
        Map<Long, CoursePackageEntity> packageMap = listPackagesByIds(occurrences.stream().map(LessonOccurrenceEntity::getPackageId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Set<Long> privateGroupIds = occurrences.stream().map(LessonOccurrenceEntity::getPrivateLessonGroupId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, List<PrivateLessonGroupMemberEntity>> groupMembersByGroup = loadPrivateGroupMembersByIds(privateGroupIds);
        Set<Long> studentIds = occurrences.stream().map(LessonOccurrenceEntity::getStudentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        groupMembersByGroup.values().stream().flatMap(List::stream)
                .map(PrivateLessonGroupMemberEntity::getStudentId)
                .filter(Objects::nonNull).forEach(studentIds::add);
        Map<Long, StudentEntity> studentMap = listStudentsByIds(studentIds);
        Map<Long, List<String>> participantNamesByGroup = new HashMap<>();
        groupMembersByGroup.forEach((groupId, members) -> participantNamesByGroup.put(groupId,
                members.stream().map(member -> studentMap.get(member.getStudentId()))
                        .filter(Objects::nonNull).map(StudentEntity::getStudentName)
                        .filter(StringUtils::hasText).toList()));
        Map<Long, TeacherEntity> teacherMap = listTeachersByIds(occurrences.stream().map(LessonOccurrenceEntity::getPlannedTeacherId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, ClassroomEntity> classroomMap = listClassroomsByIds(occurrences.stream().map(LessonOccurrenceEntity::getPlannedClassroomId).filter(Objects::nonNull).collect(Collectors.toSet()));
        // 班课名称
        Map<Long, ClassGroupEntity> classGroupMap = listClassGroupsByIds(occurrences.stream().map(LessonOccurrenceEntity::getClassGroupId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, RecurringScheduleSlotEntity> recurringSlotMap = loadRecurringSlotMap(occurrences);
        Map<Long, ClassGroupScheduleSlotEntity> classSlotMap = loadClassSlotMap(occurrences);
        Map<Long, CourseProductEntity> courseProductMap = listCourseProductsByIds(collectCourseProductIds(packageMap.values(), classGroupMap.values()));

        Map<Long, LessonRecordEntity> recordMap = loadLatestRecordByOccurrence(occurrenceIds);
        Map<String, String> statusNameMap = loadStatusNames(recordMap.values());

        return occurrences.stream()
                .map(item -> {
                    CoursePackageEntity coursePackage = packageMap.get(item.getPackageId());
                    StudentEntity student = studentMap.get(item.getStudentId());
                    TeacherEntity teacher = teacherMap.get(item.getPlannedTeacherId());
                    ClassroomEntity classroom = classroomMap.get(item.getPlannedClassroomId());
                    ClassGroupEntity classGroup = classGroupMap.get(item.getClassGroupId());
                    LessonRecordEntity record = recordMap.get(item.getId());
                    String resultStatus = record != null ? record.getResultStatus() : null;
                    String resultStatusName = resultStatus != null ? statusNameMap.getOrDefault(resultStatus, resultStatus) : null;
                    return LessonOccurrenceVO.builder()
                            .id(item.getId())
                            .lessonNo(item.getLessonNo())
                            .packageId(item.getPackageId())
                            .packageNo(coursePackage == null ? null : coursePackage.getPackageNo())
                            .packageName(coursePackage == null ? null : coursePackage.getPackageName())
                            .productName(resolveProductName(coursePackage, classGroup, courseProductMap))
                            .classGroupId(item.getClassGroupId())
                            .className(classGroup == null ? null : classGroup.getClassName())
                            .subjectName(resolveOccurrenceSubjectName(item, recurringSlotMap, classSlotMap))
                            .studentId(item.getStudentId())
                            .studentName(student == null ? null : student.getStudentName())
                            .participantNames(participantNamesByGroup.getOrDefault(
                                    item.getPrivateLessonGroupId(), Collections.emptyList()))
                            .scheduleRuleId(item.getScheduleRuleId())
                            .scheduleSlotId(item.getScheduleSlotId())
                            .occurrenceType(item.getOccurrenceType())
                            .plannedTeacherId(item.getPlannedTeacherId())
                            .plannedTeacherName(teacher == null ? null : teacher.getTeacherName())
                            .deliveryMode(item.getDeliveryMode())
                            .plannedClassroomId(item.getPlannedClassroomId())
                            .plannedClassroomName("ONLINE".equals(item.getDeliveryMode()) ? "Online" : classroom == null ? null : classroom.getRoomName())
                            .plannedStartAt(item.getPlannedStartAt())
                            .plannedEndAt(item.getPlannedEndAt())
                            .plannedUnits(item.getPlannedUnits())
                            .planningStatus(item.getPlanningStatus())
                            .sourceOccurrenceId(item.getSourceOccurrenceId())
                            .conflictChecked(item.getConflictChecked() != null && item.getConflictChecked() == 1)
                            .notes(item.getNotes())
                            .resultStatus(resultStatus)
                            .resultStatusName(resultStatusName)
                            .lessonRecordId(record == null ? null : record.getId())
                            .recordId(record == null ? null : record.getId())
                            .confirmStage(record == null ? null : record.getConfirmStage())
                            .confirmedAt(record == null ? null : record.getConfirmedAt())
                            .confirmedByUserId(record == null ? null : record.getConfirmedByUserId())
                            .teacherConfirmedAt(record == null ? null : record.getTeacherConfirmedAt())
                            .teacherConfirmedByUserId(record == null ? null : record.getTeacherConfirmedByUserId())
                            .adminConfirmedAt(record == null ? null : record.getAdminConfirmedAt())
                            .adminConfirmedByUserId(record == null ? null : record.getAdminConfirmedByUserId())
                            .rejectReason(record == null ? null : record.getRejectReason())
                            .rejectedAt(record == null ? null : record.getRejectedAt())
                            .rejectedByUserId(record == null ? null : record.getRejectedByUserId())
                            .actualTeacherId(record == null ? null : record.getActualTeacherId())
                            .actualClassroomId(record == null ? null : record.getActualClassroomId())
                            .actualStartAt(record == null ? null : record.getActualStartAt())
                            .actualEndAt(record == null ? null : record.getActualEndAt())
                            .actualUnits(record == null ? null : record.getActualUnits())
                            .privateLessonGroupId(item.getPrivateLessonGroupId())
                            .deductedUnits(record == null ? null : record.getDeductedUnits())
                            .shouldDeduct(record == null ? null : record.getShouldDeduct())
                            .build();
                })
                .toList();
    }

    private LocalDateTime[] normalizeWindow(LocalDateTime startAt, LocalDateTime endAt, int defaultDays) {
        return normalizeWindow(startAt, endAt, defaultDays, MAX_QUERY_WINDOW_DAYS);
    }

    private LocalDateTime[] normalizeWindow(LocalDateTime startAt, LocalDateTime endAt, int defaultDays, Integer maxDays) {
        LocalDateTime resolvedStart = startAt != null ? startAt : AppTime.nowLocalDateTime().toLocalDate().atStartOfDay();
        LocalDateTime resolvedEnd = endAt != null ? endAt : resolvedStart.plusDays(defaultDays);
        if (!resolvedEnd.isAfter(resolvedStart)) {
            throw new BusinessException("INVALID_QUERY_WINDOW", "Query end time must be later than the start time.");
        }
        // 按日期比较（忽略时分秒），避免 endAt=23:59:59 时误判超限
        if (maxDays != null && resolvedStart.toLocalDate().plusDays(maxDays).isBefore(resolvedEnd.toLocalDate())) {
            throw new BusinessException("QUERY_WINDOW_TOO_LARGE", "Query window cannot exceed " + maxDays + " days.");
        }
        return new LocalDateTime[]{resolvedStart, resolvedEnd};
    }

    private List<Long> findMatchedPackageIds(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        return coursePackageMapper.selectList(new LambdaQueryWrapper<CoursePackageEntity>()
                        .and(wrapper -> wrapper.like(CoursePackageEntity::getPackageNo, keyword)
                                .or()
                                .like(CoursePackageEntity::getPackageName, keyword)))
                .stream()
                .map(CoursePackageEntity::getId)
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

    private Long resolveOneOnOneTeacherFilter(OneOnOneDeductionQuery query, CurrentUserResponse currentUser) {
        if (isTeacherUser(currentUser)) {
            return currentUser.teacherId();
        }
        return query.getTeacherId();
    }

    private Long resolveOneOnOneStudentFilter(OneOnOneDeductionQuery query, CurrentUserResponse currentUser) {
        if (isStudentUser(currentUser)) {
            return currentUser.studentId();
        }
        return query.getStudentId();
    }

    private boolean isTeacherUser(CurrentUserResponse currentUser) {
        if (currentUser == null) {
            return false;
        }
        return "TEACHER".equals(currentUser.userType())
                || (currentUser.roleCodes() != null && currentUser.roleCodes().contains("TEACHER"));
    }

    private boolean isStudentUser(CurrentUserResponse currentUser) {
        if (currentUser == null) {
            return false;
        }
        return "STUDENT".equals(currentUser.userType())
                || (currentUser.roleCodes() != null && currentUser.roleCodes().contains("STUDENT"));
    }

    private void applyScheduleScope(LessonOccurrencePageQuery query, CurrentUserResponse currentUser) {
        if (query == null || currentUser == null) {
            return;
        }
        if (isTeacherUser(currentUser)) {
            if (currentUser.teacherId() == null) {
                throw new BusinessException("TEACHER_BINDING_REQUIRED", "This teacher account is not linked to a teacher profile.");
            }
            if (query.getTeacherId() != null && !Objects.equals(query.getTeacherId(), currentUser.teacherId())) {
                throw new BusinessException("FORBIDDEN", "Teachers can only view their own calendar.");
            }
            query.setTeacherId(currentUser.teacherId());
            return;
        }
        if (isStudentUser(currentUser)) {
            if (currentUser.studentId() == null) {
                throw new BusinessException("STUDENT_BINDING_REQUIRED", "This student account is not linked to a student profile.");
            }
            if (query.getStudentId() != null && !Objects.equals(query.getStudentId(), currentUser.studentId())) {
                throw new BusinessException("FORBIDDEN", "Students can only view their own calendar.");
            }
            query.setStudentId(currentUser.studentId());
        }
    }

    private void validateScheduleDetailScope(LessonOccurrenceEntity entity, CurrentUserResponse currentUser) {
        if (currentUser == null) {
            return;
        }
        if (isTeacherUser(currentUser)) {
            if (currentUser.teacherId() == null) {
                throw new BusinessException("TEACHER_BINDING_REQUIRED", "This teacher account is not linked to a teacher profile.");
            }
            if (!Objects.equals(entity.getPlannedTeacherId(), currentUser.teacherId())) {
                throw new BusinessException("FORBIDDEN", "Teachers can only view their own calendar.");
            }
            return;
        }
        if (isStudentUser(currentUser)) {
            if (currentUser.studentId() == null) {
                throw new BusinessException("STUDENT_BINDING_REQUIRED", "This student account is not linked to a student profile.");
            }
            if (Objects.equals(entity.getStudentId(), currentUser.studentId())
                    || isStudentPrivateLessonGroupMember(currentUser.studentId(), entity.getPrivateLessonGroupId())) {
                return;
            }
            if (entity.getClassGroupId() != null && isStudentEnrolledInClassForOccurrence(
                    currentUser.studentId(), entity)) {
                return;
            }
            throw new BusinessException("FORBIDDEN", "Students can only view their own calendar.");
        }
    }

    private boolean isStudentEnrolledInClassForOccurrence(Long studentId, LessonOccurrenceEntity occurrence) {
        if (occurrence.getPlannedStartAt() == null) {
            return false;
        }
        return classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getStudentId, studentId)
                        .eq(ClassEnrollmentEntity::getClassId, occurrence.getClassGroupId())
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                        .le(ClassEnrollmentEntity::getEnrollmentDate, occurrence.getPlannedStartAt().toLocalDate())
                        .ge(ClassEnrollmentEntity::getExpiryDate, occurrence.getPlannedStartAt().toLocalDate()))
                .stream()
                .anyMatch(enrollment -> enrollmentIncludesOccurrence(enrollment, occurrence));
    }

    private boolean isStudentPrivateLessonGroupMember(Long studentId, Long privateLessonGroupId) {
        if (studentId == null || privateLessonGroupId == null) {
            return false;
        }
        return privateLessonGroupMemberMapper.selectCount(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                .eq(PrivateLessonGroupMemberEntity::getStudentId, studentId)
                .eq(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, privateLessonGroupId)
                .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)) > 0;
    }

    private boolean privateGroupMemberMapperHasGroup(Long studentId) {
        return !privateGroupIdsForStudent(studentId).isEmpty();
    }

    private List<Long> privateGroupIdsForStudent(Long studentId) {
        return privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .eq(PrivateLessonGroupMemberEntity::getStudentId, studentId)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0))
                .stream().map(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId)
                .filter(Objects::nonNull).distinct().toList();
    }

    private List<LessonOccurrenceEntity> filterStudentClassOccurrences(List<LessonOccurrenceEntity> records, Long studentId) {
        if (studentId == null || records.isEmpty()) {
            return records;
        }
        List<ClassEnrollmentEntity> enrollments = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getStudentId, studentId)
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0));
        Map<Long, List<ClassEnrollmentEntity>> enrollmentsByClass = enrollments.stream()
                .collect(Collectors.groupingBy(ClassEnrollmentEntity::getClassId));
        Map<Long, ClassGroupScheduleSlotEntity> slotMap = listSlotsByIds(records.stream()
                .map(LessonOccurrenceEntity::getScheduleSlotId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return records.stream()
                .filter(record -> record.getClassGroupId() == null
                        || classOccurrenceVisibleToStudent(record, enrollmentsByClass.get(record.getClassGroupId()), slotMap))
                .toList();
    }

    private boolean classOccurrenceVisibleToStudent(LessonOccurrenceEntity occurrence,
                                                    List<ClassEnrollmentEntity> enrollments,
                                                    Map<Long, ClassGroupScheduleSlotEntity> slotMap) {
        if (enrollments == null || enrollments.isEmpty() || occurrence.getPlannedStartAt() == null) {
            return false;
        }
        LocalDateTime startAt = occurrence.getPlannedStartAt();
        ClassGroupScheduleSlotEntity slot = occurrence.getScheduleSlotId() == null ? null : slotMap.get(occurrence.getScheduleSlotId());
        return enrollments.stream().anyMatch(enrollment ->
                enrollmentIncludesOccurrence(enrollment, occurrence)
                        && (enrollment.getEnrollmentDate() == null || !startAt.toLocalDate().isBefore(enrollment.getEnrollmentDate()))
                        && (enrollment.getExpiryDate() == null || !startAt.toLocalDate().isAfter(enrollment.getExpiryDate()))
                        && attendanceMatches(enrollment.getAttendanceOption(), slot));
    }

    private boolean enrollmentIncludesOccurrence(ClassEnrollmentEntity enrollment, LessonOccurrenceEntity occurrence) {
        if (enrollment == null || occurrence == null || occurrence.getPlannedStartAt() == null) {
            return false;
        }
        LocalDateTime enrolledAt = enrollment.getCreatedAt();
        return enrolledAt == null
                || !occurrence.getPlannedStartAt().toLocalDate().isBefore(enrolledAt.toLocalDate());
    }

    private boolean attendanceMatches(String enrollmentAttendance, ClassGroupScheduleSlotEntity slot) {
        String attendance = normalizeAttendanceOption(enrollmentAttendance);
        String segment = slot == null ? null : slot.getAttendanceOptions();
        if (!StringUtils.hasText(segment)) {
            return true;
        }
        String normalizedSegment = segment.trim().toUpperCase(Locale.ROOT);
        Set<String> segments = Arrays.stream(normalizedSegment.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (segments.isEmpty() || segments.contains("ALL")) {
            return true;
        }
        if ("FULL_DAY".equals(attendance)) {
            return segments.contains("FULL_DAY")
                    || segments.contains("MORNING")
                    || segments.contains("AFTERNOON");
        }
        return segments.contains(attendance);
    }

    private String normalizeAttendanceOption(String value) {
        if (!StringUtils.hasText(value)) {
            return "FULL_DAY";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "MORNING", "AFTERNOON", "FULL_DAY" -> normalized;
            default -> "FULL_DAY";
        };
    }

    private Map<Long, ClassGroupScheduleSlotEntity> listSlotsByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return classGroupScheduleSlotMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ClassGroupScheduleSlotEntity::getId, Function.identity()));
    }

    private List<Long> findOneOnOnePackageIdsByTeacher(Long teacherId) {
        if (teacherId == null) {
            return Collections.emptyList();
        }
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getPlannedTeacherId, teacherId)
                        .isNotNull(LessonOccurrenceEntity::getPackageId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED"))
                .stream()
                .map(LessonOccurrenceEntity::getPackageId)
                .distinct()
                .toList();
    }

    private Map<Long, List<LessonOccurrenceEntity>> loadOneOnOneOccurrenceMap(List<Long> packageIds,
                                                                               LocalDateTime startAt,
                                                                               LocalDateTime endAt) {
        if (packageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<PrivateLessonGroupMemberEntity>> groupMembersByGroup = loadPrivateGroupMembersByGroup(packageIds);
        Set<Long> groupIds = groupMembersByGroup.keySet();
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .and(scope -> scope.in(LessonOccurrenceEntity::getPackageId, packageIds)
                        .or(!groupIds.isEmpty(), group -> group.in(LessonOccurrenceEntity::getPrivateLessonGroupId, groupIds)))
                .ge(startAt != null, LessonOccurrenceEntity::getPlannedStartAt, startAt)
                .le(endAt != null, LessonOccurrenceEntity::getPlannedStartAt, endAt)
                .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));
        Map<Long, List<LessonOccurrenceEntity>> result = new LinkedHashMap<>();
        for (LessonOccurrenceEntity occurrence : occurrences) {
            if (occurrence.getPrivateLessonGroupId() == null) {
                result.computeIfAbsent(occurrence.getPackageId(), key -> new ArrayList<>()).add(occurrence);
                continue;
            }
            List<PrivateLessonGroupMemberEntity> members = groupMembersByGroup.getOrDefault(
                    occurrence.getPrivateLessonGroupId(), Collections.emptyList());
            for (PrivateLessonGroupMemberEntity member : members) {
                result.computeIfAbsent(member.getPackageId(), key -> new ArrayList<>()).add(occurrence);
            }
        }
        return result;
    }

    private Map<Long, List<PrivateLessonGroupMemberEntity>> loadPrivateGroupMembersByPackage(List<Long> packageIds) {
        if (packageIds == null || packageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PrivateLessonGroupMemberEntity> seedMembers = privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .in(PrivateLessonGroupMemberEntity::getPackageId, packageIds)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE"))
                ;
        Set<Long> groupIds = seedMembers.stream()
                .map(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<PrivateLessonGroupMemberEntity>> membersByGroup = groupIds.isEmpty()
                ? Collections.emptyMap()
                : privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .in(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, groupIds)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .orderByAsc(PrivateLessonGroupMemberEntity::getParticipantIndex))
                .stream().collect(Collectors.groupingBy(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<PrivateLessonGroupMemberEntity>> result = new LinkedHashMap<>();
        for (Long packageId : packageIds) {
            List<PrivateLessonGroupMemberEntity> seed = seedMembers.stream()
                    .filter(member -> Objects.equals(member.getPackageId(), packageId))
                    .toList();
            if (seed.isEmpty()) {
                continue;
            }
            Long groupId = seed.get(0).getPrivateLessonGroupId();
            result.put(packageId, groupId == null
                    ? seed
                    : membersByGroup.getOrDefault(groupId, seed));
        }
        return result;
    }

    private Map<Long, List<PrivateLessonGroupMemberEntity>> loadPrivateGroupMembersByGroup(List<Long> packageIds) {
        if (packageIds == null || packageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> groupIds = privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .in(PrivateLessonGroupMemberEntity::getPackageId, packageIds)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE"))
                .stream()
                .map(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return loadPrivateGroupMembersByIds(groupIds);
    }

    /** 按课组批量加载当前参与学生，供课次列表和课包视图共用。 */
    private Map<Long, List<PrivateLessonGroupMemberEntity>> loadPrivateGroupMembersByIds(Set<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .in(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId, groupIds)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                        .orderByAsc(PrivateLessonGroupMemberEntity::getParticipantIndex))
                .stream().collect(Collectors.groupingBy(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, LessonRecordEntity> loadLatestRecordByOccurrence(Set<Long> occurrenceIds) {
        return loadLatestRecordIndex(occurrenceIds).byOccurrence();
    }

    /** 一次查询同时索引课次与学生课包，避免共享课把另一名学生的确认状态用于余额占用。 */
    private LatestRecordIndex loadLatestRecordIndex(Set<Long> occurrenceIds) {
        if (occurrenceIds.isEmpty()) {
            return new LatestRecordIndex(Collections.emptyMap(), Collections.emptyMap());
        }
        List<LessonRecordEntity> records = new ArrayList<>(lessonRecordMapper.selectList(
                new LambdaQueryWrapper<LessonRecordEntity>()
                        .in(LessonRecordEntity::getOccurrenceId, occurrenceIds)));
        records.sort(Comparator
                .comparing(this::recordSortTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LessonRecordEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        Map<Long, LessonRecordEntity> byOccurrence = new LinkedHashMap<>();
        Map<Long, Map<Long, LessonRecordEntity>> byPackage = new HashMap<>();
        for (LessonRecordEntity record : records) {
            byOccurrence.putIfAbsent(record.getOccurrenceId(), record);
            if (record.getPackageId() != null) {
                byPackage.computeIfAbsent(record.getPackageId(), key -> new HashMap<>())
                        .putIfAbsent(record.getOccurrenceId(), record);
            }
        }
        return new LatestRecordIndex(byOccurrence, byPackage);
    }

    private record LatestRecordIndex(Map<Long, LessonRecordEntity> byOccurrence,
                                     Map<Long, Map<Long, LessonRecordEntity>> byPackage) {

        Collection<LessonRecordEntity> allRecords() {
            List<LessonRecordEntity> records = new ArrayList<>(byOccurrence.values());
            byPackage.values().forEach(packageRecords -> records.addAll(packageRecords.values()));
            return records;
        }

        /** 独立课包兼容旧记录的空 package_id；共享课必须按学生课包取确认记录。 */
        Map<Long, LessonRecordEntity> forPackage(Long packageId, List<LessonOccurrenceEntity> occurrences) {
            Map<Long, LessonRecordEntity> ownRecords = byPackage.getOrDefault(packageId, Collections.emptyMap());
            Map<Long, LessonRecordEntity> result = new HashMap<>(ownRecords);
            for (LessonOccurrenceEntity occurrence : occurrences) {
                if (occurrence.getPrivateLessonGroupId() == null && !result.containsKey(occurrence.getId())) {
                    LessonRecordEntity legacyRecord = byOccurrence.get(occurrence.getId());
                    if (legacyRecord != null) {
                        result.put(occurrence.getId(), legacyRecord);
                    }
                }
            }
            return result;
        }
    }

    private LocalDateTime recordSortTime(LessonRecordEntity record) {
        if (record.getUpdatedAt() != null) {
            return record.getUpdatedAt();
        }
        if (record.getConfirmedAt() != null) {
            return record.getConfirmedAt();
        }
        if (record.getTeacherConfirmedAt() != null) {
            return record.getTeacherConfirmedAt();
        }
        return record.getCreatedAt();
    }

    private Map<Long, TeacherEntity> loadOneOnOneTeachers(Map<Long, List<LessonOccurrenceEntity>> occurrenceMap,
                                                          Collection<LessonRecordEntity> records,
                                                          PackageHoursContext hoursContext) {
        Set<Long> teacherIds = new LinkedHashSet<>();
        occurrenceMap.values().stream()
                .flatMap(List::stream)
                .map(LessonOccurrenceEntity::getPlannedTeacherId)
                .filter(Objects::nonNull)
                .forEach(teacherIds::add);
        records.forEach(record -> {
            if (record.getPlannedTeacherId() != null) {
                teacherIds.add(record.getPlannedTeacherId());
            }
            if (record.getActualTeacherId() != null) {
                teacherIds.add(record.getActualTeacherId());
            }
        });
        hoursContext.allocatedByTeacherByPackage().values().forEach(
                allocations -> teacherIds.addAll(allocations.keySet()));
        hoursContext.deductedByTeacherByPackage().values().forEach(
                deductions -> teacherIds.addAll(deductions.keySet()));
        hoursContext.unconfirmedScheduledByTeacherByPackage().values().forEach(
                remaining -> teacherIds.addAll(remaining.keySet()));
        return listTeachersByIds(teacherIds);
    }

    private Map<Long, ClassroomEntity> loadOneOnOneClassrooms(Map<Long, List<LessonOccurrenceEntity>> occurrenceMap,
                                                              Collection<LessonRecordEntity> records) {
        Set<Long> classroomIds = new LinkedHashSet<>();
        occurrenceMap.values().stream()
                .flatMap(List::stream)
                .map(LessonOccurrenceEntity::getPlannedClassroomId)
                .filter(Objects::nonNull)
                .forEach(classroomIds::add);
        records.forEach(record -> {
            if (record.getPlannedClassroomId() != null) {
                classroomIds.add(record.getPlannedClassroomId());
            }
            if (record.getActualClassroomId() != null) {
                classroomIds.add(record.getActualClassroomId());
            }
        });
        return listClassroomsByIds(classroomIds);
    }

    private Map<String, String> loadStatusNames(Collection<LessonRecordEntity> records) {
        Set<String> statusCodes = records.stream()
                .map(LessonRecordEntity::getResultStatus)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (statusCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return lessonStatusRuleMapper.selectList(new LambdaQueryWrapper<LessonStatusRuleEntity>()
                        .in(LessonStatusRuleEntity::getStatusCode, statusCodes))
                .stream()
                .collect(Collectors.toMap(LessonStatusRuleEntity::getStatusCode, LessonStatusRuleEntity::getStatusName, (a, b) -> a));
    }

    private record PackageHoursContext(Map<Long, Integer> allocatedByPackage,
                                       Map<Long, Map<Long, Integer>> allocatedByTeacherByPackage,
                                       Map<Long, Map<Long, Integer>> deductedByTeacherByPackage,
                                       Map<Long, Map<Long, Integer>> unconfirmedScheduledByTeacherByPackage,
                                       Map<Long, Integer> scheduledByPackage,
                                       Map<Long, Integer> pendingAdminDeductionByPackage,
                                       Map<Long, Integer> pendingProcessingByPackage,
                                       Map<Long, Integer> unresolvedPastByPackage) {}

    /** 按最新 ACTIVE 规则统计分配课时；课包待确认余额和老师未确认课时分别统计。 */
    private PackageHoursContext loadPackageHoursContext(
            List<Long> packageIds, Map<Long, Integer> scheduledByPackage,
            Map<Long, Integer> unresolvedPastByPackage,
            Map<Long, List<LessonOccurrenceEntity>> occurrencesByPackage,
            LatestRecordIndex recordIndex) {
        Map<Long, Integer> pendingAdminDeductionByPackage = new HashMap<>();
        Map<Long, Integer> pendingProcessingByPackage = new HashMap<>();
        Map<Long, Map<Long, Integer>> unconfirmedScheduledByTeacherByPackage = new HashMap<>();
        LocalDateTime now = AppTime.nowLocalDateTime();
        for (LessonRecordEntity record : lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                .in(LessonRecordEntity::getPackageId, packageIds)
                .eq(LessonRecordEntity::getIsDeleted, 0)
                .eq(LessonRecordEntity::getConfirmStage, "PENDING_ADMIN")
                .eq(LessonRecordEntity::getShouldDeduct, 1))) {
            int units = Math.max(0, safeUnits(record.getDeductedUnits()));
            pendingAdminDeductionByPackage.merge(record.getPackageId(), units, Integer::sum);
        }
        List<PrivateLessonGroupMemberEntity> privateGroupMembers = privateLessonGroupMemberMapper.selectList(
                        new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                                .in(PrivateLessonGroupMemberEntity::getPackageId, packageIds)
                                .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                                .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0));
        Map<Long, List<Long>> packageIdsByPrivateGroup = privateGroupMembers.stream()
                .filter(member -> member.getPrivateLessonGroupId() != null)
                .collect(Collectors.groupingBy(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId,
                        Collectors.mapping(PrivateLessonGroupMemberEntity::getPackageId, Collectors.toList())));
        List<Long> privateGroupIds = packageIdsByPrivateGroup.keySet().stream()
                .filter(Objects::nonNull).distinct().toList();
        List<RecurringScheduleRuleEntity> rules = recurringScheduleRuleMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                        .and(wrapper -> wrapper.in(RecurringScheduleRuleEntity::getPackageId, packageIds)
                                .or(!privateGroupIds.isEmpty(), group -> group.in(
                                        RecurringScheduleRuleEntity::getPrivateLessonGroupId, privateGroupIds)))
                        .eq(RecurringScheduleRuleEntity::getRuleStatus, "ACTIVE")
                        .orderByDesc(RecurringScheduleRuleEntity::getCreatedAt)
                        .orderByDesc(RecurringScheduleRuleEntity::getId));
        Map<Long, Integer> allocatedByPackage = new HashMap<>();
        Map<Long, Map<Long, Integer>> allocatedByTeacherByPackage = new HashMap<>();
        Map<Long, Long> currentRuleByPackage = new HashMap<>();
        Map<Long, RecurringScheduleRuleEntity> rulesById = new HashMap<>();
        Map<Long, LocalDateTime> periodStartByRule = new HashMap<>();
        Map<Long, Map<Long, Integer>> deductedByTeacherByPackage = new HashMap<>();
        if (!rules.isEmpty()) {
            rules.forEach(rule -> {
                rulesById.putIfAbsent(rule.getId(), rule);
                if (rule.getAllocationPeriodStartedAt() != null) {
                    periodStartByRule.put(rule.getId(), rule.getAllocationPeriodStartedAt());
                } else if (rule.getCreatedAt() != null) {
                    periodStartByRule.put(rule.getId(), rule.getCreatedAt());
                }
            });
            // Schedule 列表每个课包只取最新 ACTIVE 规则，避免把旧的 PAUSED 规则再次计入。
            for (RecurringScheduleRuleEntity rule : rules) {
                if (packageIds.contains(rule.getPackageId())) {
                    currentRuleByPackage.putIfAbsent(rule.getPackageId(), rule.getId());
                }
                if (rule.getPrivateLessonGroupId() != null) {
                    for (Long memberPackageId : packageIdsByPrivateGroup.getOrDefault(
                            rule.getPrivateLessonGroupId(), Collections.emptyList())) {
                        currentRuleByPackage.putIfAbsent(memberPackageId, rule.getId());
                    }
                }
            }
            Map<Long, List<Long>> packageIdsByRule = currentRuleByPackage.entrySet().stream()
                    .collect(Collectors.groupingBy(Map.Entry::getValue,
                            Collectors.mapping(Map.Entry::getKey, Collectors.toList())));
            List<RecurringScheduleSlotEntity> currentSlots = recurringScheduleSlotMapper.selectList(
                    new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                            .in(RecurringScheduleSlotEntity::getRuleId, packageIdsByRule.keySet())
                            .eq(RecurringScheduleSlotEntity::getSlotStatus, "ACTIVE"));
            Map<Long, Long> currentSlotTeacherById = new HashMap<>();
            for (RecurringScheduleSlotEntity slot : currentSlots) {
                currentSlotTeacherById.put(slot.getId(), slot.getTeacherId());
                // 新版本以规则创建时间为周期起点；仅旧规则复用 ID 时才用课位时间兼容历史数据。
                RecurringScheduleRuleEntity slotRule = rulesById.get(slot.getRuleId());
                if (slot.getCreatedAt() != null && slotRule != null
                        && slotRule.getSupersedesRuleId() == null
                        && slotRule.getAllocationPeriodStartedAt() == null) {
                    periodStartByRule.merge(slot.getRuleId(), slot.getCreatedAt(),
                            (previous, createdAt) -> previous.isAfter(createdAt) ? previous : createdAt);
                }
                for (Long packageId : packageIdsByRule.getOrDefault(slot.getRuleId(), Collections.emptyList())) {
                    allocatedByPackage.merge(packageId,
                            Math.max(0, safeUnits(slot.getAllocatedUnits())), Integer::sum);
                    if (slot.getTeacherId() != null) {
                        allocatedByTeacherByPackage
                                .computeIfAbsent(packageId, key -> new HashMap<>())
                                .merge(slot.getTeacherId(), Math.max(0, safeUnits(slot.getAllocatedUnits())), Integer::sum);
                    }
                }
            }
            Set<Long> historicalSlotIds = occurrencesByPackage.values().stream()
                    .flatMap(Collection::stream)
                    .map(LessonOccurrenceEntity::getScheduleSlotId)
                    .filter(Objects::nonNull)
                    .filter(slotId -> !currentSlotTeacherById.containsKey(slotId))
                    .collect(Collectors.toSet());
            Map<Long, RecurringScheduleSlotEntity> historicalSlotsById = historicalSlotIds.isEmpty()
                    ? Collections.emptyMap()
                    : recurringScheduleSlotMapper.selectIncludingDeletedByIds(historicalSlotIds).stream()
                            .collect(Collectors.toMap(RecurringScheduleSlotEntity::getId, slot -> slot));
            for (Long packageId : currentRuleByPackage.keySet()) {
                allocatedByPackage.putIfAbsent(packageId, 0);
            }

            // 消课按课次快照归属老师；当前周期分配额仅取排课快照，不随确认状态变化。
            for (Map.Entry<Long, Long> entry : currentRuleByPackage.entrySet()) {
                Long packageId = entry.getKey();
                Long currentRuleId = entry.getValue();
                RecurringScheduleRuleEntity currentRule = rulesById.get(currentRuleId);
                List<LessonOccurrenceEntity> packageOccurrences = occurrencesByPackage.getOrDefault(
                        packageId, Collections.emptyList());
                Map<Long, LessonRecordEntity> records = recordIndex.forPackage(packageId, packageOccurrences);
                Map<Long, Integer> leaveCarryoverBySlot = currentSlotLeaveCarryovers(
                        packageOccurrences, currentSlots, historicalSlotsById, currentRuleId);
                Map<Long, Map<Long, Integer>> reservedBySlotAndTeacher = new HashMap<>();
                Set<Long> countedOccurrenceIds = new HashSet<>();
                for (LessonOccurrenceEntity occurrence : packageOccurrences) {
                    if (occurrence.getId() == null || !countedOccurrenceIds.add(occurrence.getId())) {
                        continue;
                    }
                    LessonRecordEntity record = records.get(occurrence.getId());
                    Long historicalTeacherId = occurrence.getPlannedTeacherId();
                    boolean currentPeriod = belongsToCurrentSchedulePeriod(occurrence, record,
                            currentRule, periodStartByRule.get(currentRuleId));
                    // 补课是请假课次的延续，不是新分配；旧时段和临时课才补入当前分配快照。
                    if (currentPeriod && historicalTeacherId != null
                            && !currentSlotTeacherById.containsKey(occurrence.getScheduleSlotId())
                            && !PrivateLessonAllocation.hasEquivalentCurrentSlot(
                                    historicalSlotsById.get(occurrence.getScheduleSlotId()),
                                    currentSlots, currentRuleId, leaveCarryoverBySlot)
                            && !"MAKEUP".equals(occurrence.getOccurrenceType())
                            && !"CANCELLED".equals(occurrence.getPlanningStatus())) {
                        int exceptionUnits = Math.max(0, safeUnits(occurrence.getPlannedUnits()));
                        if (exceptionUnits > 0) {
                            allocatedByPackage.merge(packageId, exceptionUnits, Integer::sum);
                            allocatedByTeacherByPackage.computeIfAbsent(packageId, key -> new HashMap<>())
                                    .merge(historicalTeacherId, exceptionUnits, Integer::sum);
                        }
                    }
                    int units = record == null ? 0 : Math.max(0, safeUnits(record.getDeductedUnits()));
                    Long currentTeacherId = currentSlotTeacherById.get(occurrence.getScheduleSlotId());
                    // 旧规则原位换老师时，按课次计划快照转移分配额；消课状态不能再改变 Allocated。
                    int historicalAllocatedUnits = Math.max(0, safeUnits(occurrence.getPlannedUnits()));
                    if (currentPeriod && historicalAllocatedUnits > 0
                            && !"MAKEUP".equals(occurrence.getOccurrenceType())
                            && !"CANCELLED".equals(occurrence.getPlanningStatus())
                            && currentTeacherId != null
                            && historicalTeacherId != null
                            && !Objects.equals(currentTeacherId, historicalTeacherId)
                            && Objects.equals(currentRuleId, occurrence.getScheduleRuleId())) {
                        reservedBySlotAndTeacher
                                .computeIfAbsent(occurrence.getScheduleSlotId(), key -> new HashMap<>())
                                .merge(historicalTeacherId, historicalAllocatedUnits, Integer::sum);
                    }
                    if (record == null || !Objects.equals(record.getShouldDeduct(), 1)
                            || Objects.equals(record.getIsDeleted(), 1)) continue;
                    // 老师页只展示当前周期的消课；旧版本未来保留例外在本周期上完也应计入。
                    if ("CONFIRMED".equals(record.getConfirmStage()) && historicalTeacherId != null
                            && currentPeriod) {
                        deductedByTeacherByPackage
                                .computeIfAbsent(packageId, key -> new HashMap<>())
                                .merge(historicalTeacherId, units, Integer::sum);
                    }

                }
                Map<Long, Integer> teacherAllocations = allocatedByTeacherByPackage
                        .computeIfAbsent(packageId, key -> new HashMap<>());
                for (RecurringScheduleSlotEntity slot : currentSlots) {
                    if (!Objects.equals(currentRuleId, slot.getRuleId()) || slot.getTeacherId() == null) continue;
                    int slotAllocated = Math.max(0, safeUnits(slot.getAllocatedUnits()));
                    int remainingSlotUnits = slotAllocated;
                    Map<Long, Integer> historicalReservations = reservedBySlotAndTeacher
                            .getOrDefault(slot.getId(), Collections.emptyMap());
                    for (Map.Entry<Long, Integer> reservation : historicalReservations.entrySet()) {
                        int transferred = Math.min(remainingSlotUnits, Math.max(0, reservation.getValue()));
                        int currentAllocation = teacherAllocations.getOrDefault(slot.getTeacherId(), 0);
                        transferred = Math.min(transferred, currentAllocation);
                        if (transferred <= 0) continue;
                        teacherAllocations.put(slot.getTeacherId(), currentAllocation - transferred);
                        teacherAllocations.merge(reservation.getKey(), transferred, Integer::sum);
                        remainingSlotUnits -= transferred;
                    }
                }
            }
        }
        // 老师级未确认课次仍单独汇总，用于保留老师入口并核对排课明细；页眉余额按额度账本计算。
        for (Map.Entry<Long, List<LessonOccurrenceEntity>> entry : occurrencesByPackage.entrySet()) {
            Long packageId = entry.getKey();
            List<LessonOccurrenceEntity> packageOccurrences = entry.getValue();
            Map<Long, LessonRecordEntity> records = recordIndex.forPackage(packageId, packageOccurrences);
            Long currentRuleId = currentRuleByPackage.get(packageId);
            RecurringScheduleRuleEntity currentRule = rulesById.get(currentRuleId);
            Set<Long> seenOccurrenceIds = new HashSet<>();
            for (LessonOccurrenceEntity occurrence : packageOccurrences) {
                if (occurrence.getId() == null || !seenOccurrenceIds.add(occurrence.getId())) {
                    continue;
                }
                LessonRecordEntity record = records.get(occurrence.getId());
                boolean currentPeriod = currentRule == null || belongsToCurrentSchedulePeriod(
                        occurrence, record, currentRule, periodStartByRule.get(currentRuleId));
                if (PrivateLessonAllocation.isPendingProcessing(occurrence, record, now)) {
                    int units = Math.max(0, safeUnits(occurrence.getPlannedUnits()));
                    pendingProcessingByPackage.merge(packageId, units, Integer::sum);
                    if (currentPeriod && occurrence.getPlannedTeacherId() != null) {
                        unconfirmedScheduledByTeacherByPackage
                                .computeIfAbsent(packageId, key -> new HashMap<>())
                                .merge(occurrence.getPlannedTeacherId(), units, Integer::sum);
                    }
                }
                if (!currentPeriod || occurrence.getPlannedTeacherId() == null
                        || occurrence.getPlannedStartAt() == null
                        || occurrence.getPlannedStartAt().isBefore(now)
                        || "CANCELLED".equals(occurrence.getPlanningStatus())
                        || "MERGED".equals(occurrence.getPlanningStatus())) {
                    continue;
                }
                if (record != null && !Objects.equals(record.getIsDeleted(), 1)
                        && ("CONFIRMED".equals(record.getConfirmStage())
                        || "PENDING_ADMIN".equals(record.getConfirmStage())
                        || "FORMAL_LEAVE".equalsIgnoreCase(record.getResultStatus())
                        || "CANCELLED".equalsIgnoreCase(record.getResultStatus()))) {
                    continue;
                }
                int units = Math.max(0, safeUnits(occurrence.getPlannedUnits()));
                if (units > 0) {
                    unconfirmedScheduledByTeacherByPackage
                            .computeIfAbsent(packageId, key -> new HashMap<>())
                            .merge(occurrence.getPlannedTeacherId(), units, Integer::sum);
                }
            }
        }
        return new PackageHoursContext(allocatedByPackage, allocatedByTeacherByPackage,
                deductedByTeacherByPackage, unconfirmedScheduledByTeacherByPackage, scheduledByPackage,
                pendingAdminDeductionByPackage, pendingProcessingByPackage, unresolvedPastByPackage);
    }

    /** 旧周期请假的额度随补课记录保存；扣除这些增量后，按原课位定义去重历史课次。 */
    private Map<Long, Integer> currentSlotLeaveCarryovers(
            List<LessonOccurrenceEntity> occurrences,
            List<RecurringScheduleSlotEntity> currentSlots,
            Map<Long, RecurringScheduleSlotEntity> historicalSlotsById,
            Long currentRuleId) {
        Map<Long, Integer> carryovers = new HashMap<>();
        for (LessonOccurrenceEntity occurrence : occurrences) {
            int units = Math.max(0, safeUnits(occurrence.getLeaveCarryoverUnits()));
            if (units == 0 || !"MAKEUP".equals(occurrence.getOccurrenceType())
                    || !Objects.equals(currentRuleId, occurrence.getScheduleRuleId())) {
                continue;
            }
            RecurringScheduleSlotEntity sourceSlot = currentSlots.stream()
                    .filter(slot -> Objects.equals(slot.getId(), occurrence.getScheduleSlotId()))
                    .findFirst().orElse(historicalSlotsById.get(occurrence.getScheduleSlotId()));
            if (sourceSlot == null) continue;
            currentSlots.stream()
                    .filter(slot -> Objects.equals(slot.getRuleId(), currentRuleId)
                            && Objects.equals(sourceSlot.getTeacherId(), slot.getTeacherId())
                            && Objects.equals(sourceSlot.getWeekday(), slot.getWeekday())
                            && Objects.equals(sourceSlot.getStartTime(), slot.getStartTime())
                            && Objects.equals(sourceSlot.getEndTime(), slot.getEndTime())
                            && Objects.equals(sourceSlot.getPlannedUnits(), slot.getPlannedUnits()))
                    .findFirst()
                    .ifPresent(slot -> carryovers.merge(slot.getId(), units, Integer::sum));
        }
        return carryovers;
    }

    /** 以最近一次排课分配为周期边界；旧规则保留的未来例外课次按实际发生日期归属。 */
    private boolean belongsToCurrentSchedulePeriod(LessonOccurrenceEntity occurrence,
                                                    LessonRecordEntity record,
                                                    RecurringScheduleRuleEntity currentRule,
                                                    LocalDateTime periodStart) {
        return PrivateSchedulePeriod.contains(occurrence, record, currentRule, periodStart);
    }

    /** 管理员按课包内实际排课的老师分别建立入口；老师账号查询时只保留本人入口。 */
    private Set<Long> resolveOneOnOneGroupTeacherIds(CoursePackageEntity coursePackage,
                                                     Map<Long, List<LessonOccurrenceEntity>> occurrenceMap,
                                                     Long requestedTeacherId,
                                                     PackageHoursContext hoursContext) {
        if (requestedTeacherId != null) {
            return Set.of(requestedTeacherId);
        }
        List<LessonOccurrenceEntity> occurrences = occurrenceMap.getOrDefault(
                coursePackage.getId(), Collections.emptyList());
        Set<Long> teacherIds = occurrences.stream()
                .map(LessonOccurrenceEntity::getPlannedTeacherId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        teacherIds.addAll(hoursContext.allocatedByTeacherByPackage()
                .getOrDefault(coursePackage.getId(), Collections.emptyMap()).keySet());
        teacherIds.addAll(hoursContext.unconfirmedScheduledByTeacherByPackage()
                .getOrDefault(coursePackage.getId(), Collections.emptyMap()).keySet());
        return teacherIds.isEmpty() ? Collections.singleton(null) : teacherIds;
    }

    private OneOnOneTeacherVO toOneOnOneTeacherVO(Long teacherId,
                                                  LinkedHashMap<Long, List<CoursePackageEntity>> studentPackages,
                                                  Map<Long, StudentEntity> studentMap,
                                                  Map<Long, TeacherEntity> teacherMap,
                                                  Map<Long, ClassroomEntity> classroomMap,
                                                  Map<Long, CourseProductEntity> courseProductMap,
                                                  Map<Long, EnrollmentOrderItemEntity> orderItemMap,
                                                  Map<Long, List<LessonOccurrenceEntity>> occurrenceMap,
                                                  LatestRecordIndex recordIndex,
                                                  PackageHoursContext hoursContext,
                                                  Map<String, String> statusNameMap,
                                                  Map<Long, List<PrivateLessonGroupMemberEntity>> groupMembersByPackage,
                                                  List<CoursePackageEntity> allPackages,
                                                  Map<Long, List<OneOnOneScheduleChangeVO>> scheduleChangesByPackage) {
        List<OneOnOneStudentVO> students = studentPackages.entrySet().stream()
                .map(entry -> toOneOnOneStudentVO(entry.getKey(), entry.getValue(), studentMap, teacherMap,
                        classroomMap, courseProductMap, orderItemMap, occurrenceMap, recordIndex,
                        hoursContext, statusNameMap,
                        groupMembersByPackage, allPackages, scheduleChangesByPackage, teacherId))
                .toList();
        TeacherEntity teacher = teacherId == null ? null : teacherMap.get(teacherId);
        int packageCount = studentPackages.values().stream().mapToInt(List::size).sum();
        List<CoursePackageEntity> packages = studentPackages.values().stream()
                .flatMap(List::stream)
                .toList();
        UnitSummary units = summarizeUnits(packages);
        return OneOnOneTeacherVO.builder()
                .teacherId(teacherId)
                .teacherNo(teacher == null ? null : teacher.getTeacherNo())
                .teacherName(teacher == null ? "Unassigned" : teacher.getTeacherName())
                .totalUnits(UnitConverter.toHoursDecimal(units.totalUnits()))
                .consumedUnits(UnitConverter.toHoursDecimal(units.consumedUnits()))
                .remainingUnits(UnitConverter.toHoursDecimal(units.remainingUnits()))
                .studentCount(students.size())
                .packageCount(packageCount)
                .students(students)
                .build();
    }

    private OneOnOneStudentVO toOneOnOneStudentVO(Long studentId,
                                                  List<CoursePackageEntity> packages,
                                                  Map<Long, StudentEntity> studentMap,
                                                  Map<Long, TeacherEntity> teacherMap,
                                                  Map<Long, ClassroomEntity> classroomMap,
                                                  Map<Long, CourseProductEntity> courseProductMap,
                                                  Map<Long, EnrollmentOrderItemEntity> orderItemMap,
                                                  Map<Long, List<LessonOccurrenceEntity>> occurrenceMap,
                                                  LatestRecordIndex recordIndex,
                                                  PackageHoursContext hoursContext,
                                                  Map<String, String> statusNameMap,
                                                  Map<Long, List<PrivateLessonGroupMemberEntity>> groupMembersByPackage,
                                                  List<CoursePackageEntity> allPackages,
                                                  Map<Long, List<OneOnOneScheduleChangeVO>> scheduleChangesByPackage,
                                                  Long teacherId) {
        StudentEntity student = studentMap.get(studentId);
        List<OneOnOnePackageVO> packageVOs = packages.stream()
                .map(item -> toOneOnOnePackageVO(item, teacherMap, classroomMap, courseProductMap,
                        orderItemMap, occurrenceMap, recordIndex, hoursContext, statusNameMap,
                        groupMembersByPackage, allPackages,
                        studentMap, teacherId, scheduleChangesByPackage))
                .toList();
        UnitSummary units = summarizeUnits(packages);
        return OneOnOneStudentVO.builder()
                .studentId(studentId)
                .studentNo(student == null ? null : student.getStudentNo())
                .studentName(student == null ? null : student.getStudentName())
                .grade(student == null ? null : student.getGrade())
                .school(student == null ? null : student.getSchool())
                .totalUnits(UnitConverter.toHoursDecimal(units.totalUnits()))
                .consumedUnits(UnitConverter.toHoursDecimal(units.consumedUnits()))
                .remainingUnits(UnitConverter.toHoursDecimal(units.remainingUnits()))
                .renewalReminder(packageVOs.stream().anyMatch(item -> Boolean.TRUE.equals(item.renewalReminder())))
                .packages(packageVOs)
                .build();
    }

    private OneOnOnePackageVO toOneOnOnePackageVO(CoursePackageEntity coursePackage,
                                                  Map<Long, TeacherEntity> teacherMap,
                                                  Map<Long, ClassroomEntity> classroomMap,
                                                  Map<Long, CourseProductEntity> courseProductMap,
                                                  Map<Long, EnrollmentOrderItemEntity> orderItemMap,
                                                  Map<Long, List<LessonOccurrenceEntity>> occurrenceMap,
                                                  LatestRecordIndex recordIndex,
                                                  PackageHoursContext hoursContext,
                                                  Map<String, String> statusNameMap,
                                                  Map<Long, List<PrivateLessonGroupMemberEntity>> groupMembersByPackage,
                                                  List<CoursePackageEntity> allPackages,
                                                  Map<Long, StudentEntity> studentMap,
                                                  Long teacherId,
                                                  Map<Long, List<OneOnOneScheduleChangeVO>> scheduleChangesByPackage) {
        // 课包可由多位老师轮流授课；详情只返回当前老师负责的排课记录，避免跨老师混在一起。
        List<LessonOccurrenceEntity> occurrences = occurrenceMap.getOrDefault(coursePackage.getId(), Collections.emptyList())
                .stream()
                .filter(item -> Objects.equals(item.getPlannedTeacherId(), teacherId))
                .toList();
        Map<Long, LessonRecordEntity> packageRecords = recordIndex.forPackage(coursePackage.getId(), occurrences);
        Map<Long, RecurringScheduleSlotEntity> recurringSlotMap = loadRecurringSlotMap(occurrences);
        List<OneOnOneLessonVO> lessons = occurrences.stream()
                .map(item -> toOneOnOneLessonVO(item, packageRecords.get(item.getId()), teacherMap, classroomMap,
                        recurringSlotMap, statusNameMap))
                .toList();
        EnrollmentOrderItemEntity orderItem = orderItemMap.get(coursePackage.getEnrollmentOrderItemId());
        int totalUnitsRaw = safeUnits(coursePackage.getTotalUnits());
        // 课包可累计多笔续费；赠课只从明确记录的 bonus_units 读取，不能减去最新一笔订单反推。
        int bonusUnits = safeUnits(coursePackage.getBonusUnits());
        int purchasedUnits = Math.max(0, totalUnitsRaw - bonusUnits);
        List<PrivateLessonGroupMemberEntity> groupMembers = groupMembersByPackage.getOrDefault(
                coursePackage.getId(), Collections.emptyList()).stream()
                .sorted(Comparator.comparing(PrivateLessonGroupMemberEntity::getParticipantIndex,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<Long, CoursePackageEntity> packageMap = allPackages.stream()
                .collect(Collectors.toMap(CoursePackageEntity::getId, Function.identity(), (left, right) -> left));
        List<OneOnOneParticipantVO> participants = groupMembers.stream()
                .map(member -> {
                    StudentEntity participant = studentMap.get(member.getStudentId());
                    CoursePackageEntity participantPackage = packageMap.get(member.getPackageId());
                    return OneOnOneParticipantVO.builder()
                            .studentId(member.getStudentId())
                            .studentName(participant == null ? null : participant.getStudentName())
                            .studentNo(participant == null ? null : participant.getStudentNo())
                            .packageId(member.getPackageId())
                            .priceMultiplier(member.getPriceMultiplier())
                            .remainingUnits(participantPackage == null ? null
                                    : UnitConverter.toHoursDecimal(participantPackage.getRemainingUnits()))
                            .build();
                }).toList();
        Long privateGroupId = groupMembers.isEmpty() ? null : groupMembers.get(0).getPrivateLessonGroupId();
        boolean primary = groupMembers.isEmpty() || Objects.equals(groupMembers.get(0).getPackageId(), coursePackage.getId());
        Long packageId = coursePackage.getId();
        Integer allocatedUnits = hoursContext.allocatedByPackage().get(packageId);
        List<OneOnOneTeacherHoursVO> teacherHours = toTeacherHoursVOs(packageId, teacherMap, hoursContext);
        int availablePackageUnits = Math.max(0, safeUnits(coursePackage.getRemainingUnits())
                - hoursContext.pendingAdminDeductionByPackage().getOrDefault(packageId, 0));
        int usableRemainingUnits = Math.max(0, availablePackageUnits
                - hoursContext.unresolvedPastByPackage().getOrDefault(packageId, 0));
        return OneOnOnePackageVO.builder()
                .packageId(coursePackage.getId())
                .packageNo(coursePackage.getPackageNo())
                .courseProductId(coursePackage.getCourseProductId())
                .courseProductName(coursePackage.getCourseProductId() == null
                        ? null
                        : Optional.ofNullable(courseProductMap.get(coursePackage.getCourseProductId()))
                        .map(CourseProductEntity::getProductName)
                        .orElse(null))
                .gradeBand(orderItem == null ? null : orderItem.getGradeBand())
                .packageName(coursePackage.getPackageName())
                .totalUnits(UnitConverter.toHoursDecimal(coursePackage.getTotalUnits()))
                .startingHours(UnitConverter.toHoursDecimal(
                        Math.max(0, totalUnitsRaw - safeUnits(coursePackage.getOpeningUsedUnits()))))
                .purchasedUnits(UnitConverter.toHoursDecimal(purchasedUnits))
                .bonusUnits(UnitConverter.toHoursDecimal(bonusUnits))
                .consumedUnits(UnitConverter.toHoursDecimal(coursePackage.getConsumedUnits()))
                .remainingUnits(UnitConverter.toHoursDecimal(coursePackage.getRemainingUnits()))
                .usableRemainingUnits(UnitConverter.toHoursDecimal(usableRemainingUnits))
                .availablePackageUnits(UnitConverter.toHoursDecimal(availablePackageUnits))
                .pendingDeductionUnits(UnitConverter.toHoursDecimal(
                        hoursContext.pendingProcessingByPackage().getOrDefault(packageId, 0)))
                .allocatedUnits(allocatedUnits == null ? null : UnitConverter.toHoursDecimal(allocatedUnits))
                .teacherHours(teacherHours)
                .scheduledReservedUnits(UnitConverter.toHoursDecimal(
                        hoursContext.scheduledByPackage().getOrDefault(packageId, 0)))
                .renewalReminder(safeUnits(coursePackage.getRemainingUnits()) <= renewalReminderUnits(coursePackage))
                .packageStatus(coursePackage.getPackageStatus())
                .startDate(coursePackage.getStartDate())
                .estimatedEndDate(coursePackage.getEstimatedEndDate())
                .privateLessonGroupId(privateGroupId)
                .participantCount(participants.isEmpty() ? 1 : participants.size())
                .privateLessonGroupPrimary(primary)
                .participants(participants)
                .lessons(lessons)
                .scheduleChanges(scheduleChangesByPackage.getOrDefault(packageId, Collections.emptyList()))
                .build();
    }

    private List<OneOnOneTeacherHoursVO> toTeacherHoursVOs(Long packageId,
                                                           Map<Long, TeacherEntity> teacherMap,
                                                           PackageHoursContext hoursContext) {
        Map<Long, Integer> allocatedByTeacher = hoursContext.allocatedByTeacherByPackage()
                .getOrDefault(packageId, Collections.emptyMap());
        Map<Long, Integer> deductedByTeacher = hoursContext.deductedByTeacherByPackage()
                .getOrDefault(packageId, Collections.emptyMap());
        Map<Long, Integer> unconfirmedByTeacher = hoursContext.unconfirmedScheduledByTeacherByPackage()
                .getOrDefault(packageId, Collections.emptyMap());
        Set<Long> teacherIds = new LinkedHashSet<>(allocatedByTeacher.keySet());
        teacherIds.addAll(deductedByTeacher.keySet());
        teacherIds.addAll(unconfirmedByTeacher.keySet());
        return teacherIds.stream()
                .map(teacherId -> {
                    int allocated = allocatedByTeacher.getOrDefault(teacherId, 0);
                    int deducted = deductedByTeacher.getOrDefault(teacherId, 0);
                    // 当前周期未消课余额由同周期分配额扣除已确认消课得到，不能受课次生成缺漏影响。
                    int remaining = allocated - deducted;
                    TeacherEntity teacher = teacherMap.get(teacherId);
                    return OneOnOneTeacherHoursVO.builder()
                            .teacherId(teacherId)
                            .teacherName(teacher == null ? null : teacher.getTeacherName())
                            .allocatedUnits(UnitConverter.toHoursDecimal(allocated))
                            .deductedUnits(UnitConverter.toHoursDecimal(deducted))
                            .remainingUnits(UnitConverter.toHoursDecimal(remaining))
                            .build();
                })
                .sorted(Comparator.comparing(item -> item.teacherName() == null ? "" : item.teacherName(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private int renewalReminderUnits(CoursePackageEntity coursePackage) {
        return 8;
    }

    private OneOnOneLessonVO toOneOnOneLessonVO(LessonOccurrenceEntity occurrence,
                                                LessonRecordEntity record,
                                                Map<Long, TeacherEntity> teacherMap,
                                                Map<Long, ClassroomEntity> classroomMap,
                                                Map<Long, RecurringScheduleSlotEntity> recurringSlotMap,
                                                Map<String, String> statusNameMap) {
        // 过去课次只读取自身或消课记录快照，避免当前周排课覆盖历史老师和科目。
        boolean started = occurrence.getPlannedStartAt() != null
                && occurrence.getPlannedStartAt().isBefore(AppTime.nowLocalDateTime());
        Long plannedTeacherId = record != null && record.getPlannedTeacherId() != null
                ? record.getPlannedTeacherId() : occurrence.getPlannedTeacherId();
        if (plannedTeacherId == null && !started) {
            plannedTeacherId = resolveSlotTeacherId(occurrence, recurringSlotMap);
        }
        Long actualTeacherId = record == null ? null : record.getActualTeacherId();
        Long plannedClassroomId = record != null && record.getPlannedClassroomId() != null
                ? record.getPlannedClassroomId() : occurrence.getPlannedClassroomId();
        String deliveryMode = occurrence.getDeliveryMode();
        Long actualClassroomId = record == null ? null : record.getActualClassroomId();
        TeacherEntity plannedTeacher = plannedTeacherId == null ? null : teacherMap.get(plannedTeacherId);
        TeacherEntity actualTeacher = actualTeacherId == null ? null : teacherMap.get(actualTeacherId);
        ClassroomEntity plannedClassroom = plannedClassroomId == null ? null : classroomMap.get(plannedClassroomId);
        ClassroomEntity actualClassroom = actualClassroomId == null ? null : classroomMap.get(actualClassroomId);
        String resultStatus = record == null ? null : record.getResultStatus();
        return OneOnOneLessonVO.builder()
                .occurrenceId(occurrence.getId())
                .lessonRecordId(record == null ? null : record.getId())
                .lessonNo(occurrence.getLessonNo())
                .packageId(occurrence.getPackageId())
                .scheduleRuleId(occurrence.getScheduleRuleId())
                .plannedTeacherId(plannedTeacherId)
                .plannedTeacherName(plannedTeacher == null ? null : plannedTeacher.getTeacherName())
                .subjectName(resolveOneOnOneSubjectName(occurrence, recurringSlotMap, started))
                .deliveryMode(deliveryMode)
                .plannedClassroomId(plannedClassroomId)
                .plannedClassroomName("ONLINE".equals(deliveryMode) ? "Online" : plannedClassroom == null ? null : plannedClassroom.getRoomName())
                .plannedStartAt(occurrence.getPlannedStartAt())
                .plannedEndAt(occurrence.getPlannedEndAt())
                .plannedUnits(UnitConverter.toHoursDecimal(occurrence.getPlannedUnits()))
                .planningStatus(occurrence.getPlanningStatus())
                .resultStatus(resultStatus)
                .resultStatusName(resultStatus == null ? null : statusNameMap.getOrDefault(resultStatus, resultStatus))
                .confirmStage(record == null ? null : record.getConfirmStage())
                .actualTeacherId(actualTeacherId)
                .actualTeacherName(actualTeacher == null ? null : actualTeacher.getTeacherName())
                .actualClassroomId(actualClassroomId)
                .actualClassroomName(actualClassroom == null ? null : actualClassroom.getRoomName())
                .actualStartAt(record == null ? null : record.getActualStartAt())
                .actualEndAt(record == null ? null : record.getActualEndAt())
                .actualUnits(record == null ? null : UnitConverter.toHoursDecimal(record.getActualUnits()))
                .deductedUnits(record == null ? null : UnitConverter.toHoursDecimal(record.getDeductedUnits()))
                .teacherConfirmedAt(record == null ? null : record.getTeacherConfirmedAt())
                .adminConfirmedAt(record == null ? null : record.getAdminConfirmedAt())
                .rejectReason(record == null ? null : record.getRejectReason())
                .notes(record == null ? occurrence.getNotes() : record.getNotes())
                .build();
    }

    /** 读取一对一课次科目：旧课次只在关联时段未晚于课次生成时才允许安全回退。 */
    private String resolveOneOnOneSubjectName(LessonOccurrenceEntity occurrence,
                                               Map<Long, RecurringScheduleSlotEntity> recurringSlotMap,
                                               boolean started) {
        if (!started || StringUtils.hasText(occurrence.getSubjectName())) {
            return resolveOccurrenceSubjectName(occurrence, recurringSlotMap);
        }

        RecurringScheduleSlotEntity slot = recurringSlotMap.get(occurrence.getScheduleSlotId());
        if (slot == null) {
            return null;
        }
        LocalDateTime occurrenceCreatedAt = occurrence.getCreatedAt();
        LocalDateTime slotUpdatedAt = slot.getUpdatedAt();
        // 软删时段是历史版本；旧库缺少审计时间时也沿用该版本数据，不能用活跃时段猜测历史名称。
        if (Integer.valueOf(1).equals(slot.getIsDeleted())
                && (occurrenceCreatedAt == null || slotUpdatedAt == null)) {
            return StringUtils.hasText(slot.getSubjectName()) ? slot.getSubjectName() : null;
        }
        if (occurrenceCreatedAt == null || slotUpdatedAt == null || slotUpdatedAt.isAfter(occurrenceCreatedAt)) {
            return null;
        }
        return StringUtils.hasText(slot.getSubjectName()) ? slot.getSubjectName() : null;
    }

    private Map<Long, RecurringScheduleSlotEntity> loadRecurringSlotMap(List<LessonOccurrenceEntity> occurrences) {
        List<Long> slotIds = occurrences.stream()
                .map(LessonOccurrenceEntity::getScheduleSlotId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (slotIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return recurringScheduleSlotMapper.selectIncludingDeletedByIds(slotIds)
                    .stream()
                    .collect(Collectors.toMap(RecurringScheduleSlotEntity::getId, Function.identity()));
        } catch (RuntimeException ex) {
            log.warn("Unable to load recurring schedule slot metadata for one-on-one deductions; "
                    + "returning lessons without slot lesson names.", ex);
            return Collections.emptyMap();
        }
    }

    private String resolveOccurrenceSubjectName(LessonOccurrenceEntity occurrence,
                                                Map<Long, RecurringScheduleSlotEntity> recurringSlotMap) {
        return resolveOccurrenceSubjectName(occurrence, recurringSlotMap, Collections.emptyMap());
    }

    private Long resolveSlotTeacherId(LessonOccurrenceEntity occurrence,
                                      Map<Long, RecurringScheduleSlotEntity> recurringSlotMap) {
        if (occurrence == null || occurrence.getScheduleSlotId() == null || occurrence.getClassGroupId() != null) {
            return null;
        }
        RecurringScheduleSlotEntity slot = recurringSlotMap.get(occurrence.getScheduleSlotId());
        return slot == null ? null : slot.getTeacherId();
    }

    private Map<Long, ClassGroupScheduleSlotEntity> loadClassSlotMap(List<LessonOccurrenceEntity> occurrences) {
        Set<Long> slotIds = occurrences.stream()
                .filter(item -> item.getClassGroupId() != null)
                .map(LessonOccurrenceEntity::getScheduleSlotId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (slotIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return listSlotsByIds(slotIds);
        } catch (RuntimeException ex) {
            log.warn("Unable to load class schedule slot metadata; returning class lessons without taught subjects.", ex);
            return Collections.emptyMap();
        }
    }

    private Set<Long> collectCourseProductIds(Collection<CoursePackageEntity> packages,
                                              Collection<ClassGroupEntity> classGroups) {
        Set<Long> productIds = new LinkedHashSet<>();
        packages.stream()
                .map(CoursePackageEntity::getCourseProductId)
                .filter(Objects::nonNull)
                .forEach(productIds::add);
        classGroups.stream()
                .map(ClassGroupEntity::getProductId)
                .filter(Objects::nonNull)
                .forEach(productIds::add);
        return productIds;
    }

    private String resolveProductName(CoursePackageEntity coursePackage,
                                      ClassGroupEntity classGroup,
                                      Map<Long, CourseProductEntity> courseProductMap) {
        Long productId = coursePackage != null ? coursePackage.getCourseProductId() : null;
        if (productId == null && classGroup != null) {
            productId = classGroup.getProductId();
        }
        CourseProductEntity product = productId == null ? null : courseProductMap.get(productId);
        return product == null ? null : product.getProductName();
    }

    private String resolveOccurrenceSubjectName(LessonOccurrenceEntity occurrence,
                                                Map<Long, RecurringScheduleSlotEntity> recurringSlotMap,
                                                Map<Long, ClassGroupScheduleSlotEntity> classSlotMap) {
        if (StringUtils.hasText(occurrence.getSubjectName())) {
            return occurrence.getSubjectName();
        }
        if (occurrence.getScheduleSlotId() == null) {
            return null;
        }
        if (occurrence.getClassGroupId() != null) {
            ClassGroupScheduleSlotEntity classSlot = classSlotMap.get(occurrence.getScheduleSlotId());
            return classSlot == null || !StringUtils.hasText(classSlot.getSubjectName()) ? null : classSlot.getSubjectName();
        }
        RecurringScheduleSlotEntity slot = recurringSlotMap.get(occurrence.getScheduleSlotId());
        return slot == null || !StringUtils.hasText(slot.getSubjectName()) ? null : slot.getSubjectName();
    }

    private int safeUnits(Integer units) {
        return units == null ? 0 : units;
    }

    private UnitSummary summarizeUnits(Collection<CoursePackageEntity> packages) {
        return new UnitSummary(
                sumPackageUnits(packages, CoursePackageEntity::getTotalUnits),
                sumPackageUnits(packages, CoursePackageEntity::getConsumedUnits),
                sumPackageUnits(packages, CoursePackageEntity::getRemainingUnits)
        );
    }

    private int sumPackageUnits(Collection<CoursePackageEntity> packages, Function<CoursePackageEntity, Integer> getter) {
        return packages.stream()
                .map(getter)
                .mapToInt(this::safeUnits)
                .sum();
    }

    private record UnitSummary(int totalUnits, int consumedUnits, int remainingUnits) {
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

    private Map<Long, CourseProductEntity> listCourseProductsByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<CourseProductEntity> products = courseProductMapper.selectBatchIds(ids);
        if (products == null || products.isEmpty()) {
            return Collections.emptyMap();
        }
        return products.stream()
                .collect(Collectors.toMap(CourseProductEntity::getId, item -> item));
    }

    private Map<Long, EnrollmentOrderItemEntity> listEnrollmentOrderItemsByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return enrollmentOrderItemMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(EnrollmentOrderItemEntity::getId, item -> item));
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

    private Map<Long, ClassGroupEntity> listClassGroupsByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return classGroupMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ClassGroupEntity::getId, item -> item));
    }

}
