package com.jb.ess.common.sql;

import com.jb.ess.common.domain.EmpCalendar;
import java.util.List;
import java.util.Map;

public class EmpCalendarSqlProvider {
    @SuppressWarnings("unchecked")
    public String insertBatch(Map<String, Object> params) {
        List<EmpCalendar> list = (List<EmpCalendar>) params.get("list");

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO HRTWORKEMPCALENDAR (WORK_PATTERN_CODE, YYYYMMDD, SHIFT_CODE, SHIFT_CODE_ORIG, EMP_CODE, DEPT_CODE, HOLIDAY_YN) VALUES ");

        for (int i = 0; i < list.size(); i++) {
            sb.append("(")
                .append("#{list[").append(i).append("].workPatternCode}, ")
                .append("#{list[").append(i).append("].yyyymmdd}, ")
                .append("#{list[").append(i).append("].shiftCode}, ")
                .append("#{list[").append(i).append("].shiftCodeOrig}, ")
                .append("#{list[").append(i).append("].empCode}, ")
                .append("#{list[").append(i).append("].deptCode}, ")
                .append("#{list[").append(i).append("].holidayYn})");

            if (i < list.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }
}