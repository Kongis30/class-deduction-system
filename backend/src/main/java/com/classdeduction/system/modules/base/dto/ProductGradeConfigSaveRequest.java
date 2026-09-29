package com.classdeduction.system.modules.base.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * grade 配置批量保存请求：一次提交整份顺序，改名项通过 newLabel 体现。
 */
@Data
@Schema(description = "Product grade config batch save request")
public class ProductGradeConfigSaveRequest {

    @Valid
    @Schema(description = "Grade config items in target display order")
    private List<Item> items;

    @Data
    @Schema(description = "One grade config item")
    public static class Item {

        @NotBlank(message = "Current grade label is required")
        @Size(max = 64, message = "Current grade label must be at most 64 characters")
        @Schema(description = "Current grade label used by existing rows", example = "G3-G6")
        private String currentLabel;

        @Size(max = 64, message = "New grade label must be at most 64 characters")
        @Schema(description = "New grade label; blank or equal to currentLabel means keep the name", example = "G3-6")
        private String newLabel;

        @Schema(description = "Display order", example = "1")
        private Integer displayOrder;

        @Schema(description = "Grade-wide actual class period start date")
        private LocalDate startDate;

        @Schema(description = "Grade-wide actual class period end date")
        private LocalDate endDate;

        @Schema(description = "When true, save the provided class date range, including clearing both dates")
        private Boolean updateClassPeriod;
    }
}
