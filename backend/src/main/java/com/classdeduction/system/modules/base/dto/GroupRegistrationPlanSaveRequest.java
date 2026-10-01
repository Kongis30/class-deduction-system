package com.classdeduction.system.modules.base.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class GroupRegistrationPlanSaveRequest {

    @NotBlank
    @Size(max = 64)
    private String planCode;

    @NotBlank
    @Size(max = 128)
    private String planName;

    @NotBlank
    private String coverageType;

    @Size(max = 32)
    private String termCode;

    private LocalDate coverageStartDate;

    private LocalDate coverageEndDate;

    @NotBlank
    private String packageUnit;

    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 6, fraction = 2)
    private BigDecimal packageQuantity;

    @NotNull
    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal fullDayPrice;

    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal halfDayPrice;

    private String currencyCode;

    private Integer displayOrder;

    private String status;
}
