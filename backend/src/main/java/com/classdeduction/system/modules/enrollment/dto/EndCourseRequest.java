package com.classdeduction.system.modules.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 指定收据中的课程和学生，避免结束其他报名。 */
@Data
public class EndCourseRequest {
    @NotNull
    private Long orderItemId;

    @NotNull
    private Long studentId;
}
