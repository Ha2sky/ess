package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.AnnualDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;

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

    // 연차 잔여량 업데이트
    @Update("""
        UPDATE HRTANNUALDETAIL 
        SET BALANCE_DAY = #{balanceDay}
        WHERE EMP_CODE = #{empCode}
    """)
    void updateBalanceDay(@Param("empCode") String empCode, @Param("balanceDay") BigDecimal balanceDay);

    @Update("""
        UPDATE HRTANNUALDETAIL 
        SET BALANCE_DAY = BALANCE_DAY - #{deductDays}
        WHERE EMP_CODE = #{empCode} 
        AND BALANCE_DAY >= #{deductDays}
    """)
    boolean updateBalanceDayWithCheck(@Param("empCode") String empCode,
                                      @Param("deductDays") BigDecimal deductDays);

    // USE_DAY 증가 메서드 추가
    @Update("""
        UPDATE HRTANNUALDETAIL 
        SET USE_DAY = USE_DAY + #{useDays}
        WHERE EMP_CODE = #{empCode}
    """)
    void updateUseDayIncrease(@Param("empCode") String empCode, @Param("useDays") BigDecimal useDays);
}
