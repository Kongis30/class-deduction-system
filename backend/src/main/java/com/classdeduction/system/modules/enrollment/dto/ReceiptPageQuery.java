package com.classdeduction.system.modules.enrollment.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 收据分页查询参数。
 */
@Getter
@Setter
@Schema(description = "收据分页查询参数")
public class ReceiptPageQuery extends PageQuery {

    @Schema(description = "关键字，支持收据编号和学生姓名", example = "RC2026")
    private String keyword;

    @Schema(description = "学生ID", example = "1880000000000000001")
    private Long studentId;

    @Schema(description = "报名订单ID", example = "1880000000000000001")
    private Long enrollmentOrderId;

    @Schema(description = "模板编码", example = "DEFAULT_RECEIPT")
    private String templateCode;
}
