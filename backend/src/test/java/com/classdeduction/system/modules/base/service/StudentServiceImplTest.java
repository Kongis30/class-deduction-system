package com.classdeduction.system.modules.base.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.classdeduction.system.common.model.PageResponse;
import com.classdeduction.system.common.util.StudentNoGenerator;
import com.classdeduction.system.modules.base.dto.StudentPageQuery;
import com.classdeduction.system.modules.base.mapper.CampusMapper;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import com.classdeduction.system.modules.base.service.impl.StudentServiceImpl;
import com.classdeduction.system.modules.base.vo.StudentListItemVO;
import com.classdeduction.system.modules.auth.entity.UserAccountEntity;
import com.classdeduction.system.modules.auth.mapper.AuthSessionMapper;
import com.classdeduction.system.modules.auth.mapper.UserAccountMapper;
import com.classdeduction.system.modules.auth.mapper.UserRoleMapper;
import com.classdeduction.system.modules.base.entity.StudentEntity;
import com.classdeduction.system.modules.enrollment.mapper.PackageTeacherAssignmentMapper;
import com.classdeduction.system.modules.enrollment.mapper.CoursePackageMapper;
import com.classdeduction.system.modules.finance.mapper.TeacherPayRateMapper;
import com.classdeduction.system.modules.schedule.mapper.ClassEnrollmentMapper;
import com.classdeduction.system.modules.schedule.mapper.LessonOccurrenceMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleRuleMapper;
import com.classdeduction.system.modules.schedule.mapper.RecurringScheduleSlotMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceImplTest {

    @Mock
    private StudentMapper studentMapper;
    @Mock
    private StudentNoGenerator studentNoGenerator;
    @Mock
    private CoursePackageMapper coursePackageMapper;
    @Mock
    private ClassEnrollmentMapper classEnrollmentMapper;
    @Mock
    private CampusMapper campusMapper;
    @Mock
    private UserAccountMapper userAccountMapper;
    @Mock
    private AuthSessionMapper authSessionMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private LessonOccurrenceMapper lessonOccurrenceMapper;
    @Mock
    private RecurringScheduleRuleMapper recurringScheduleRuleMapper;
    @Mock
    private RecurringScheduleSlotMapper recurringScheduleSlotMapper;
    @Mock
    private PackageTeacherAssignmentMapper packageTeacherAssignmentMapper;
    @Mock
    private TeacherPayRateMapper teacherPayRateMapper;

    private StudentServiceImpl studentService;

    @BeforeEach
    void setUp() {
        studentService = new StudentServiceImpl(
                studentMapper,
                studentNoGenerator,
                coursePackageMapper,
                classEnrollmentMapper,
                campusMapper,
                userAccountMapper,
                authSessionMapper,
                userRoleMapper,
                lessonOccurrenceMapper,
                recurringScheduleRuleMapper,
                recurringScheduleSlotMapper,
                packageTeacherAssignmentMapper,
                teacherPayRateMapper
        );
    }

    @Test
    void shouldReturnEnrollmentSummaryAndNoEnrollmentStatusInStudentPage() {
        StudentPageQuery query = new StudentPageQuery();
        query.setKeyword(" Ava ");
        query.setGuardian(" Lin ");
        query.setStatus(" ACTIVE ");
        query.setCampusId(1001L);

        Page<Map<String, Object>> mapperPage = new Page<>(1, 10);
        mapperPage.setRecords(List.of(enrolledRow(), noEnrollmentRow()));
        mapperPage.setTotal(2);
        when(studentMapper.selectStudentOperationPage(any(), same(query))).thenReturn(mapperPage);

        PageResponse<StudentListItemVO> result = studentService.pageStudents(query);

        assertThat(query.getKeyword()).isEqualTo("Ava");
        assertThat(query.getGuardian()).isEqualTo("Lin");
        assertThat(query.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.total()).isEqualTo(2);

        StudentListItemVO enrolled = result.records().get(0);
        assertThat(enrolled.studentName()).isEqualTo("Ava Chen");
        assertThat(enrolled.latestSignupDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(enrolled.enrollmentCount()).isEqualTo(2L);
        assertThat(enrolled.enrollmentAmountDue()).isEqualByComparingTo("1800.00");
        assertThat(enrolled.enrollmentPaidAmount()).isEqualByComparingTo("1200.00");
        assertThat(enrolled.enrollmentBalanceAmount()).isEqualByComparingTo("600.00");
        assertThat(enrolled.aggregatePaymentStatus()).isEqualTo("PARTIAL");
        assertThat(enrolled.hasEnrollmentOrder()).isTrue();

        StudentListItemVO notEnrolled = result.records().get(1);
        assertThat(notEnrolled.enrollmentCount()).isZero();
        assertThat(notEnrolled.enrollmentAmountDue()).isZero();
        assertThat(notEnrolled.enrollmentPaidAmount()).isZero();
        assertThat(notEnrolled.enrollmentBalanceAmount()).isZero();
        assertThat(notEnrolled.aggregatePaymentStatus()).isEqualTo("NO_ENROLLMENT");
        assertThat(notEnrolled.hasEnrollmentOrder()).isFalse();
    }

    @Test
    void shouldNormalizeEnrollmentFiltersBeforeMapperQuery() {
        StudentPageQuery query = new StudentPageQuery();
        query.setPaymentStatus(" PAID ");
        query.setHasEnrollmentOrder(false);

        Page<Map<String, Object>> mapperPage = new Page<>(1, 10);
        mapperPage.setRecords(List.of());
        mapperPage.setTotal(0);
        when(studentMapper.selectStudentOperationPage(any(), same(query))).thenReturn(mapperPage);

        studentService.pageStudents(query);

        assertThat(query.getPaymentStatus()).isEqualTo("PAID");
        assertThat(query.getHasEnrollmentOrder()).isFalse();
    }

    @Test
    void studentOperationPageGuardianFilterShouldCoverNameAndBothPhones() throws NoSuchMethodException {
        Select select = StudentMapper.class
                .getMethod("selectStudentOperationPage", Page.class, StudentPageQuery.class)
                .getAnnotation(Select.class);
        String sql = String.join("\n", select.value());

        assertThat(sql).contains("s.guardian_name LIKE CONCAT('%', #{query.guardian}, '%')");
        assertThat(sql).contains("s.guardian_phone LIKE CONCAT('%', #{query.guardian}, '%')");
        assertThat(sql).contains("s.guardian_phone_2 LIKE CONCAT('%', #{query.guardian}, '%')");
    }

    @Test
    void studentOperationPageShouldExcludeCancelledAndRefundedOrdersFromPaymentTotals() throws NoSuchMethodException {
        Select select = StudentMapper.class
                .getMethod("selectStudentOperationPage", Page.class, StudentPageQuery.class)
                .getAnnotation(Select.class);
        String sql = String.join("\n", select.value());

        assertThat(sql).contains("AND eo.order_status &lt;&gt; 'CANCELLED'");
        assertThat(sql).contains("AND eo.payment_status &lt;&gt; 'REFUNDED'");
    }

    @Test
    void studentOperationPageCanIncludeStudentsWithActiveReceiptsWithoutChangingOtherQueries() throws NoSuchMethodException {
        Select select = StudentMapper.class
                .getMethod("selectStudentOperationPage", Page.class, StudentPageQuery.class)
                .getAnnotation(Select.class);
        String sql = String.join("\n", select.value());

        assertThat(sql).contains("FROM receipt_record rr");
        assertThat(sql).contains("rr.student_id = s.id");
        assertThat(sql).contains("rr.is_deleted = 0");
        assertThat(sql).contains("query.includeReceiptStudents != null and query.includeReceiptStudents");
        assertThat(sql).contains("OR EXISTS (");
        assertThat(sql).contains("student_list.hasEnrollmentOrder = 1 OR student_list.hasActiveReceipt = 1");
    }

    @Test
    void shouldCascadeDeleteLoginAndOperationalDataWhenDeletingStudent() {
        StudentEntity student = new StudentEntity();
        student.setId(1L);
        student.setStudentName("Ava Chen");
        UserAccountEntity account = new UserAccountEntity();
        account.setId(101L);
        account.setStudentId(1L);
        when(studentMapper.selectById(1L)).thenReturn(student);
        when(userAccountMapper.selectList(any())).thenReturn(List.of(account));
        when(coursePackageMapper.selectList(any())).thenReturn(List.of());

        studentService.deleteStudent(1L);

        verify(userRoleMapper).delete(any());
        verify(authSessionMapper).delete(any());
        verify(userAccountMapper).delete(any());
        verify(lessonOccurrenceMapper).delete(any());
        verify(classEnrollmentMapper).delete(any());
        verify(teacherPayRateMapper).delete(any());
        verify(studentMapper).deleteById(1L);
    }

    @Test
    void shouldClearServiceAddressWhenServiceIsNotRequired() {
        StudentEntity student = new StudentEntity();
        student.setId(1L);
        student.setStudentNo("S-0001");
        when(studentMapper.selectById(1L)).thenReturn(student);
        when(studentMapper.selectOne(any())).thenReturn(null);

        var request = new com.classdeduction.system.modules.base.dto.StudentSaveRequest();
        request.setStudentNo("S-0001");
        request.setStudentName("Ava Chen");
        request.setPickupRequired(false);
        request.setPickupAddress("100 Main St");
        request.setDropoffRequired(true);
        request.setDropoffAddress(" 200 Center Ave ");

        studentService.updateStudent(1L, request);

        var captor = forClass(StudentEntity.class);
        verify(studentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPickupRequired()).isFalse();
        assertThat(captor.getValue().getPickupAddress()).isNull();
        assertThat(captor.getValue().getDropoffRequired()).isTrue();
        assertThat(captor.getValue().getDropoffAddress()).isEqualTo("200 Center Ave");
    }

    private Map<String, Object> enrolledRow() {
        Map<String, Object> row = baseRow(1L, "S-0001", "Ava Chen");
        row.put("latestSignupDate", LocalDate.of(2026, 7, 15));
        row.put("enrollmentCount", 2L);
        row.put("enrollmentAmountDue", new BigDecimal("1800.00"));
        row.put("enrollmentPaidAmount", new BigDecimal("1200.00"));
        row.put("enrollmentBalanceAmount", new BigDecimal("600.00"));
        row.put("aggregatePaymentStatus", "PARTIAL");
        row.put("hasEnrollmentOrder", 1);
        return row;
    }

    private Map<String, Object> noEnrollmentRow() {
        Map<String, Object> row = baseRow(2L, "S-0002", "Ben Li");
        row.put("enrollmentCount", 0L);
        row.put("enrollmentAmountDue", BigDecimal.ZERO);
        row.put("enrollmentPaidAmount", BigDecimal.ZERO);
        row.put("enrollmentBalanceAmount", BigDecimal.ZERO);
        row.put("aggregatePaymentStatus", "NO_ENROLLMENT");
        row.put("hasEnrollmentOrder", 0);
        return row;
    }

    private Map<String, Object> baseRow(Long id, String studentNo, String studentName) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("studentNo", studentNo);
        row.put("studentName", studentName);
        row.put("englishName", studentName);
        row.put("gender", "FEMALE");
        row.put("birthDate", LocalDate.of(2015, 1, 1));
        row.put("grade", "G5");
        row.put("school", "Central School");
        row.put("sourceChannel", "REFERRAL");
        row.put("status", "ACTIVE");
        row.put("guardianName", "Mei Lin");
        row.put("phone", "555-0100");
        row.put("guardianPhone2", "555-0101");
        row.put("guardianEmail", "parent@example.com");
        row.put("guardianRelationType", "MOTHER");
        row.put("pickupRequired", 1);
        row.put("pickupAddress", "100 Main St");
        row.put("dropoffRequired", 0);
        row.put("campusId", 1001L);
        row.put("campusName", "North Campus");
        row.put("createdAt", LocalDateTime.of(2026, 7, 1, 9, 0));
        row.put("updatedAt", LocalDateTime.of(2026, 7, 2, 9, 0));
        return row;
    }
}
