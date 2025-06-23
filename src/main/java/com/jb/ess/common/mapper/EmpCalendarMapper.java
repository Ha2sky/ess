package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.EmpCalendar;
import com.jb.ess.common.sql.EmpCalendarSqlProvider;
import java.util.List;

import org.apache.ibatis.annotations.*;

@Mapper
public interface EmpCalendarMapper {
    @Select("""
        SELECT COUNT(YYYYMMDD)
        FROM HRTWORKEMPCALENDAR
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
        AND EMP_CODE = #{empCode}
        AND YYYYMMDD LIKE CONCAT(#{dateStr}, '%')
    """)
    int getCountEmpCalendar(@Param("workPatternCode") String workPatternCode,
                            @Param("dateStr") String dateStr,
                            @Param("empCode") String empCode);

    @InsertProvider(type = EmpCalendarSqlProvider.class, method = "insertBatch")
    void insertBatch(@Param("list") List<EmpCalendar> empCalendars);

    @Delete("""
        DELETE
        FROM HRTWORKEMPCALENDAR
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
        AND YYYYMMDD LIKE CONCAT(#{dateStr}, '%')
    """)
        /* 월 단위 삭제 (예: 202505, 202506 형태) */
    void deleteEmpCalendarForUpdate(@Param("workPatternCode") String workPatternCode,
                                    @Param("dateStr") String dateStr);

    @Delete("""
        DELETE
        FROM HRTWORKEMPCALENDAR
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
    """)
        /* workPatternCode 데이터 모두 삭제 */
    void deleteEmpCalendar(String workPatternCode);

    @Delete("""
        DELETE
        FROM HRTWORKEMPCALENDAR
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
        AND DEPT_CODE = #{deptCode}
    """)
    void deleteEmpCalendarByDeptCode(@Param("workPatternCode") String workPatternCode,
                                     @Param("deptCode") String deptCode);

    @Delete("""
        DELETE
        FROM HRTWORKEMPCALENDAR
        WHERE DEPT_CODE = #{deptCode}
        AND EMP_CODE = #{empCode}
    """)
    void deleteEmpCalendarByEmpCode(@Param("deptCode") String deptCode,
                                    @Param("empCode") String empCode);

    @Select("""
        SELECT HOLIDAY_YN
        FROM HRTWORKEMPCALENDAR
        WHERE EMP_CODE = #{empCode}
        AND YYYYMMDD = #{date}
    """)
    String getHolidayYnByEmpCodeAndDate(@Param("empCode") String empCode,
                                        @Param("date") String date);

    @Select("""
        SELECT SHIFT_CODE
        FROM HRTWORKEMPCALENDAR
        WHERE EMP_CODE = #{empCode}
        AND YYYYMMDD = #{date}
    """)
    String findShiftCodeByEmpCodeAndDate(@Param("empCode") String empCode,
                                         @Param("date") String date);

    @Select("""
        SELECT *
        FROM HRTWORKEMPCALENDAR
        WHERE EMP_CODE = #{empCode}
        AND YYYYMMDD = #{date}
    """)
    EmpCalendar getCodeAndHolidayByEmpCodeAndDate(@Param("empCode") String empCode,
                                                  @Param("date") String date);

    @Update("""
        UPDATE HRTWORKEMPCALENDAR
        SET SHIFT_CODE = #{shiftCode}
        WHERE EMP_CODE = #{empCode}
        AND YYYYMMDD = #{workDate}
    """)
    void updateShiftCodeByEmpCodeAndDate(@Param("empCode") String empCode,
                                         @Param("workDate") String workDate,
                                         @Param("shiftCode") String shiftCode);

    @Select("""
        SELECT SHIFT_NAME
        FROM HRTSHIFTMASTER
        WHERE SHIFT_CODE IN (
            SELECT DISTINCT SHIFT_CODE_ORIG
            FROM HRTWORKEMPCALENDAR
            WHERE DEPT_CODE = #{deptCode}
            AND YYYYMMDD = #{workDate})
    """)
    List<String> getShiftCodeByDeptCodeAndWorkDate(@Param("deptCode") String deptCode,
                                                   @Param("workDate") String workDate);

    // 수정: 기타근태 날짜 범위 검증을 위한 휴일 정보 조회 메서드 추가
    @Select("""
        SELECT SHIFT_CODE, HOLIDAY_YN
        FROM HRTWORKEMPCALENDAR
        WHERE YYYYMMDD = #{date}
        GROUP BY SHIFT_CODE, HOLIDAY_YN
    """)
    List<EmpCalendar> getHolidayInfoByDate(@Param("date") String date);
}
