package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.modules.base.dto.GroupCourseProjectSaveRequest;
import com.classdeduction.system.modules.base.dto.GroupRegistrationPlanSaveRequest;
import com.classdeduction.system.modules.base.vo.GroupCourseProductSetupVO;

import java.time.LocalDate;
import java.util.List;

public interface GroupCourseModelService {

    GroupCourseProductSetupVO getProductSetup(Long productId, boolean activeOnly);

    List<LocalDate> getScheduledClassDates(Long classId, LocalDate startDate, LocalDate endDate);

    Long createProject(Long productId, GroupCourseProjectSaveRequest request);

    void updateProject(Long projectId, GroupCourseProjectSaveRequest request);

    void deleteProject(Long projectId);

    Long createPlan(Long projectId, GroupRegistrationPlanSaveRequest request);

    void updatePlan(Long planId, GroupRegistrationPlanSaveRequest request);

    void deletePlan(Long planId);
}
