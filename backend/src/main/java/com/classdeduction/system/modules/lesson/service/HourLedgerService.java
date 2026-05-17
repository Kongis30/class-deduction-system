package com.classdeduction.system.modules.lesson.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.lesson.dto.HourLedgerPageQuery;
import com.classdeduction.system.modules.lesson.vo.HourLedgerVO;
import com.classdeduction.system.modules.lesson.vo.PackageBalanceSnapshotVO;

public interface HourLedgerService {

    PageResponse<HourLedgerVO> pageLedger(HourLedgerPageQuery query);

    PackageBalanceSnapshotVO getBalanceSnapshot(Long packageId);
}
