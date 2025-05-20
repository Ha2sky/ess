package com.jb.ess.pattern.mapper;

import com.jb.ess.common.domain.ShiftCalendar;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShiftCalendarMapper {
    @Insert("""
        INSERT INTO HRTSHIFTCALENDAR (WORK_PATTERN_CODE, SHIFT_DATE, SHIFT_CODE)
        VALUES (#{workPatternCode}, #{shiftDate}, #{shiftCode})
    """)
    /* 캘린더 생성 */
    void insertShiftCalendar(ShiftCalendar shiftCalendar);

    @Delete("""
        DELETE
        FROM HRTSHIFTCALENDAR
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
    """)
    /* 근태패턴 삭제 */
    void deleteShiftCalendar(String workPatternCode);

    @Select("""
        SELECT COUNT(SHIFT_DATE)
        FROM HRTSHIFTCALENDAR
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
        AND SHIFT_DATE LIKE '${dateStr}%'
    """)
    int getCountShiftCalendar(@Param("workPatternCode") String workPatternCode,
                              @Param("dateStr") String dateStr);

    @Select("""
        SELECT SHIFT_CODE
        FROM HRTSHIFTCALENDAR
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
        AND SHIFT_DATE = #{dateStr}
    """)
    String getShiftCodeByPatternCodeAndDate(@Param("workPatternCode") String workPatternCode,
                                            @Param("dateStr") String dateStr);

    // 월 단위 삭제 (예: 202505, 202506 형태)
    @Delete("""
        DELETE
        FROM HRTSHIFTCALENDAR
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
        AND SHIFT_DATE LIKE '${month}%'
    """)
    void deleteShiftCalendarByMonth(@Param("workPatternCode") String workPatternCode,
        @Param("month") String month);
}
