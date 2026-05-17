package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.modules.base.dto.DiscountPresetSaveRequest;
import com.classdeduction.system.modules.base.vo.DiscountPresetVO;

import java.util.List;

/**
 * Discount preset service.
 */
public interface DiscountPresetService {

    /**
     * List all active discount presets, ordered by sort_order.
     */
    List<DiscountPresetVO> listAllActive();

    /**
     * Get discount preset by id.
     */
    DiscountPresetVO getPreset(Long id);

    /**
     * Create a new discount preset.
     */
    Long createPreset(DiscountPresetSaveRequest request);

    /**
     * Update an existing discount preset.
     */
    void updatePreset(Long id, DiscountPresetSaveRequest request);

    /**
     * Delete a discount preset.
     */
    void deletePreset(Long id);
}
