package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.vo.PackageTeacherAssignmentVO;

import java.util.List;

/**
 * 课时包老师分配服务接口。
 */
public interface PackageTeacherAssignmentService {

    /**
     * 根据课时包ID查询老师分配列表。
     */
    List<PackageTeacherAssignmentVO> listByPackageId(Long packageId);

    /**
     * 获取分配详情。
     */
    PackageTeacherAssignmentVO getAssignment(Long id);

}
