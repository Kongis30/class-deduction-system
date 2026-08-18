package com.classdeduction.system.modules.base.service;

import com.classdeduction.system.modules.base.dto.CourseProductSaveRequest;
import com.classdeduction.system.modules.base.entity.CourseProductEntity;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseProductServiceImplTest {

    @Mock
    private CourseProductMapper courseProductMapper;
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
                coursePackageMapper,
                classGroupMapper,
                transactionManager
        );
    }

    @Test
    void shouldCreateGroupProductWithoutOriginalPriceOrDefaultResources() {
        CourseProductSaveRequest request = buildGroupRequest();
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        doAnswer(invocation -> {
            CourseProductEntity product = invocation.getArgument(0);
            product.setId(201L);
            return 1;
        }).when(courseProductMapper).insert(any(CourseProductEntity.class));

        courseProductService.createCourseProduct(request);

        verify(courseProductMapper).insert(argThat((CourseProductEntity product) ->
                product.getListPrice() == null
                        && product.getDefaultTeacherId() == null
                        && product.getDefaultClassroomId() == null
                        && product.getBillingType() == null
                        && product.getStartDate() == null
                        && product.getTotalSessions() == null));
        verify(classGroupMapper, never()).insert(any(ClassGroupEntity.class));
        verify(transactionManager).commit(transactionStatus);
    }

    private CourseProductSaveRequest buildGroupRequest() {
        CourseProductSaveRequest request = new CourseProductSaveRequest();
        request.setProductCode("PRD-0201");
        request.setProductName("Group Math");
        request.setSubjectName("Math");
        request.setTeachingMode("GROUP");
        request.setStatus("ACTIVE");
        return request;
    }
}
