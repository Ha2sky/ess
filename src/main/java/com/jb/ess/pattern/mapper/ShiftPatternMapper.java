package com.jb.ess.pattern.mapper;

import com.jb.ess.common.domain.ShiftPattern;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShiftPatternMapper {

    @Select("""
        SELECT *
        FROM HRTSHIFTPATTERN
        WHERE #{patternName} IS NULL OR WORK_PATTERN_NAME LIKE CONCAT('%', #{patternName}, '%')
    """)
    /* 근태패턴명으로 근태패턴 검색 */
    List<ShiftPattern> findPatternsByName(@Param("patternName") String patternName);
}
