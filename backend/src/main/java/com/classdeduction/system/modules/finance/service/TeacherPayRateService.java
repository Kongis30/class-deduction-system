package com.classdeduction.system.modules.finance.service;

import com.classdeduction.system.modules.finance.dto.TeacherPayRateSaveRequest;
import com.classdeduction.system.modules.finance.vo.TeacherPayRateVO;

import java.util.List;

public interface TeacherPayRateService {

    List<TeacherPayRateVO> listPayRates(Long teacherId, Long courseProductId);

    Long savePayRate(TeacherPayRateSaveRequest request);

    void deletePayRate(Long id);
}
