package com.classdeduction.system.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "登录请求参数")
public class LoginRequest {

    @NotBlank(message = "Username is required")
    @Schema(description = "登录用户名", example = "staff")
    private String username;

    @NotBlank(message = "Password is required")
    @Schema(description = "登录密码", example = "Staff@123456")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
