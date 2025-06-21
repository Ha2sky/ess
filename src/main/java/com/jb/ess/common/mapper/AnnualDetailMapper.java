package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.AnnualDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;

@Mapper
public interface AnnualDetailMapper {

    // 🔧 기본 사원별 연차정보 조회
    @Select("""
        SELECT TOP 1 EMP_CODE, POSITION_CODE, DEPT_CODE, ANNUAL_START_DATE, 
               ANNUAL_END_DATE, TOTAL_WORK_DAY, REAL_WORK_DAY, TOT_DAY, 
               USE_DAY, BALANCE_DAY
        FROM HRTANNUALDETAIL 
        WHERE EMP_CODE = #{empCode}
        ORDER BY ANNUAL_START_DATE DESC
    """)
    AnnualDetail findByEmpCode(String empCode);

    // 🔧 수정: 강제 재조회 - 조회 시에도 정확한 계산 보장
    @Select("""
        SELECT TOP 1 EMP_CODE, POSITION_CODE, DEPT_CODE, ANNUAL_START_DATE, 
               ANNUAL_END_DATE, TOTAL_WORK_DAY, REAL_WORK_DAY, TOT_DAY, 
               USE_DAY, 
               CASE 
                   WHEN (TOT_DAY - USE_DAY) != BALANCE_DAY THEN (TOT_DAY - USE_DAY)
                   ELSE BALANCE_DAY 
               END AS BALANCE_DAY
        FROM HRTANNUALDETAIL 
        WHERE EMP_CODE = #{empCode}
        ORDER BY ANNUAL_START_DATE DESC
    """)
    AnnualDetail findByEmpCodeForceRefresh(String empCode);

    // 🔧 기본 연차 잔여량 체크 후 차감
    @Update("""
        UPDATE HRTANNUALDETAIL 
        SET BALANCE_DAY = BALANCE_DAY - #{deductDays}
        WHERE EMP_CODE = #{empCode} 
        AND BALANCE_DAY >= #{deductDays}
    """)
    boolean updateBalanceDayWithCheck(@Param("empCode") String empCode,
                                      @Param("deductDays") BigDecimal deductDays);

    // 🔧 수정: 울트라 정확한 연차 잔여량 차감 - 트랜잭션 안전성 강화
    @Update("""
        UPDATE HRTANNUALDETAIL 
        SET BALANCE_DAY = CASE 
            WHEN BALANCE_DAY >= #{deductDays} THEN BALANCE_DAY - #{deductDays}
            ELSE BALANCE_DAY
        END
        WHERE EMP_CODE = #{empCode} 
        AND BALANCE_DAY >= #{deductDays}
    """)
    boolean updateBalanceDayWithCheckUltra(@Param("empCode") String empCode,
                                           @Param("deductDays") BigDecimal deductDays);

    // 🔧 기본 USE_DAY 증가
    @Update("""
        UPDATE HRTANNUALDETAIL 
        SET USE_DAY = USE_DAY + #{useDays}
        WHERE EMP_CODE = #{empCode}
    """)
    void updateUseDayIncrease(@Param("empCode") String empCode, @Param("useDays") BigDecimal useDays);

    // 🔧 수정: 울트라 정확한 USE_DAY 증가 - 계산 정확성 보장
    @Update("""
        UPDATE HRTANNUALDETAIL 
        SET USE_DAY = USE_DAY + #{useDays},
            BALANCE_DAY = TOT_DAY - (USE_DAY + #{useDays})
        WHERE EMP_CODE = #{empCode}
    """)
    void updateUseDayIncreaseUltra(@Param("empCode") String empCode, @Param("useDays") BigDecimal useDays);

    // 🔧 추가: 강제 재계산 - 계산 오류 발생 시 정확한 값으로 수정
    @Update("""
        UPDATE HRTANNUALDETAIL 
        SET BALANCE_DAY = #{expectedBalance},
            USE_DAY = #{expectedUse}
        WHERE EMP_CODE = #{empCode}
    """)
    void forceRecalculateAnnual(@Param("empCode") String empCode,
                                @Param("expectedBalance") BigDecimal expectedBalance,
                                @Param("expectedUse") BigDecimal expectedUse);
}
