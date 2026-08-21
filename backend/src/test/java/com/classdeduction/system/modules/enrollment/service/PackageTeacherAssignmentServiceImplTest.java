package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.base.entity.TeacherEntity;
import com.classdeduction.system.modules.base.mapper.TeacherMapper;
import com.classdeduction.system.modules.enrollment.entity.PackageTeacherAssignmentEntity;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.service.impl.PackageTeacherAssignmentServiceImpl;
import com.classdeduction.system.modules.lesson.mapper.LessonRecordMapper;
import com.classdeduction.system.modules.enrollment.vo.PackageTeacherAssignmentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageTeacherAssignmentServiceImplTest {

    @Mock
    private PackageTeacherAssignmentMapper assignmentMapper;

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private LessonRecordMapper lessonRecordMapper;

    private PackageTeacherAssignmentService service;

    @BeforeEach
    void setUp() {
        service = new PackageTeacherAssignmentServiceImpl(
                assignmentMapper,
                teacherMapper,
                lessonRecordMapper
        );
    }

    @Test
    void shouldExposeHistoricalTeacherAssignmentAsReadOnlyData() {
        PackageTeacherAssignmentEntity assignment = new PackageTeacherAssignmentEntity();
        assignment.setId(1L);
        assignment.setPackageId(101L);
        assignment.setTeacherId(201L);
        assignment.setAllocatedUnits(60);
        assignment.setSubjectName("Chemistry");
        assignment.setIsPrimary(1);
        assignment.setAssignmentStatus("ACTIVE");
        when(assignmentMapper.selectList(any())).thenReturn(List.of(assignment));
        TeacherEntity teacher = new TeacherEntity();
        teacher.setId(201L);
        when(teacherMapper.selectBatchIds(any())).thenReturn(List.of(teacher));
        when(lessonRecordMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<PackageTeacherAssignmentVO> result = service.listByPackageId(101L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).allocatedHours()).isEqualTo(30.0);
        assertThat(result.get(0).subjectName()).isEqualTo("Chemistry");
    }
}
