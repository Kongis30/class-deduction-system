package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.modules.base.mapper.CourseProductMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.CoursePackagePageQuery;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.service.impl.CoursePackageServiceImpl;
import com.classdeduction.system.modules.enrollment.vo.CoursePackageListItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课时包服务测试：覆盖主老师筛选为空时的短路逻辑，避免分页总数和列表记录不一致。
 */
@ExtendWith(MockitoExtension.class)
class CoursePackageServiceImplTest {

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private CourseProductMapper courseProductMapper;

    private CoursePackageServiceImpl coursePackageService;

    @BeforeEach
    void setUp() {
        coursePackageService = new CoursePackageServiceImpl(
                coursePackageMapper,
                packageTeacherAssignmentMapper,
                studentMapper,
                teacherMapper,
                courseProductMapper
        );
    }

    @Test
    void shouldReturnEmptyPageWhenTeacherFilterMatchesNoPrimaryPackage() {
        CoursePackagePageQuery query = new CoursePackagePageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setTeacherId(301L);
        when(packageTeacherAssignmentMapper.selectList(any())).thenReturn(Collections.emptyList());

        PageResponse<CoursePackageListItemVO> result = coursePackageService.pagePackages(query);

        assertThat(result.records()).isEmpty();
        assertThat(result.total()).isZero();
        verify(coursePackageMapper, never()).selectPage(any(), any());
    }
}
