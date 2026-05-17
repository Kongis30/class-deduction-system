package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.enrollment.dto.CoursePackagePageQuery;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageUpdateRequest;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageDetailVO;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageListItemVO;

public interface CoursePackageService {

    /**
     * 分页查询课时包列表。
     */
    PageResponse<CoursePackageListItemVO> pagePackages(CoursePackagePageQuery query);

    /**
     * 获取课时包详情。
     */
    CoursePackageDetailVO getPackage(Long id);

    /**
     * 更新课时包。
     */
    void updatePackage(Long id, CoursePackageUpdateRequest request);

    /**
     * 获取课时包余额。
     */
    Integer getPackageBalance(Long id);

    /**
     * 关闭课时包。
     */
    void closePackage(Long id);
}
