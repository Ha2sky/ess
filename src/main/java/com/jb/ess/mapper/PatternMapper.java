package com.jb.ess.mapper;

import com.jb.ess.domain.PatternDetail;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PatternMapper {
    @Select("""
        SELECT *
        FROM HRTSHIFTPATTERNDTL
    """)
    List<PatternDetail> findAllPatterns(); // 실제론 사용자별 패턴 필요
}
