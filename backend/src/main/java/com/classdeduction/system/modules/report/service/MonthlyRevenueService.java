package com.classdeduction.system.modules.report.service;

import com.classdeduction.system.modules.report.dto.MonthlyRevenueQuery;
import com.classdeduction.system.modules.report.vo.MonthlyRevenueVO;

public interface MonthlyRevenueService {

    /** 按月、按收据校区、按课程产品汇总实际收款净额（退款按其发生月份冲减）。 */
    MonthlyRevenueVO getMonthlyRevenue(MonthlyRevenueQuery query);
}
