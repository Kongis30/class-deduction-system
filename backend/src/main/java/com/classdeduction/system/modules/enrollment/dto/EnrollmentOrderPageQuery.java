package com.classdeduction.system.modules.enrollment.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 报名单分页查询参数。
 */
@Getter
@Setter
@Schema(description = "报名单分页查询参数")
public class EnrollmentOrderPageQuery extends PageQuery {

    @Schema(description = "关键字，支持报名单号、学生姓名、1v1课程名和班课名", example = "EN2026")
    private String keyword;

    @Schema(description = "订单状态", example = "CONFIRMED")
    private String orderStatus;

    @Schema(description = "支付状态", example = "PAID")
    private String paymentStatus;

    @Schema(description = "学生ID", example = "1880000000000000001")
    private Long studentId;

    @Schema(description = "班课ID（筛选班课订单时使用）", example = "1880000000000000005")
    private Long classId;

    @Schema(description = "课程类型视图：ONE_ON_ONE 或 GROUP；按报名单中的课程项筛选")
    private String teachingMode;

    @Schema(description = "排除班课订单（仅查询1v1订单）")
    private boolean excludeClassOrders;
}
