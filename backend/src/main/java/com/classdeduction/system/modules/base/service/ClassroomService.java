package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.dto.ClassroomPageQuery;
import com.classdeduction.system.modules.base.dto.ClassroomSaveRequest;
import com.classdeduction.system.modules.base.vo.ClassroomVO;

import java.util.List;

public interface ClassroomService {

    /**
     * List all active classrooms for dropdown use.
     */
    List<ClassroomVO> listAllActive();

    /**
     * Page classrooms with filters.
     */
    PageResponse<ClassroomVO> pageClassrooms(ClassroomPageQuery query);

    /**
     * Get classroom by id.
     */
    ClassroomVO getClassroom(Long id);

    /**
     * Create a new classroom.
     */
    Long createClassroom(ClassroomSaveRequest request);

    /**
     * Update an existing classroom.
     */
    void updateClassroom(Long id, ClassroomSaveRequest request);

    /**
     * Delete a classroom.
     */
    void deleteClassroom(Long id);
}
