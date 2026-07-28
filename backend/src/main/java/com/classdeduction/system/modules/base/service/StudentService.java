package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.dto.StudentPageQuery;
import com.classdeduction.system.modules.base.dto.StudentReuseQuery;
import com.classdeduction.system.modules.base.dto.StudentSaveRequest;
import com.classdeduction.system.modules.base.vo.StudentDetailVO;
import com.classdeduction.system.modules.base.vo.StudentListItemVO;

import java.util.List;

public interface StudentService {

    List<StudentListItemVO> listAllActive();

    PageResponse<StudentListItemVO> pageStudents(StudentPageQuery query);

    StudentDetailVO getStudent(Long id);

    StudentDetailVO findReusableStudent(StudentReuseQuery query);

    Long createStudent(StudentSaveRequest request);

    void updateStudent(Long id, StudentSaveRequest request);

    void deleteStudent(Long id);

    /**
     * 列出已软删除的学生档案，用于"已删除"查看窗口。
     */
    List<StudentListItemVO> listDeleted();
}
