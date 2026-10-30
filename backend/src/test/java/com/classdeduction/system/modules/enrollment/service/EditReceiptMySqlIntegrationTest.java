package com.classdeduction.system.modules.enrollment.service;

import com.classdeduction.system.modules.enrollment.dto.EnrollmentOrderCorrectionRequest;
import com.classdeduction.system.modules.enrollment.dto.RegistrationIntakeRequest;
import com.classdeduction.system.modules.enrollment.mapper.EnrollmentOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
