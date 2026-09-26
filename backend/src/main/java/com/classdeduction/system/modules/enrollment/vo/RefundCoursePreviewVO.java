package com.classdeduction.system.modules.enrollment.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/** 退款窗口用的课程级已付与消耗金额预览。 */
@Schema(description = "Refund course usage preview")
public record RefundCoursePreviewVO(
        List<CourseItem> items
) {

    @Schema(description = "Refund course item preview")
    public record CourseItem(
            Long orderItemId,
            Integer lineNo,
            String detail,
            String courseType,
            Long classId,
            BigDecimal paidAmount,
            Integer consumedLessonCount,
            BigDecimal consumedAmount,
            boolean automaticStopSupported
    ) {
    }
}
