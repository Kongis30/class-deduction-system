package com.classdeduction.system.modules.schedule.util;

import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;
import com.classdeduction.system.modules.schedule.entity.RecurringScheduleSlotEntity;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 私教课包的排课占用口径：未确认的有效课次仍占用余额，确认后由消课账本扣减。
 * 排课重算和消课页摘要共用，避免把已消课的历史课次再次计入 Allocated。
 */
public final class PrivateLessonAllocation {

    private PrivateLessonAllocation() {
    }

    /** 旧课位配置未变时，其历史课次仍包含在当前分配额内；请假增量先从当前额度中还原。 */
    public static boolean hasEquivalentCurrentSlot(RecurringScheduleSlotEntity historicalSlot,
                                                   List<RecurringScheduleSlotEntity> currentSlots,
                                                   Long currentRuleId) {
        return hasEquivalentCurrentSlot(historicalSlot, currentSlots, currentRuleId, Map.of());
    }

    /** 请假带入的分配增量不改变历史课位定义，因此比较时先还原当前课位的基础分配额。 */
    public static boolean hasEquivalentCurrentSlot(RecurringScheduleSlotEntity historicalSlot,
                                                   List<RecurringScheduleSlotEntity> currentSlots,
                                                   Long currentRuleId,
                                                   Map<Long, Integer> leaveCarryoverBySlot) {
        if (historicalSlot == null || !Objects.equals(historicalSlot.getRuleId(), currentRuleId)) {
            return false;
        }
        return currentSlots.stream().anyMatch(currentSlot ->
                Objects.equals(currentSlot.getRuleId(), currentRuleId)
                        && Objects.equals(historicalSlot.getTeacherId(), currentSlot.getTeacherId())
                        && Objects.equals(historicalSlot.getWeekday(), currentSlot.getWeekday())
                        && Objects.equals(historicalSlot.getStartTime(), currentSlot.getStartTime())
                        && Objects.equals(historicalSlot.getEndTime(), currentSlot.getEndTime())
                        && Objects.equals(historicalSlot.getPlannedUnits(), currentSlot.getPlannedUnits())
                        && Objects.equals(historicalSlot.getAllocatedUnits(), Math.max(0,
                        (currentSlot.getAllocatedUnits() == null ? 0 : currentSlot.getAllocatedUnits())
                                - (currentSlot.getId() == null ? 0
                                : leaveCarryoverBySlot.getOrDefault(currentSlot.getId(), 0)))));
    }

    /** 分配额或老师调整不改变重复课位的时间身份；旧版本对应课次已由当前快照覆盖。 */
    public static boolean hasCurrentSlotForSameRecurringTime(RecurringScheduleSlotEntity historicalSlot,
                                                              List<RecurringScheduleSlotEntity> currentSlots,
                                                              Long currentRuleId) {
        if (historicalSlot == null || !Objects.equals(historicalSlot.getRuleId(), currentRuleId)) {
            return false;
        }
        return currentSlots.stream().anyMatch(currentSlot ->
                Objects.equals(currentSlot.getRuleId(), currentRuleId)
                        && Objects.equals(historicalSlot.getWeekday(), currentSlot.getWeekday())
                        && Objects.equals(historicalSlot.getStartTime(), currentSlot.getStartTime())
                        && Objects.equals(historicalSlot.getEndTime(), currentSlot.getEndTime())
                        && Objects.equals(historicalSlot.getPlannedUnits(), currentSlot.getPlannedUnits()));
    }

    /** 旧版重建的课位仅在同一规则、时段唯一匹配时接续，避免把不同周期或歧义课位合并。 */
    public static RecurringScheduleSlotEntity findUniqueCurrentSlot(
            RecurringScheduleSlotEntity historical, List<RecurringScheduleSlotEntity> currentSlots) {
        if (historical == null || historical.getRuleId() == null || historical.getWeekday() == null
                || historical.getStartTime() == null || historical.getEndTime() == null
                || historical.getPlannedUnits() == null) return null;
        List<RecurringScheduleSlotEntity> matches = currentSlots.stream()
                .filter(slot -> Objects.equals(historical.getRuleId(), slot.getRuleId()))
                .filter(slot -> Objects.equals(historical.getWeekday(), slot.getWeekday())
                        && Objects.equals(historical.getStartTime(), slot.getStartTime())
                        && Objects.equals(historical.getEndTime(), slot.getEndTime())
                        && Objects.equals(historical.getPlannedUnits(), slot.getPlannedUnits()))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    /** 同一课次可能出现在多个老师或共享课成员视图中，按课次 ID 只统计一次。 */
    public static int reservedUnits(Collection<LessonOccurrenceEntity> occurrences,
                                    Map<Long, LessonRecordEntity> recordsByOccurrenceId) {
        int total = 0;
        Set<Long> seenIds = new HashSet<>();
        for (LessonOccurrenceEntity occurrence : occurrences) {
            if (occurrence.getId() != null && !seenIds.add(occurrence.getId())) {
                continue;
            }
            total += reservedUnits(occurrence, recordsByOccurrenceId.get(occurrence.getId()));
        }
        return total;
    }

    /** 过去尚无有效消课结果的课次先预留课时；待管理员确认的扣课由 pending 口径单独计算。 */
    public static int unresolvedPastUnits(Collection<LessonOccurrenceEntity> occurrences,
                                          Map<Long, LessonRecordEntity> recordsByOccurrenceId,
                                          LocalDateTime now) {
        int total = 0;
        Set<Long> seenIds = new HashSet<>();
        for (LessonOccurrenceEntity occurrence : occurrences) {
            if (occurrence.getId() != null && !seenIds.add(occurrence.getId())) {
                continue;
            }
            if (occurrence.getPlannedStartAt() == null || !occurrence.getPlannedStartAt().isBefore(now)
                    || "CANCELLED".equals(occurrence.getPlanningStatus())) {
                continue;
            }
            LessonRecordEntity record = recordsByOccurrenceId.get(occurrence.getId());
            if (record != null && ("CONFIRMED".equals(record.getConfirmStage())
                    || "PENDING_ADMIN".equals(record.getConfirmStage()))) {
                continue;
            }
            total += Math.max(0, occurrence.getPlannedUnits() == null ? 0 : occurrence.getPlannedUnits());
        }
        return total;
    }

    /** 已结束但管理员尚未最终处理的课次；老师未提交、退回重交和待管理员审核均计入。 */
    public static boolean isPendingProcessing(LessonOccurrenceEntity occurrence,
                                              LessonRecordEntity record,
                                              LocalDateTime now) {
        if (occurrence == null || occurrence.getPlannedEndAt() == null
                || occurrence.getPlannedEndAt().isAfter(now)
                || "CANCELLED".equals(occurrence.getPlanningStatus())
                || "MERGED".equals(occurrence.getPlanningStatus())) {
            return false;
        }
        return record == null || !"CONFIRMED".equals(record.getConfirmStage());
    }

    /** 已确认课次由课包账本处理；老师提交的任何待审核结果均按原计划课时预留。 */
    public static int reservedUnits(LessonOccurrenceEntity occurrence, LessonRecordEntity record) {
        if (occurrence == null || "CANCELLED".equals(occurrence.getPlanningStatus())
                || record != null && "CONFIRMED".equals(record.getConfirmStage())) {
            return 0;
        }
        return Math.max(0, occurrence.getPlannedUnits() == null ? 0 : occurrence.getPlannedUnits());
    }
}
