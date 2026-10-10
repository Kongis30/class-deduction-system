package com.classdeduction.system.modules.schedule.vo;

import java.time.LocalDate;

/** 旧纸质签字表的历史课次，不参与课包扣费与老师工资。 */
public record PrivateSignatureHistoryVO(
        Long id,
        Long packageId,
        LocalDate lessonDate,
        Long teacherId,
        String teacherName,
        String subjectName,
        Double plannedHours,
        String signatureText
) {
}
