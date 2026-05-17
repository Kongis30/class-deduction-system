package com.classdeduction.system.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.schedule.dto.ScheduleAdjustmentRequest;
import com.classdeduction.system.modules.schedule.entity.LessonAdjustmentLogEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonAdjustmentLogMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.service.ScheduleAdjustmentService;
import com.classdeduction.system.modules.schedule.vo.ScheduleAdjustmentResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 调课服务：负责对既有课次进行变更，并记录前后快照日志。
 */
@Service
@RequiredArgsConstructor
public class ScheduleAdjustmentServiceImpl implements ScheduleAdjustmentService {

    private static final DateTimeFormatter LESSON_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final LessonOccurrenceMapper lessonOccurrenceMapper;
    private final LessonAdjustmentLogMapper lessonAdjustmentLogMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final JsonHelper jsonHelper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleAdjustmentResultVO adjust(ScheduleAdjustmentRequest request) {
        LessonOccurrenceEntity source = requireOccurrence(request.getOccurrenceId());
        Map<String, Object> beforeSnapshot = toSnapshot(source);
        validateAdjustmentType(request.getAdjustmentType());

        LessonOccurrenceEntity targetOccurrence;
        Long createdOccurrenceId = null;
        if ("MAKEUP_CREATE".equals(request.getAdjustmentType())) {
            targetOccurrence = createMakeupOccurrence(source, request);
            createdOccurrenceId = targetOccurrence.getId();
        } else {
            targetOccurrence = applyUpdate(source, request);
            lessonOccurrenceMapper.updateById(targetOccurrence);
        }

        LessonAdjustmentLogEntity log = new LessonAdjustmentLogEntity();
        log.setOccurrenceId(source.getId());
        log.setAdjustmentType(request.getAdjustmentType());
        log.setBeforeSnapshot(jsonHelper.toJson(beforeSnapshot));
        log.setAfterSnapshot(jsonHelper.toJson(toSnapshot(targetOccurrence)));
        log.setReason(request.getReason());
        log.setAdjustedBy(request.getAdjustedBy());
        log.setAdjustedAt(AppTime.nowLocalDateTime());
        lessonAdjustmentLogMapper.insert(log);

        return ScheduleAdjustmentResultVO.builder()
                .adjustmentLogId(log.getId())
                .affectedOccurrenceId(source.getId())
                .createdOccurrenceId(createdOccurrenceId)
                .adjustmentType(request.getAdjustmentType())
                .build();
    }

    private LessonOccurrenceEntity createMakeupOccurrence(LessonOccurrenceEntity source, ScheduleAdjustmentRequest request) {
        LessonOccurrenceEntity newOccurrence = new LessonOccurrenceEntity();
        newOccurrence.setLessonNo(generateLessonNo());
        newOccurrence.setPackageId(source.getPackageId());
        newOccurrence.setStudentId(source.getStudentId());
        newOccurrence.setScheduleRuleId(source.getScheduleRuleId());
        newOccurrence.setScheduleSlotId(source.getScheduleSlotId());
        newOccurrence.setOccurrenceType("MAKEUP");
        newOccurrence.setSourceOccurrenceId(source.getId());
        newOccurrence.setPlanningStatus("SCHEDULED");
        newOccurrence.setConflictChecked(1);
        applyRequestedPlanningFields(newOccurrence, source, request);
        validatePlannedWindow(newOccurrence.getPlannedStartAt(), newOccurrence.getPlannedEndAt(), newOccurrence.getPlannedUnits());
        validateTeacher(newOccurrence.getPlannedTeacherId());
        validateClassroom(newOccurrence.getPlannedClassroomId());
        ensureNoConflicts(newOccurrence, null);
        lessonOccurrenceMapper.insert(newOccurrence);
        return newOccurrence;
    }

    private LessonOccurrenceEntity applyUpdate(LessonOccurrenceEntity source, ScheduleAdjustmentRequest request) {
        LessonOccurrenceEntity updated = new LessonOccurrenceEntity();
        updated.setId(source.getId());
        updated.setLessonNo(source.getLessonNo());
        updated.setPackageId(source.getPackageId());
        updated.setStudentId(source.getStudentId());
        updated.setScheduleRuleId(source.getScheduleRuleId());
        updated.setScheduleSlotId(source.getScheduleSlotId());
        updated.setOccurrenceType(source.getOccurrenceType());
        updated.setSourceOccurrenceId(source.getSourceOccurrenceId());
        updated.setNotes(source.getNotes());
        updated.setConflictChecked(1);
        updated.setPlanningStatus("ADJUSTED");
        applyRequestedPlanningFields(updated, source, request);
        validatePlannedWindow(updated.getPlannedStartAt(), updated.getPlannedEndAt(), updated.getPlannedUnits());
        validateTeacher(updated.getPlannedTeacherId());
        validateClassroom(updated.getPlannedClassroomId());
        ensureNoConflicts(updated, source.getId());
        return updated;
    }

    private void applyRequestedPlanningFields(LessonOccurrenceEntity target, LessonOccurrenceEntity source, ScheduleAdjustmentRequest request) {
        target.setPlannedTeacherId(request.getPlannedTeacherId() != null ? request.getPlannedTeacherId() : source.getPlannedTeacherId());
        target.setPlannedClassroomId(request.getPlannedClassroomId() != null ? request.getPlannedClassroomId() : source.getPlannedClassroomId());
        target.setPlannedStartAt(request.getPlannedStartAt() != null ? request.getPlannedStartAt() : source.getPlannedStartAt());
        target.setPlannedEndAt(request.getPlannedEndAt() != null ? request.getPlannedEndAt() : source.getPlannedEndAt());
        target.setPlannedUnits(request.getPlannedUnits() != null ? request.getPlannedUnits() : source.getPlannedUnits());
    }

    private void validateAdjustmentType(String adjustmentType) {
        List<String> allowed = List.of("RESCHEDULE", "CHANGE_ROOM", "CHANGE_TEACHER", "MAKEUP_CREATE");
        if (!allowed.contains(adjustmentType)) {
            throw new BusinessException("UNSUPPORTED_ADJUSTMENT_TYPE", "Unsupported adjustment type.");
        }
    }

    private void validatePlannedWindow(LocalDateTime startAt, LocalDateTime endAt, Integer plannedUnits) {
        if (startAt == null || endAt == null || plannedUnits == null) {
            throw new BusinessException("INCOMPLETE_ADJUSTMENT", "Adjusted lesson must include start time, end time, and planned units.");
        }
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException("INVALID_ADJUSTMENT_TIME_RANGE", "Adjusted end time must be later than the start time.");
        }
        long minutes = java.time.temporal.ChronoUnit.MINUTES.between(startAt, endAt);
        if (minutes != plannedUnits * 30L) {
            throw new BusinessException("ADJUSTMENT_UNITS_MISMATCH", "Adjusted lesson duration must match planned units at 0.5h per unit.");
        }
    }

    private void validateTeacher(Long teacherId) {
        if (teacherId == null) {
            return;
        }
        TeacherEntity teacher = teacherMapper.selectById(teacherId);
        if (teacher == null || !"ACTIVE".equalsIgnoreCase(teacher.getStatus())) {
            throw new BusinessException("TEACHER_NOT_AVAILABLE", "The selected teacher does not exist or is not active.");
        }
    }

    private void validateClassroom(Long classroomId) {
        if (classroomId == null) {
            return;
        }
        ClassroomEntity classroom = classroomMapper.selectById(classroomId);
        if (classroom == null || !"ACTIVE".equalsIgnoreCase(classroom.getStatus())) {
            throw new BusinessException("CLASSROOM_NOT_AVAILABLE", "The selected classroom does not exist or is not active.");
        }
    }

    private void ensureNoConflicts(LessonOccurrenceEntity target, Long ignoredOccurrenceId) {
        if (hasConflict(target, LessonOccurrenceEntity::getStudentId, target.getStudentId(), ignoredOccurrenceId)) {
            throw new BusinessException("SCHEDULE_CONFLICT", "Student conflict detected for the adjusted lesson.");
        }
        if (target.getPlannedTeacherId() != null
                && hasConflict(target, LessonOccurrenceEntity::getPlannedTeacherId, target.getPlannedTeacherId(), ignoredOccurrenceId)) {
            throw new BusinessException("SCHEDULE_CONFLICT", "Teacher conflict detected for the adjusted lesson.");
        }
        if (target.getPlannedClassroomId() != null
                && hasConflict(target, LessonOccurrenceEntity::getPlannedClassroomId, target.getPlannedClassroomId(), ignoredOccurrenceId)) {
            throw new BusinessException("SCHEDULE_CONFLICT", "Classroom conflict detected for the adjusted lesson.");
        }
    }

    private boolean hasConflict(LessonOccurrenceEntity target,
                                com.baomidou.mybatisplus.core.toolkit.support.SFunction<LessonOccurrenceEntity, Long> field,
                                Long fieldValue,
                                Long ignoredOccurrenceId) {
        LambdaQueryWrapper<LessonOccurrenceEntity> wrapper = new LambdaQueryWrapper<LessonOccurrenceEntity>()
                .eq(field, fieldValue)
                .ne(LessonOccurrenceEntity::getPlanningStatus, "CANCELLED")
                .lt(LessonOccurrenceEntity::getPlannedStartAt, target.getPlannedEndAt())
                .gt(LessonOccurrenceEntity::getPlannedEndAt, target.getPlannedStartAt());
        if (ignoredOccurrenceId != null) {
            wrapper.ne(LessonOccurrenceEntity::getId, ignoredOccurrenceId);
        }
        return lessonOccurrenceMapper.selectCount(wrapper) > 0;
    }

    private LessonOccurrenceEntity requireOccurrence(Long occurrenceId) {
        LessonOccurrenceEntity occurrence = lessonOccurrenceMapper.selectById(occurrenceId);
        if (occurrence == null) {
            throw new BusinessException("LESSON_OCCURRENCE_NOT_FOUND", "Lesson occurrence not found.");
        }
        if ("CANCELLED".equals(occurrence.getPlanningStatus())) {
            throw new BusinessException("LESSON_OCCURRENCE_CANCELLED", "Cancelled lesson occurrences cannot be adjusted.");
        }
        return occurrence;
    }

    private Map<String, Object> toSnapshot(LessonOccurrenceEntity occurrence) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", occurrence.getId());
        snapshot.put("lessonNo", occurrence.getLessonNo());
        snapshot.put("packageId", occurrence.getPackageId());
        snapshot.put("studentId", occurrence.getStudentId());
        snapshot.put("occurrenceType", occurrence.getOccurrenceType());
        snapshot.put("plannedTeacherId", occurrence.getPlannedTeacherId());
        snapshot.put("plannedClassroomId", occurrence.getPlannedClassroomId());
        snapshot.put("plannedStartAt", occurrence.getPlannedStartAt());
        snapshot.put("plannedEndAt", occurrence.getPlannedEndAt());
        snapshot.put("plannedUnits", occurrence.getPlannedUnits());
        snapshot.put("planningStatus", occurrence.getPlanningStatus());
        snapshot.put("sourceOccurrenceId", occurrence.getSourceOccurrenceId());
        return snapshot;
    }

    private String generateLessonNo() {
        return "LS" + LESSON_NO_FORMATTER.format(AppTime.nowLocalDateTime())
                + String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1000, 10000));
    }
}
