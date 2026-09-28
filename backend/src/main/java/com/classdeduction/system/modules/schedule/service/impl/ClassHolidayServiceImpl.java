package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
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

/**
 * 维护全局班课假期，并协调未来班课课次和 SESSION 报名周期。
 * Prices 页面调用此服务；课次生成、计费和调课也复用其日期查询。
 */
@Service
@RequiredArgsConstructor
public class ClassHolidayServiceImpl implements ClassHolidayService {

    private final ClassHolidayMapper classHolidayMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final LessonRecordMapper lessonRecordMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ClassHolidayVO> listHolidays() {
        return classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                        .eq(ClassHolidayEntity::getIsDeleted, 0)
                        .orderByAsc(ClassHolidayEntity::getStartDate)
                        .orderByAsc(ClassHolidayEntity::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassHolidayVO> listHolidays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return List.of();
        }
        return classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                        .eq(ClassHolidayEntity::getIsDeleted, 0)
                        .le(ClassHolidayEntity::getStartDate, endDate)
                        .ge(ClassHolidayEntity::getEndDate, startDate)
                        .orderByAsc(ClassHolidayEntity::getStartDate))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date) {
        if (date == null) return false;
        return classHolidayMapper.selectCount(new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getIsDeleted, 0)
                .le(ClassHolidayEntity::getStartDate, date)
                .ge(ClassHolidayEntity::getEndDate, date)) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public ClassHolidayPreviewVO previewHoliday(Long holidayId, ClassHolidaySaveRequest request) {
        validateRequest(holidayId, request);
        LocalDate from = request.getStartDate();
        LocalDate through = request.getEndDate();
        ClassHolidayEntity existing = holidayId == null ? null : requireHoliday(holidayId);
        if (existing != null) {
            from = from.isBefore(existing.getStartDate()) ? from : existing.getStartDate();
            through = through.isAfter(existing.getEndDate()) ? through : existing.getEndDate();
        }
        List<ClassHolidayEntity> activeHolidays = holidaysAfterChange(holidayId, request);
        List<String> conflicts = new ArrayList<>();
        int affectedOccurrences = 0;
        Set<Long> occurrenceIds = new HashSet<>();
        for (LessonOccurrenceEntity occurrence : affectedOccurrences(from, through)) {
            LocalDate date = occurrence.getPlannedStartAt().toLocalDate();
            ClassHolidayEntity targetHoliday = holidayForDate(activeHolidays, date);
            boolean currentlyOnHoliday = occurrence.getClassHolidayId() != null;
            if (targetHoliday != null && occurrence.getClassHolidayId() == null
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
        int affectedEnrollments = countAffectedSessionEnrollments(from, through);
        conflicts.addAll(validateSessionExpiryChanges(from, through, activeHolidays));
        return new ClassHolidayPreviewVO(affectedOccurrences, affectedEnrollments, List.copyOf(conflicts));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassHolidayVO createHoliday(ClassHolidaySaveRequest request) {
        assertPreviewHasNoConflicts(null, request);
        ClassHolidayEntity entity = new ClassHolidayEntity();
        entity.setHolidayName(request.getHolidayName().trim());
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setIsDeleted(0);
        classHolidayMapper.insert(entity);
        reconcileAffectedDates(request.getStartDate(), request.getEndDate());
        recalculateSessionEnrollments(request.getStartDate(), request.getEndDate());
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassHolidayVO updateHoliday(Long id, ClassHolidaySaveRequest request) {
        ClassHolidayEntity existing = requireHoliday(id);
        assertPreviewHasNoConflicts(id, request);
        LocalDate from = request.getStartDate().isBefore(existing.getStartDate())
                ? request.getStartDate() : existing.getStartDate();
        LocalDate through = request.getEndDate().isAfter(existing.getEndDate())
                ? request.getEndDate() : existing.getEndDate();
        existing.setHolidayName(request.getHolidayName().trim());
        existing.setStartDate(request.getStartDate());
        existing.setEndDate(request.getEndDate());
        classHolidayMapper.updateById(existing);
        reconcileAffectedDates(from, through);
        recalculateSessionEnrollments(from, through);
        return toVO(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHoliday(Long id) {
        ClassHolidayEntity existing = requireHoliday(id);
        classHolidayMapper.deleteById(id);
        reconcileAffectedDates(existing.getStartDate(), existing.getEndDate());
        recalculateSessionEnrollments(existing.getStartDate(), existing.getEndDate());
    }

    private void assertPreviewHasNoConflicts(Long holidayId, ClassHolidaySaveRequest request) {
        ClassHolidayPreviewVO preview = previewHoliday(holidayId, request);
        if (!preview.conflicts().isEmpty()) {
            throw new BusinessException("CLASS_HOLIDAY_CONFLICT", String.join(" ", preview.conflicts()));
        }
    }

    private void validateRequest(Long holidayId, ClassHolidaySaveRequest request) {
        if (request == null || !StringUtils.hasText(request.getHolidayName())
                || request.getStartDate() == null || request.getEndDate() == null) {
            throw new BusinessException("CLASS_HOLIDAY_INVALID", "Enter a holiday name and date range.");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("CLASS_HOLIDAY_INVALID", "Holiday end date cannot be earlier than its start date.");
        }
        LocalDate today = LocalDate.now(AppTime.APP_ZONE_ID);
        if (request.getStartDate().isBefore(today)) {
            ClassHolidayEntity current = holidayId == null ? null : requireHoliday(holidayId);
            if (current == null || !request.getStartDate().equals(current.getStartDate())) {
                throw new BusinessException("CLASS_HOLIDAY_PAST_DATE", "A new class holiday must start today or later.");
            }
        }
        LambdaQueryWrapper<ClassHolidayEntity> overlap = new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getIsDeleted, 0)
                .le(ClassHolidayEntity::getStartDate, request.getEndDate())
                .ge(ClassHolidayEntity::getEndDate, request.getStartDate())
                .ne(holidayId != null, ClassHolidayEntity::getId, holidayId);
        if (classHolidayMapper.selectCount(overlap) > 0) {
            throw new BusinessException("CLASS_HOLIDAY_OVERLAP", "Holiday dates overlap another saved holiday.");
        }
    }

    /** 保存前后都调用本方法，确保新增、修改、删除采用相同的课次状态规则。 */
    private void reconcileAffectedDates(LocalDate from, LocalDate through) {
        List<ClassHolidayEntity> holidays = classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getIsDeleted, 0));
        for (LessonOccurrenceEntity occurrence : affectedOccurrences(from, through)) {
            ClassHolidayEntity target = holidayForDate(holidays, occurrence.getPlannedStartAt().toLocalDate());
            if (target != null && occurrence.getClassHolidayId() == null
                    && !"CANCELLED".equals(occurrence.getPlanningStatus())) {
                ensureNoLessonRecord(occurrence);
                if (occurrence.getPlannedStartAt().isBefore(AppTime.nowLocalDateTime())) {
                    throw new BusinessException("CLASS_HOLIDAY_CONFLICT", "A class lesson has already started on "
                            + occurrence.getPlannedStartAt().toLocalDate() + ".");
                }
                occurrence.setHolidayPreviousStatus(occurrence.getPlanningStatus());
                occurrence.setClassHolidayId(target.getId());
                occurrence.setPlanningStatus("CANCELLED");
                lessonOccurrenceMapper.updateById(occurrence);
            } else if (target != null && occurrence.getClassHolidayId() != null) {
                if (!Objects.equals(occurrence.getClassHolidayId(), target.getId())) {
                    occurrence.setClassHolidayId(target.getId());
                    lessonOccurrenceMapper.updateById(occurrence);
                }
            } else if (target == null && occurrence.getClassHolidayId() != null) {
                if (occurrence.getPlannedStartAt().isBefore(AppTime.nowLocalDateTime())) {
                    continue;
                }
                String previousStatus = StringUtils.hasText(occurrence.getHolidayPreviousStatus())
                        ? occurrence.getHolidayPreviousStatus() : "SCHEDULED";
                ensureNoResourceConflict(occurrence);
                occurrence.setPlanningStatus(previousStatus);
                occurrence.setClassHolidayId(null);
                occurrence.setHolidayPreviousStatus(null);
                lessonOccurrenceMapper.updateById(occurrence);
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
                .ne(LessonOccurrenceEntity::getId, occurrence.getId())
                .eq(LessonOccurrenceEntity::getIsDeleted, 0)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .isNull(LessonOccurrenceEntity::getClassHolidayId)
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
                    "Removing this holiday would restore a class lesson that now conflicts with another lesson.");
        }
    }

    private List<LessonOccurrenceEntity> affectedOccurrences(LocalDate from, LocalDate through) {
        return lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .isNotNull(LessonOccurrenceEntity::getClassGroupId)
                .eq(LessonOccurrenceEntity::getIsDeleted, 0)
                .ge(LessonOccurrenceEntity::getPlannedStartAt, from.atStartOfDay())
                .lt(LessonOccurrenceEntity::getPlannedStartAt, through.plusDays(1).atStartOfDay())
                .orderByAsc(LessonOccurrenceEntity::getPlannedStartAt));
    }

    private List<ClassHolidayEntity> holidaysAfterChange(Long holidayId, ClassHolidaySaveRequest request) {
        List<ClassHolidayEntity> result = new ArrayList<>(classHolidayMapper.selectList(
                new LambdaQueryWrapper<ClassHolidayEntity>().eq(ClassHolidayEntity::getIsDeleted, 0)));
        if (holidayId != null) result.removeIf(item -> Objects.equals(item.getId(), holidayId));
        ClassHolidayEntity updated = new ClassHolidayEntity();
        updated.setId(holidayId);
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

    private int countAffectedSessionEnrollments(LocalDate from, LocalDate through) {
        return (int) classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                        .in(ClassEnrollmentEntity::getBillingType, "PER_SESSION", "SESSION")
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .le(ClassEnrollmentEntity::getEnrollmentDate, through)
                        .ge(ClassEnrollmentEntity::getExpiryDate, from))
                .size();
    }

    private List<String> validateSessionExpiryChanges(LocalDate from, LocalDate through,
                                                       List<ClassHolidayEntity> holidays) {
        List<String> conflicts = new ArrayList<>();
        List<ClassEnrollmentEntity> enrollments = classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                .in(ClassEnrollmentEntity::getBillingType, "PER_SESSION", "SESSION")
                .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                .le(ClassEnrollmentEntity::getEnrollmentDate, through)
                .ge(ClassEnrollmentEntity::getExpiryDate, from));
        for (ClassEnrollmentEntity enrollment : enrollments) {
            ClassGroupEntity group = classGroupMapper.selectById(enrollment.getClassId());
            if (group == null || group.getEndDate() == null || enrollment.getPurchasedQuantity() == null) continue;
            List<ClassGroupScheduleSlotEntity> slots = loadSlots(group.getId());
            LocalDate projected = nthSessionDate(enrollment.getEnrollmentDate(), enrollment.getPurchasedQuantity(), slots, holidays);
            if (projected != null && projected.isAfter(group.getEndDate())) {
                conflicts.add("A SESSION enrollment in class " + group.getClassName()
                        + " needs dates beyond the class end date " + group.getEndDate() + ".");
                break;
            }
        }
        return conflicts;
    }

    private void recalculateSessionEnrollments(LocalDate from, LocalDate through) {
        List<ClassHolidayEntity> holidays = classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getIsDeleted, 0));
        List<ClassEnrollmentEntity> enrollments = classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                .in(ClassEnrollmentEntity::getBillingType, "PER_SESSION", "SESSION")
                .in(ClassEnrollmentEntity::getStatus, "ACTIVE", "EXPIRED")
                .le(ClassEnrollmentEntity::getEnrollmentDate, through)
                .ge(ClassEnrollmentEntity::getExpiryDate, from));
        LocalDate today = LocalDate.now(AppTime.APP_ZONE_ID);
        for (ClassEnrollmentEntity enrollment : enrollments) {
            ClassGroupEntity group = classGroupMapper.selectById(enrollment.getClassId());
            if (group == null || enrollment.getPurchasedQuantity() == null || enrollment.getPurchasedQuantity() < 1) continue;
            LocalDate projected = nthSessionDate(enrollment.getEnrollmentDate(), enrollment.getPurchasedQuantity(),
                    loadSlots(group.getId()), holidays);
            if (projected == null) continue;
            if (group.getEndDate() != null && projected.isAfter(group.getEndDate())) {
                projected = group.getEndDate();
            }
            enrollment.setExpiryDate(projected);
            enrollment.setStatus(projected.isBefore(today) ? "EXPIRED" : "ACTIVE");
            classEnrollmentMapper.updateById(enrollment);
        }
    }

    private LocalDate nthSessionDate(LocalDate startDate, int quantity,
                                     List<ClassGroupScheduleSlotEntity> slots,
                                     List<ClassHolidayEntity> holidays) {
        if (startDate == null || quantity < 1 || slots.isEmpty()) return null;
        Set<Integer> weekdays = new HashSet<>();
        slots.stream().map(ClassGroupScheduleSlotEntity::getWeekday).filter(Objects::nonNull).forEach(weekdays::add);
        if (weekdays.isEmpty()) return null;
        int sessions = 0;
        for (LocalDate date = startDate; !date.isAfter(startDate.plusYears(10)); date = date.plusDays(1)) {
            if (weekdays.contains(date.getDayOfWeek().getValue()) && holidayForDate(holidays, date) == null
                    && ++sessions >= quantity) return date;
        }
        return null;
    }

    private List<ClassGroupScheduleSlotEntity> loadSlots(Long classId) {
        return classGroupScheduleSlotMapper.selectList(new LambdaQueryWrapper<ClassGroupScheduleSlotEntity>()
                .eq(ClassGroupScheduleSlotEntity::getClassGroupId, classId)
                .eq(ClassGroupScheduleSlotEntity::getIsDeleted, 0));
    }

    private ClassHolidayEntity requireHoliday(Long id) {
        ClassHolidayEntity holiday = classHolidayMapper.selectById(id);
        if (holiday == null || !Objects.equals(holiday.getIsDeleted(), 0)) {
            throw new BusinessException("CLASS_HOLIDAY_NOT_FOUND", "Class holiday not found.");
        }
        return holiday;
    }

    private ClassHolidayVO toVO(ClassHolidayEntity entity) {
        return new ClassHolidayVO(entity.getId(), entity.getHolidayName(), entity.getStartDate(), entity.getEndDate());
    }
}
