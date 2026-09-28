package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.enrollment.util.BusinessNoGenerator;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 维护 Course 级班课假期，并协调该 Course 下未来班课课次和 SESSION 报名周期。
 * Prices 页面调用此服务；课次生成、计费和调课也复用其日期查询。
 */
@Service
@RequiredArgsConstructor
public class ClassHolidayServiceImpl implements ClassHolidayService {

    private final ClassHolidayMapper classHolidayMapper;
    private final CourseProductMapper courseProductMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassGroupScheduleSlotMapper classGroupScheduleSlotMapper;
    private final LessonRecordMapper lessonRecordMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ClassHolidayVO> listHolidays(Long courseProductId) {
        if (courseProductId == null) return List.of();
        return classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                        .eq(ClassHolidayEntity::getCourseProductId, courseProductId)
                        .eq(ClassHolidayEntity::getIsDeleted, 0)
                        .orderByAsc(ClassHolidayEntity::getStartDate)
                        .orderByAsc(ClassHolidayEntity::getId))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassHolidayVO> listHolidays(Long courseProductId, LocalDate startDate, LocalDate endDate) {
        if (courseProductId == null || startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return List.of();
        }
        return classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                        .eq(ClassHolidayEntity::getCourseProductId, courseProductId)
                        .eq(ClassHolidayEntity::getIsDeleted, 0)
                        .le(ClassHolidayEntity::getStartDate, endDate)
                        .ge(ClassHolidayEntity::getEndDate, startDate)
                        .orderByAsc(ClassHolidayEntity::getStartDate))
                .stream().map(this::toVO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isHoliday(Long courseProductId, LocalDate date) {
        if (courseProductId == null || date == null) return false;
        return classHolidayMapper.selectCount(new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getCourseProductId, courseProductId)
                .eq(ClassHolidayEntity::getIsDeleted, 0)
                .le(ClassHolidayEntity::getStartDate, date)
                .ge(ClassHolidayEntity::getEndDate, date)) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public ClassHolidayPreviewVO previewHoliday(Long courseProductId, Long holidayId, ClassHolidaySaveRequest request) {
        validateRequest(courseProductId, holidayId, request);
        LocalDate from = request.getStartDate();
        LocalDate through = request.getEndDate();
        ClassHolidayEntity existing = holidayId == null ? null : requireHoliday(courseProductId, holidayId);
        if (existing != null) {
            from = from.isBefore(existing.getStartDate()) ? from : existing.getStartDate();
            through = through.isAfter(existing.getEndDate()) ? through : existing.getEndDate();
        }
        List<ClassHolidayEntity> activeHolidays = holidaysAfterChange(courseProductId, holidayId, request);
        List<String> conflicts = new ArrayList<>();
        int affectedOccurrences = 0;
        Set<Long> occurrenceIds = new HashSet<>();
        for (LessonOccurrenceEntity occurrence : affectedOccurrences(courseProductId, from, through)) {
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
        int affectedEnrollments = countAffectedSessionEnrollments(courseProductId, from, through);
        return new ClassHolidayPreviewVO(affectedOccurrences, affectedEnrollments, List.copyOf(conflicts));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassHolidayVO createHoliday(Long courseProductId, ClassHolidaySaveRequest request) {
        assertPreviewHasNoConflicts(courseProductId, null, request);
        ClassHolidayEntity entity = new ClassHolidayEntity();
        entity.setCourseProductId(courseProductId);
        entity.setHolidayName(request.getHolidayName().trim());
        entity.setStartDate(request.getStartDate());
        entity.setEndDate(request.getEndDate());
        entity.setIsDeleted(0);
        classHolidayMapper.insert(entity);
        reconcileAffectedDates(courseProductId, request.getStartDate(), request.getEndDate());
        synchronizeCourseClassSchedules(courseProductId);
        recalculateSessionEnrollments(courseProductId, request.getStartDate(), request.getEndDate());
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ClassHolidayVO updateHoliday(Long courseProductId, Long id, ClassHolidaySaveRequest request) {
        ClassHolidayEntity existing = requireHoliday(courseProductId, id);
        assertPreviewHasNoConflicts(courseProductId, id, request);
        LocalDate from = request.getStartDate().isBefore(existing.getStartDate())
                ? request.getStartDate() : existing.getStartDate();
        LocalDate through = request.getEndDate().isAfter(existing.getEndDate())
                ? request.getEndDate() : existing.getEndDate();
        existing.setHolidayName(request.getHolidayName().trim());
        existing.setStartDate(request.getStartDate());
        existing.setEndDate(request.getEndDate());
        classHolidayMapper.updateById(existing);
        reconcileAffectedDates(courseProductId, from, through);
        synchronizeCourseClassSchedules(courseProductId);
        recalculateSessionEnrollments(courseProductId, from, through);
        return toVO(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHoliday(Long courseProductId, Long id) {
        ClassHolidayEntity existing = requireHoliday(courseProductId, id);
        classHolidayMapper.deleteById(id);
        reconcileAffectedDates(courseProductId, existing.getStartDate(), existing.getEndDate());
        synchronizeCourseClassSchedules(courseProductId);
        recalculateSessionEnrollments(courseProductId, existing.getStartDate(), existing.getEndDate());
    }

    private void assertPreviewHasNoConflicts(Long courseProductId, Long holidayId, ClassHolidaySaveRequest request) {
        ClassHolidayPreviewVO preview = previewHoliday(courseProductId, holidayId, request);
        if (!preview.conflicts().isEmpty()) {
            throw new BusinessException("CLASS_HOLIDAY_CONFLICT", String.join(" ", preview.conflicts()));
        }
    }

    private void validateRequest(Long courseProductId, Long holidayId, ClassHolidaySaveRequest request) {
        requireGroupCourse(courseProductId);
        if (request == null || !StringUtils.hasText(request.getHolidayName())
                || request.getStartDate() == null || request.getEndDate() == null) {
            throw new BusinessException("CLASS_HOLIDAY_INVALID", "Enter a holiday name and date range.");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("CLASS_HOLIDAY_INVALID", "Holiday end date cannot be earlier than its start date.");
        }
        LocalDate today = LocalDate.now(AppTime.APP_ZONE_ID);
        if (request.getStartDate().isBefore(today)) {
            ClassHolidayEntity current = holidayId == null ? null : requireHoliday(courseProductId, holidayId);
            if (current == null || !request.getStartDate().equals(current.getStartDate())) {
                throw new BusinessException("CLASS_HOLIDAY_PAST_DATE", "A new class holiday must start today or later.");
            }
        }
        LambdaQueryWrapper<ClassHolidayEntity> overlap = new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getCourseProductId, courseProductId)
                .eq(ClassHolidayEntity::getIsDeleted, 0)
                .le(ClassHolidayEntity::getStartDate, request.getEndDate())
                .ge(ClassHolidayEntity::getEndDate, request.getStartDate())
                .ne(holidayId != null, ClassHolidayEntity::getId, holidayId);
        if (classHolidayMapper.selectCount(overlap) > 0) {
            throw new BusinessException("CLASS_HOLIDAY_OVERLAP", "Holiday dates overlap another saved holiday.");
        }
    }

    /** 保存前后都调用本方法，确保新增、修改、删除采用相同的课次状态规则。 */
    private void reconcileAffectedDates(Long courseProductId, LocalDate from, LocalDate through) {
        List<ClassHolidayEntity> holidays = classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getCourseProductId, courseProductId)
                .eq(ClassHolidayEntity::getIsDeleted, 0));
        for (LessonOccurrenceEntity occurrence : affectedOccurrences(courseProductId, from, through)) {
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
                occurrence.setPlanningStatus(previousStatus);
                occurrence.setCourseHolidayId(null);
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

    private List<LessonOccurrenceEntity> affectedOccurrences(Long courseProductId, LocalDate from, LocalDate through) {
        List<Long> groupIds = classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroupEntity>()
                        .select(ClassGroupEntity::getId)
                        .eq(ClassGroupEntity::getProductId, courseProductId)
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

    private List<ClassHolidayEntity> holidaysAfterChange(Long courseProductId, Long holidayId, ClassHolidaySaveRequest request) {
        List<ClassHolidayEntity> result = new ArrayList<>(classHolidayMapper.selectList(
                new LambdaQueryWrapper<ClassHolidayEntity>()
                        .eq(ClassHolidayEntity::getCourseProductId, courseProductId)
                        .eq(ClassHolidayEntity::getIsDeleted, 0)));
        if (holidayId != null) result.removeIf(item -> Objects.equals(item.getId(), holidayId));
        ClassHolidayEntity updated = new ClassHolidayEntity();
        updated.setId(holidayId);
        updated.setCourseProductId(courseProductId);
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

    private int countAffectedSessionEnrollments(Long courseProductId, LocalDate from, LocalDate through) {
        Set<Long> groupIds = courseGroupIds(courseProductId);
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

    /**
     * 假期会减少班级原周期内可排的上课日，因此按 Course 假期重新计算课次终点，
     * 并只补齐未来缺少的周课次。Prices 的假期新增、编辑、删除都在落库后调用本方法。
     */
    private void synchronizeCourseClassSchedules(Long courseProductId) {
        List<ClassHolidayEntity> holidays = classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getCourseProductId, courseProductId)
                .eq(ClassHolidayEntity::getIsDeleted, 0));
        List<ClassGroupEntity> groups = classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getProductId, courseProductId)
                .eq(ClassGroupEntity::getIsDeleted, 0));
        LocalDate today = LocalDate.now(AppTime.APP_ZONE_ID);
        LocalDateTime now = AppTime.nowLocalDateTime();

        for (ClassGroupEntity group : groups) {
            if (group.getStartDate() == null) {
                continue;
            }
            List<ClassGroupScheduleSlotEntity> slots = loadSlots(group.getId());
            LocalDate originalEnd = resolveScheduleEnd(group);
            int requiredSessions = isSessionBilling(group.getBillingType())
                    ? (group.getTotalSessions() == null ? 0 : group.getTotalSessions())
                    : ClassScheduleDates.countScheduledDays(group.getStartDate(), originalEnd, slots);
            if (requiredSessions < 1) continue;
            LocalDate targetEnd = ClassScheduleDates.nthScheduledDay(group.getStartDate(), requiredSessions,
                    slots, date -> holidayForDate(holidays, date) != null);
            if (targetEnd == null) continue;
            if (group.getEndDate() == null || targetEnd.isAfter(group.getEndDate())) {
                group.setEndDate(targetEnd);
                classGroupMapper.updateById(group);
            }

            LocalDate generationStart = group.getStartDate().isBefore(today) ? today : group.getStartDate();
            if (targetEnd.isBefore(generationStart)) continue;
            List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                    .eq(LessonOccurrenceEntity::getClassGroupId, group.getId())
                    .eq(LessonOccurrenceEntity::getIsDeleted, 0)
                    .ge(LessonOccurrenceEntity::getPlannedStartAt, generationStart.atStartOfDay())
                    .lt(LessonOccurrenceEntity::getPlannedStartAt, targetEnd.plusDays(1).atStartOfDay()));
            Set<String> existingKeys = occurrences.stream()
                    .map(item -> occurrenceKey(item.getPlannedStartAt(), item.getPlannedEndAt()))
                    .collect(java.util.stream.Collectors.toSet());

            for (LocalDate date = generationStart; !date.isAfter(targetEnd); date = date.plusDays(1)) {
                if (holidayForDate(holidays, date) != null) continue;
                int weekday = date.getDayOfWeek().getValue();
                for (ClassGroupScheduleSlotEntity slot : slots) {
                    if (!Objects.equals(slot.getWeekday(), weekday)) continue;
                    LocalDateTime startAt = LocalDateTime.of(date, slot.getStartTime());
                    if (startAt.isBefore(now)) continue;
                    LocalDateTime endAt = LocalDateTime.of(date, slot.getEndTime());
                    String key = occurrenceKey(startAt, endAt);
                    if (existingKeys.contains(key)) continue;

                    LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
                    occurrence.setLessonNo(BusinessNoGenerator.generateClassLessonOccurrenceNo());
                    occurrence.setClassGroupId(group.getId());
                    occurrence.setScheduleSlotId(slot.getId());
                    occurrence.setOccurrenceType("REGULAR");
                    occurrence.setPlannedTeacherId(slot.getTeacherId() == null ? group.getTeacherId() : slot.getTeacherId());
                    Long classroomId = slot.getClassroomId() != null ? slot.getClassroomId()
                            : ("ONLINE".equals(slot.getDeliveryMode()) ? null : group.getClassroomId());
                    occurrence.setDeliveryMode(slot.getDeliveryMode() == null && classroomId != null
                            ? "IN_PERSON" : slot.getDeliveryMode());
                    occurrence.setPlannedClassroomId(classroomId);
                    occurrence.setPlannedStartAt(startAt);
                    occurrence.setPlannedEndAt(endAt);
                    occurrence.setPlannedUnits((int) (ChronoUnit.MINUTES.between(slot.getStartTime(), slot.getEndTime()) / 30));
                    occurrence.setPlanningStatus("SCHEDULED");
                    occurrence.setConflictChecked(1);
                    occurrence.setIsDeleted(0);
                    ensureNoResourceConflict(occurrence);
                    ensureNoStudentConflict(occurrence);
                    lessonOccurrenceMapper.insert(occurrence);
                    existingKeys.add(key);
                }
            }
        }
    }

    private boolean isSessionBilling(String billingType) {
        return "SESSION".equalsIgnoreCase(billingType) || "PER_SESSION".equalsIgnoreCase(billingType);
    }

    private LocalDate resolveScheduleEnd(ClassGroupEntity group) {
        if (group.getEndDate() != null) return group.getEndDate();
        return classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .select(ClassEnrollmentEntity::getExpiryDate)
                        .eq(ClassEnrollmentEntity::getClassId, group.getId())
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0))
                .stream().map(ClassEnrollmentEntity::getExpiryDate).filter(Objects::nonNull)
                .max(LocalDate::compareTo).orElse(group.getStartDate().plusDays(60));
    }

    private String occurrenceKey(LocalDateTime startAt, LocalDateTime endAt) {
        return startAt + "|" + endAt;
    }

    private void ensureNoStudentConflict(LessonOccurrenceEntity occurrence) {
        LocalDate lessonDate = occurrence.getPlannedStartAt().toLocalDate();
        List<Long> studentIds = classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                        .eq(ClassEnrollmentEntity::getClassId, occurrence.getClassGroupId())
                        .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                        .eq(ClassEnrollmentEntity::getIsDeleted, 0))
                .stream().map(ClassEnrollmentEntity::getStudentId).filter(Objects::nonNull).distinct().toList();
        for (Long studentId : studentIds) {
            List<Long> otherClassIds = classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                            .eq(ClassEnrollmentEntity::getStudentId, studentId)
                            .eq(ClassEnrollmentEntity::getStatus, "ACTIVE")
                            .eq(ClassEnrollmentEntity::getIsDeleted, 0)
                            .ne(ClassEnrollmentEntity::getClassId, occurrence.getClassGroupId())
                            .and(wrapper -> wrapper.isNull(ClassEnrollmentEntity::getEnrollmentDate)
                                    .or().le(ClassEnrollmentEntity::getEnrollmentDate, lessonDate))
                            .and(wrapper -> wrapper.isNull(ClassEnrollmentEntity::getExpiryDate)
                                    .or().ge(ClassEnrollmentEntity::getExpiryDate, lessonDate)))
                    .stream().map(ClassEnrollmentEntity::getClassId).filter(Objects::nonNull).distinct().toList();
            List<Long> privateGroupIds = privateLessonGroupMemberMapper.selectList(new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                            .eq(PrivateLessonGroupMemberEntity::getStudentId, studentId)
                            .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                            .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0))
                    .stream().map(PrivateLessonGroupMemberEntity::getPrivateLessonGroupId)
                    .filter(Objects::nonNull).distinct().toList();
            LambdaQueryWrapper<LessonOccurrenceEntity> conflictQuery = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                    .eq(LessonOccurrenceEntity::getIsDeleted, 0)
                    .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                    .lt(LessonOccurrenceEntity::getPlannedStartAt, occurrence.getPlannedEndAt())
                    .gt(LessonOccurrenceEntity::getPlannedEndAt, occurrence.getPlannedStartAt())
                    .and(other -> other.isNull(LessonOccurrenceEntity::getClassGroupId)
                            .or().ne(LessonOccurrenceEntity::getClassGroupId, occurrence.getClassGroupId()))
                    .and(studentMatches -> studentMatches.eq(LessonOccurrenceEntity::getStudentId, studentId)
                            .or(!privateGroupIds.isEmpty(), group -> group.in(
                                    LessonOccurrenceEntity::getPrivateLessonGroupId, privateGroupIds))
                            .or(!otherClassIds.isEmpty(), classes -> classes.in(
                                    LessonOccurrenceEntity::getClassGroupId, otherClassIds)));
            if (lessonOccurrenceMapper.selectCount(conflictQuery) > 0) {
                throw new BusinessException("CLASS_HOLIDAY_SCHEDULE_CONFLICT",
                        "Extending this course schedule would overlap another lesson for an enrolled student on "
                                + lessonDate + ". Resolve the student schedule conflict first.");
            }
        }
    }

    private void recalculateSessionEnrollments(Long courseProductId, LocalDate from, LocalDate through) {
        List<ClassHolidayEntity> holidays = classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getCourseProductId, courseProductId)
                .eq(ClassHolidayEntity::getIsDeleted, 0));
        Set<Long> groupIds = courseGroupIds(courseProductId);
        if (groupIds.isEmpty()) return;
        List<ClassEnrollmentEntity> enrollments = classEnrollmentMapper.selectList(new LambdaQueryWrapper<ClassEnrollmentEntity>()
                .in(ClassEnrollmentEntity::getClassId, groupIds)
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

    private Set<Long> courseGroupIds(Long courseProductId) {
        return classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroupEntity>()
                        .select(ClassGroupEntity::getId)
                        .eq(ClassGroupEntity::getProductId, courseProductId)
                        .eq(ClassGroupEntity::getIsDeleted, 0))
                .stream().map(ClassGroupEntity::getId).collect(java.util.stream.Collectors.toSet());
    }

    private ClassHolidayEntity requireHoliday(Long courseProductId, Long id) {
        ClassHolidayEntity holiday = classHolidayMapper.selectById(id);
        if (holiday == null || !Objects.equals(holiday.getIsDeleted(), 0)
                || !Objects.equals(holiday.getCourseProductId(), courseProductId)) {
            throw new BusinessException("CLASS_HOLIDAY_NOT_FOUND", "Class holiday not found.");
        }
        return holiday;
    }

    private void requireGroupCourse(Long courseProductId) {
        CourseProductEntity product = courseProductMapper.selectById(courseProductId);
        if (product == null || !Objects.equals(product.getTeachingMode(), "GROUP")) {
            throw new BusinessException("CLASS_HOLIDAY_INVALID_COURSE", "Holidays can only be configured for a group-class course.");
        }
    }

    private ClassHolidayVO toVO(ClassHolidayEntity entity) {
        return new ClassHolidayVO(entity.getId(), entity.getHolidayName(), entity.getStartDate(), entity.getEndDate());
    }
}
