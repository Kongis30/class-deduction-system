package com.classdeduction.system.modules.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.dto.GroupRegistrationPlanSaveRequest;
import com.classdeduction.system.modules.base.entity.GroupCourseProjectEntity;
import com.classdeduction.system.modules.base.entity.GroupRegistrationPlanEntity;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.GroupCourseProjectMapper;
import com.classdeduction.system.modules.base.mapper.GroupRegistrationPlanMapper;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderItemMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupScheduleSlotMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassHolidayMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupCourseModelServiceImplTest {

    @Mock private CourseProductMapper courseProductMapper;
    @Mock private CampusMapper campusMapper;
    @Mock private GroupCourseProjectMapper projectMapper;
    @Mock private GroupRegistrationPlanMapper planMapper;
    @Mock private ClassGroupMapper classGroupMapper;
    @Mock private ClassGroupScheduleSlotMapper scheduleSlotMapper;
    @Mock private EnrollmentOrderItemMapper orderItemMapper;
    @Mock private ClassHolidayMapper holidayMapper;

    private GroupCourseModelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GroupCourseModelServiceImpl(courseProductMapper, campusMapper, projectMapper, planMapper,
                classGroupMapper, scheduleSlotMapper, orderItemMapper, holidayMapper);
        GroupCourseProjectEntity project = new GroupCourseProjectEntity();
        project.setId(91L);
        when(projectMapper.selectByIdForUpdate(91L)).thenReturn(project);
        lenient().when(planMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
    }

    @Test
    void shouldSaveFixedPricePlanForWholeClassWithoutExplicitDates() {
        service.createPlan(91L, fixedPricePlan(null, null));

        ArgumentCaptor<GroupRegistrationPlanEntity> planCaptor = ArgumentCaptor.forClass(GroupRegistrationPlanEntity.class);
        verify(planMapper).insert(planCaptor.capture());
        assertThat(planCaptor.getValue().getCoverageType()).isEqualTo("FIXED_PRICE");
        assertThat(planCaptor.getValue().getCoverageStartDate()).isNull();
        assertThat(planCaptor.getValue().getCoverageEndDate()).isNull();
        assertThat(planCaptor.getValue().getPackageUnit()).isEqualTo("SESSION");
        assertThat(planCaptor.getValue().getPackageQuantity()).isEqualByComparingTo("1");
    }

    @Test
    void shouldSaveFixedPricePlanWithACompleteDateRange() {
        service.createPlan(91L, fixedPricePlan(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31)));

        verify(planMapper).insert(any(GroupRegistrationPlanEntity.class));
    }

    @Test
    void shouldRejectFixedPricePlanWithOnlyOneBoundaryDate() {
        assertThatThrownBy(() -> service.createPlan(91L,
                fixedPricePlan(LocalDate.of(2026, 11, 1), null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("GROUP_PLAN_DATES_INVALID");

        verify(planMapper, never()).insert(any(GroupRegistrationPlanEntity.class));
    }

    @Test
    void shouldRejectFixedPricePlanWithReversedDatesOrMonthUnit() {
        assertThatThrownBy(() -> service.createPlan(91L,
                fixedPricePlan(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 11, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("GROUP_PLAN_DATES_INVALID");

        GroupRegistrationPlanSaveRequest monthPlan = fixedPricePlan(null, null);
        monthPlan.setPackageUnit("MONTH");
        assertThatThrownBy(() -> service.createPlan(91L, monthPlan))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("GROUP_PLAN_UNIT_INVALID");

        verify(planMapper, never()).insert(any(GroupRegistrationPlanEntity.class));
    }

    private GroupRegistrationPlanSaveRequest fixedPricePlan(LocalDate start, LocalDate end) {
        GroupRegistrationPlanSaveRequest request = new GroupRegistrationPlanSaveRequest();
        request.setPlanCode("FIXED-699");
        request.setPlanName("Fixed total price");
        request.setCoverageType("FIXED_PRICE");
        request.setPackageUnit("SESSION");
        request.setPackageQuantity(BigDecimal.ONE);
        request.setFullDayPrice(new BigDecimal("699"));
        request.setCoverageStartDate(start);
        request.setCoverageEndDate(end);
        return request;
    }
}
