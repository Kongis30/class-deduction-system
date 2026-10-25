package com.classdeduction.system.modules.base.vo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Prices 与 Registration 共用的班课项目、方案和实际班级目录。 */
public record GroupCourseProductSetupVO(Long productId, String productName, List<Project> projects) {

    public record Project(Long id, String projectCode, String projectName, String description, String status,
                          Integer displayOrder, List<Plan> plans, List<ActualClass> classes) {
    }

    public record Plan(Long id, String planCode, String planName, String coverageType, String termCode,
                       LocalDate coverageStartDate, LocalDate coverageEndDate, String packageUnit,
                       BigDecimal packageQuantity, BigDecimal fullDayPrice, BigDecimal halfDayPrice,
                       String currencyCode, String status, Integer displayOrder, Long referenceCount) {
    }

    public record ActualClass(Long id, String className, String actualClass, Long campusId, String campusName, LocalDate startDate,
                              LocalDate endDate, String status, boolean hasSchedule) {
    }
}
