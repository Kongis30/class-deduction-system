package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.modules.base.dto.CampusSaveRequest;
import com.classdeduction.system.modules.base.vo.CampusVO;

import java.util.List;

/**
 * Campus service.
 */
public interface CampusService {

    /**
     * List all active campuses.
     */
    List<CampusVO> listAllActive();

    /**
     * Get campus by id.
     */
    CampusVO getCampus(Long id);

    /**
     * Create a new campus.
     */
    Long createCampus(CampusSaveRequest request);

    /**
     * Update an existing campus.
     */
    void updateCampus(Long id, CampusSaveRequest request);

    /**
     * Delete a campus.
     */
    void deleteCampus(Long id);
}
