package com.classdeduction.system.modules.enrollment.util;

import com.classdeduction.system.common.time.AppTime;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Business number generator utility for enrollment module.
 * Generates unique business numbers with format: PREFIX + yyyyMMdd + 4-digit sequence.
 */
public final class BusinessNoGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);

    /**
     * Initialize the sequence from a persistent counter (e.g. max sequence from DB).
     * Call at startup to avoid collisions after restart.
     */
    public static void initSequence(int currentMax) {
        SEQUENCE.set(currentMax);
    }

    private BusinessNoGenerator() {
        // Utility class, no instantiation
    }

    /**
     * Generate a unique enrollment order number.
     * Format: EO + yyyyMMdd + 4-digit sequence
     */
    public static String generateEnrollmentNo() {
        return generate("EO");
    }

    /**
     * Generate a unique course package number.
     * Format: PK + yyyyMMdd + 4-digit sequence
     */
    public static String generatePackageNo() {
        return generate("PK");
    }

    /**
     * Generate a unique payment record number.
     * Format: PMT + yyyyMMdd + 4-digit sequence
     */
    public static String generatePaymentNo() {
        return generate("PMT");
    }

    /**
     * Generate a unique receipt record number.
     * Format: RCP + yyyyMMdd + 4-digit sequence
     */
    public static String generateReceiptNo() {
        return generate("RCP");
    }

    /**
     * Generate a unique student number.
     * Format: STU + yyyyMMdd + 4-digit sequence
     */
    public static String generateStudentNo() {
        return generate("STU");
    }

    /**
     * Generate a unique business number with given prefix.
     */
    private static String generate(String prefix) {
        String dateStr = LocalDate.now(AppTime.APP_ZONE_ID).format(DATE_FORMATTER);
        int seq = SEQUENCE.updateAndGet(s -> {
            if (s >= 9999) {
                return 1;
            }
            return s + 1;
        });
        return String.format("%s%s%04d", prefix, dateStr, seq);
    }
}
