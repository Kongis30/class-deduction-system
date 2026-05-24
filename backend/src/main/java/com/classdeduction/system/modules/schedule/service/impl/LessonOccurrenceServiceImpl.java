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
import com.classdeduction.system.modules.schedule.dto.ClassroomOccupancyPageQuery;
import com.classdeduction.system.modules.schedule.dto.LessonOccurrencePageQuery;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.entity.LessonStatusRuleEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.lesson.mapper.LessonStatusRuleMapper;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.service.LessonOccurrenceService;
import com.classdeduction.system.modules.schedule.vo.ClassroomOccupancyVO;
import com.classdeduction.system.modules.schedule.vo.LessonOccurrenceVO;
import com.classdeduction.system.modules.schedule.vo.StudentScheduleSlotVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final ClassGroupMapper classGroupMapper;

    @Override
    public PageResponse<LessonOccurrenceVO> pageOccurrences(LessonOccurrencePageQuery query) {
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

        LambdaQueryWrapper<LessonOccurrenceEntity> wrapper = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .and(StringUtils.hasText(query.getKeyword()), nested -> nested
                        .like(LessonOccurrenceEntity::getLessonNo, query.getKeyword())
                        .or(!matchedPackageIds.isEmpty(), inner -> inner.in(LessonOccurrenceEntity::getPackageId, matchedPackageIds))
                        .or(!matchedStudentIds.isEmpty(), inner -> inner.in(LessonOccurrenceEntity::getStudentId, matchedStudentIds)))
                .eq(query.getPackageId() != null, LessonOccurrenceEntity::getPackageId, query.getPackageId())
                .eq(query.getStudentId() != null, LessonOccurrenceEntity::getStudentId, query.getStudentId())
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
        LessonOccurrenceEntity entity = lessonOccurrenceMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("NOT_FOUND", "Lesson occurrence not found.");
        }
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

    private List<LessonOccurrenceVO> toOccurrenceVOs(List<LessonOccurrenceEntity> occurrences) {
        Set<Long> occurrenceIds = occurrences.stream().map(LessonOccurrenceEntity::getId).collect(Collectors.toSet());
        Map<Long, CoursePackageEntity> packageMap = listPackagesByIds(occurrences.stream().map(LessonOccurrenceEntity::getPackageId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, StudentEntity> studentMap = listStudentsByIds(occurrences.stream().map(LessonOccurrenceEntity::getStudentId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, TeacherEntity> teacherMap = listTeachersByIds(occurrences.stream().map(LessonOccurrenceEntity::getPlannedTeacherId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, ClassroomEntity> classroomMap = listClassroomsByIds(occurrences.stream().map(LessonOccurrenceEntity::getPlannedClassroomId).filter(Objects::nonNull).collect(Collectors.toSet()));
        // 班课名称
        Map<Long, ClassGroupEntity> classGroupMap = listClassGroupsByIds(occurrences.stream().map(LessonOccurrenceEntity::getClassGroupId).filter(Objects::nonNull).collect(Collectors.toSet()));

        // Batch load linked lesson records to surface result status
        Map<Long, LessonRecordEntity> recordMap = Collections.emptyMap();
        Map<String, String> statusNameMap = Collections.emptyMap();
        if (!occurrenceIds.isEmpty()) {
            List<LessonRecordEntity> records = lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                    .in(LessonRecordEntity::getOccurrenceId, occurrenceIds));
            recordMap = records.stream().collect(Collectors.toMap(LessonRecordEntity::getOccurrenceId, r -> r, (a, b) -> a));
            Set<String> statusCodes = records.stream().map(LessonRecordEntity::getResultStatus).collect(Collectors.toSet());
            if (!statusCodes.isEmpty()) {
                List<LessonStatusRuleEntity> rules = lessonStatusRuleMapper.selectList(new LambdaQueryWrapper<LessonStatusRuleEntity>()
                        .in(LessonStatusRuleEntity::getStatusCode, statusCodes));
                statusNameMap = rules.stream().collect(Collectors.toMap(LessonStatusRuleEntity::getStatusCode, LessonStatusRuleEntity::getStatusName, (a, b) -> a));
            }
        }
        final Map<Long, LessonRecordEntity> finalRecordMap = recordMap;
        final Map<String, String> finalStatusNameMap = statusNameMap;

        return occurrences.stream()
                .map(item -> {
                    CoursePackageEntity coursePackage = packageMap.get(item.getPackageId());
                    StudentEntity student = studentMap.get(item.getStudentId());
                    TeacherEntity teacher = teacherMap.get(item.getPlannedTeacherId());
                    ClassroomEntity classroom = classroomMap.get(item.getPlannedClassroomId());
                    ClassGroupEntity classGroup = classGroupMap.get(item.getClassGroupId());
                    LessonRecordEntity record = finalRecordMap.get(item.getId());
                    String resultStatus = record != null ? record.getResultStatus() : null;
                    String resultStatusName = resultStatus != null ? finalStatusNameMap.getOrDefault(resultStatus, resultStatus) : null;
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

    private Map<Long, ClassGroupEntity> listClassGroupsByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return classGroupMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ClassGroupEntity::getId, item -> item));
    }
}
