package com.classdeduction.system.modules.auth.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.auth.dto.StudentAccountCreateRequest;
import com.classdeduction.system.modules.auth.dto.StudentAccountPageQuery;
import com.classdeduction.system.modules.auth.vo.StudentAccountVO;

/**
 * 学生账号服务：负责学生账号与 student 档案的一对一绑定。
 */
public interface StudentAccountService {

    PageResponse<StudentAccountVO> pageStudentAccounts(StudentAccountPageQuery query);

    Long createStudentAccount(StudentAccountCreateRequest request);
}
