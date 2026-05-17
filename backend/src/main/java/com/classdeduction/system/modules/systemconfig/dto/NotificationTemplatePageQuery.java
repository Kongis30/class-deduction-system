package com.classdeduction.system.modules.systemconfig.dto;

import com.classdeduction.system.common.model.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 通知模板分页查询参数。
 */
@Getter
@Setter
@Schema(description = "Notification template page query")
public class NotificationTemplatePageQuery extends PageQuery {

    @Schema(description = "Keyword matching template code or name", example = "LESSON")
    private String keyword;

    @Schema(description = "Channel type", example = "EMAIL")
    private String channelType;

    @Schema(description = "Template status", example = "ACTIVE")
    private String status;
}
