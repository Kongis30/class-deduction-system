package com.classdeduction.system.modules.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
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
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleStartingBalanceEntity;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleStartingBalanceMapper;
import com.classdeduction.system.modules.schedule.vo.PrivateSchedulePrintRevisionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
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
    private final TeacherMapper teacherMapper;
    private final ClassroomMapper classroomMapper;

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
        List<RecurringScheduleSlotEntity> slots = slotMapper.selectList(
                new LambdaQueryWrapper<RecurringScheduleSlotEntity>()
                        .in(RecurringScheduleSlotEntity::getRuleId, ruleIds)
                        .orderByAsc(RecurringScheduleSlotEntity::getWeekday)
                        .orderByAsc(RecurringScheduleSlotEntity::getStartTime));
        Map<Long, List<RecurringScheduleSlotEntity>> slotsByRule = slots.stream()
                .collect(Collectors.groupingBy(RecurringScheduleSlotEntity::getRuleId));
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
                TeacherEntity teacher = teachers.get(slot.getTeacherId());
                ClassroomEntity classroom = classrooms.get(slot.getClassroomId());
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
                    UnitConverter.toHoursDecimal(allocatedUnits), printSlots);
        }).toList();
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
