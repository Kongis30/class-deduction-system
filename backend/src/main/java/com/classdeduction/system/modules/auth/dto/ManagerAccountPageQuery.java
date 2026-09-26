package com.classdeduction.system.modules.auth.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "报名管理者账号查询参数")
public class ManagerAccountPageQuery extends PageQuery {

    private String keyword;

    private String status;
}
