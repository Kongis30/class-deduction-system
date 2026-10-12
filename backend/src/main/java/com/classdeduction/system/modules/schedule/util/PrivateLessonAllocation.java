package com.classdeduction.system.modules.schedule.util;

import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.schedule.entity.LessonOccurrenceEntity;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 私教课包的排课占用口径：未确认的有效课次仍占用余额，确认后由消课账本扣减。
 * 排课重算和消课页摘要共用，避免把已消课的历史课次再次计入 Allocated。
 */
public final class PrivateLessonAllocation {

    private PrivateLessonAllocation() {
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
