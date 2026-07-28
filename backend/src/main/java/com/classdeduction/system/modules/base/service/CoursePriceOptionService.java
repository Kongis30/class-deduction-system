package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.dto.CoursePriceOptionPageQuery;
import com.classdeduction.system.modules.base.dto.CoursePriceOptionSaveRequest;
import com.classdeduction.system.modules.base.vo.CoursePriceOptionVO;

import java.util.List;

public interface CoursePriceOptionService {

    List<CoursePriceOptionVO> listActiveOptions(String courseType);

    PageResponse<CoursePriceOptionVO> pageOptions(CoursePriceOptionPageQuery query);

    Long createOption(CoursePriceOptionSaveRequest request);

    void updateOption(Long id, CoursePriceOptionSaveRequest request);

    void deleteOption(Long id);
}
