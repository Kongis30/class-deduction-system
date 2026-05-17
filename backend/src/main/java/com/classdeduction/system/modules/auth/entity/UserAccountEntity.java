package com.classdeduction.system.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * User account entity mapped to sys_user.
 */
@Getter
@Setter
@TableName("sys_user")
public class UserAccountEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    private String passwordHash;

    private String userType;

    private String displayName;

    private String mobile;

    private String email;

    private Long teacherId;

    private String status;

    private LocalDateTime lastLoginAt;

    private String remark;
}
