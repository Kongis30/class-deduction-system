package com.classdeduction.system.modules.schedule.service;

import com.classdeduction.system.common.dto.CurrentUserResponse;
import com.classdeduction.system.common.exception.BusinessException;
import com.classdeduction.system.modules.lesson.dto.LessonRecordConfirmRequest;
import com.classdeduction.system.modules.lesson.service.LessonRecordService;
import com.classdeduction.system.modules.lesson.vo.LessonRecordConfirmResultVO;
import com.classdeduction.system.modules.schedule.dto.OneOnOneDeductionQuery;
import com.classdeduction.system.modules.schedule.vo.OneOnOnePackageVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 用隔离快照复现 Angela 的管理员直接消课，所有更改在测试后回滚。 */
@EnabledIfEnvironmentVariable(named = "ANGELA_REHEARSAL_TEST", matches = "1")
@ActiveProfiles("group-course-v2")
@SpringBootTest(properties = {
        "spring.datasource.url=${ANGELA_REHEARSAL_URL}",
        "spring.datasource.username=${ANGELA_REHEARSAL_USERNAME}",
        "spring.datasource.password=${ANGELA_REHEARSAL_PASSWORD}",
        "app.database.required-name=jin_angela_repair_20261030",
        "app.schedule.renewal-initial-delay-ms=600000", "app.balance.sync-initial-delay-ms=600000"
})
@Transactional
class AngelaDeductionSnapshotReproTest {
    private static final long PACKAGE_ID = 2105336690043879425L;
    private static final long WEDNESDAY_SLOT_ID = 2105452959699095554L;
    private static final long SUNDAY_LESSON_ID = 2105452959711678466L;
    private static final long TEACHER_ID = 2102805856267108353L;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SqlSessionTemplate sqlSession;
    @Autowired private LessonRecordService lessonRecordService;
    @Autowired private LessonOccurrenceService lessonOccurrenceService;

    @Test
    void oldAllocationReproducesReportedDeductionError() {
        prepare(8);
        assertThatThrownBy(() -> lessonRecordService.confirmLesson(request(), admin()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("confirmed or protected lessons exceed its allocated hours");
    }

    @Test
    void correctedAllocationAllowsAdminToDeductSundayLesson() {
        prepare(12);
        OneOnOnePackageVO before = packageView();
        assertThat(before.remainingUnits()).isEqualTo(10.0);
        assertThat(before.lessons()).hasSize(6);

        LessonRecordConfirmResultVO result = lessonRecordService.confirmLesson(request(), admin());
        assertThat(result.deductedUnits()).isEqualTo(2.0);
        assertThat(result.remainingUnits()).isEqualTo(8.0);
        sqlSession.clearCache();
        OneOnOnePackageVO after = packageView();
        assertThat(after.remainingUnits()).isEqualTo(8.0);
        assertThat(after.lessons()).hasSize(6);
        assertThat(after.teacherHours()).singleElement().satisfies(hours -> {
            assertThat(hours.allocatedUnits()).isEqualTo(12.0);
            assertThat(hours.deductedUnits()).isEqualTo(4.0);
            assertThat(hours.remainingUnits()).isEqualTo(8.0);
        });
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM lesson_record WHERE occurrence_id = ? AND package_id = ?
                    AND is_deleted = 0 AND confirm_stage = 'CONFIRMED'
                    AND result_status = 'COMPLETED' AND deducted_units = 4
                """, Integer.class, SUNDAY_LESSON_ID, PACKAGE_ID)).isEqualTo(1);
    }

    private void prepare(int allocatedUnits) {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class))
                .isEqualTo("jin_angela_repair_20261030");
        assertThat(jdbc.update("UPDATE recurring_schedule_slot SET allocated_units = ? WHERE id = ?",
                allocatedUnits, WEDNESDAY_SLOT_ID)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT remaining_units FROM course_package WHERE id = ?",
                Integer.class, PACKAGE_ID)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_record WHERE occurrence_id = ? AND is_deleted = 0",
                Integer.class, SUNDAY_LESSON_ID)).isZero();
        sqlSession.clearCache();
    }

    private LessonRecordConfirmRequest request() {
        LessonRecordConfirmRequest request = new LessonRecordConfirmRequest();
        request.setOccurrenceId(SUNDAY_LESSON_ID);
        request.setResultStatus("COMPLETED");
        request.setActualStartAt(LocalDateTime.of(2026, 10, 4, 12, 0));
        request.setActualEndAt(LocalDateTime.of(2026, 10, 4, 14, 0));
        request.setActualUnits(4);
        return request;
    }

    private CurrentUserResponse admin() {
        return new CurrentUserResponse(1L, "admin", "System Admin", "ADMIN", null, null, List.of("ADMIN"));
    }

    private OneOnOnePackageVO packageView() {
        OneOnOneDeductionQuery query = new OneOnOneDeductionQuery();
        query.setPackageId(PACKAGE_ID);
        query.setTeacherId(TEACHER_ID);
        return lessonOccurrenceService.listOneOnOneDeductions(query, null).stream()
                .flatMap(teacher -> teacher.students().stream())
                .flatMap(student -> student.packages().stream())
                .filter(item -> item.packageId().equals(PACKAGE_ID))
                .findFirst().orElseThrow();
    }
}
