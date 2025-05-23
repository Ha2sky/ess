package com.jb.ess.common.domain;
import lombok.Data;

@Data
public class WorkEmpCallendar {
    private String txnId;            // 채번용
    private String empCode;          // 사원코드
    private String yyyyMmDd;         // 근무일자
    private String shiftCode;        // 근태코드
    private String workPatternCode;  // 근태패턴코드
    private String deptCode;         // 부서코드
    private String shiftCodeOrig;    // 기존 근태코드
    private String holidayYn;        // 휴일여부
}
