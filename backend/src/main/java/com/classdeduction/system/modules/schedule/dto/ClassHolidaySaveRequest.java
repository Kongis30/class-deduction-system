package com.classdeduction.system.modules.schedule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ClassHolidaySaveRequest {

    @NotBlank
    @Size(max = 100)
    private String holidayName;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;
}
