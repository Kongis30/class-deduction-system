package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderCorrectionRequest;
import com.classdeduction.system.modules.enrollment.dto.RegistrationIntakeRequest;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import com.classdeduction.system.common.time.AppTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 仅在本机专用临时 MySQL 中验证订单更正的实际落库和回滚。 */
@EnabledIfEnvironmentVariable(named = "EDIT_RECEIPT_MYSQL_TEST", matches = "1")
@ActiveProfiles("group-course-v2")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:13319/class_deduction_system_v2?serverTimezone=America/New_York",
        "spring.datasource.username=root", "spring.datasource.password=",
        "app.schedule.renewal-initial-delay-ms=600000", "app.balance.sync-initial-delay-ms=600000"
})
@Transactional
class EditReceiptMySqlIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EnrollmentOrderService service;
    @Autowired private EnrollmentOrderMapper orderMapper;

    @Test
    void cancelZeroFeePickupRecalculatesOrderWithoutChangingPayment() {
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(13319);
        jdbc.update("INSERT INTO student (id,student_no,student_name) VALUES (11001,'RECEIPT-TEST','Receipt Test')");
        jdbc.update("INSERT INTO enrollment_order (id,enrollment_no,student_id,order_status,tuition_subtotal_amount,pickup_fee_amount,pickup_required_snapshot,pickup_address_snapshot,discount_amount,original_amount,amount_due,received_amount,balance_amount,payment_status) VALUES (12001,'RECEIPT-TEST-ORDER',11001,'CONFIRMED',3499,300,1,'School',400,3799,3399,3399,0,'PAID')");
        jdbc.update("INSERT INTO enrollment_order_item (id,enrollment_order_id,line_no,register_class,detail,course_type,price_amount,discount_amount,pickup_required_snapshot,pickup_address_snapshot,pickup_fee_amount) VALUES (13001,12001,1,'Test','Test','GROUP',3499,400,1,'School',0)");
        jdbc.update("INSERT INTO payment_record (id,enrollment_order_id,payment_no,payment_method,amount,payment_status) VALUES (14001,12001,'RECEIPT-TEST-PAY','CASH',3399,'PAID')");
        jdbc.update("INSERT INTO receipt_record (id,receipt_no,enrollment_order_id,student_id,template_code,receipt_payload) VALUES (15001,'RECEIPT-TEST-R',12001,11001,'DEFAULT_RECEIPT',JSON_OBJECT('items',JSON_ARRAY(JSON_OBJECT('lineNo',1,'orderItemId','13001'))))");

        RegistrationIntakeRequest.CourseItemPayload item = new RegistrationIntakeRequest.CourseItemPayload();
        item.setOrderItemId(13001L);
        item.setPickupRequired(false);
        item.setCorrectionChangedFields(List.of("pickupRequired"));
        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        request.setItems(List.of(item));
        request.setPickupFeeAmount(BigDecimal.ZERO);
        request.setPickupFeeCharged(true);
        service.correctOrder(12001L, request);

        assertThat(jdbc.queryForObject("SELECT pickup_required_snapshot FROM enrollment_order_item WHERE id=13001", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT pickup_required_snapshot FROM enrollment_order WHERE id=12001", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT pickup_address_snapshot FROM enrollment_order WHERE id=12001", String.class)).isNull();
        assertThat(orderMapper.selectTransportOrderIds()).doesNotContain(12001L);
        assertThat(money("pickup_fee_amount")).isEqualByComparingTo("0.00");
        assertThat(money("amount_due")).isEqualByComparingTo("3099.00");
        assertThat(money("received_amount")).isEqualByComparingTo("3399.00");
        assertThat(money("balance_amount")).isEqualByComparingTo("-300.00");
        assertThat(jdbc.queryForObject("SELECT amount FROM payment_record WHERE id=14001", BigDecimal.class)).isEqualByComparingTo("3399.00");
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM payment_allocation WHERE payment_record_id=14001 AND is_deleted=0", BigDecimal.class)).isEqualByComparingTo("3399.00");
        assertThat(jdbc.queryForObject("SELECT balance_amount FROM enrollment_order_item WHERE id=13001", BigDecimal.class)).isEqualByComparingTo("-300.00");
        assertThat(jdbc.queryForObject("SELECT CAST(JSON_UNQUOTE(JSON_EXTRACT(receipt_payload, '$.amountDue')) AS DECIMAL(12,2)) FROM receipt_record WHERE id=15001", BigDecimal.class)).isEqualByComparingTo("3099.00");
    }

    private BigDecimal money(String field) {
        return jdbc.queryForObject("SELECT " + field + " FROM enrollment_order WHERE id=12001", BigDecimal.class);
    }

    @Test
    void cancellingOneCoursePickupKeepsTheOtherCourseService() {
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(13319);
        jdbc.update("INSERT INTO student (id,student_no,student_name) VALUES (11003,'RECEIPT-TRANSPORT','Transport Test')");
        jdbc.update("INSERT INTO enrollment_order (id,enrollment_no,student_id,order_status,tuition_subtotal_amount,pickup_fee_amount,pickup_required_snapshot,pickup_address_snapshot,original_amount,amount_due,balance_amount) VALUES (12003,'RECEIPT-TRANSPORT-ORDER',11003,'CONFIRMED',2000,50,1,'A',2050,2050,2050)");
        jdbc.update("INSERT INTO enrollment_order_item (id,enrollment_order_id,line_no,register_class,detail,course_type,price_amount,pickup_required_snapshot,pickup_address_snapshot,pickup_fee_amount) VALUES (13004,12003,1,'A','A','GROUP',1000,1,'A',20),(13005,12003,2,'B','B','GROUP',1000,1,'B',30)");
        RegistrationIntakeRequest.CourseItemPayload item = new RegistrationIntakeRequest.CourseItemPayload();
        item.setOrderItemId(13004L);
        item.setPickupRequired(false);
        item.setCorrectionChangedFields(List.of("pickupRequired"));
        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        request.setItems(List.of(item));
        service.correctOrder(12003L, request);

        assertThat(jdbc.queryForObject("SELECT pickup_fee_amount FROM enrollment_order WHERE id=12003", BigDecimal.class)).isEqualByComparingTo("30");
        assertThat(jdbc.queryForObject("SELECT pickup_required_snapshot FROM enrollment_order WHERE id=12003", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT pickup_address_snapshot FROM enrollment_order WHERE id=12003", String.class)).isEqualTo("B");
        assertThat(jdbc.queryForObject("SELECT pickup_fee_amount FROM enrollment_order_item WHERE id=13005", BigDecimal.class)).isEqualByComparingTo("30");
        assertThat(orderMapper.selectTransportOrderIds()).contains(12003L);
    }

    @Test
    void priceDecreaseKeepsExistingAllocationsAcrossTwoCourses() {
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(13319);
        jdbc.update("INSERT INTO student (id,student_no,student_name) VALUES (11002,'RECEIPT-MULTI','Multi Test')");
        jdbc.update("INSERT INTO enrollment_order (id,enrollment_no,student_id,order_status,tuition_subtotal_amount,original_amount,amount_due,received_amount,balance_amount,payment_status) VALUES (12002,'RECEIPT-MULTI-ORDER',11002,'CONFIRMED',2000,2000,2000,1800,200,'PARTIAL')");
        jdbc.update("INSERT INTO enrollment_order_item (id,enrollment_order_id,line_no,register_class,detail,course_type,price_amount,discount_amount) VALUES (13002,12002,1,'A','A','GROUP',1000,0),(13003,12002,2,'B','B','GROUP',1000,0)");
        jdbc.update("INSERT INTO payment_record (id,enrollment_order_id,payment_no,payment_method,amount,payment_status) VALUES (14002,12002,'RECEIPT-MULTI-PAY','CASH',1800,'PAID')");
        jdbc.update("INSERT INTO payment_allocation (id,payment_record_id,enrollment_order_id,enrollment_order_item_id,amount) VALUES (15002,14002,12002,13002,900),(15003,14002,12002,13003,900)");
        RegistrationIntakeRequest.CourseItemPayload item = new RegistrationIntakeRequest.CourseItemPayload();
        item.setOrderItemId(13002L);
        item.setPriceAmount(new BigDecimal("500"));
        item.setCorrectionChangedFields(List.of("priceAmount"));
        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        request.setItems(List.of(item));
        service.correctOrder(12002L, request);

        assertThat(jdbc.queryForObject("SELECT amount_due FROM enrollment_order WHERE id=12002", BigDecimal.class)).isEqualByComparingTo("1500");
        assertThat(jdbc.queryForObject("SELECT balance_amount FROM enrollment_order WHERE id=12002", BigDecimal.class)).isEqualByComparingTo("-300");
        assertThat(jdbc.queryForObject("SELECT amount FROM payment_allocation WHERE id=15002", BigDecimal.class)).isEqualByComparingTo("900");
        assertThat(jdbc.queryForObject("SELECT amount FROM payment_allocation WHERE id=15003", BigDecimal.class)).isEqualByComparingTo("900");
        assertThat(jdbc.queryForObject("SELECT amount FROM payment_record WHERE id=14002", BigDecimal.class)).isEqualByComparingTo("1800");
    }

    @Test
    void priceDecreaseFirstAllocatesPreviouslyUnassignedPayment() {
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(13319);
        jdbc.update("INSERT INTO student (id,student_no,student_name) VALUES (11005,'RECEIPT-UNALLOCATED','Unallocated Test')");
        jdbc.update("INSERT INTO enrollment_order (id,enrollment_no,student_id,order_status,tuition_subtotal_amount,original_amount,amount_due,received_amount,balance_amount,payment_status) VALUES (12005,'RECEIPT-UNALLOCATED-ORDER',11005,'CONFIRMED',2000,2000,2000,1800,200,'PARTIAL')");
        jdbc.update("INSERT INTO enrollment_order_item (id,enrollment_order_id,line_no,register_class,detail,course_type,price_amount,discount_amount) VALUES (13007,12005,1,'A','A','GROUP',1000,0),(13008,12005,2,'B','B','GROUP',1000,0)");
        jdbc.update("INSERT INTO payment_record (id,enrollment_order_id,payment_no,payment_method,amount,payment_status) VALUES (14005,12005,'RECEIPT-UNALLOCATED-PAY','CASH',1800,'PAID')");
        RegistrationIntakeRequest.CourseItemPayload item = new RegistrationIntakeRequest.CourseItemPayload();
        item.setOrderItemId(13007L);
        item.setPriceAmount(new BigDecimal("500"));
        item.setCorrectionChangedFields(List.of("priceAmount"));
        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        request.setItems(List.of(item));
        service.correctOrder(12005L, request);

        assertThat(jdbc.queryForObject("SELECT amount_due FROM enrollment_order WHERE id=12005", BigDecimal.class)).isEqualByComparingTo("1500");
        assertThat(jdbc.queryForObject("SELECT balance_amount FROM enrollment_order WHERE id=12005", BigDecimal.class)).isEqualByComparingTo("-300");
        assertThat(jdbc.queryForObject("SELECT amount FROM payment_allocation WHERE payment_record_id=14005 AND enrollment_order_item_id=13007 AND is_deleted=0", BigDecimal.class)).isEqualByComparingTo("900");
        assertThat(jdbc.queryForObject("SELECT amount FROM payment_allocation WHERE payment_record_id=14005 AND enrollment_order_item_id=13008 AND is_deleted=0", BigDecimal.class)).isEqualByComparingTo("900");
    }

    @Test
    void changingClassPreservesSharedScheduleAndConfirmedHistory() {
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(13319);
        jdbc.update("INSERT INTO student (id,student_no,student_name) VALUES (11004,'RECEIPT-CLASS','Class Test')");
        jdbc.update("INSERT INTO course_product (id,product_code,product_name,teaching_mode) VALUES (16001,'TEST-CLASS-PRODUCT','Course A','GROUP'),(16007,'TEST-CLASS-PRODUCT-B','Course B','GROUP')");
        jdbc.update("INSERT INTO group_course_project (id,course_product_id,project_code,project_name) VALUES (16002,16001,'TEST-PROJECT','Project A'),(16008,16007,'TEST-PROJECT-B','Project B')");
        jdbc.update("INSERT INTO group_registration_plan (id,project_id,plan_code,plan_name,coverage_type,package_unit,package_quantity,full_day_price) VALUES (16003,16002,'TEST-PLAN','Plan A','ROLLING_MONTHS','MONTH',2,1000),(16009,16008,'TEST-PLAN-B','Plan B','ROLLING_MONTHS','MONTH',2,1000)");
        jdbc.update("INSERT INTO class_group (id,class_name,product_id,group_project_id,actual_class,start_date,end_date) VALUES (16004,'Old Class',16001,16002,'A','2026-01-01','2027-12-31'),(16005,'New Class',16007,16008,'B','2026-01-01','2027-12-31')");
        jdbc.update("INSERT INTO class_group_schedule_slot (id,class_group_id,weekday,start_time,end_time) VALUES (16006,16005,1,'09:00:00','11:00:00')");
        jdbc.update("INSERT INTO enrollment_order (id,enrollment_no,student_id,class_id,order_status,signup_date,tuition_subtotal_amount,original_amount,amount_due,balance_amount) VALUES (12004,'RECEIPT-CLASS-ORDER',11004,16004,'CONFIRMED','2026-10-01',1000,1000,1000,1000)");
        jdbc.update("INSERT INTO enrollment_order_item (id,enrollment_order_id,line_no,register_class,detail,course_type,course_product_id,group_project_id,group_registration_plan_id,class_id,package_unit,package_quantity,start_date,price_amount) VALUES (13006,12004,1,'Test','Test','GROUP',16001,16002,16003,16004,'MONTH',2,'2026-11-02',1000)");
        jdbc.update("INSERT INTO class_enrollment (id,class_id,student_id,enrollment_order_item_id,enrollment_date,expiry_date,billing_type,purchased_quantity,group_project_id,group_registration_plan_id,status) VALUES (17001,16004,11004,13006,'2026-11-02','2026-12-31','MONTHLY',2,16002,16003,'ACTIVE')");
        jdbc.update("UPDATE class_enrollment SET logical_course_id=17001 WHERE id=17001");
        jdbc.update("UPDATE enrollment_order_item SET logical_course_id=17001 WHERE id=13006");
        jdbc.update("INSERT INTO lesson_occurrence (id,lesson_no,class_group_id,occurrence_type,planned_start_at,planned_end_at,planned_units,planning_status) VALUES (18001,'TEST-OLD-SHARED',16004,'REGULAR','2026-11-02 09:00:00','2026-11-02 11:00:00',4,'SCHEDULED'),(18002,'TEST-OLD-HISTORY',16004,'REGULAR','2026-10-05 09:00:00','2026-10-05 11:00:00',4,'MERGED')");
        jdbc.update("INSERT INTO lesson_record (id,occurrence_id,student_id,class_enrollment_id,planned_start_at,result_status,confirm_stage) VALUES (19001,18002,11004,17001,'2026-10-05 09:00:00','COMPLETED','CONFIRMED')");
        RegistrationIntakeRequest.CourseItemPayload item = new RegistrationIntakeRequest.CourseItemPayload();
        item.setOrderItemId(13006L);
        item.setCourseType("GROUP");
        item.setCourseProductId(16007L);
        item.setGroupProjectId(16008L);
        item.setGroupRegistrationPlanId(16009L);
        item.setClassId(16005L);
        item.setPackageUnit("MONTH");
        item.setPackageQuantity(new BigDecimal("2"));
        item.setPurchasedMonths(2);
        item.setPriceAmount(new BigDecimal("1000"));
        item.setStartDate(LocalDate.of(2026, 11, 2));
        item.setCorrectionChangedFields(List.of("classId", "courseProductId", "groupProjectId", "groupRegistrationPlanId"));
        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        request.setItems(List.of(item));
        service.correctOrder(12004L, request);

        assertThat(jdbc.queryForObject("SELECT status FROM class_enrollment WHERE id=17001", String.class)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT logical_course_id FROM class_enrollment WHERE id=17001", Long.class)).isEqualTo(17001L);
        assertThat(jdbc.queryForObject("SELECT logical_course_id FROM enrollment_order_item WHERE id=13006", Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM class_enrollment WHERE student_id=11004 AND class_id=16005 AND status='ACTIVE' AND is_deleted=0", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT planning_status FROM lesson_occurrence WHERE id=18001", String.class)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT class_enrollment_id FROM lesson_record WHERE id=19001 AND is_deleted=0", Long.class)).isEqualTo(17001L);

        // 同一课程换班应保持续报链；跨产品换课则保留旧课程链作为历史。
        Long currentEnrollment = jdbc.queryForObject("SELECT id FROM class_enrollment WHERE student_id=11004 AND class_id=16005 AND status='ACTIVE'", Long.class);
        jdbc.update("UPDATE class_enrollment SET logical_course_id=? WHERE id=?", currentEnrollment, currentEnrollment);
        jdbc.update("UPDATE enrollment_order_item SET logical_course_id=? WHERE id=13006", currentEnrollment);
        jdbc.update("INSERT INTO class_group (id,class_name,product_id,group_project_id,actual_class,start_date,end_date) VALUES (16010,'Another Class',16007,16008,'C','2026-01-01','2027-12-31')");
        jdbc.update("INSERT INTO class_group_schedule_slot (id,class_group_id,weekday,start_time,end_time) VALUES (16011,16010,1,'09:00:00','11:00:00')");
        item.setClassId(16010L);
        item.setCorrectionChangedFields(List.of("classId"));
        service.correctOrder(12004L, request);
        assertThat(jdbc.queryForObject("SELECT logical_course_id FROM class_enrollment WHERE student_id=11004 AND class_id=16010 AND status='ACTIVE'", Long.class)).isEqualTo(currentEnrollment);
        assertThat(jdbc.queryForObject("SELECT logical_course_id FROM enrollment_order_item WHERE id=13006", Long.class)).isEqualTo(currentEnrollment);
    }

    @Test
    void changingPrivateCourseCancelsFutureScheduleAndPreservesPastLesson() {
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(13319);
        jdbc.update("INSERT INTO student (id,student_no,student_name) VALUES (11006,'RECEIPT-PRIVATE','Private Test')");
        jdbc.update("INSERT INTO course_product (id,product_code,product_name,teaching_mode) VALUES (16020,'PRIVATE-A','Private A','ONE_ON_ONE'),(16021,'PRIVATE-B','Private B','ONE_ON_ONE')");
        jdbc.update("INSERT INTO enrollment_order (id,enrollment_no,student_id,order_status,tuition_subtotal_amount,original_amount,amount_due,balance_amount) VALUES (12006,'RECEIPT-PRIVATE-ORDER',11006,'CONFIRMED',1000,1000,1000,1000)");
        jdbc.update("INSERT INTO enrollment_order_item (id,enrollment_order_id,line_no,register_class,detail,course_type,course_product_id,one_on_one_units,price_amount) VALUES (13009,12006,1,'Private','Private A','ONE_ON_ONE',16020,20,1000)");
        jdbc.update("INSERT INTO course_package (id,package_no,enrollment_order_id,enrollment_order_item_id,student_id,course_product_id,package_name,teaching_mode,total_units,consumed_units,remaining_units,package_status) VALUES (20001,'PRIVATE-PACKAGE',12006,13009,11006,16020,'Private A','ONE_ON_ONE',20,4,16,'ACTIVE')");
        jdbc.update("INSERT INTO recurring_schedule_rule (id,package_id,rule_status,start_date) VALUES (21001,20001,'ACTIVE','2026-10-01')");
        jdbc.update("INSERT INTO recurring_schedule_slot (id,rule_id,weekday,start_time,end_time,planned_units,allocated_units,slot_status) VALUES (21002,21001,1,'09:00:00','11:00:00',4,16,'ACTIVE')");
        LocalDateTime future = AppTime.nowLocalDateTime().plusDays(7);
        jdbc.update("INSERT INTO lesson_occurrence (id,lesson_no,package_id,student_id,schedule_rule_id,schedule_slot_id,occurrence_type,planned_start_at,planned_end_at,planned_units,planning_status) VALUES (21003,'PRIVATE-FUTURE',20001,11006,21001,21002,'REGULAR',?,?,4,'SCHEDULED'),(21004,'PRIVATE-PAST',20001,11006,21001,21002,'REGULAR','2026-10-05 09:00:00','2026-10-05 11:00:00',4,'MERGED')", future, future.plusHours(2));
        jdbc.update("INSERT INTO lesson_record (id,occurrence_id,student_id,package_id,planned_start_at,result_status,confirm_stage) VALUES (21005,21004,11006,20001,'2026-10-05 09:00:00','COMPLETED','CONFIRMED')");
        RegistrationIntakeRequest.CourseItemPayload item = new RegistrationIntakeRequest.CourseItemPayload();
        item.setOrderItemId(13009L);
        item.setCourseProductId(16021L);
        item.setDetail("Private B");
        item.setCorrectionChangedFields(List.of("courseProductId", "detail"));
        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        request.setItems(List.of(item));
        service.correctOrder(12006L, request);

        assertThat(jdbc.queryForObject("SELECT planning_status FROM lesson_occurrence WHERE id=21003", String.class)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT rule_status FROM recurring_schedule_rule WHERE id=21001", String.class)).isEqualTo("INACTIVE");
        assertThat(jdbc.queryForObject("SELECT slot_status FROM recurring_schedule_slot WHERE id=21002", String.class)).isEqualTo("INACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_record WHERE id=21005 AND is_deleted=0", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT course_product_id FROM course_package WHERE id=20001", Long.class)).isEqualTo(16021L);
        assertThat(jdbc.queryForObject("SELECT consumed_units FROM course_package WHERE id=20001", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT remaining_units FROM course_package WHERE id=20001", Integer.class)).isEqualTo(16);
    }

    @Test
    void changingPrivateCourseDoesNotDiscardSubmittedFutureLesson() {
        assertThat(jdbc.queryForObject("SELECT @@port", Integer.class)).isEqualTo(13319);
        jdbc.update("INSERT INTO student (id,student_no,student_name) VALUES (11007,'RECEIPT-PENDING','Pending Test')");
        jdbc.update("INSERT INTO course_product (id,product_code,product_name,teaching_mode) VALUES (16022,'PENDING-A','Private A','ONE_ON_ONE'),(16023,'PENDING-B','Private B','ONE_ON_ONE')");
        jdbc.update("INSERT INTO enrollment_order (id,enrollment_no,student_id,order_status) VALUES (12007,'RECEIPT-PENDING-ORDER',11007,'CONFIRMED')");
        jdbc.update("INSERT INTO enrollment_order_item (id,enrollment_order_id,line_no,register_class,detail,course_type,course_product_id,one_on_one_units) VALUES (13010,12007,1,'Private','Private A','ONE_ON_ONE',16022,20)");
        jdbc.update("INSERT INTO course_package (id,package_no,enrollment_order_id,enrollment_order_item_id,student_id,course_product_id,package_name,teaching_mode,total_units,remaining_units,package_status) VALUES (20002,'PENDING-PACKAGE',12007,13010,11007,16022,'Private A','ONE_ON_ONE',20,20,'ACTIVE')");
        LocalDateTime future = AppTime.nowLocalDateTime().plusDays(7);
        jdbc.update("INSERT INTO lesson_occurrence (id,lesson_no,package_id,student_id,occurrence_type,planned_start_at,planned_end_at,planned_units,planning_status) VALUES (21006,'PENDING-FUTURE',20002,11007,'REGULAR',?,?,4,'SCHEDULED')", future, future.plusHours(2));
        jdbc.update("INSERT INTO lesson_record (id,occurrence_id,student_id,package_id,planned_start_at,result_status,confirm_stage) VALUES (21007,21006,11007,20002,?,'COMPLETED','PENDING_ADMIN')", future);
        RegistrationIntakeRequest.CourseItemPayload item = new RegistrationIntakeRequest.CourseItemPayload();
        item.setOrderItemId(13010L);
        item.setCourseProductId(16023L);
        item.setCorrectionChangedFields(List.of("courseProductId"));
        EnrollmentOrderCorrectionRequest request = new EnrollmentOrderCorrectionRequest();
        request.setItems(List.of(item));
        assertThatThrownBy(() -> service.correctOrder(12007L, request))
                .hasMessageContaining("existing attendance decision");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lesson_record WHERE id=21007 AND is_deleted=0", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT planning_status FROM lesson_occurrence WHERE id=21006", String.class)).isEqualTo("SCHEDULED");
    }
}
