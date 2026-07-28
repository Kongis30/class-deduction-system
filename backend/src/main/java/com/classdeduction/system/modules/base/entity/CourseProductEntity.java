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
import java.time.LocalDate;

/**
 * Course product entity defining default units and pricing templates.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("course_product")
@Schema(description = "Course product entity mapped to the course_product table")
public class CourseProductEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String productCode;

    private String productName;

    private String courseType;

    private String registerClass;

    private String subjectName;

    private String teachingMode;

    private Integer defaultTotalUnits;

    private Integer defaultLessonUnits;

    /** GROUP 计费类型：PER_SESSION（课次收费）/ MONTHLY（月收费）。 */
    private String billingType;

    /** PER_SESSION：起始日期。 */
    private LocalDate startDate;

    /** PER_SESSION：总课次。 */
    private Integer totalSessions;

    /** MONTHLY：起始月份（存当月 1 号）。 */
    private LocalDate startMonth;

    /** MONTHLY：结束月份（存当月 1 号）。 */
    private LocalDate endMonth;

    private Long defaultClassroomId;

    private Long defaultTeacherId;

    private BigDecimal listPrice;

    private String status;

    private String notes;
}
