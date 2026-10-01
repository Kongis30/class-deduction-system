package com.classdeduction.system.modules.enrollment.util;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupClassEntitlementRulesTest {

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private ClassEnrollmentMapper enrollmentMapper;

    @Test
    void locksStudentRowsInStableOrder() {
        when(studentMapper.selectByIdForUpdate(101L)).thenReturn(student(101L));
        when(studentMapper.selectByIdForUpdate(202L)).thenReturn(student(202L));

        GroupClassEntitlementRules.lockStudents(studentMapper, List.of(202L, 101L, 202L));

        InOrder order = inOrder(studentMapper);
        order.verify(studentMapper).selectByIdForUpdate(101L);
        order.verify(studentMapper).selectByIdForUpdate(202L);
    }

    @Test
    void failsWhenStudentRowCannotBeLocked() {
        when(studentMapper.selectByIdForUpdate(101L)).thenReturn(null);

        assertThatThrownBy(() -> GroupClassEntitlementRules.lockStudents(studentMapper, List.of(101L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("STUDENT_NOT_FOUND"));
    }

    @Test
    void rejectsExistingOverlapButAllowsTheEntitlementBeingCorrected() {
        LocalDate startDate = LocalDate.of(2026, 10, 1);
        LocalDate endDate = LocalDate.of(2026, 12, 1);
        when(enrollmentMapper.selectActiveOverlappingForUpdate(101L, 501L, startDate, endDate))
                .thenReturn(List.of(301L));

        assertThatThrownBy(() -> GroupClassEntitlementRules.requireNoOverlappingActiveEntitlement(
                enrollmentMapper, 101L, 501L, startDate, endDate, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo("GROUP_ENTITLEMENT_DATE_OVERLAP"));

        GroupClassEntitlementRules.requireNoOverlappingActiveEntitlement(
                enrollmentMapper, 101L, 501L, startDate, endDate, 301L);
        verify(enrollmentMapper, org.mockito.Mockito.times(2))
                .selectActiveOverlappingForUpdate(101L, 501L, startDate, endDate);
    }

    private StudentEntity student(Long id) {
        StudentEntity student = new StudentEntity();
        student.setId(id);
        return student;
    }
}
