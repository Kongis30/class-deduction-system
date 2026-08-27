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

    /**
     * 管理员强制删除课程产品（软删除）：用于清理系统改造前遗留、已开班或已被课包引用而无法走常规删除的历史脏数据。
     * 会级联软删除关联班级（取消未来课次、退出报名），并软删除产品本身；历史消课与流水保留供审计。
     */
    void forceDeleteCourseProduct(Long id, String reason);
}
