package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.EmpCalendar;
import com.jb.ess.common.sql.EmpCalendarSqlProvider;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.InsertProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
