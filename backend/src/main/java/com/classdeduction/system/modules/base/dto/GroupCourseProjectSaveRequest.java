package com.classdeduction.system.modules.base.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroupCourseProjectSaveRequest {

    @NotBlank
    @Size(max = 64)
    private String projectCode;

    @NotBlank
    @Size(max = 128)
    private String projectName;

    @Size(max = 500)
    private String description;

    private Integer displayOrder;

    private String status;
}
