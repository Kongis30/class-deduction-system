package com.classdeduction.system.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "报名管理者账号创建请求")
public class ManagerAccountCreateRequest {

    @NotBlank
    @Size(max = 64)
    private String username;

    @NotBlank
    @Size(min = 8, max = 64)
    private String password;

    @NotBlank
    @Size(max = 64)
    private String displayName;

    @Size(max = 32)
    private String mobile;

    @Size(max = 128)
    private String email;

    @Size(max = 500)
    private String remark;
}
