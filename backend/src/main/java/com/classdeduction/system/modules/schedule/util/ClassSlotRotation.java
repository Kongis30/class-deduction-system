package com.classdeduction.system.modules.schedule.util;

import com.classdeduction.system.modules.schedule.entity.ClassGroupScheduleSlotEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 班课单个周时段的隔次轮换规则。课次生成、假期跳过和续期共用此计算。
 *
 * <p>轮换按用户配置的日期序列计数；假期日期也应计数，只是当天不生成课次。
 * 这样取消假期内课次不会改变假期外课次的老师、科目和教室。</p>
 */
public final class ClassSlotRotation {

    private ClassSlotRotation() {
    }

    /** 根据配置的课次日期计算本次完整的老师、科目、授课方式及教室。 */
    public static Assignment resolve(ClassGroupScheduleSlotEntity slot,
                                     LocalDate lessonDate,
                                     LocalDate fallbackAnchor) {
        return resolve(slot, lessonDate, fallbackAnchor, null);
    }

    public static Assignment resolve(ClassGroupScheduleSlotEntity slot,
                                     LocalDate lessonDate,
                                     LocalDate fallbackAnchor,
                                     LocalDate baseEndDate) {
        return resolve(slot, lessonDate, fallbackAnchor, baseEndDate, List.of(slot));
    }

    /** 同一逻辑课位跨日期段沿用轮换序号，不因编辑时拆分过去和未来而重置 A/B。 */
    public static Assignment resolve(ClassGroupScheduleSlotEntity slot,
                                     LocalDate lessonDate,
                                     LocalDate fallbackAnchor,
                                     LocalDate baseEndDate,
                                     List<ClassGroupScheduleSlotEntity> allSlots) {
        boolean alternating = slot.getAlternateTeacherId() != null
                || hasText(slot.getAlternateSubjectName())
                || slot.getAlternateClassroomId() != null
                || hasText(slot.getAlternateDeliveryMode());
        if (!alternating) {
            return primary(slot);
        }

        LocalDate anchor = slot.getRotationAnchorDate() == null ? fallbackAnchor : slot.getRotationAnchorDate();
        if (anchor == null) {
            anchor = lessonDate;
        }
        LocalDate firstSlotDate = firstDateOnOrAfter(anchor, slot.getWeekday());
        if (lessonDate.isBefore(firstSlotDate)) {
            return primary(slot);
        }

        int classIndex = 0;
        Long lineageId = slot.getSlotLineageId() == null ? slot.getId() : slot.getSlotLineageId();
        for (LocalDate date = firstSlotDate; !date.isAfter(lessonDate); date = date.plusWeeks(1)) {
            LocalDate candidateDate = date;
            boolean activeLineage = allSlots.stream().anyMatch(candidate ->
                    Objects.equals(candidate.getSlotLineageId() == null ? candidate.getId() : candidate.getSlotLineageId(), lineageId)
                            && ClassScheduleDates.slotAppliesOn(candidate, candidateDate, baseEndDate, allSlots));
            if (activeLineage) {
                classIndex++;
            }
        }
        boolean useAlternate = classIndex > 0 && classIndex % 2 == 0;
        return useAlternate
                ? new Assignment(slot.getAlternateTeacherId(), slot.getAlternateSubjectName(),
                    slot.getAlternateClassroomId(), slot.getAlternateDeliveryMode(), true)
                : primary(slot);
    }

    /** 返回轮换锚点之后该星期的第一个上课日。 */
    public static LocalDate firstDateOnOrAfter(LocalDate anchor, Integer weekday) {
        if (anchor == null || weekday == null || weekday < 1 || weekday > 7) {
            return anchor;
        }
        int daysForward = Math.floorMod(weekday - anchor.getDayOfWeek().getValue(), 7);
        return anchor.plusDays(daysForward);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Assignment primary(ClassGroupScheduleSlotEntity slot) {
        return new Assignment(slot.getTeacherId(), slot.getSubjectName(), slot.getClassroomId(),
                slot.getDeliveryMode(), false);
    }

    public record Assignment(Long teacherId, String subjectName, Long classroomId,
                             String deliveryMode, boolean alternate) {
    }
}
