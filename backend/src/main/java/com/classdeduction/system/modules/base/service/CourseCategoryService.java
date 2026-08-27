package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.modules.base.dto.CourseCategorySaveRequest;
import com.classdeduction.system.modules.base.vo.CourseCategoryVO;

import java.util.List;

/**
 * Course category service. The set of category codes is fixed; only display name
 * and display order can be edited.
 */
public interface CourseCategoryService {

    List<CourseCategoryVO> listAll();

    void updateCategory(Long id, CourseCategorySaveRequest request);
}
