package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.PackageTeacherAssignmentRequest;
import com.classdeduction.system.modules.enrollment.dto.PackageTeacherAllocationRequest;
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

    /**
     * 创建老师分配记录。
     */
    Long createAssignment(PackageTeacherAssignmentRequest request);

    /**
     * 更新分配记录状态（结束分配）。
     */
    void endAssignment(Long id, String changeReason);

    /**
     * 切换主老师。
     */
    void switchPrimaryTeacher(Long packageId, Long newTeacherId, String changeReason);

    /**
     * Replace active teacher-hour allocations while preserving assignment history.
     */
    void replaceAllocations(Long packageId, PackageTeacherAllocationRequest request);
}
