package com.classdeduction.system.modules.schedule.vo;

/** 老师日历侧栏的一项课程分类关联。 */
public record TeacherCalendarCategoryVO(
        Long teacherId,
        String categoryKey,
        String categoryName
) {
}
