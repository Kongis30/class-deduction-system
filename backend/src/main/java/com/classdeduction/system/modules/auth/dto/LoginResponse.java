package com.classdeduction.system.modules.auth.dto;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Login response payload.
 */
@Schema(description = "登录响应结果")
public record LoginResponse(
        @Schema(description = "登录会话令牌")
        String token,
        @Schema(description = "当前登录用户")
        CurrentUserResponse user
) {
}
