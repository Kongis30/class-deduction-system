package com.classdeduction.system.modules.transport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 课程学生与接送司机的持久化关联；Pickup 和 Drop-off 分别保存司机。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("transport_driver_assignment")
public class TransportDriverAssignmentEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("assignment_key")
    private String assignmentKey;

    @TableField("order_id")
    private Long orderId;

    @TableField("order_item_id")
    private Long orderItemId;

    @TableField("student_id")
    private Long studentId;

    @TableField("participant_index")
    private Integer participantIndex;

    @TableField("transport_type")
    private String transportType;

    @TableField("driver_id")
    private Long driverId;
}
