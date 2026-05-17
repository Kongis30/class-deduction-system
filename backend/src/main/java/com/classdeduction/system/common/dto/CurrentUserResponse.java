package com.classdeduction.system.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "当前登录用户信息")
public record CurrentUserResponse(
        @Schema(description = "用户ID")
        Long userId,
        @Schema(description = "用户名")
        String username,
        @Schema(description = "显示名称")
        String displayName,
        @Schema(description = "用户类型")
        String userType,
        @Schema(description = "绑定的老师ID，教师账号才会有值")
        Long teacherId,
        @Schema(description = "角色编码列表")
        List<String> roleCodes
) {
}
