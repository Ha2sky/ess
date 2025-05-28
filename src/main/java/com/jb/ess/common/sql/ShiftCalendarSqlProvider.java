package com.jb.ess.common.sql;

import com.jb.ess.common.domain.ShiftCalendar;
import java.util.List;
import java.util.Map;

public class ShiftCalendarSqlProvider {
    @SuppressWarnings("unchecked")
    public String insertBatch(Map<String, Object> params) {
        List<ShiftCalendar> list = (List<ShiftCalendar>) params.get("list");

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO HRTSHIFTCALENDAR (WORK_PATTERN_CODE, SHIFT_DATE, SHIFT_CODE) VALUES ");

        for (int i = 0; i < list.size(); i++) {
            sb.append("(")
                .append("#{list[").append(i).append("].workPatternCode}, ")
                .append("#{list[").append(i).append("].shiftDate}, ")
                .append("#{list[").append(i).append("].shiftCode})");

            if (i < list.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }
}