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
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
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
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.service.LessonOccurrenceService;
import com.classdeduction.system.modules.schedule.vo.ClassroomOccupancyVO;
import com.classdeduction.system.modules.schedule.vo.LessonOccurrenceVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOneLessonVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOnePackageVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOneStudentVO;
import com.classdeduction.system.modules.schedule.vo.OneOnOneTeacherVO;
import com.classdeduction.system.modules.schedule.vo.PendingClassroomAssignmentVO;
import com.classdeduction.system.modules.schedule.vo.ScheduleConflictDetailVO;
import com.classdeduction.system.modules.schedule.vo.StudentScheduleSlotVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
public class LessonOccurrenceServiceImpl implements LessonOccurrenceService {

    private static final int DEFAULT_OCCURRENCE_WINDOW_DAYS = 30;
    private static final int DEFAULT_OCCUPANCY_WINDOW_DAYS = 7;
    private static final int MAX_QUERY_WINDOW_DAYS = 180;
    static final int MAX_OCCURRENCE_WINDOW_DAYS = 730; // 2年，用于按课包查询

    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonRecordMapper lessonRecordMapper;
    private final LessonStatusRuleMapper lessonStatusRuleMapper;
    private final CoursePackageMapper coursePackageMapper;
    private final EnrollmentOrderItemMapper enrollmentOrderItemMapper;
    private final PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final CourseProductMapper courseProductMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;

    @Override
    public PageResponse<LessonOccurrenceVO> pageOccurrences(LessonOccurrencePageQuery query) {
        return pageOccurrences(query, null);
    }

    @Override
    public PageResponse<LessonOccurrenceVO> pageOccurrences(LessonOccurrencePageQuery query, CurrentUserResponse currentUser) {
        applyScheduleScope(query, currentUser);
        // 按课包查询时放宽时间窗口限制（课包本身有起止边界）
        int maxDays = query.getPackageId() != null ? MAX_OCCURRENCE_WINDOW_DAYS : MAX_QUERY_WINDOW_DAYS;
        LocalDateTime[] window = normalizeWindow(query.getStartAt(), query.getEndAt(), DEFAULT_OCCURRENCE_WINDOW_DAYS, maxDays);
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
                    List<Long> enrolledClassGroupIds = classEnrollmentMapper.selectList(
                                    new LambdaQueryWrapper<ClassEnrollmentEntity>()
                                            .eq(ClassEnrollmentEntity::getStudentId, query.getStudentId())
                                            .eq(ClassEnrollmentEntity::getStatus, "ACTIVE"))
                            .stream().map(ClassEnrollmentEntity::getClassId)
                            .filter(Objects::nonNull).toList();
                    if (!enrolledClassGroupIds.isEmpty()) {
                        studentWrapper.or().in(LessonOccurrenceEntity::getClassGroupId, enrolledClassGroupIds);
                    }
                })
                .eq(query.getTeacherId() != null, LessonOccurrenceEntity::getPlannedTeacherId, query.getTeacherId())
                .eq(query.getClassroomId() != null, LessonOccurrenceEntity::getPlannedClassroomId, query.getClassroomId())
                .eq(query.getClassGroupId() != null, LessonOccurrenceEntity::getClassGroupId, query.getClassGroupId())
                .ge(LessonOccurrenceEntity::getPlannedStartAt, window[0])
                .lt(LessonOccurrenceEntity::getPlannedStartAt, window[1])
                .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt);

        if (singleStatus != null) {
            wrapper.eq(LessonOccurrenceEntity::getPlanningStatus, singleStatus);
        } else if (planningStatuses != null && !planningStatuses.isEmpty()) {
            wrapper.in(LessonOccurrenceEntity::getPlanningStatus, planningStatuses);
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
    @Transactional(rollbackFor = Exception.class)
    public PendingClassroomAssignmentVO backfillPendingClassroom(PendingClassroomBackfillRequest request) {
        ClassroomEntity classroom = requireActiveClassroomForUpdate(request.getClassroomId());
        String targetType = request.getTargetType() == null ? "" : request.getTargetType().trim().toUpperCase(Locale.ROOT);
        if ("CLASS_GROUP".equals(targetType)) {
            return backfillClassGroupClassroom(request.getTargetId(), classroom);
        }
        if ("OCCURRENCE".equals(targetType)) {
            return backfillOccurrenceClassroom(request.getTargetId(), classroom);
        }
        throw new BusinessException("INVALID_TARGET_TYPE", "Target type must be CLASS_GROUP or OCCURRENCE.");
    }

    private PendingClassroomAssignmentVO backfillClassGroupClassroom(Long classGroupId, ClassroomEntity classroom) {
        ClassGroupEntity classGroup = classGroupMapper.selectById(classGroupId);
        if (classGroup == null || !"ACTIVE".equals(classGroup.getStatus())) {
            throw new BusinessException("CLASS_GROUP_NOT_FOUND", "Active class group not found.");
        }

        LocalDateTime now = AppTime.nowLocalDateTime();
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classGroupId)
                        .isNull(LessonOccurrenceEntity::getPlannedClassroomId)
                        .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                        .ge(LessonOccurrenceEntity::getPlannedEndAt, now)
                        .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));
        Set<Long> occurrenceIds = occurrences.stream()
                .map(LessonOccurrenceEntity::getId)
                .collect(Collectors.toSet());
        for (LessonOccurrenceEntity occurrence : occurrences) {
            occurrence.setPlannedClassroomId(classroom.getId());
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

    private PendingClassroomAssignmentVO backfillOccurrenceClassroom(Long occurrenceId, ClassroomEntity classroom) {
        LessonOccurrenceEntity occurrence = lessonOccurrenceMapper.selectById(occurrenceId);
        if (occurrence == null) {
            throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "Lesson occurrence not found.");
        }
        if ("CANCELLED".equals(occurrence.getPlanningStatus())) {
            throw new BusinessException("LESSON_OCCURRENCE_CANCELLED", "Cancelled lesson occurrences cannot be backfilled.");
        }
        if (occurrence.getPlannedClassroomId() != null) {
            throw new BusinessException("CLASSROOM_ALREADY_ASSIGNED", "This lesson occurrence already has a classroom.");
        }

        occurrence.setPlannedClassroomId(classroom.getId());
        ensureNoClassroomBackfillConflicts(List.of(occurrence), Set.of(occurrence.getId()));
        occurrence.setConflictChecked(1);
        requireOccurrenceUpdate(lessonOccurrenceMapper.updateById(occurrence));

        PendingContext context = loadPendingContext(List.of(occurrence), List.of());
        return toPendingOccurrenceVO(occurrence, context);
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
        classGroups.stream()
                .map(ClassGroupEntity::getId)
                .filter(Objects::nonNull)
                .forEach(classGroupIds::add);
        return new PendingContext(
                listPackagesByIds(packageIds),
                listStudentsByIds(studentIds),
                listTeachersByIds(teacherIds),
                listClassGroupsByIds(classGroupIds)
        );
    }

    private PendingClassroomAssignmentVO toPendingClassGroupVO(ClassGroupEntity classGroup,
                                                               LessonOccurrenceEntity nextOccurrence,
                                                               PendingContext context) {
        TeacherEntity teacher = classGroup.getTeacherId() == null ? null : context.teacherMap().get(classGroup.getTeacherId());
        return PendingClassroomAssignmentVO.builder()
                .targetType("CLASS_GROUP")
                .targetId(classGroup.getId())
                .occurrenceId(nextOccurrence == null ? null : nextOccurrence.getId())
                .lessonNo(nextOccurrence == null ? null : nextOccurrence.getLessonNo())
                .classGroupId(classGroup.getId())
                .className(classGroup.getClassName())
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
        return PendingClassroomAssignmentVO.builder()
                .targetType("OCCURRENCE")
                .targetId(occurrence.getId())
                .occurrenceId(occurrence.getId())
                .lessonNo(occurrence.getLessonNo())
                .classGroupId(occurrence.getClassGroupId())
                .className(classGroup == null ? null : classGroup.getClassName())
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

    private void ensureNoInternalClassroomConflicts(List<LessonOccurrenceEntity> occurrences) {
        for (int i = 0; i < occurrences.size(); i++) {
            LessonOccurrenceEntity left = occurrences.get(i);
            for (int j = i + 1; j < occurrences.size(); j++) {
                LessonOccurrenceEntity right = occurrences.get(j);
                if (overlaps(left, right)) {
                    throw new BusinessException("SCHEDULE_CONFLICT",
                            "Pending lessons overlap and cannot be assigned to the same classroom together.",
                            List.of(toClassroomConflictDetail(left.getPlannedClassroomId(), right)));
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
                        List.of(toClassroomConflictDetail(occurrence.getPlannedClassroomId(), conflicts.get(0))));
            }
        }
    }

    private ScheduleConflictDetailVO toClassroomConflictDetail(Long classroomId, LessonOccurrenceEntity existing) {
        ClassroomEntity classroom = classroomId == null ? null : classroomMapper.selectById(classroomId);
        return ScheduleConflictDetailVO.builder()
                .resourceType("CLASSROOM")
                .resourceId(classroomId)
                .resourceName(classroom == null ? null : classroom.getRoomName())
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
            Map<Long, ClassGroupEntity> classGroupMap
    ) {
    }

    @Override
    public List<StudentScheduleSlotVO> getActiveStudentSchedule(Long studentId) {
        LocalDateTime now = AppTime.nowLocalDateTime();
        LocalDateTime futureWindowEnd = now.plusMonths(6);

        LambdaQueryWrapper<LessonOccurrenceEntity> wrapper = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(LessonOccurrenceEntity::getStudentId, studentId)
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

        List<Long> teacherPackageIds = teacherId == null ? Collections.emptyList() : findOneOnOnePackageIdsByPrimaryTeacher(teacherId);
        if (teacherId != null && teacherPackageIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> matchedStudentIds = findMatchedStudentIds(safeQuery.getKeyword());

        LambdaQueryWrapper<CoursePackageEntity> wrapper = new LambdaQueryWrapper<CoursePackageEntity>()
                .and(teachingMode -> teachingMode
                        .eq(CoursePackageEntity::getTeachingMode, "ONE_ON_ONE")
                        .or()
                        .isNull(CoursePackageEntity::getTeachingMode))
                .eq(CoursePackageEntity::getPackageStatus, "ACTIVE")
                .in(teacherId != null, CoursePackageEntity::getId, teacherPackageIds)
                .eq(studentId != null, CoursePackageEntity::getStudentId, studentId)
                .eq(safeQuery.getPackageId() != null, CoursePackageEntity::getId, safeQuery.getPackageId())
                .and(StringUtils.hasText(safeQuery.getKeyword()), keyword -> keyword
                        .like(CoursePackageEntity::getPackageNo, safeQuery.getKeyword())
                        .or()
                        .like(CoursePackageEntity::getPackageName, safeQuery.getKeyword())
                        .or(!matchedStudentIds.isEmpty(), nested -> nested.in(CoursePackageEntity::getStudentId, matchedStudentIds)))
                .orderByDesc(CoursePackageEntity::getCreatedAt);
        List<CoursePackageEntity> packages = coursePackageMapper.selectList(wrapper);
        if (packages.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> packageIds = packages.stream().map(CoursePackageEntity::getId).toList();
        Map<Long, PackageTeacherAssignmentEntity> assignmentMap = loadPrimaryAssignments(packageIds);
        Map<Long, List<LessonOccurrenceEntity>> occurrenceMap = loadOneOnOneOccurrenceMap(packageIds, safeQuery.getStartAt(), safeQuery.getEndAt());
        Set<Long> occurrenceIds = occurrenceMap.values().stream()
                .flatMap(List::stream)
                .map(LessonOccurrenceEntity::getId)
                .collect(Collectors.toSet());
        Map<Long, LessonRecordEntity> recordMap = loadLatestRecordByOccurrence(occurrenceIds);

        Map<Long, StudentEntity> studentMap = listStudentsByIds(packages.stream()
                .map(CoursePackageEntity::getStudentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, TeacherEntity> teacherMap = loadOneOnOneTeachers(packages, assignmentMap, occurrenceMap, recordMap);
        Map<Long, ClassroomEntity> classroomMap = loadOneOnOneClassrooms(occurrenceMap, recordMap);
        Map<Long, CourseProductEntity> courseProductMap = listCourseProductsByIds(packages.stream()
                .map(CoursePackageEntity::getCourseProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, EnrollmentOrderItemEntity> orderItemMap = listEnrollmentOrderItemsByIds(packages.stream()
                .map(CoursePackageEntity::getEnrollmentOrderItemId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<String, String> statusNameMap = loadStatusNames(recordMap.values());

        LinkedHashMap<Long, LinkedHashMap<Long, List<CoursePackageEntity>>> grouped = new LinkedHashMap<>();
        for (CoursePackageEntity coursePackage : packages) {
            Long groupTeacherId = resolveOneOnOneGroupTeacherId(coursePackage, assignmentMap, occurrenceMap);
            grouped.computeIfAbsent(groupTeacherId, key -> new LinkedHashMap<>())
                    .computeIfAbsent(coursePackage.getStudentId(), key -> new ArrayList<>())
                    .add(coursePackage);
        }

        return grouped.entrySet().stream()
                .map(teacherEntry -> toOneOnOneTeacherVO(teacherEntry.getKey(), teacherEntry.getValue(),
                        studentMap, teacherMap, classroomMap, courseProductMap, orderItemMap,
                        occurrenceMap, recordMap, statusNameMap))
                .toList();
    }

    private List<LessonOccurrenceVO> toOccurrenceVOs(List<LessonOccurrenceEntity> occurrences) {
        Set<Long> occurrenceIds = occurrences.stream().map(LessonOccurrenceEntity::getId).collect(Collectors.toSet());
        Map<Long, CoursePackageEntity> packageMap = listPackagesByIds(occurrences.stream().map(LessonOccurrenceEntity::getPackageId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, StudentEntity> studentMap = listStudentsByIds(occurrences.stream().map(LessonOccurrenceEntity::getStudentId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, TeacherEntity> teacherMap = listTeachersByIds(occurrences.stream().map(LessonOccurrenceEntity::getPlannedTeacherId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, ClassroomEntity> classroomMap = listClassroomsByIds(occurrences.stream().map(LessonOccurrenceEntity::getPlannedClassroomId).filter(Objects::nonNull).collect(Collectors.toSet()));
        // 班课名称
        Map<Long, ClassGroupEntity> classGroupMap = listClassGroupsByIds(occurrences.stream().map(LessonOccurrenceEntity::getClassGroupId).filter(Objects::nonNull).collect(Collectors.toSet()));

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
                            .classGroupId(item.getClassGroupId())
                            .className(classGroup == null ? null : classGroup.getClassName())
                            .studentId(item.getStudentId())
                            .studentName(student == null ? null : student.getStudentName())
                            .scheduleRuleId(item.getScheduleRuleId())
                            .scheduleSlotId(item.getScheduleSlotId())
                            .occurrenceType(item.getOccurrenceType())
                            .plannedTeacherId(item.getPlannedTeacherId())
                            .plannedTeacherName(teacher == null ? null : teacher.getTeacherName())
                            .plannedClassroomId(item.getPlannedClassroomId())
                            .plannedClassroomName(classroom == null ? null : classroom.getRoomName())
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
                            .build();
                })
                .toList();
    }

    private LocalDateTime[] normalizeWindow(LocalDateTime startAt, LocalDateTime endAt, int defaultDays) {
        return normalizeWindow(startAt, endAt, defaultDays, MAX_QUERY_WINDOW_DAYS);
    }

    private LocalDateTime[] normalizeWindow(LocalDateTime startAt, LocalDateTime endAt, int defaultDays, int maxDays) {
        LocalDateTime resolvedStart = startAt != null ? startAt : AppTime.nowLocalDateTime().toLocalDate().atStartOfDay();
        LocalDateTime resolvedEnd = endAt != null ? endAt : resolvedStart.plusDays(defaultDays);
        if (!resolvedEnd.isAfter(resolvedStart)) {
            throw new BusinessException("INVALID_QUERY_WINDOW", "Query end time must be later than the start time.");
        }
        // 按日期比较（忽略时分秒），避免 endAt=23:59:59 时误判超限
        if (resolvedStart.toLocalDate().plusDays(maxDays).isBefore(resolvedEnd.toLocalDate())) {
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
            if (Objects.equals(entity.getStudentId(), currentUser.studentId())) {
                return;
            }
            if (entity.getClassGroupId() != null && isStudentEnrolledInClass(currentUser.studentId(), entity.getClassGroupId())) {
                return;
            }
            throw new BusinessException("FORBIDDEN", "Students can only view their own calendar.");
        }
    }

    private boolean isStudentEnrolledInClass(Long studentId, Long classGroupId) {
        return classEnrollmentMapper.selectCount(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                .eq(ClassEnrollmentEntity::getStudentId, studentId)
                .eq(ClassEnrollmentEntity::getClassId, classGroupId)
                .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")) > 0;
    }

    private List<Long> findOneOnOnePackageIdsByPrimaryTeacher(Long teacherId) {
        if (teacherId == null) {
            return Collections.emptyList();
        }
        return packageTeacherAssignmentMapper.selectList(new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                        .eq(PackageTeacherAssignmentEntity::getTeacherId, teacherId)
                        .eq(PackageTeacherAssignmentEntity::getIsPrimary, 1)
                        .eq(PackageTeacherAssignmentEntity::getAssignmentStatus, "ACTIVE"))
                .stream()
                .map(PackageTeacherAssignmentEntity::getPackageId)
                .distinct()
                .toList();
    }

    private Map<Long, PackageTeacherAssignmentEntity> loadPrimaryAssignments(List<Long> packageIds) {
        if (packageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PackageTeacherAssignmentEntity> assignments = packageTeacherAssignmentMapper.selectList(
                new LambdaQueryWrapper<PackageTeacherAssignmentEntity>()
                        .in(PackageTeacherAssignmentEntity::getPackageId, packageIds)
                        .eq(PackageTeacherAssignmentEntity::getIsPrimary, 1)
                        .eq(PackageTeacherAssignmentEntity::getAssignmentStatus, "ACTIVE")
                        .orderByDesc(PackageTeacherAssignmentEntity::getEffectiveFrom));
        Map<Long, PackageTeacherAssignmentEntity> result = new LinkedHashMap<>();
        for (PackageTeacherAssignmentEntity assignment : assignments) {
            result.putIfAbsent(assignment.getPackageId(), assignment);
        }
        return result;
    }

    private Map<Long, List<LessonOccurrenceEntity>> loadOneOnOneOccurrenceMap(List<Long> packageIds,
                                                                               LocalDateTime startAt,
                                                                               LocalDateTime endAt) {
        if (packageIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getPackageId, packageIds)
                .ge(startAt != null, LessonOccurrenceEntity::getPlannedStartAt, startAt)
                .le(endAt != null, LessonOccurrenceEntity::getPlannedStartAt, endAt)
                .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));
        return occurrences.stream().collect(Collectors.groupingBy(LessonOccurrenceEntity::getPackageId, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<Long, LessonRecordEntity> loadLatestRecordByOccurrence(Set<Long> occurrenceIds) {
        if (occurrenceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<LessonRecordEntity> records = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                .in(LessonRecordEntity::getOccurrenceId, occurrenceIds));
        records.sort(Comparator
                .comparing(this::recordSortTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(LessonRecordEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        Map<Long, LessonRecordEntity> result = new LinkedHashMap<>();
        for (LessonRecordEntity record : records) {
            result.putIfAbsent(record.getOccurrenceId(), record);
        }
        return result;
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

    private Map<Long, TeacherEntity> loadOneOnOneTeachers(List<CoursePackageEntity> packages,
                                                          Map<Long, PackageTeacherAssignmentEntity> assignmentMap,
                                                          Map<Long, List<LessonOccurrenceEntity>> occurrenceMap,
                                                          Map<Long, LessonRecordEntity> recordMap) {
        Set<Long> teacherIds = new LinkedHashSet<>();
        packages.stream()
                .map(item -> assignmentMap.get(item.getId()))
                .filter(Objects::nonNull)
                .map(PackageTeacherAssignmentEntity::getTeacherId)
                .filter(Objects::nonNull)
                .forEach(teacherIds::add);
        occurrenceMap.values().stream()
                .flatMap(List::stream)
                .map(LessonOccurrenceEntity::getPlannedTeacherId)
                .filter(Objects::nonNull)
                .forEach(teacherIds::add);
        recordMap.values().forEach(record -> {
            if (record.getPlannedTeacherId() != null) {
                teacherIds.add(record.getPlannedTeacherId());
            }
            if (record.getActualTeacherId() != null) {
                teacherIds.add(record.getActualTeacherId());
            }
        });
        return listTeachersByIds(teacherIds);
    }

    private Map<Long, ClassroomEntity> loadOneOnOneClassrooms(Map<Long, List<LessonOccurrenceEntity>> occurrenceMap,
                                                              Map<Long, LessonRecordEntity> recordMap) {
        Set<Long> classroomIds = new LinkedHashSet<>();
        occurrenceMap.values().stream()
                .flatMap(List::stream)
                .map(LessonOccurrenceEntity::getPlannedClassroomId)
                .filter(Objects::nonNull)
                .forEach(classroomIds::add);
        recordMap.values().forEach(record -> {
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

    private Long resolveOneOnOneGroupTeacherId(CoursePackageEntity coursePackage,
                                               Map<Long, PackageTeacherAssignmentEntity> assignmentMap,
                                               Map<Long, List<LessonOccurrenceEntity>> occurrenceMap) {
        PackageTeacherAssignmentEntity assignment = assignmentMap.get(coursePackage.getId());
        if (assignment != null && assignment.getTeacherId() != null) {
            return assignment.getTeacherId();
        }
        return occurrenceMap.getOrDefault(coursePackage.getId(), Collections.emptyList()).stream()
                .map(LessonOccurrenceEntity::getPlannedTeacherId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private OneOnOneTeacherVO toOneOnOneTeacherVO(Long teacherId,
                                                  LinkedHashMap<Long, List<CoursePackageEntity>> studentPackages,
                                                  Map<Long, StudentEntity> studentMap,
                                                  Map<Long, TeacherEntity> teacherMap,
                                                  Map<Long, ClassroomEntity> classroomMap,
                                                  Map<Long, CourseProductEntity> courseProductMap,
                                                  Map<Long, EnrollmentOrderItemEntity> orderItemMap,
                                                  Map<Long, List<LessonOccurrenceEntity>> occurrenceMap,
                                                  Map<Long, LessonRecordEntity> recordMap,
                                                  Map<String, String> statusNameMap) {
        List<OneOnOneStudentVO> students = studentPackages.entrySet().stream()
                .map(entry -> toOneOnOneStudentVO(entry.getKey(), entry.getValue(), studentMap, teacherMap,
                        classroomMap, courseProductMap, orderItemMap, occurrenceMap, recordMap, statusNameMap))
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
                                                  Map<Long, LessonRecordEntity> recordMap,
                                                  Map<String, String> statusNameMap) {
        StudentEntity student = studentMap.get(studentId);
        List<OneOnOnePackageVO> packageVOs = packages.stream()
                .map(item -> toOneOnOnePackageVO(item, teacherMap, classroomMap, courseProductMap,
                        orderItemMap, occurrenceMap, recordMap, statusNameMap))
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
                                                  Map<Long, LessonRecordEntity> recordMap,
                                                  Map<String, String> statusNameMap) {
        List<OneOnOneLessonVO> lessons = occurrenceMap.getOrDefault(coursePackage.getId(), Collections.emptyList()).stream()
                .map(item -> toOneOnOneLessonVO(item, recordMap.get(item.getId()), teacherMap, classroomMap, statusNameMap))
                .toList();
        EnrollmentOrderItemEntity orderItem = orderItemMap.get(coursePackage.getEnrollmentOrderItemId());
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
                .subjectName(coursePackage.getSubjectName())
                .totalUnits(UnitConverter.toHoursDecimal(coursePackage.getTotalUnits()))
                .consumedUnits(UnitConverter.toHoursDecimal(coursePackage.getConsumedUnits()))
                .remainingUnits(UnitConverter.toHoursDecimal(coursePackage.getRemainingUnits()))
                .defaultLessonUnits(UnitConverter.toHoursDecimal(coursePackage.getDefaultLessonUnits()))
                .renewalReminder(safeUnits(coursePackage.getRemainingUnits()) <= renewalReminderUnits(coursePackage))
                .packageStatus(coursePackage.getPackageStatus())
                .startDate(coursePackage.getStartDate())
                .estimatedEndDate(coursePackage.getEstimatedEndDate())
                .lessons(lessons)
                .build();
    }

    private int renewalReminderUnits(CoursePackageEntity coursePackage) {
        int lessonUnits = safeUnits(coursePackage.getDefaultLessonUnits());
        return lessonUnits > 0 ? lessonUnits * 2 : 8;
    }

    private OneOnOneLessonVO toOneOnOneLessonVO(LessonOccurrenceEntity occurrence,
                                                LessonRecordEntity record,
                                                Map<Long, TeacherEntity> teacherMap,
                                                Map<Long, ClassroomEntity> classroomMap,
                                                Map<String, String> statusNameMap) {
        Long plannedTeacherId = record == null ? occurrence.getPlannedTeacherId() : record.getPlannedTeacherId();
        Long actualTeacherId = record == null ? null : record.getActualTeacherId();
        Long plannedClassroomId = record == null ? occurrence.getPlannedClassroomId() : record.getPlannedClassroomId();
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
                .plannedTeacherId(plannedTeacherId)
                .plannedTeacherName(plannedTeacher == null ? null : plannedTeacher.getTeacherName())
                .plannedClassroomId(plannedClassroomId)
                .plannedClassroomName(plannedClassroom == null ? null : plannedClassroom.getRoomName())
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
        return courseProductMapper.selectBatchIds(ids).stream()
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
