package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.entity.GroupCourseProjectEntity;
import com.classdeduction.system.modules.base.mapper.GroupCourseProjectMapper;
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
import com.classdeduction.system.modules.schedule.util.ClassSlotRotation;
import com.classdeduction.system.modules.schedule.util.ScheduleSlotTime;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 维护教学项目级班课假期，并协调项目下未来课次和 SESSION 报名周期。
 * Prices 页面调用此服务；课次生成、计费和调课也复用其日期查询。
 */
@Service
@RequiredArgsConstructor
public class ClassHolidayServiceImpl implements ClassHolidayService {

    private final ClassHolidayMapper classHolidayMapper;
    private final GroupCourseProjectMapper projectMapper;
    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final ClassEnrollmentMapper classEnrollmentMapper;
    private final PrivateLessonGroupMemberMapper privateLessonGroupMemberMapper;
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
        synchronizeProjectClassSchedules(groupProjectId);
        recalculateSessionEnrollments(groupProjectId, request.getStartDate(), request.getEndDate());
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
        synchronizeProjectClassSchedules(groupProjectId);
        recalculateSessionEnrollments(groupProjectId, from, through);
        return toVO(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHoliday(Long groupProjectId, Long id) {
        lockProjectClasses(groupProjectId);
        ClassHolidayEntity existing = requireHoliday(groupProjectId, id);
        classHolidayMapper.deleteById(id);
        reconcileAffectedDates(groupProjectId, existing.getStartDate(), existing.getEndDate());
        synchronizeProjectClassSchedules(groupProjectId);
        recalculateSessionEnrollments(groupProjectId, existing.getStartDate(), existing.getEndDate());
    }

    /**
     * 假期修改会重算项目内全部班级；先用锁定读取锁住项目，再按班级 ID 锁其实际班级。
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

    /**
     * 假期会减少 Grade 下各班级原周期内可排的上课日，因此按该 Grade 假期重新计算课次终点，
     * 并只补齐未来缺少的周课次。Prices 的假期新增、编辑、删除都在落库后调用本方法。
     */
    private void synchronizeProjectClassSchedules(Long groupProjectId) {
        List<ClassHolidayEntity> holidays = classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getGroupProjectId, groupProjectId)
                .eq(ClassHolidayEntity::getIsDeleted, 0));
        List<ClassGroupEntity> groups = classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroupEntity>()
                .eq(ClassGroupEntity::getGroupProjectId, groupProjectId)
                .eq(ClassGroupEntity::getIsDeleted, 0));
        LocalDate today = LocalDate.now(AppTime.APP_ZONE_ID);
        LocalDateTime now = AppTime.nowLocalDateTime();

        for (ClassGroupEntity group : groups) {
            if (group.getStartDate() == null) {
                continue;
            }
            List<ClassGroupScheduleSlotEntity> slots = loadSlots(group.getId());
            LocalDate effectiveEnd = resolveScheduleEnd(group);
            LocalDate baseEnd = ClassScheduleDates.resolveBaseEndDate(group.getScheduleBaseEndDate(), effectiveEnd);
            LocalDate targetEnd = ClassScheduleDates.endDateAfterHolidays(group.getStartDate(), baseEnd,
                    group.getBillingType(), group.getTotalSessions(), slots,
                    date -> holidayForDate(holidays, date) != null);
            if (targetEnd == null) continue;
            boolean needsUpdate = !targetEnd.equals(group.getEndDate()) || group.getScheduleBaseEndDate() == null;
            group.setScheduleBaseEndDate(baseEnd);
            if (needsUpdate) {
                group.setEndDate(targetEnd);
                classGroupMapper.updateById(group);
            }

            removeFutureRegularOccurrencesAfter(group.getId(), targetEnd, now);

            LocalDate generationStart = group.getStartDate().isBefore(today) ? today : group.getStartDate();
            if (targetEnd.isBefore(generationStart)) continue;
            List<LessonOccurrenceEntity> occurrences = lessonOccurrenceMapper.selectList(new LambdaQueryWrapper<LessonOccurrenceEntity>()
                    .eq(LessonOccurrenceEntity::getClassGroupId, group.getId())
                    .eq(LessonOccurrenceEntity::getIsDeleted, 0)
                    .ge(LessonOccurrenceEntity::getPlannedStartAt, generationStart.atStartOfDay())
                    .lt(LessonOccurrenceEntity::getPlannedStartAt, targetEnd.plusDays(1).atStartOfDay()));
            Map<Long, ClassGroupScheduleSlotEntity> slotsById = slots.stream()
                    .collect(Collectors.toMap(ClassGroupScheduleSlotEntity::getId, item -> item));
            Set<Long> occurrenceIds = occurrences.stream().map(LessonOccurrenceEntity::getId).collect(Collectors.toSet());
            Set<Long> recordedOccurrenceIds = occurrenceIds.isEmpty() ? Set.of()
                    : lessonRecordMapper.selectList(new LambdaQueryWrapper<LessonRecordEntity>()
                                    .select(LessonRecordEntity::getOccurrenceId)
                                    .in(LessonRecordEntity::getOccurrenceId, occurrenceIds)
                                    .eq(LessonRecordEntity::getIsDeleted, 0))
                            .stream().map(LessonRecordEntity::getOccurrenceId).collect(Collectors.toSet());

            // 假期會改變後續輪換次序；只更新未開始、未消課、仍由模板控制的未來課次。
            for (LessonOccurrenceEntity occurrence : occurrences) {
                ClassGroupScheduleSlotEntity slot = slotsById.get(occurrence.getScheduleSlotId());
                if (slot == null || !"SCHEDULED".equals(occurrence.getPlanningStatus())
                        || occurrence.getCourseHolidayId() != null
                        || occurrence.getPlannedStartAt().isBefore(now)
                        || recordedOccurrenceIds.contains(occurrence.getId())) {
                    continue;
                }
                ClassSlotRotation.Assignment assignment = ClassSlotRotation.resolve(slot,
                        occurrence.getPlannedStartAt().toLocalDate(), group.getStartDate(),
                        date -> holidayForDate(holidays, date) != null);
                Long resolvedTeacherId = assignment.teacherId() == null ? group.getTeacherId() : assignment.teacherId();
                Long resolvedClassroomId = assignment.alternate()
                        ? ("ONLINE".equals(assignment.deliveryMode()) ? null : assignment.classroomId())
                        : "ONLINE".equals(assignment.deliveryMode()) ? null
                                : assignment.classroomId() != null ? assignment.classroomId() : group.getClassroomId();
                String resolvedDeliveryMode = assignment.deliveryMode() == null && resolvedClassroomId != null
                        ? "IN_PERSON" : assignment.deliveryMode();
                if (!Objects.equals(resolvedTeacherId, occurrence.getPlannedTeacherId())
                        || !Objects.equals(assignment.subjectName(), occurrence.getSubjectName())
                        || !Objects.equals(resolvedClassroomId, occurrence.getPlannedClassroomId())
                        || !Objects.equals(resolvedDeliveryMode, occurrence.getDeliveryMode())) {
                    occurrence.setPlannedTeacherId(resolvedTeacherId);
                    occurrence.setSubjectName(assignment.subjectName());
                    occurrence.setPlannedClassroomId(resolvedClassroomId);
                    occurrence.setDeliveryMode(resolvedDeliveryMode);
                    ensureNoResourceConflict(occurrence);
                    lessonOccurrenceMapper.updateById(occurrence);
                }
            }
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
                    LocalDateTime endAt = ScheduleSlotTime.endAt(date, slot.getStartTime(), slot.getEndTime());
                    String key = occurrenceKey(startAt, endAt);
                    if (existingKeys.contains(key)) continue;

                    LessonOccurrenceEntity occurrence = new LessonOccurrenceEntity();
                    occurrence.setLessonNo(BusinessNoGenerator.generateClassLessonOccurrenceNo());
                    occurrence.setClassGroupId(group.getId());
                    occurrence.setScheduleSlotId(slot.getId());
                    occurrence.setOccurrenceType("REGULAR");
                    ClassSlotRotation.Assignment assignment = ClassSlotRotation.resolve(slot, date,
                            group.getStartDate(), holidayDate -> holidayForDate(holidays, holidayDate) != null);
                    occurrence.setPlannedTeacherId(assignment.teacherId() == null
                            ? group.getTeacherId() : assignment.teacherId());
                    occurrence.setSubjectName(assignment.subjectName());
                    Long classroomId = assignment.alternate()
                            ? ("ONLINE".equals(assignment.deliveryMode()) ? null : assignment.classroomId())
                            : "ONLINE".equals(assignment.deliveryMode()) ? null
                                    : assignment.classroomId() != null ? assignment.classroomId() : group.getClassroomId();
                    occurrence.setDeliveryMode(assignment.deliveryMode() == null && classroomId != null
                            ? "IN_PERSON" : assignment.deliveryMode());
                    occurrence.setPlannedClassroomId(classroomId);
                    occurrence.setPlannedStartAt(startAt);
                    occurrence.setPlannedEndAt(endAt);
                    occurrence.setPlannedUnits(ScheduleSlotTime.durationMinutes(slot.getStartTime(), slot.getEndTime()) / 30);
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

    /** 假期缩短后，清理旧周期尾部被顺延出来且尚未上课的自动周课次。 */
    private void removeFutureRegularOccurrencesAfter(Long classGroupId, LocalDate targetEnd, LocalDateTime now) {
        List<LessonOccurrenceEntity> excessOccurrences = lessonOccurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classGroupId)
                        .eq(LessonOccurrenceEntity::getOccurrenceType, "REGULAR")
                        .eq(LessonOccurrenceEntity::getPlanningStatus, "SCHEDULED")
                        .isNotNull(LessonOccurrenceEntity::getScheduleSlotId)
                        .eq(LessonOccurrenceEntity::getIsDeleted, 0)
                        .gt(LessonOccurrenceEntity::getPlannedStartAt, now)
                        .ge(LessonOccurrenceEntity::getPlannedStartAt, targetEnd.plusDays(1).atStartOfDay()));
        for (LessonOccurrenceEntity occurrence : excessOccurrences) {
            ensureNoLessonRecord(occurrence);
            lessonOccurrenceMapper.deleteById(occurrence.getId());
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

    private void recalculateSessionEnrollments(Long groupProjectId, LocalDate from, LocalDate through) {
        List<ClassHolidayEntity> holidays = classHolidayMapper.selectList(new LambdaQueryWrapper<ClassHolidayEntity>()
                .eq(ClassHolidayEntity::getGroupProjectId, groupProjectId)
                .eq(ClassHolidayEntity::getIsDeleted, 0));
        Set<Long> groupIds = projectClassIds(groupProjectId);
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
