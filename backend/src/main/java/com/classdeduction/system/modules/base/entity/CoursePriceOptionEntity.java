package com.classdeduction.system.modules.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Structured course price option used by the registration course selector.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("course_price_option")
@Schema(description = "Course price option entity")
public class CoursePriceOptionEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String optionCode;

    private String courseType;

    private String registerClass;

    private String gradeBand;

    private String periodLabel;

    private String durationLabel;

    private String timeSlotLabel;

    private String detailText;

    private BigDecimal oneOnOneHours;

    private BigDecimal priceAmount;

    private String currencyCode;

    private Long courseProductId;

    private Long classId;

    private Integer sortOrder;

    private String status;

    private String notes;
}
