package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.dto.DiscountPresetSaveRequest;
import com.classdeduction.system.modules.base.entity.DiscountPresetEntity;
import com.classdeduction.system.modules.base.mapper.DiscountPresetMapper;
import com.classdeduction.system.modules.base.service.impl.DiscountPresetServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DiscountPresetServiceImplTest {

    @Mock
    private DiscountPresetMapper mapper;

    @InjectMocks
    private DiscountPresetServiceImpl service;

    @Test
    void fixedDiscountRejectsCentsBeforeSaving() {
        DiscountPresetSaveRequest request = new DiscountPresetSaveRequest();
        request.setPresetName("Fixed discount");
        request.setDiscountType("FIXED");
        request.setDiscountValue(new BigDecimal("10.25"));

        assertThatThrownBy(() -> service.createPreset(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("whole dollar");
        verify(mapper, never()).insert(any(DiscountPresetEntity.class));
    }
}
