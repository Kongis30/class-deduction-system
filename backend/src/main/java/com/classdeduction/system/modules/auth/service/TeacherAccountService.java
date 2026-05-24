package com.classdeduction.system.modules.auth.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.dto.TeacherAccountCreateRequest;
import com.classdeduction.system.modules.auth.dto.TeacherAccountPageQuery;
import com.classdeduction.system.modules.auth.dto.TeacherAccountUpdateRequest;
import com.classdeduction.system.modules.auth.vo.TeacherAccountVO;

public interface TeacherAccountService {

    PageResponse<TeacherAccountVO> pageTeacherAccounts(TeacherAccountPageQuery query);

    Long createTeacherAccount(TeacherAccountCreateRequest request);

    void updateTeacherAccount(Long userId, TeacherAccountUpdateRequest request);
}
