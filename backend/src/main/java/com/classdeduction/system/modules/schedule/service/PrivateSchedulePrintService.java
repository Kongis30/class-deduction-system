package com.classdeduction.system.modules.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.common.util.JsonHelper;
import com.classdeduction.system.common.util.UnitConverter;
import com.classdeduction.system.modules.base.entity.ClassroomEntity;
import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.PrivateLessonGroupMemberEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PrivateLessonGroupMemberMapper;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleRuleEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.LessonAdjustmentLogEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleStartingBalanceEntity;
import com.classdeduction.system.modules.schedule.mapper.LessonAdjustmentLogMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleStartingBalanceMapper;
import com.classdeduction.system.modules.schedule.vo.PrivateSchedulePrintRevisionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 为学生和老师签字表读取完整的排课版本链，不修改课包或课次。 */
@Service
@RequiredArgsConstructor
public class PrivateSchedulePrintService {

    private final CoursePackageMapper coursePackageMapper;
    private final PrivateLessonGroupMemberMapper groupMemberMapper;
    private final RecurringScheduleRuleMapper ruleMapper;
    private final RecurringScheduleSlotMapper slotMapper;
    private final RecurringScheduleStartingBalanceMapper startingBalanceMapper;
    private final LessonOccurrenceMapper occurrenceMapper;
    private final LessonAdjustmentLogMapper adjustmentLogMapper;
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;
    private final JsonHelper jsonHelper;

    /** 共享课成员沿用同一规则版本；每位成员读取自己的 Starting Hours 快照。 */
    public List<PrivateSchedulePrintRevisionVO> listRevisions(Long packageId) {
        if (coursePackageMapper.selectById(packageId) == null) {
            throw new BusinessException("PACKAGE_NOT_FOUND", "Course package not found.");
        }
        PrivateLessonGroupMemberEntity member = groupMemberMapper.selectOne(
                new LambdaQueryWrapper<PrivateLessonGroupMemberEntity>()
                        .eq(PrivateLessonGroupMemberEntity::getPackageId, packageId)
                        .eq(PrivateLessonGroupMemberEntity::getMemberStatus, "ACTIVE")
                        .eq(PrivateLessonGroupMemberEntity::getIsDeleted, 0)
                        .last("LIMIT 1"));
        Long groupId = member == null ? null : member.getPrivateLessonGroupId();
        List<RecurringScheduleRuleEntity> rules = ruleMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleRuleEntity>()
                        .and(scope -> scope.eq(RecurringScheduleRuleEntity::getPackageId, packageId)
                                .or(groupId != null, group -> group.eq(
                                        RecurringScheduleRuleEntity::getPrivateLessonGroupId, groupId)))
                        .orderByAsc(RecurringScheduleRuleEntity::getCreatedAt)
                        .orderByAsc(RecurringScheduleRuleEntity::getId));
        if (rules.isEmpty()) return Collections.emptyList();

        List<Long> ruleIds = rules.stream().map(RecurringScheduleRuleEntity::getId).toList();
        List<RecurringScheduleSlotEntity> slots = slotMapper.selectIncludingDeletedByRuleIds(ruleIds);
        Map<Long, List<RecurringScheduleSlotEntity>> slotsByRule = slots.stream()
                .collect(Collectors.groupingBy(RecurringScheduleSlotEntity::getRuleId));
        Map<Long, List<PrivateSchedulePrintRevisionVO.DetailsChange>> detailsChangesByRule =
                detailsChangesByRule(ruleIds);
        Map<Long, Integer> startingByRule = startingBalanceMapper.selectList(
                        new LambdaQueryWrapper<RecurringScheduleStartingBalanceEntity>()
                                .in(RecurringScheduleStartingBalanceEntity::getScheduleRuleId, ruleIds)
                                .eq(RecurringScheduleStartingBalanceEntity::getPackageId, packageId))
                .stream().collect(Collectors.toMap(RecurringScheduleStartingBalanceEntity::getScheduleRuleId,
                        RecurringScheduleStartingBalanceEntity::getStartingUnits));
        Map<Long, TeacherEntity> teachers = teachersById(slots);
        Map<Long, ClassroomEntity> classrooms = classroomsById(slots);

        return java.util.stream.IntStream.range(0, rules.size()).mapToObj(index -> {
            RecurringScheduleRuleEntity rule = rules.get(index);
            List<RecurringScheduleSlotEntity> ruleSlots = slotsByRule.getOrDefault(rule.getId(), List.of());
            List<PrivateSchedulePrintRevisionVO.Slot> printSlots = ruleSlots.stream().map(slot -> {
                TeacherEntity teacher = slot.getTeacherId() == null ? null : teachers.get(slot.getTeacherId());
                ClassroomEntity classroom = slot.getClassroomId() == null ? null : classrooms.get(slot.getClassroomId());
                return new PrivateSchedulePrintRevisionVO.Slot(slot.getWeekday(), slot.getStartTime(),
                        slot.getEndTime(), slot.getSubjectName(),
                        teacher == null ? null : teacher.getTeacherName(),
                        classroom == null ? null : classroom.getRoomName(), slot.getDeliveryMode(),
                        UnitConverter.toHoursDecimal(slot.getAllocatedUnits()));
            }).toList();
            Integer startingUnits = startingByRule.get(rule.getId());
            int allocatedUnits = ruleSlots.stream().mapToInt(slot ->
                    slot.getAllocatedUnits() == null ? 0 : Math.max(0, slot.getAllocatedUnits())).sum();
            return new PrivateSchedulePrintRevisionVO(rule.getId(), rule.getSupersedesRuleId(),
                    index + 1, rule.getCreatedAt(), rule.getStartDate(), rule.getEndDate(),
                    startingUnits == null ? null : UnitConverter.toHoursDecimal(startingUnits),
                    UnitConverter.toHoursDecimal(allocatedUnits), printSlots,
                    detailsChangesByRule.getOrDefault(rule.getId(), List.of()));
        }).toList();
    }

    /** 将周排课字段修改日志附到对应版本，供 Schedule 打印页保留每次改动。 */
    private Map<Long, List<PrivateSchedulePrintRevisionVO.DetailsChange>> detailsChangesByRule(List<Long> ruleIds) {
        List<LessonOccurrenceEntity> occurrences = occurrenceMapper.selectIncludingDeletedByRuleIds(ruleIds);
        if (occurrences.isEmpty()) return Map.of();
        Map<Long, Long> ruleByOccurrence = occurrences.stream().collect(Collectors.toMap(
                LessonOccurrenceEntity::getId, LessonOccurrenceEntity::getScheduleRuleId, (left, right) -> left));
        List<Long> occurrenceIds = new ArrayList<>(ruleByOccurrence.keySet());
        List<LessonAdjustmentLogEntity> logs = adjustmentLogMapper.selectList(
                new LambdaQueryWrapper<LessonAdjustmentLogEntity>()
                        .in(LessonAdjustmentLogEntity::getOccurrenceId, occurrenceIds)
                        .eq(LessonAdjustmentLogEntity::getAdjustmentType, "SCHEDULE_DETAILS_UPDATE")
                        .orderByAsc(LessonAdjustmentLogEntity::getAdjustedAt)
                        .orderByAsc(LessonAdjustmentLogEntity::getId));
        Map<Long, LinkedHashSet<String>> uniqueByRule = new HashMap<>();
        Map<Long, List<PrivateSchedulePrintRevisionVO.DetailsChange>> changesByRule = new HashMap<>();
        for (LessonAdjustmentLogEntity log : logs) {
            Long ruleId = ruleByOccurrence.get(log.getOccurrenceId());
            if (ruleId == null) continue;
            String before = detailsSummary(log.getBeforeSnapshot());
            String after = detailsSummary(log.getAfterSnapshot());
            String key = log.getAdjustedAt() + "|" + before + "|" + after;
            if (!uniqueByRule.computeIfAbsent(ruleId, ignored -> new LinkedHashSet<>()).add(key)) continue;
            changesByRule.computeIfAbsent(ruleId, ignored -> new ArrayList<>()).add(
                    new PrivateSchedulePrintRevisionVO.DetailsChange(log.getAdjustedAt(), before, after));
        }
        return changesByRule;
    }

    private String detailsSummary(String json) {
        Map<String, Object> snapshot = jsonHelper.fromJson(json, new TypeReference<>() {});
        if (snapshot == null) return "Schedule details unavailable";
        String[] weekdays = {"", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        Object weekdayValue = snapshot.get("weekday");
        int weekday = weekdayValue instanceof Number number ? number.intValue() : 0;
        String day = weekday > 0 && weekday < weekdays.length ? weekdays[weekday] : "Day";
        return String.join(" · ", day + " " + valueOrDash(snapshot.get("startTime")) + "–"
                        + valueOrDash(snapshot.get("endTime")),
                valueOrDash(snapshot.get("subjectName")), valueOrDash(snapshot.get("teacherName")),
                valueOrDash(snapshot.get("classroomName")));
    }

    private String valueOrDash(Object value) {
        return value == null || value.toString().isBlank() ? "—" : value.toString();
    }

    private Map<Long, TeacherEntity> teachersById(List<RecurringScheduleSlotEntity> slots) {
        Set<Long> ids = slots.stream().map(RecurringScheduleSlotEntity::getTeacherId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : teacherMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(TeacherEntity::getId, Function.identity()));
    }

    private Map<Long, ClassroomEntity> classroomsById(List<RecurringScheduleSlotEntity> slots) {
        Set<Long> ids = slots.stream().map(RecurringScheduleSlotEntity::getClassroomId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : classroomMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ClassroomEntity::getId, Function.identity()));
    }
}
