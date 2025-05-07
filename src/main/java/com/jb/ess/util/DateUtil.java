package com.jb.ess.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateUtil {
    private static final String MAX_DATE = "99991231";
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter OUTPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // yyyyMMdd → yyyy-MM-dd
    public static String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return "-";
        if (MAX_DATE.equals(dateStr)) return "-";

        try {
            LocalDate date = LocalDate.parse(dateStr, INPUT_FORMAT);
            return date.format(OUTPUT_FORMAT);
        } catch (Exception e) {
            return "";
        }
    }
}
