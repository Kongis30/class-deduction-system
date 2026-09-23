package com.classdeduction.system.modules.transport.service;

import com.classdeduction.system.modules.transport.dto.TransportDriverAssignmentSaveRequest;
import com.classdeduction.system.modules.transport.dto.TransportDriverSaveRequest;
import com.classdeduction.system.modules.transport.vo.TransportDriverAssignmentVO;
import com.classdeduction.system.modules.transport.vo.TransportDriverVO;

import java.util.List;

/** 接送司机和课程学生司机设置的业务接口。 */
public interface TransportDriverService {

    List<TransportDriverVO> listDrivers();

    Long createDriver(TransportDriverSaveRequest request);

    void updateDriver(Long id, TransportDriverSaveRequest request);

    void deleteDriver(Long id);

    List<TransportDriverAssignmentVO> listAssignments();

    void saveAssignment(TransportDriverAssignmentSaveRequest request);
}
