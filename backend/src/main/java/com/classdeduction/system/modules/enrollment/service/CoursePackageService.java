package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.enrollment.dto.CoursePackagePageQuery;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageHoursAdjustmentRequest;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageDeductedHoursRequest;
import com.classdeduction.system.modules.enrollment.dto.CoursePackageUpdateRequest;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageDetailVO;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageListItemVO;
import com.classdeduction.system.modules.enrollment.vo.GroupClassPackageProgressVO;
import com.classdeduction.system.modules.enrollment.vo.GroupClassStudentProgressVO;

import java.util.List;

public interface CoursePackageService {

    /**
     * 分页查询课时包列表。
     */
    PageResponse<CoursePackageListItemVO> pagePackages(CoursePackagePageQuery query);

    /**
     * 分页查询班课产品进度。
     */
    PageResponse<GroupClassPackageProgressVO> pageGroupClassPackages(CoursePackagePageQuery query);

    /**
     * 查询班课内学生各自进度。
     */
    List<GroupClassStudentProgressVO> listGroupClassStudentProgress(Long classId);

    /**
     * 获取课时包详情。
     */
    CoursePackageDetailVO getPackage(Long id);

    /** 保存课包的手动置顶状态；为空的自动状态按余额阈值计算。 */
    void setRenewalPriority(Long id, boolean pinned, Long operatorId);

    /**
     * 更新课时包。
     */
    void updatePackage(Long id, CoursePackageUpdateRequest request);

    /**
     * Adjust available package hours without changing order charges.
     */
    void adjustPackageHours(Long id, CoursePackageHoursAdjustmentRequest request, Long operatorId);

    /** 更新已消课总课时，保留已确认课次并同步重算未来排课。 */
    void updateDeductedHours(Long id, CoursePackageDeductedHoursRequest request, Long operatorId);

    /**
     * Pause a 1-on-1 package and stop future unrecorded lessons.
     */
    void pausePackage(Long id);

    /** 恢复 1 对 1 课包；旧排课保持暂停，由管理员重新创建排课。 */
    void resumePackage(Long id);

    /**
     * 获取课时包余额。
     */
    Integer getPackageBalance(Long id);

    /**
     * 关闭课时包。
     */
    void closePackage(Long id);

    /** 关闭课包并保留归档操作人审计。 */
    void closePackage(Long id, Long operatorId);
}
