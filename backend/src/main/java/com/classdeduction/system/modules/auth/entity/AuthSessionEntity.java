package com.classdeduction.system.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Session persistence entity: MySQL fallback when Redis is unavailable.
 * This is the source of truth when Redis is down.
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("auth_session")
public class AuthSessionEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String token;

    private Long userId;

    /** JSON-serialized CurrentUserResponse */
    private String sessionData;

    private LocalDateTime expiresAt;
}