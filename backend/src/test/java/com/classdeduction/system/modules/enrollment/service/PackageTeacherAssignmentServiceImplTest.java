package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.dto.PackageTeacherAllocationRequest;
import com.classdeduction.system.modules.enrollment.entity.CoursePackageEntity;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.service.impl.PackageTeacherAssignmentServiceImpl;
import com.classdeduction.system.modules.lesson.entity.LessonRecordEntity;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageTeacherAssignmentServiceImplTest {

    @Mock
    private PackageTeacherAssignmentMapper assignmentMapper;

    @Mock
    private CoursePackageMapper coursePackageMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    private PackageTeacherAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new PackageTeacherAssignmentServiceImpl(
                assignmentMapper,
                coursePackageMapper,
                teacherMapper,
                lessonRecordMapper
        );
    }

    @Test
    void shouldPreserveExplicitTeacherHourSplit() {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(101L);
        coursePackage.setTeachingMode("ONE_ON_ONE");
        coursePackage.setTotalUnits(120);
        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);
        when(teacherMapper.selectById(any())).thenAnswer(invocation -> {
            TeacherEntity teacher = new TeacherEntity();
            teacher.setId(invocation.getArgument(0));
            return teacher;
        });
        when(assignmentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        PackageTeacherAllocationRequest request = new PackageTeacherAllocationRequest();
        request.setReason("Custom allocation");
        request.setAllocations(List.of(
                allocation(201L, "30.0", true),
                allocation(202L, "10.0", false),
                allocation(203L, "20.0", false)
        ));

        service.replaceAllocations(101L, request);

        ArgumentCaptor<PackageTeacherAssignmentEntity> captor =
                ArgumentCaptor.forClass(PackageTeacherAssignmentEntity.class);
        verify(assignmentMapper, times(3)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PackageTeacherAssignmentEntity::getAllocatedUnits)
                .containsExactly(60, 20, 40);
        assertThat(captor.getAllValues())
                .extracting(PackageTeacherAssignmentEntity::getIsPrimary)
                .containsExactly(1, 0, 0);
    }

    @Test
    void shouldRejectAllocationBelowConfirmedTeachingHours() {
        CoursePackageEntity coursePackage = new CoursePackageEntity();
        coursePackage.setId(101L);
        coursePackage.setTeachingMode("ONE_ON_ONE");
        coursePackage.setTotalUnits(120);
        when(coursePackageMapper.selectById(101L)).thenReturn(coursePackage);
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(201L);
        when(teacherMapper.selectById(201L)).thenReturn(teacher);

        LessonRecordEntity confirmed = new LessonRecordEntity();
        confirmed.setActualTeacherId(201L);
        confirmed.setActualUnits(20);
        when(lessonRecordMapper.selectList(any())).thenReturn(List.of(confirmed));

        PackageTeacherAllocationRequest request = new PackageTeacherAllocationRequest();
        request.setAllocations(List.of(allocation(201L, "9.5", true)));

        assertThatThrownBy(() -> service.replaceAllocations(101L, request))
                .hasMessageContaining("confirmed teaching hours");
    }

    private PackageTeacherAllocationRequest.AllocationItem allocation(Long teacherId,
                                                                      String hours,
                                                                      boolean primary) {
        PackageTeacherAllocationRequest.AllocationItem item =
                new PackageTeacherAllocationRequest.AllocationItem();
        item.setTeacherId(teacherId);
        item.setAllocatedHours(new BigDecimal(hours));
        item.setPrimary(primary);
        return item;
    }
}
