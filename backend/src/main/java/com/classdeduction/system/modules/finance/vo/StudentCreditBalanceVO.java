package com.classdeduction.system.modules.finance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "学生 Credit 余额")
@Getter
@Setter
@NoArgsConstructor
public class StudentCreditBalanceVO {
    private Long studentId;
    private String studentNo;
    private String studentName;
    private String grade;
    private String studentClass;
    private String guardianPhone;
    private BigDecimal creditBalance;
    private LocalDateTime lastOccurredAt;
}
