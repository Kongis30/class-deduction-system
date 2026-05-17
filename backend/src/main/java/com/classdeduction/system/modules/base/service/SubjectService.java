package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.modules.base.dto.SubjectSaveRequest;
import com.classdeduction.system.modules.base.vo.SubjectVO;

/**
 * Subject service.
 */
public interface SubjectService {

    java.util.List<SubjectVO> listAllActive();

    SubjectVO getSubject(Long id);

    Long createSubject(SubjectSaveRequest request);

    void updateSubject(Long id, SubjectSaveRequest request);

    void deleteSubject(Long id);
}
