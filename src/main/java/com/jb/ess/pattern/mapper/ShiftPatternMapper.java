package com.jb.ess.pattern.mapper;

import com.jb.ess.common.domain.ShiftPattern;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShiftPatternMapper {

    @Select("""
        SELECT *
        FROM HRTSHIFTPATTERN
        WHERE #{workPatternCode} IS NULL OR WORK_PATTERN_NAME LIKE CONCAT('%', #{workPatternCode}, '%')
    """)
    /* 근태패턴명으로 근태패턴 검색 */
    List<ShiftPattern> findPatternsByCode(String workPatternCode);

    @Delete("""
        DELETE
        FROM HRTSHIFTPATTERN
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
    """)
    /* 근태패턴 삭제 */
    void deletePattern(String workPatternCode);

//    @Insert("""
//        INSERT INTO HRTSHIFTPATTERNDTL (
//            WORK_PATTERN_CODE,
//            WORK_PATTERN_NAME,
//            MON_SHIFT_CODE,
//            TUE_SHIFT_CODE,
//            WED_SHIFT_CODE,
//            THU_SHIFT_CODE,
//            FRI_SHIFT_CODE,
//            SAT_SHIFT_CODE,
//            SUN_SHIFT_CODE
//        )
//        VALUES (
//            #{workPatternCode},
//            #{workPatternName},
//            #{monShiftCode},
//            #{tueShiftCode},
//            #{wedShiftCode},
//            #{thuShiftCode},
//            #{friShiftCode},
//            #{satShiftCode},
//            #{sunShiftCode}
//        )
//    """)
//        /* 근태패턴 저장 */
//    void insertShiftPattern(PatternDetail pattern);
}
