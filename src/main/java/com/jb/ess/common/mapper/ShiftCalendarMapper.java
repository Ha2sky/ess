package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.ShiftCalendar;
import com.jb.ess.common.sql.ShiftCalendarSqlProvider;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShiftCalendarMapper {
    @InsertProvider(type = ShiftCalendarSqlProvider.class, method = "insertBatch")
    void insertBatch(@Param("list") List<ShiftCalendar> shiftCalendars);

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
        AND SHIFT_DATE LIKE CONCAT(#{dateStr}, '%')
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


    @Delete("""
        DELETE
        FROM HRTSHIFTCALENDAR
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
        AND SHIFT_DATE LIKE CONCAT(#{month}, '%')
    """)
    /* 월 단위 삭제 (예: 202505, 202506 형태) */
    void deleteShiftCalendarByMonth(@Param("workPatternCode") String workPatternCode,
                                    @Param("month") String month);
}
