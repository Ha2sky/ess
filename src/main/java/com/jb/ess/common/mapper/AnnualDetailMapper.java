package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.AnnualDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AnnualDetailMapper {

    // 사원별 연차정보 조회
    @Select("""
        SELECT TOP 1 EMP_CODE, POSITION_CODE, DEPT_CODE, ANNUAL_START_DATE, 
               ANNUAL_END_DATE, TOTAL_WORK_DAY, REAL_WORK_DAY, TOT_DAY, 
               USE_DAY, BALANCE_DAY
        FROM HRTANNUALDETAIL 
        WHERE EMP_CODE = #{empCode}
        ORDER BY ANNUAL_START_DATE DESC
    """)
    AnnualDetail findByEmpCode(String empCode);
}
