package com.classdeduction.system.modules.auth.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.dto.ManagerAccountCreateRequest;
import com.classdeduction.system.modules.auth.dto.ManagerAccountPageQuery;
import com.classdeduction.system.modules.auth.dto.ManagerAccountUpdateRequest;
import com.classdeduction.system.modules.auth.vo.ManagerAccountVO;

public interface ManagerAccountService {

    PageResponse<ManagerAccountVO> pageManagerAccounts(ManagerAccountPageQuery query);

    Long createManagerAccount(ManagerAccountCreateRequest request);

    void updateManagerAccount(Long userId, ManagerAccountUpdateRequest request);
}
