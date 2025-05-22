package com.jb.ess.pattern.mapper;

import com.jb.ess.common.domain.ShiftPattern;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShiftPatternMapper {
    @Select("""
        SELECT *
        FROM HRTSHIFTPATTERN
    """)
    /* 모든 근태패턴 검색 */
    List<ShiftPattern> findAllPatterns();

    @Select("""
        SELECT *
        FROM HRTSHIFTPATTERN
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
    """)
    /* 근태패턴코드로 근태패턴 모두 검색 */
    List<ShiftPattern> findPatternsByCode(String workPatternCode);

    @Select("""
        SELECT *
        FROM HRTSHIFTPATTERN
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
    """)
        /* 근태패턴코드로 근태패턴 검색 */
    ShiftPattern findPatternByCode(String workPatternCode);

    @Delete("""
        DELETE
        FROM HRTSHIFTPATTERN
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
    """)
    /* 근태패턴 삭제 */
    void deletePattern(String workPatternCode);

    @Insert("""
        INSERT
        INTO HRTSHIFTPATTERN (WORK_PATTERN_CODE,
                              WORK_PATTERN_NAME,
                              TOTAL_WORKING_HOURS,
                              USE_YN,
                              CREATE_AT)
        VALUES (#{workPatternCode},
                #{workPatternName},
                #{totalWorkingHours},
                #{useYn},
                #{createAt})
    """)
    /* 근태패턴 저장 */
    void insertShiftPattern(ShiftPattern shiftPattern);
}
