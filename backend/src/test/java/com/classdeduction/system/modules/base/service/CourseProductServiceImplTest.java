package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.modules.base.dto.CourseProductSaveRequest;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.ClassroomMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.CourseProductScheduleSlotMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.base.service.impl.CourseProductServiceImpl;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.schedule.entity.ClassGroupEntity;
import com.classdeduction.system.modules.schedule.mapper.ClassGroupMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    void shouldCreateGroupProductWithoutOriginalPriceOrDefaultResources() {
        CourseProductSaveRequest request = buildGroupRequest();
        request.setListPrice(null);
        request.setDefaultTeacherId(null);
        request.setDefaultClassroomId(null);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        doAnswer(invocation -> {
            CourseProductEntity product = invocation.getArgument(0);
            product.setId(201L);
            return 1;
        }).when(courseProductMapper).insert(any(CourseProductEntity.class));

        courseProductService.createCourseProduct(request);

        verify(courseProductMapper).insert(argThat((CourseProductEntity product) ->
                product.getListPrice().signum() == 0
                        && product.getDefaultTeacherId() == null
                        && product.getDefaultClassroomId() == null
                        && product.getBillingType() == null
                        && product.getStartDate() == null
                        && product.getTotalSessions() == null));
        verify(classGroupMapper, never()).insert(any(ClassGroupEntity.class));
        verify(transactionManager).commit(transactionStatus);
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
        request.setStatus("ACTIVE");
        CourseProductSaveRequest.ProductScheduleSlotDTO slot = new CourseProductSaveRequest.ProductScheduleSlotDTO();
        slot.setWeekday(1);
        slot.setStartTime("09:00");
        slot.setEndTime("10:30");
        request.setSlots(List.of(slot));
        return request;
    }
}
