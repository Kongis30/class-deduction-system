package com.classdeduction.system.modules.enrollment.util;

import com.classdeduction.system.common.time.AppTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BusinessNoGenerator.
 */
class BusinessNoGeneratorTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static String todayInAppZone() {
        return LocalDate.now(AppTime.APP_ZONE_ID).format(DATE_FORMATTER);
    }

    @Test
    @DisplayName("Should generate enrollment number with correct format")
    void shouldGenerateEnrollmentNumberWithCorrectFormat() {
        // When
        String enrollmentNo = BusinessNoGenerator.generateEnrollmentNo();

        // Then
        assertThat(enrollmentNo).isNotNull();
        assertThat(enrollmentNo).startsWith("EO");
        assertThat(enrollmentNo).hasSize(2 + 8 + 4); // EO + yyyyMMdd + 4 digits
        assertThat(enrollmentNo.substring(2, 10)).isEqualTo(todayInAppZone());
    }

    @Test
    @DisplayName("Should generate package number with correct format")
    void shouldGeneratePackageNumberWithCorrectFormat() {
        // When
        String packageNo = BusinessNoGenerator.generatePackageNo();

        // Then
        assertThat(packageNo).isNotNull();
        assertThat(packageNo).startsWith("PK");
        assertThat(packageNo).hasSize(2 + 8 + 4); // PK + yyyyMMdd + 4 digits
        assertThat(packageNo.substring(2, 10)).isEqualTo(todayInAppZone());
    }

    @Test
    @DisplayName("Should generate payment number with correct format")
    void shouldGeneratePaymentNumberWithCorrectFormat() {
        // When
        String paymentNo = BusinessNoGenerator.generatePaymentNo();

        // Then
        assertThat(paymentNo).isNotNull();
        assertThat(paymentNo).startsWith("PMT");
        assertThat(paymentNo).hasSize(3 + 8 + 4); // PMT + yyyyMMdd + 4 digits
        assertThat(paymentNo.substring(3, 11)).isEqualTo(todayInAppZone());
    }

    @Test
    @DisplayName("Should generate receipt number with correct format")
    void shouldGenerateReceiptNumberWithCorrectFormat() {
        // When
        String receiptNo = BusinessNoGenerator.generateReceiptNo();

        // Then
        assertThat(receiptNo).isNotNull();
        assertThat(receiptNo).startsWith("RCP");
        assertThat(receiptNo).hasSize(3 + 8 + 4); // RCP + yyyyMMdd + 4 digits
        assertThat(receiptNo.substring(3, 11)).isEqualTo(todayInAppZone());
    }

    @Test
    @DisplayName("Should generate unique numbers across multiple calls")
    void shouldGenerateUniqueNumbersAcrossMultipleCalls() {
        // Given
        Set<String> generatedNumbers = new HashSet<>();
        int totalToGenerate = 100;

        // When
        for (int i = 0; i < totalToGenerate; i++) {
            generatedNumbers.add(BusinessNoGenerator.generateEnrollmentNo());
        }

        // Then
        assertThat(generatedNumbers).hasSize(totalToGenerate);
    }

    @Test
    @DisplayName("Generated number sequence should be within valid range")
    void generatedNumberSequenceShouldBeWithinValidRange() {
        // When
        String enrollmentNo = BusinessNoGenerator.generateEnrollmentNo();

        // Then - Extract the sequence part and verify it's 4 digits
        String sequenceStr = enrollmentNo.substring(10);
        assertThat(sequenceStr).hasSize(4);
        int sequence = Integer.parseInt(sequenceStr);
        assertThat(sequence).isBetween(0, 9999);
    }
}
