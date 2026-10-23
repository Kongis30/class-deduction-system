package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.RegistrationIntakeRequest;
import com.classdeduction.system.modules.enrollment.vo.RegistrationIntakeResultVO;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderEntity;
import com.classdeduction.system.modules.enrollment.entity.EnrollmentOrderItemEntity;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;

import java.util.List;
import java.util.Map;

public interface RegistrationIntakeService {

    RegistrationIntakeResultVO submitIntake(RegistrationIntakeRequest request);

    RegistrationIntakeResultVO submitIntake(RegistrationIntakeRequest request, CurrentUserResponse operator);

    EnrollmentOrderItemEntity appendCourseToOrder(EnrollmentOrderEntity order,
                                                  StudentEntity student,
                                                  RegistrationIntakeRequest.CourseItemPayload payload,
                                                  Long campusId,
                                                  List<EnrollmentOrderItemEntity> currentItems,
                                                  Map<Long, ClassGroupEntity> lockedClasses);
}
