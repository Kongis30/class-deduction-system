package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.base.dto.CourseProductSaveRequest;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductScheduleSlotMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.base.service.impl.CourseProductServiceImpl;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseProductServiceImplTest {

    @Mock
    private CourseProductMapper courseProductMapper;
    @Mock
    private CourseProductScheduleSlotMapper scheduleSlotMapper;
    @Mock
    private ClassroomMapper classroomMapper;
    @Mock
    private TeacherMapper teacherMapper;
    @Mock
    private CoursePackageMapper coursePackageMapper;
    @Mock
    private ClassGroupMapper classGroupMapper;
    @Mock
    private PlatformTransactionManager transactionManager;

    private CourseProductServiceImpl courseProductService;

    @BeforeEach
    void setUp() {
        courseProductService = new CourseProductServiceImpl(
                courseProductMapper,
                scheduleSlotMapper,
                classroomMapper,
                teacherMapper,
                coursePackageMapper,
                classGroupMapper,
                transactionManager
        );
    }

    @Test
    void shouldRejectInvalidScheduleSlotTimeRange() {
        CourseProductEntity existing = buildGroupProduct();
        CourseProductSaveRequest request = buildGroupRequest();
        request.getSlots().get(0).setEndTime("08:30");
        when(courseProductMapper.selectById(201L)).thenReturn(existing);

        assertThatThrownBy(() -> courseProductService.updateCourseProduct(201L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Schedule slot end time must be after start time.");
    }

    @Test
    void shouldBlockCriticalFieldChangesWhenProductIsReferenced() {
        CourseProductEntity existing = buildGroupProduct();
        CourseProductSaveRequest request = buildGroupRequest();
        request.setDefaultTeacherId(302L);
        when(courseProductMapper.selectById(201L)).thenReturn(existing);
        when(coursePackageMapper.selectCount(any())).thenReturn(0L);
        when(classGroupMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> courseProductService.updateCourseProduct(201L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("This course product is already used.");
    }

    private CourseProductEntity buildGroupProduct() {
        CourseProductEntity product = new CourseProductEntity();
        product.setId(201L);
        product.setProductCode("PRD-0201");
        product.setProductName("Group Math");
        product.setSubjectName("Math");
        product.setTeachingMode("GROUP");
        product.setBillingType("PER_SESSION");
        product.setStartDate(java.time.LocalDate.of(2026, 9, 1));
        product.setTotalSessions(20);
        product.setDefaultClassroomId(401L);
        product.setDefaultTeacherId(301L);
        product.setListPrice(new BigDecimal("600.00"));
        product.setStatus("ACTIVE");
        return product;
    }

    private CourseProductSaveRequest buildGroupRequest() {
        CourseProductSaveRequest request = new CourseProductSaveRequest();
        request.setProductCode("PRD-0201");
        request.setProductName("Group Math");
        request.setSubjectName("Math");
        request.setTeachingMode("GROUP");
        request.setBillingType("PER_SESSION");
        request.setStartDate(java.time.LocalDate.of(2026, 9, 1));
        request.setTotalSessions(20);
        request.setDefaultClassroomId(401L);
        request.setDefaultTeacherId(301L);
        request.setListPrice(new BigDecimal("600.00"));
        request.setStatus("ACTIVE");
        CourseProductSaveRequest.ProductScheduleSlotDTO slot = new CourseProductSaveRequest.ProductScheduleSlotDTO();
        slot.setWeekday(1);
        slot.setStartTime("09:00");
        slot.setEndTime("10:30");
        request.setSlots(List.of(slot));
        return request;
    }
}
