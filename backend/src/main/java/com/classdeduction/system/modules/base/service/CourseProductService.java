package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.dto.CourseProductPageQuery;
import com.classdeduction.system.modules.base.dto.CourseProductSaveRequest;
import com.classdeduction.system.modules.base.vo.CourseProductVO;

public interface CourseProductService {

    PageResponse<CourseProductVO> pageCourseProducts(CourseProductPageQuery query);

    CourseProductVO getCourseProduct(Long id);

    Long createCourseProduct(CourseProductSaveRequest request);

    void updateCourseProduct(Long id, CourseProductSaveRequest request);

    void deleteCourseProduct(Long id);
}
