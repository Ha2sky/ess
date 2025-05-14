package com.jb.ess.mapper;

import com.jb.ess.domain.PatternDetail;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PatternMapper {
    @Select("""
        SELECT *
        FROM HRTSHIFTPATTERNDTL
    """)
    /* 모든 근태패턴 조회 */
    List<PatternDetail> findAllPatterns();

    @Insert("""
        INSERT INTO HRTSHIFTPATTERNDTL (
            WORK_PATTERN_CODE,
            WORK_PATTERN_NAME,
            MON_SHIFT_CODE,
            TUE_SHIFT_CODE,
            WED_SHIFT_CODE,
            THU_SHIFT_CODE,
            FRI_SHIFT_CODE,
            SAT_SHIFT_CODE,
            SUN_SHIFT_CODE
        )
        VALUES (
            #{workPatternCode},
            #{workPatternName},
            #{monShiftCode},
            #{tueShiftCode},
            #{wedShiftCode},
            #{thuShiftCode},
            #{friShiftCode},
            #{satShiftCode},
            #{sunShiftCode}
        )
    """)
    /* 근태패턴 저장 */
    void insertShiftPattern(PatternDetail pattern);
}
