package com.classdeduction.system.modules.base.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 复制课程模板时由用户指定的新课程名称。 */
@Getter
@Setter
public class CourseProductCopyRequest {

    @NotBlank
    @Size(max = 128)
    private String productName;

    @Min(0)
    @Max(10)
    private Integer dateShiftYears = 1;
}
