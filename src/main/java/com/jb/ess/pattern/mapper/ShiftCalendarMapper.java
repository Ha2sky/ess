package com.jb.ess.pattern.mapper;

import com.jb.ess.common.domain.ShiftCalendar;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ShiftCalendarMapper {
    @Insert("""
        INSERT INTO HRTSHIFTCALENDAR (WORK_PATTERN_CODE, SHIFT_DATE, SHIFT_CODE)
        VALUES (#{workPatternCode}, #{shiftDate}, #{shiftCode})
    """)
    /* 캘린더 생성 */
    void insertShiftCalendar(ShiftCalendar shiftCalendar);
}
