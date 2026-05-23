package com.classdeduction.system.modules.enrollment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 课时包实体：报名后生成的独立课时账户。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("course_package")
@Schema(description = "课时包实体")
public class CoursePackageEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String packageNo;

    private Long enrollmentOrderId;

    private Long studentId;

    @TableField("course_product_id")
    private Long courseProductId;

    private String packageName;

    private String subjectName;

    private String teachingMode;

    private Integer totalUnits;

    private Integer defaultLessonUnits;

    private Integer consumedUnits;

    private Integer remainingUnits;

    private BigDecimal originalAmount;

    private BigDecimal discountAmount;

    private BigDecimal receivedAmount;

    private String packageStatus;

    private LocalDate startDate;

    private LocalDate estimatedEndDate;

    private LocalDateTime completedAt;

    private String remark;
}
