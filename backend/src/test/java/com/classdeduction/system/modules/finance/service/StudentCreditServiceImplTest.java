package com.classdeduction.system.modules.finance.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.finance.entity.StudentCreditLedgerEntity;
import com.classdeduction.system.modules.finance.mapper.StudentCreditLedgerMapper;
import com.classdeduction.system.modules.finance.service.impl.StudentCreditServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentCreditServiceImplTest {

    @Mock
    private StudentCreditLedgerMapper ledgerMapper;

    @InjectMocks
    private StudentCreditServiceImpl service;

    @Test
    void adjustBalanceStoresOnlyTheDifferenceFromCurrentBalance() {
        when(ledgerMapper.lockStudent(8L)).thenReturn(8L);
        when(ledgerMapper.selectBalance(8L)).thenReturn(new BigDecimal("100.00"));

        service.adjustBalance(8L, new BigDecimal("150.00"), 3L, "Admin", "Correction");

        ArgumentCaptor<StudentCreditLedgerEntity> captor = ArgumentCaptor.forClass(StudentCreditLedgerEntity.class);
        verify(ledgerMapper).insert(captor.capture());
        StudentCreditLedgerEntity entry = captor.getValue();
        assertThat(entry.getEntryType()).isEqualTo("MANUAL_ADJUSTMENT");
        assertThat(entry.getAmount()).isEqualByComparingTo("50.00");
        assertThat(entry.getHandledByUserId()).isEqualTo(3L);
        assertThat(entry.getRemark()).isEqualTo("Correction");
    }

    @Test
    void zeroDifferenceDoesNotCreateAnUnnecessaryLedgerEntry() {
        when(ledgerMapper.lockStudent(8L)).thenReturn(8L);
        when(ledgerMapper.selectBalance(8L)).thenReturn(new BigDecimal("100.00"));

        service.adjustBalance(8L, new BigDecimal("100.00"), 3L, "Admin", null);

        verify(ledgerMapper, never()).insert(org.mockito.ArgumentMatchers.any(StudentCreditLedgerEntity.class));
    }

    @Test
    void adjustmentCanReduceTheBalanceWithoutDeletingEarlierHistory() {
        when(ledgerMapper.lockStudent(8L)).thenReturn(8L);
        when(ledgerMapper.selectBalance(8L)).thenReturn(new BigDecimal("100.00"));

        service.adjustBalance(8L, new BigDecimal("70.00"), 3L, "Admin", "Remove duplicate credit");

        ArgumentCaptor<StudentCreditLedgerEntity> captor = ArgumentCaptor.forClass(StudentCreditLedgerEntity.class);
        verify(ledgerMapper).insert(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("-30.00");
    }

    @Test
    void adjustmentRequiresAnExistingStudent() {
        when(ledgerMapper.lockStudent(8L)).thenReturn(null);

        assertThatThrownBy(() -> service.adjustBalance(8L, new BigDecimal("100.00"), 3L, "Admin", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Credit student");
    }
}
