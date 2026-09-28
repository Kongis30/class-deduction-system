package com.classdeduction.system.modules.enrollment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.classdeduction.system.common.model.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 收据记录实体：保存收据快照和导出文件信息。
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@TableName("receipt_record")
@Schema(description = "收据记录实体")
public class ReceiptRecordEntity extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String receiptNo;

    private Long enrollmentOrderId;

    private Long studentId;

    /** 退款收据对应的不可变退款流水；普通报名收据为空。 */
    private Long paymentRecordId;

    private String templateCode;

    private String receiptPayload;

    private LocalDateTime issuedAt;

    private Long issuedBy;

    private String fileUrl;
}
