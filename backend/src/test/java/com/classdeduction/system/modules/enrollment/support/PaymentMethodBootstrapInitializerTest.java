package com.classdeduction.system.modules.enrollment.support;

import com.classdeduction.system.modules.enrollment.entity.PaymentMethodEntity;
import com.classdeduction.system.modules.enrollment.mapper.PaymentMethodMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentMethodBootstrapInitializerTest {

    @Mock
    private PaymentMethodMapper paymentMethodMapper;

    @Mock
    private ApplicationArguments applicationArguments;

    @Test
    void shouldRestoreSoftDeletedDefaultMethodInsteadOfInsertingDuplicateCode() {
        PaymentMethodEntity transfer = new PaymentMethodEntity();
        transfer.setId(1003L);
        transfer.setMethodCode("TRANSFER");
        transfer.setIsDeleted(1);

        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("CASH"))
                .thenReturn(existingMethod(1001L, "CASH"));
        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("ZELLE"))
                .thenReturn(existingMethod(1002L, "ZELLE"));
        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("TRANSFER"))
                .thenReturn(transfer);
        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("CHECK"))
                .thenReturn(existingMethod(1004L, "CHECK"));
        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("OTHER"))
                .thenReturn(existingMethod(1005L, "OTHER"));

        new PaymentMethodBootstrapInitializer(paymentMethodMapper).run(applicationArguments);

        verify(paymentMethodMapper).restoreDefaultMethodById(1003L, "Transfer", 30, "ACTIVE");
        verify(paymentMethodMapper, never()).insert(any(PaymentMethodEntity.class));
    }

    @Test
    void shouldInsertDefaultMethodOnlyWhenCodeDoesNotExistAtAll() {
        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("CASH"))
                .thenReturn(existingMethod(1001L, "CASH"));
        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("ZELLE"))
                .thenReturn(existingMethod(1002L, "ZELLE"));
        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("TRANSFER"))
                .thenReturn(existingMethod(1003L, "TRANSFER"));
        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("CHECK"))
                .thenReturn(null);
        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("OTHER"))
                .thenReturn(existingMethod(1005L, "OTHER"));

        new PaymentMethodBootstrapInitializer(paymentMethodMapper).run(applicationArguments);

        ArgumentCaptor<PaymentMethodEntity> captor = ArgumentCaptor.forClass(PaymentMethodEntity.class);
        verify(paymentMethodMapper).insert(captor.capture());
        assertThat(captor.getValue().getMethodCode()).isEqualTo("CHECK");
        assertThat(captor.getValue().getMethodName()).isEqualTo("Check");
        assertThat(captor.getValue().getSortOrder()).isEqualTo(40);
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldLeaveExistingActiveMethodsUnchangedOnStartup() {
        PaymentMethodEntity cash = existingMethod(1001L, "CASH");
        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("CASH")).thenReturn(cash);
        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("ZELLE"))
                .thenReturn(existingMethod(1002L, "ZELLE"));
        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("TRANSFER"))
                .thenReturn(existingMethod(1003L, "TRANSFER"));
        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("CHECK"))
                .thenReturn(existingMethod(1004L, "CHECK"));
        when(paymentMethodMapper.selectByMethodCodeIgnoreLogicDelete("OTHER"))
                .thenReturn(existingMethod(1005L, "OTHER"));

        new PaymentMethodBootstrapInitializer(paymentMethodMapper).run(applicationArguments);

        verify(paymentMethodMapper, never()).restoreDefaultMethodById(eq(1001L), eq("Cash"), eq(10), eq("ACTIVE"));
    }

    private static PaymentMethodEntity existingMethod(Long id, String code) {
        PaymentMethodEntity entity = new PaymentMethodEntity();
        entity.setId(id);
        entity.setMethodCode(code);
        entity.setIsDeleted(0);
        return entity;
    }
}
