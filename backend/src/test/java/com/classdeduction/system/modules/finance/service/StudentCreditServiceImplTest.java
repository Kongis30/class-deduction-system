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
    void manualCreditBalanceCannotBeFractional() {
        assertThatThrownBy(() -> service.adjustBalance(8L, new BigDecimal("150.25"), 3L, "Admin", "Correction"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Credit balance must be a whole dollar");
        verify(ledgerMapper, never()).insert(org.mockito.ArgumentMatchers.any(StudentCreditLedgerEntity.class));
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

    @Test
    void paymentConsumesCreditWhenBalanceCoversTheAmount() {
        when(ledgerMapper.lockStudent(8L)).thenReturn(8L);
        when(ledgerMapper.selectBalance(8L)).thenReturn(new BigDecimal("195.00"));

        service.consumeForPayment(8L, new BigDecimal("195.00"), 21L, 34L, 3L, "Admin", "Registration payment");

        ArgumentCaptor<StudentCreditLedgerEntity> captor = ArgumentCaptor.forClass(StudentCreditLedgerEntity.class);
        verify(ledgerMapper).insert(captor.capture());
        StudentCreditLedgerEntity entry = captor.getValue();
        assertThat(entry.getEntryType()).isEqualTo("CREDIT_USED");
        assertThat(entry.getAmount()).isEqualByComparingTo("-195.00");
        assertThat(entry.getSourceEnrollmentOrderId()).isEqualTo(21L);
        assertThat(entry.getSourcePaymentRecordId()).isEqualTo(34L);
    }

    @Test
    void paymentRejectsCreditWhenBalanceIsInsufficient() {
        when(ledgerMapper.lockStudent(8L)).thenReturn(8L);
        when(ledgerMapper.selectBalance(8L)).thenReturn(new BigDecimal("194.99"));

        assertThatThrownBy(() -> service.consumeForPayment(8L, new BigDecimal("195.00"), 21L, 34L, 3L, "Admin", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not enough");

        verify(ledgerMapper, never()).insert(org.mockito.ArgumentMatchers.any(StudentCreditLedgerEntity.class));
    }

    @Test
    void topUpStoresCreditAsPositiveLedgerDelta() {
        service.creditFromTopUp(8L, new BigDecimal("20.00"), 21L, 34L, 3L, "Admin", "Top-up");

        ArgumentCaptor<StudentCreditLedgerEntity> captor = ArgumentCaptor.forClass(StudentCreditLedgerEntity.class);
        verify(ledgerMapper).insert(captor.capture());
        StudentCreditLedgerEntity entry = captor.getValue();
        assertThat(entry.getEntryType()).isEqualTo("TOP_UP_CREDIT");
        assertThat(entry.getAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void topUpReversalAppendsAuditableNegativeLedgerEntryWhenBalanceIsAvailable() {
        when(ledgerMapper.lockStudent(8L)).thenReturn(8L);
        when(ledgerMapper.selectOrderAmount(8L, 21L, "TOP_UP_CREDIT")).thenReturn(new BigDecimal("100.00"));
        when(ledgerMapper.selectOrderAmount(8L, 21L, "TOP_UP_REVERSAL")).thenReturn(BigDecimal.ZERO);
        when(ledgerMapper.selectBalance(8L)).thenReturn(new BigDecimal("125.00"));

        service.reverseTopUp(8L, 21L, new BigDecimal("100.00"), 3L, "Admin", "Delete top-up receipt");

        ArgumentCaptor<StudentCreditLedgerEntity> captor = ArgumentCaptor.forClass(StudentCreditLedgerEntity.class);
        verify(ledgerMapper).insert(captor.capture());
        StudentCreditLedgerEntity entry = captor.getValue();
        assertThat(entry.getEntryType()).isEqualTo("TOP_UP_REVERSAL");
        assertThat(entry.getAmount()).isEqualByComparingTo("-100.00");
        assertThat(entry.getSourceEnrollmentOrderId()).isEqualTo(21L);
        assertThat(entry.getHandledByName()).isEqualTo("Admin");
    }

    @Test
    void topUpReversalIsRejectedWhenAvailableCreditIsInsufficient() {
        when(ledgerMapper.lockStudent(8L)).thenReturn(8L);
        when(ledgerMapper.selectOrderAmount(8L, 21L, "TOP_UP_CREDIT")).thenReturn(new BigDecimal("100.00"));
        when(ledgerMapper.selectOrderAmount(8L, 21L, "TOP_UP_REVERSAL")).thenReturn(BigDecimal.ZERO);
        when(ledgerMapper.selectBalance(8L)).thenReturn(new BigDecimal("99.00"));

        assertThatThrownBy(() -> service.reverseTopUp(8L, 21L, new BigDecimal("100.00"), 3L, "Admin", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Available Credit is less");
        verify(ledgerMapper, never()).insert(org.mockito.ArgumentMatchers.any(StudentCreditLedgerEntity.class));
    }

    @Test
    void topUpReversalRejectsMismatchedOrPreviouslyReversedLedger() {
        when(ledgerMapper.lockStudent(8L)).thenReturn(8L);
        when(ledgerMapper.selectOrderAmount(8L, 21L, "TOP_UP_CREDIT")).thenReturn(new BigDecimal("100.00"));
        when(ledgerMapper.selectOrderAmount(8L, 21L, "TOP_UP_REVERSAL")).thenReturn(new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.reverseTopUp(8L, 21L, new BigDecimal("100.00"), 3L, "Admin", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not match");
        verify(ledgerMapper, never()).insert(org.mockito.ArgumentMatchers.any(StudentCreditLedgerEntity.class));
    }
}
