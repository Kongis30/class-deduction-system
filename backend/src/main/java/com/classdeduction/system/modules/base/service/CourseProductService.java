package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.dto.CourseProductPageQuery;
import com.classdeduction.system.modules.base.dto.CourseProductSaveRequest;
import com.classdeduction.system.modules.base.vo.CourseProductVO;

import java.util.List;

public interface CourseProductService {

    PageResponse<CourseProductVO> pageCourseProducts(CourseProductPageQuery query);

    List<CourseProductVO> listCourseProducts();

    CourseProductVO getCourseProduct(Long id);

    Long createCourseProduct(CourseProductSaveRequest request);

    /** 复制课程配置，生成独立的新产品及其价格、班级和每周课表。 */
    Long copyCourseProduct(Long sourceId, String newProductName, int dateShiftYears);

    void updateCourseProduct(Long id, CourseProductSaveRequest request);

    /**
     * 保存 Data Summary Product Tab 的系统级共享顺序（ADMIN 专用）。
     * 提交 ID 必须为当前未删除产品且无重复；遗漏的产品按现有顺序追加到末尾。
     */
    void reorderCourseProducts(List<Long> productIds, Long operatorId);

    void deleteCourseProduct(Long id);

    /**
     * 管理员强制删除课程产品（软删除）：用于清理系统改造前遗留、已开班或已被课包引用而无法走常规删除的历史脏数据。
     * 会级联软删除关联班级（取消未来课次、退出报名），并软删除产品本身；历史消课与流水保留供审计。
     */
    void forceDeleteCourseProduct(Long id, String reason);
}
