package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.dto.TeacherPageQuery;
import com.classdeduction.system.modules.base.dto.TeacherSaveRequest;
import com.classdeduction.system.modules.base.vo.TeacherVO;

public interface TeacherService {

    java.util.List<TeacherVO> listAllActive();

    PageResponse<TeacherVO> pageTeachers(TeacherPageQuery query);

    TeacherVO getTeacher(Long id);

    Long createTeacher(TeacherSaveRequest request);

    void updateTeacher(Long id, TeacherSaveRequest request);

    void deleteTeacher(Long id);

    /**
     * 列出已软删除的教师档案，用于"已删除"查看窗口。
     */
    java.util.List<TeacherVO> listDeleted();
}
