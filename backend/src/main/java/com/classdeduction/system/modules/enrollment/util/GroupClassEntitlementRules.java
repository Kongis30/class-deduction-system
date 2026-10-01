package com.classdeduction.system.modules.enrollment.util;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 班课权益的并发和日期边界校验，供报名与收据更正共用。 */
public final class GroupClassEntitlementRules {

    private GroupClassEntitlementRules() {
    }

    /**
     * 锁定涉及的学生行，令并发报名和收据更正先后执行，再检查日期是否冲突。
     * 排序可以降低一笔收据包含多个学生时的死锁概率。
     */
    public static void lockStudents(StudentMapper studentMapper, Collection<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return;
        }
        studentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .forEach(studentId -> {
                    if (studentMapper.selectByIdForUpdate(studentId) == null) {
                        throw new BusinessException("STUDENT_NOT_FOUND", "Student not found or no longer active.");
                    }
                });
    }

    /** 阻止同一学生在同一个实际班级里同时拥有重叠的有效权益。 */
    public static void requireNoOverlappingActiveEntitlement(ClassEnrollmentMapper enrollmentMapper,
                                                              Long studentId,
                                                              Long classId,
                                                              LocalDate startDate,
                                                              LocalDate endDate,
                                                              Long excludedEnrollmentId) {
        if (studentId == null || classId == null || startDate == null || endDate == null
                || endDate.isBefore(startDate)) {
            throw new BusinessException("GROUP_ENTITLEMENT_DATES_INVALID",
                    "The class enrollment period is invalid.");
        }
        List<Long> overlaps = enrollmentMapper.selectActiveOverlappingForUpdate(
                studentId, classId, startDate, endDate);
        boolean hasOtherOverlap = overlaps != null && overlaps.stream()
                .anyMatch(enrollmentId -> !Objects.equals(enrollmentId, excludedEnrollmentId));
        if (hasOtherOverlap) {
            throw new BusinessException("GROUP_ENTITLEMENT_DATE_OVERLAP",
                    "This student already has an active entitlement for one or more of these class dates.");
        }
    }
}
