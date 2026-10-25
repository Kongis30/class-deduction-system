package com.classdeduction.system.modules.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonAdjustmentLogMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 单节调课仍占据原周课位；整表编辑、假期补课和每日续排共用此保留集合。 */
@Component
@RequiredArgsConstructor
public class ClassAdjustedSlotReservations {

    private final LessonOccurrenceMapper occurrenceMapper;
    private final LessonAdjustmentLogMapper adjustmentLogMapper;
    private final ClassGroupScheduleSlotMapper slotMapper;

    public Set<Reservation> load(Long classId) {
        List<LessonOccurrenceEntity> adjusted = occurrenceMapper.selectList(
                new LambdaQueryWrapper<LessonOccurrenceEntity>()
                        .eq(LessonOccurrenceEntity::getClassGroupId, classId)
                        .eq(LessonOccurrenceEntity::getPlanningStatus, "ADJUSTED"))
                .stream().filter(occurrence -> occurrence.getScheduleSlotId() != null).toList();
        if (adjusted.isEmpty()) return Set.of();
        Map<Long, Map<String, Object>> snapshots = new HashMap<>();
        for (Map<String, Object> snapshot : adjustmentLogMapper.selectFirstClassAdjustmentSnapshots(
                adjusted.stream().map(LessonOccurrenceEntity::getId).toList())) {
            Long occurrenceId = longValue(snapshot.get("occurrence_id"));
            if (occurrenceId != null) snapshots.put(occurrenceId, snapshot);
        }
        return adjusted.stream().map(occurrence -> {
            Map<String, Object> snapshot = snapshots.get(occurrence.getId());
            LocalDate originalDate = occurrence.getAllocationAnchorDate();
            if (originalDate == null && snapshot != null && snapshot.get("original_start_at") != null) {
                String value = String.valueOf(snapshot.get("original_start_at"));
                if (value.length() >= 10) {
                    try { originalDate = LocalDate.parse(value.substring(0, 10)); }
                    catch (RuntimeException ignored) { /* 由下方统一拒绝无法恢复的历史数据。 */ }
                }
            }
            Long slotId = snapshot == null ? null : longValue(snapshot.get("original_slot_id"));
            if (slotId == null) slotId = occurrence.getScheduleSlotId();
            ClassGroupScheduleSlotEntity originalSlot = slotMapper.selectIncludingDeletedById(slotId);
            if (originalDate == null || originalSlot == null) {
                throw new BusinessException("CLASS_ADJUSTMENT_AMBIGUOUS",
                        "An adjusted lesson has no recoverable original schedule slot. Resolve it before editing the schedule.");
            }
            Long lineageId = originalSlot.getSlotLineageId() == null
                    ? originalSlot.getId() : originalSlot.getSlotLineageId();
            return new Reservation(originalDate, lineageId);
        }).collect(Collectors.toSet());
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    public record Reservation(LocalDate originalDate, Long slotLineageId) { }
}
