package com.classdeduction.system.modules.enrollment.util;

import com.classdeduction.system.common.time.AppTime;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Business number generator utility for enrollment module.
 * Generates unique business numbers with format: PREFIX + yyyyMMdd + 4-digit sequence.
 * Each prefix maintains its own independent sequence to avoid collisions.
 */
public final class BusinessNoGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Map<String, AtomicInteger> SEQUENCES = new ConcurrentHashMap<>();

    private BusinessNoGenerator() {
        // Utility class, no instantiation
    }

    /**
     * Initialize the sequence for a specific prefix from a persistent counter.
     * Call at startup to avoid collisions after restart.
     */
    public static void initSequence(String prefix, int currentMax) {
        SEQUENCES.put(prefix, new AtomicInteger(currentMax));
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
     * Generate a unique business number with given prefix.
     */
    private static String generate(String prefix) {
        String dateStr = LocalDate.now(AppTime.APP_ZONE_ID).format(DATE_FORMATTER);
        AtomicInteger seq = SEQUENCES.computeIfAbsent(prefix, k -> new AtomicInteger(0));
        int current = seq.updateAndGet(s -> {
            if (s >= 9999) {
                return 1;
            }
            return s + 1;
        });
        return String.format("%s%s%04d", prefix, dateStr, current);
    }
}
