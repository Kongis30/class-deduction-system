package com.classdeduction.system.modules.enrollment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

/** 管理员提交的起始课时与页面所见版本；服务端在锁内计算余额和流水。 */
@Data
public class CoursePackageStartingHoursRequest {
    @NotNull
    @DecimalMin("0.0")
    private BigDecimal startingHours;

    @NotNull
    @PositiveOrZero
    private Integer expectedVersion;
}
