package com.classdeduction.system.modules.report.mapper;

import lombok.Getter;
import lombok.Setter;

/** 课程产品的展示信息；查询忽略软删除，保证历史收入仍能显示产品名。 */
@Getter
@Setter
public class ProductLabelRow {

    private Long productId;
    private String productName;
    private String teachingMode;
    private Integer sortOrder;
}
