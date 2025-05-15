package com.jb.ess.common.domain;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class AnnualDetail {
    private String empCode;          // 사번
    private String positionCode;     // 직위
    private String deptCode;         // 부서코드
    private String annualStartDate;  // 연차 시작일
    private String annualEndDate;    // 연차 종료일
    private BigDecimal totalWorkDay; // 총 근무 가능일
    private BigDecimal realWorkDay;  // 총 근무일 ( 총 근무 가능일 - 휴일)
    private BigDecimal totDay;       // 총 연차 생성일
    private BigDecimal useDay;       // 총 연차 사용일
    private BigDecimal balanceDay;   // 현재 보유 연차일
}
