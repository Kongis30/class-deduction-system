package com.classdeduction.system.modules.lesson.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 课包余额快照实体：用于快速展示余额，不替代流水账本。
 */
@Getter
@Setter
@TableName("package_balance_snapshot")
@Schema(description = "Package balance snapshot entity")
public class PackageBalanceSnapshotEntity {

    @TableId
    private Long packageId;

    private Integer totalUnits;

    private Integer consumedUnits;

    private Integer remainingUnits;

    private Long lastLedgerId;

    private LocalDateTime snapshotAt;
}
