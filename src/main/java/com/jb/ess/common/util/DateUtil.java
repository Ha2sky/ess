package com.jb.ess.common.util;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DateUtil {
    private DateUtil() {}
    private static final String MAX_DATE = "99991231";
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter OUTPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /* yyyyMMdd → yyyy-MM-dd */
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

    /* 1일 2일 -> 1일(월) 2일(화) */
    public static List<String> getDateHeaders(YearMonth month) {
        List<String> headers = new ArrayList<>();
        int daysInMonth = month.lengthOfMonth();

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = month.atDay(day);
            String dayOfWeek = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN); // 월, 화, 수
            headers.add(day + "일(" + dayOfWeek + ")");
        }

        return headers;
    }

    /* 현재시각 String 변환 */
    public static String getDateTimeNow() {
        return LocalDate.now().format(INPUT_FORMAT);
    }
}
