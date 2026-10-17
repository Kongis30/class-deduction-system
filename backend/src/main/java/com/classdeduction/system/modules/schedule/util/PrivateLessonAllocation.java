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

    /** 旧课位只改过名称、教室或到课模式时，其课次仍属于当前课位的固定分配额。 */
    public static boolean hasEquivalentCurrentSlot(RecurringScheduleSlotEntity historicalSlot,
                                                   List<RecurringScheduleSlotEntity> currentSlots,
                                                   Long currentRuleId) {
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
                        && Objects.equals(historicalSlot.getAllocatedUnits(), currentSlot.getAllocatedUnits()));
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

    /** 已确认课次由课包账本扣减；待确认课次按实际待扣课时预留，其余计划课次按排课时长预留。 */
    public static int reservedUnits(LessonOccurrenceEntity occurrence, LessonRecordEntity record) {
        if (occurrence == null || "CANCELLED".equals(occurrence.getPlanningStatus())
                || record != null && "CONFIRMED".equals(record.getConfirmStage())) {
            return 0;
        }
        if (record != null && "PENDING_ADMIN".equals(record.getConfirmStage())) {
            if (Integer.valueOf(0).equals(record.getShouldDeduct())) {
                return 0;
            }
            if (Integer.valueOf(1).equals(record.getShouldDeduct()) && record.getDeductedUnits() != null) {
                return Math.max(0, record.getDeductedUnits());
            }
        }
        return Math.max(0, occurrence.getPlannedUnits() == null ? 0 : occurrence.getPlannedUnits());
    }
}
