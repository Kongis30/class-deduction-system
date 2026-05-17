package com.classdeduction.system.common.model;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Shared audit base entity with created, updated, soft-delete, and version fields.
 */
@Getter
@Setter
public abstract class BaseEntity {

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField("created_by")
    private Long createdBy;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField("updated_by")
    private Long updatedBy;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("deleted_at")
    private LocalDateTime deletedAt;

    @TableField("deleted_by")
    private Long deletedBy;

    @Version
    private Integer version;
}
