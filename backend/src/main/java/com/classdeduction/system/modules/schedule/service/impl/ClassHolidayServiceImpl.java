package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.GroupCourseProjectEntity;
import com.classdeduction.system.modules.base.mapper.GroupCourseProjectMapper;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.schedule.dto.ClassHolidaySaveRequest;
import com.classdeduction.system.modules.schedule.entity.ClassEnrollmentEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.ClassHolidayEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassHolidayMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.service.ClassHolidayService;
import com.classdeduction.system.modules.schedule.util.ClassScheduleDates;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayPreviewVO;
import com.classdeduction.system.modules.schedule.vo.ClassHolidayVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 维护教学项目级班课假期。假期只取消/恢复假期范围内的课次，
 * 不改变班级结束日、报名有效期，也不重排假期外课次或补造课次。
 * Prices 页面调用此服务；课次生成、计费和调课也复用其日期查询。
 */
@Service
@RequiredArgsConstructor
public class ClassHolidayServiceImpl implements ClassHolidayService {

    private final ClassHolidayMapper classHolidayMapper;
    private final GroupCourseProjectMapper projectMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final LessonRecordMapper lessonRecordMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ClassHolidayVO> listHolidays(Long groupProjectId) {
        if (groupProjectId == null) return List.of();
        return classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                        .eq(ClassHolidayEntity::getGroupProjectId, groupProjectId)
                        .eq(ClassHolidayEntity::getIsDeleted, 0)
                        .orderByAsc(ClassHolidayEntity::getStartDate)
                        .orderByAsc(ClassHolidayEntity::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassHolidayVO> listHolidays(Long groupProjectId, LocalDate startDate, LocalDate endDate) {
        if (groupProjectId == null
                || startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return List.of();
        }
        return classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                        .eq(ClassHolidayEntity::getGroupProjectId, groupProjectId)
                        .eq(ClassHolidayEntity::getIsDeleted, 0)
                        .le(ClassHolidayEntity::getStartDate, endDate)
                        .ge(ClassHolidayEntity::getEndDate, startDate)
                        .orderByAsc(ClassHolidayEntity::getStartDate))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isHoliday(Long groupProjectId, LocalDate date) {
        if (groupProjectId == null || date == null) return false;
        return classHolidayMapper.selectCount(new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getGroupProjectId, groupProjectId)
                .eq(ClassHolidayEntity::getIsDeleted, 0)
                .le(ClassHolidayEntity::getStartDate, date)
                .ge(ClassHolidayEntity::getEndDate, date)) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public ClassHolidayPreviewVO previewHoliday(Long groupProjectId, Long holidayId, ClassHolidaySaveRequest request) {
        validateRequest(groupProjectId, holidayId, request);
        LocalDate from = request.getStartDate();
        LocalDate through = request.getEndDate();
        ClassHolidayEntity existing = holidayId == null ? null : requireHoliday(groupProjectId, holidayId);
        if (existing != null) {
            from = from.isBefore(existing.getStartDate()) ? from : existing.getStartDate();
            through = through.isAfter(existing.getEndDate()) ? through : existing.getEndDate();
        }
        List<ClassHolidayEntity> activeHolidays = holidaysAfterChange(groupProjectId, holidayId, request);
        List<String> conflicts = new ArrayList<>();
        int affectedOccurrences = 0;
        Set<Long> occurrenceIds = new HashSet<>();
        for (LessonOccurrenceEntity occurrence : affectedOccurrences(groupProjectId, from, through)) {
            LocalDate date = occurrence.getPlannedStartAt().toLocalDate();
            ClassHolidayEntity targetHoliday = holidayForDate(activeHolidays, date);
            boolean currentlyOnHoliday = occurrence.getCourseHolidayId() != null;
            if (targetHoliday != null && occurrence.getCourseHolidayId() == null
                    && !"CANCELLED".equals(occurrence.getPlanningStatus())) {
                affectedOccurrences++;
                occurrenceIds.add(occurrence.getId());
                if (occurrence.getPlannedStartAt().isBefore(AppTime.nowLocalDateTime())) {
                    conflicts.add("A class lesson has already started on " + date + ".");
                }
            } else if (targetHoliday == null && currentlyOnHoliday) {
                affectedOccurrences++;
            }
        }
        if (!occurrenceIds.isEmpty()) {
            long withRecords = lessonRecordMapper.selectCount(new LambdaQueryWrapper<LessonRecordEntity>()
                    .in(LessonRecordEntity::getOccurrenceId, occurrenceIds)
                    .eq(LessonRecordEntity::getIsDeleted, 0));
            if (withRecords > 0) {
                conflicts.add(withRecords + " class lesson(s) already have lesson records. Resolve them before applying the holiday.");
            }
        }
        int affectedEnrollments = countAffectedSessionEnrollments(groupProjectId, from, through);
        return new ClassHolidayPreviewVO(affectedOccurrences, affectedEnrollments, List.copyOf(conflicts));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassHolidayVO createHoliday(Long groupProjectId, ClassHolidaySaveRequest request) {
        lockProjectClasses(groupProjectId);
        assertPreviewHasNoConflicts(groupProjectId, null, request);
        ClassHolidayEntity entity = new ClassHolidayEntity();
        entity.setGroupProjectId(groupProjectId);
        entity.setHolidayName(request.getHolidayName().trim());
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setIsDeleted(0);
        classHolidayMapper.insert(entity);
        reconcileAffectedDates(groupProjectId, request.getStartDate(), request.getEndDate());
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassHolidayVO updateHoliday(Long groupProjectId, Long id, ClassHolidaySaveRequest request) {
        lockProjectClasses(groupProjectId);
        ClassHolidayEntity existing = requireHoliday(groupProjectId, id);
        assertPreviewHasNoConflicts(groupProjectId, id, request);
        LocalDate from = request.getStartDate().isBefore(existing.getStartDate())
                ? request.getStartDate() : existing.getStartDate();
        LocalDate through = request.getEndDate().isAfter(existing.getEndDate())
                ? request.getEndDate() : existing.getEndDate();
        existing.setHolidayName(request.getHolidayName().trim());
        existing.setStartDate(request.getStartDate());
        existing.setEndDate(request.getEndDate());
        classHolidayMapper.updateById(existing);
        reconcileAffectedDates(groupProjectId, from, through);
        return toVO(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHoliday(Long groupProjectId, Long id) {
        lockProjectClasses(groupProjectId);
        ClassHolidayEntity existing = requireHoliday(groupProjectId, id);
        classHolidayMapper.deleteById(id);
        reconcileAffectedDates(groupProjectId, existing.getStartDate(), existing.getEndDate());
    }

    /**
     * 假期修改只作用于假期范围内的课次；先用锁定读取锁住项目，再按班级 ID 锁其实际班级。
     * 报名、更正和新建班级也先锁项目，避免普通查询建立旧 RR 快照或形成反向锁顺序。
     */
    private void lockProjectClasses(Long groupProjectId) {
        GroupCourseProjectEntity project = projectMapper.selectByIdForUpdate(groupProjectId);
        if (project == null || !Objects.equals(project.getIsDeleted(), 0)
                || !"ACTIVE".equals(project.getStatus())) {
            throw new BusinessException("CLASS_HOLIDAY_INVALID_PROJECT",
                    "Holidays can only be configured for an active teaching project.");
        }
        classGroupMapper.selectByProjectIdForUpdate(groupProjectId);
    }

    private void assertPreviewHasNoConflicts(Long groupProjectId, Long holidayId, ClassHolidaySaveRequest request) {
        ClassHolidayPreviewVO preview = previewHoliday(groupProjectId, holidayId, request);
        if (!preview.conflicts().isEmpty()) {
            throw new BusinessException("CLASS_HOLIDAY_CONFLICT", String.join(" ", preview.conflicts()));
        }
    }

    private void validateRequest(Long groupProjectId, Long holidayId, ClassHolidaySaveRequest request) {
        requireActiveProject(groupProjectId);
        if (request == null || !StringUtils.hasText(request.getHolidayName())
                || request.getStartDate() == null || request.getEndDate() == null) {
            throw new BusinessException("CLASS_HOLIDAY_INVALID", "Enter a holiday name and date range.");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("CLASS_HOLIDAY_INVALID", "Holiday end date cannot be earlier than its start date.");
        }
        LocalDate today = LocalDate.now(AppTime.APP_ZONE_ID);
        if (request.getStartDate().isBefore(today)) {
            ClassHolidayEntity current = holidayId == null ? null : requireHoliday(groupProjectId, holidayId);
            if (current == null || !request.getStartDate().equals(current.getStartDate())) {
                throw new BusinessException("CLASS_HOLIDAY_PAST_DATE", "A new class holiday must start today or later.");
            }
        }
        LambdaQueryWrapper<ClassHolidayEntity> overlap = new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getGroupProjectId, groupProjectId)
                .eq(ClassHolidayEntity::getIsDeleted, 0)
                .le(ClassHolidayEntity::getStartDate, request.getEndDate())
                .ge(ClassHolidayEntity::getEndDate, request.getStartDate())
                .ne(holidayId != null, ClassHolidayEntity::getId, holidayId);
        if (classHolidayMapper.selectCount(overlap) > 0) {
            throw new BusinessException("CLASS_HOLIDAY_OVERLAP", "Holiday dates overlap another saved holiday.");
        }
    }

    /** 保存前后都调用本方法，确保新增、修改、删除采用相同的课次状态规则。 */
    private void reconcileAffectedDates(Long groupProjectId, LocalDate from, LocalDate through) {
        List<ClassHolidayEntity> holidays = classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getGroupProjectId, groupProjectId)
                .eq(ClassHolidayEntity::getIsDeleted, 0));
        for (LessonOccurrenceEntity occurrence : affectedOccurrences(groupProjectId, from, through)) {
            ClassHolidayEntity target = holidayForDate(holidays, occurrence.getPlannedStartAt().toLocalDate());
            if (target != null && occurrence.getCourseHolidayId() == null
                    && !"CANCELLED".equals(occurrence.getPlanningStatus())) {
                ensureNoLessonRecord(occurrence);
                if (occurrence.getPlannedStartAt().isBefore(AppTime.nowLocalDateTime())) {
                    throw new BusinessException("CLASS_HOLIDAY_CONFLICT", "A class lesson has already started on "
                            + occurrence.getPlannedStartAt().toLocalDate() + ".");
                }
                occurrence.setHolidayPreviousStatus(occurrence.getPlanningStatus());
                occurrence.setCourseHolidayId(target.getId());
                occurrence.setPlanningStatus("CANCELLED");
                lessonOccurrenceMapper.updateById(occurrence);
            } else if (target != null && occurrence.getCourseHolidayId() != null) {
                if (!Objects.equals(occurrence.getCourseHolidayId(), target.getId())) {
                    occurrence.setCourseHolidayId(target.getId());
                    lessonOccurrenceMapper.updateById(occurrence);
                }
            } else if (target == null && occurrence.getCourseHolidayId() != null) {
                if (occurrence.getPlannedStartAt().isBefore(AppTime.nowLocalDateTime())) {
                    continue;
                }
                String previousStatus = StringUtils.hasText(occurrence.getHolidayPreviousStatus())
                        ? occurrence.getHolidayPreviousStatus() : "SCHEDULED";
                ensureNoResourceConflict(occurrence);
                // updateById 默认忽略 null 字段，恢复假期时必须显式清空假期引用。
                lessonOccurrenceMapper.update(null, new LambdaUpdateWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getId, occurrence.getId())
                        .set(LessonOccurrenceEntity::getPlanningStatus, previousStatus)
                        .set(LessonOccurrenceEntity::getCourseHolidayId, null)
                        .set(LessonOccurrenceEntity::getHolidayPreviousStatus, null));
            }
        }
    }

    private void ensureNoLessonRecord(LessonOccurrenceEntity occurrence) {
        long count = lessonRecordMapper.selectCount(new LambdaQueryWrapper<LessonRecordEntity>()
                .eq(LessonRecordEntity::getOccurrenceId, occurrence.getId())
                .eq(LessonRecordEntity::getIsDeleted, 0));
        if (count > 0) {
            throw new BusinessException("CLASS_HOLIDAY_CONFLICT", "A class lesson on "
                    + occurrence.getPlannedStartAt().toLocalDate() + " already has a lesson record.");
        }
    }

    private void ensureNoResourceConflict(LessonOccurrenceEntity occurrence) {
        if (occurrence.getPlannedTeacherId() == null && occurrence.getPlannedClassroomId() == null) return;
        LambdaQueryWrapper<LessonOccurrenceEntity> overlap = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .ne(occurrence.getId() != null, LessonOccurrenceEntity::getId, occurrence.getId())
                .eq(LessonOccurrenceEntity::getIsDeleted, 0)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .isNull(LessonOccurrenceEntity::getCourseHolidayId)
                .lt(LessonOccurrenceEntity::getPlannedStartAt, occurrence.getPlannedEndAt())
                .gt(LessonOccurrenceEntity::getPlannedEndAt, occurrence.getPlannedStartAt());
        if (occurrence.getPlannedTeacherId() != null && occurrence.getPlannedClassroomId() != null) {
            overlap.and(wrapper -> wrapper
                    .eq(LessonOccurrenceEntity::getPlannedTeacherId, occurrence.getPlannedTeacherId())
                    .or().eq(LessonOccurrenceEntity::getPlannedClassroomId, occurrence.getPlannedClassroomId()));
        } else if (occurrence.getPlannedTeacherId() != null) {
            overlap.eq(LessonOccurrenceEntity::getPlannedTeacherId, occurrence.getPlannedTeacherId());
        } else {
            overlap.eq(LessonOccurrenceEntity::getPlannedClassroomId, occurrence.getPlannedClassroomId());
        }
        if (lessonOccurrenceMapper.selectCount(overlap) > 0) {
            throw new BusinessException("CLASS_HOLIDAY_RESTORE_CONFLICT",
                    "Restoring or extending this class schedule would conflict with another lesson. Resolve the resource conflict first.");
        }
    }

    private List<LessonOccurrenceEntity> affectedOccurrences(Long groupProjectId, LocalDate from, LocalDate through) {
        List<Long> groupIds = classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroupEntity>()
                        .select(ClassGroupEntity::getId)
                        .eq(ClassGroupEntity::getGroupProjectId, groupProjectId)
                        .eq(ClassGroupEntity::getIsDeleted, 0))
                .stream().map(ClassGroupEntity::getId).toList();
        if (groupIds.isEmpty()) return List.of();
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .in(LessonOccurrenceEntity::getClassGroupId, groupIds)
                .eq(LessonOccurrenceEntity::getIsDeleted, 0)
                .ge(LessonOccurrenceEntity::getPlannedStartAt, from.atStartOfDay())
                .lt(LessonOccurrenceEntity::getPlannedStartAt, through.plusDays(1).atStartOfDay())
                .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));
    }

    private List<ClassHolidayEntity> holidaysAfterChange(Long groupProjectId, Long holidayId,
                                                          ClassHolidaySaveRequest request) {
        List<ClassHolidayEntity> result = new ArrayList<>(classHolidayMapper.selectList(
                new LambdaQueryWrapper<ClassHolidayEntity>()
                        .eq(ClassHolidayEntity::getGroupProjectId, groupProjectId)
                        .eq(ClassHolidayEntity::getIsDeleted, 0)));
        if (holidayId != null) result.removeIf(item -> Objects.equals(item.getId(), holidayId));
        ClassHolidayEntity updated = new ClassHolidayEntity();
        updated.setId(holidayId);
        updated.setGroupProjectId(groupProjectId);
        updated.setHolidayName(request.getHolidayName());
        updated.setStartDate(request.getStartDate());
        updated.setEndDate(request.getEndDate());
        result.add(updated);
        return result;
    }

    private ClassHolidayEntity holidayForDate(List<ClassHolidayEntity> holidays, LocalDate date) {
        return holidays.stream()
                .filter(item -> !date.isBefore(item.getStartDate()) && !date.isAfter(item.getEndDate()))
                .findFirst().orElse(null);
    }

    private int countAffectedSessionEnrollments(Long groupProjectId, LocalDate from, LocalDate through) {
        Set<Long> groupIds = projectClassIds(groupProjectId);
        if (groupIds.isEmpty()) return 0;
        return (int) classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .in(ClassEnrollmentEntity::getClassId, groupIds)
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                        .in(ClassEnrollmentEntity::getBillingType, "PER_SESSION", "SESSION")
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .le(ClassEnrollmentEntity::getEnrollmentDate, through)
                        .ge(ClassEnrollmentEntity::getExpiryDate, from))
                .size();
    }

    @Override
    public void recalculateClassSessionEnrollments(Long classId) {
        ClassGroupEntity group = classGroupMapper.selectById(classId);
        if (group == null) return;
        List<ClassGroupScheduleSlotEntity> slots = loadSlots(classId);
        List<ClassHolidayEntity> holidays = classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getGroupProjectId, group.getGroupProjectId())
                .eq(ClassHolidayEntity::getIsDeleted, 0));
        List<ClassEnrollmentEntity> enrollments = classEnrollmentMapper.selectList(
                new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, classId)
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                        .in(ClassEnrollmentEntity::getBillingType, "PER_SESSION", "SESSION")
                        .in(ClassEnrollmentEntity::getStatus, "ACTIVE", "EXPIRED"));
        LocalDate today = LocalDate.now(AppTime.APP_ZONE_ID);
        for (ClassEnrollmentEntity enrollment : enrollments) {
            if (enrollment.getPurchasedQuantity() == null || enrollment.getPurchasedQuantity() < 1) continue;
            LocalDate projected = projectedExpiry(enrollment, group, slots, holidays);
            if (projected == null || group.getEndDate() != null && projected.isAfter(group.getEndDate())) {
                throw new BusinessException("CLASS_SCHEDULE_INSUFFICIENT",
                        "The current periods do not provide enough class dates for some students’ purchased sessions. Add weekly class times or extend the class end date, then preview again.");
            }
            enrollment.setExpiryDate(projected);
            enrollment.setStatus(projected.isBefore(today) ? "EXPIRED" : "ACTIVE");
            classEnrollmentMapper.updateById(enrollment);
        }
    }

    private LocalDate projectedExpiry(ClassEnrollmentEntity enrollment, ClassGroupEntity group,
                                      List<ClassGroupScheduleSlotEntity> slots,
                                      List<ClassHolidayEntity> holidays) {
        return ClassScheduleDates.nthScheduledDay(enrollment.getEnrollmentDate(), enrollment.getPurchasedQuantity(),
                ClassScheduleDates.resolveBaseEndDate(group.getScheduleBaseEndDate(), group.getEndDate()),
                slots, date -> holidayForDate(holidays, date) != null);
    }

    private List<ClassGroupScheduleSlotEntity> loadSlots(Long classId) {
        return classGroupScheduleSlotMapper.selectList(new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classId)
                .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0));
    }

    private Set<Long> projectClassIds(Long groupProjectId) {
        return classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroupEntity>()
                        .select(ClassGroupEntity::getId)
                        .eq(ClassGroupEntity::getGroupProjectId, groupProjectId)
                        .eq(ClassGroupEntity::getIsDeleted, 0))
                .stream().map(ClassGroupEntity::getId).collect(java.util.stream.Collectors.toSet());
    }

    private ClassHolidayEntity requireHoliday(Long groupProjectId, Long id) {
        ClassHolidayEntity holiday = classHolidayMapper.selectById(id);
        if (holiday == null || !Objects.equals(holiday.getIsDeleted(), 0)
                || !Objects.equals(holiday.getGroupProjectId(), groupProjectId)) {
            throw new BusinessException("CLASS_HOLIDAY_NOT_FOUND", "Class holiday not found.");
        }
        return holiday;
    }

    private void requireActiveProject(Long groupProjectId) {
        GroupCourseProjectEntity project = projectMapper.selectById(groupProjectId);
        if (project == null || !Objects.equals(project.getIsDeleted(), 0)
                || !"ACTIVE".equals(project.getStatus())) {
            throw new BusinessException("CLASS_HOLIDAY_INVALID_PROJECT",
                    "Holidays can only be configured for an active teaching project.");
        }
    }

    private ClassHolidayVO toVO(ClassHolidayEntity entity) {
        return new ClassHolidayVO(entity.getId(), entity.getHolidayName(), entity.getStartDate(), entity.getEndDate());
    }
}
