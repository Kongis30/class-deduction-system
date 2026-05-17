package com.classdeduction.system.modules.base.entity;

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

/**
 * Classroom entity used for scheduling occupancy and room management.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("classroom")
@Schema(description = "Classroom entity mapped to the classroom table")
public class ClassroomEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("room_code")
    private String roomCode;

    @TableField("room_name")
    private String roomName;

    @TableField("campus")
    private String campus;

    @TableField("campus_id")
    private Long campusId;

    @TableField("capacity")
    private Integer capacity;

    @TableField("status")
    private String status;

    @TableField("notes")
    private String notes;
}
