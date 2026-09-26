package com.classdeduction.system.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "报名管理者账号更新请求")
public class ManagerAccountUpdateRequest {

    @Size(min = 8, max = 64)
    private String password;

    @Size(max = 64)
    private String displayName;

    @Size(max = 32)
    private String mobile;

    @Email
    @Size(max = 128)
    private String email;

    @Schema(description = "ACTIVE 或 INACTIVE")
    private String status;

    @Size(max = 500)
    private String remark;
}
