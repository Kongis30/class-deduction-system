package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 班课（group class）计费周期计算服务。
 *
 * <p>报名新增与收据更正/班级迁移两条业务链路共用同一套到期日规则，
 * 保证 class_enrollment.enrollment_date / expiry_date 的口径完全一致。</p>
 */
public interface ClassBillingService {

    /**
     * 根据班级配置与课包参数计算在册有效期。
     *
     * <p>规则与报名流程一致：MONTH 按整自然月取月末，WEEK 按周数减一天，
     * SESSION 按周历课时数推算；班级配置了结束日时到期日不越过该日。</p>
     *
     * @param classGroup      目标班级（含班级起始/结束日与周历课时配置）
     * @param startDate       起始日期；为空时回退到报名/更正操作基线日期 baselineDate，
     *                        仍为空时使用业务时区当天
     * @param baselineDate    起始日期缺失时的回退基线（通常是订单 signupDate）
     * @param packageUnit     计费单位：MONTH/WEEK/SESSION；为空时按 MONTH 处理
     * @param packageQuantity 购买数量（月数/周数/次数）；空值视为 1
     * @return 计费结果（enrollment_date、expiry_date 与购买数量快照）
     */
    ClassBilling compute(ClassGroupEntity classGroup,
                         LocalDate startDate,
                         LocalDate baselineDate,
                         String packageUnit,
                         BigDecimal packageQuantity);

    /**
     * 班课计费结果快照。
     */
    record ClassBilling(
            /** 在册起始日：写入 class_enrollment.enrollment_date */
            LocalDate enrollmentDate,
            /** 在册到期日：写入 class_enrollment.expiry_date */
            LocalDate expiryDate,
            /** 购买数量快照：写入 class_enrollment.purchased_quantity */
            Integer purchasedQuantity
    ) {
    }
}
