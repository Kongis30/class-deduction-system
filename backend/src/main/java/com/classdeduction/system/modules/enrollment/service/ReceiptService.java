package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.enrollment.dto.ReceiptPageQuery;
import com.classdeduction.system.modules.enrollment.vo.ReceiptRecordVO;

public interface ReceiptService {

    PageResponse<ReceiptRecordVO> pageReceipts(ReceiptPageQuery query);

    ReceiptRecordVO getReceipt(Long id);
}
