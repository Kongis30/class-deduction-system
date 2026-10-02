package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.modules.schedule.vo.TeacherCalendarCategoryVO;

import java.util.List;

public interface TeacherCalendarRosterService {

    List<TeacherCalendarCategoryVO> listCategories();
}
