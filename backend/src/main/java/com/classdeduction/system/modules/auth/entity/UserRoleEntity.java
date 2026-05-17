package com.classdeduction.system.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * User-role mapping entity mapped to sys_user_role.
 */
@Getter
@Setter
@TableName("sys_user_role")
public class UserRoleEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long roleId;

    private LocalDateTime createdAt;
}
