package com.jb.ess.pattern.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShiftPatternDtlMapper {
    @Select("""
        SELECT SHIFT_CODE
        FROM HRTSHIFTPATTERNDTL
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
        AND DAY_OF_WEEK = #{dayOfWeek}
    """)
        /* 각 근태패턴의 요일별 근태코드 찾기 */
    String getShiftCodeByPatternAndDay(@Param("workPatternCode") String workPatternCode,
                                       @Param("dayOfWeek") int dayOfWeek);

    @Delete("""
        DELETE
        FROM HRTSHIFTPATTERNDTL
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
    """)
    /* 근태패턴 삭제 */
    void deletePatternDtl(@Param("workPatternCode") String workPatternCode);
}
