package com.classdeduction.system.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * Role entity mapped to sys_role.
 */
@Getter
@Setter
@TableName("sys_role")
public class RoleEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String roleCode;

    private String roleName;

    private String status;

    private String remark;
}
