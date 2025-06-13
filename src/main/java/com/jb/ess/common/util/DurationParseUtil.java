package com.jb.ess.common.util;

import java.time.Duration;

public class DurationParseUtil {
    private DurationParseUtil() {}

    public static Duration parseHourStringToDuration(String hourString) {
        if (hourString == null || hourString.isEmpty()) return Duration.ZERO;
        double hours = Double.parseDouble(hourString);
        long minutes = Math.round(hours * 60);
        return Duration.ofMinutes(minutes);
    }
}