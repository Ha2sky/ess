package com.jb.ess.common.domain;

import lombok.Data;

@Data
public class EmpCalendar {
    private int txnId;              // 채번용
    private String empCode;         // 사원번호
    private String yyyymmdd;        // 날짜
    private String shiftCode;       // 근태코드
    private String workPatternCode; // 근태패턴코드
    private String shiftCodeOrig;   // 기존근태코드
    private String holidayYn;       // 휴일여부
    private String deptCode;        // 부서코드

    public EmpCalendar(String workPatternCode, String yyyymmdd, String shiftCode,
                       String empCode, String deptCode, String holidayYn) {
        this.workPatternCode = workPatternCode;
        this.yyyymmdd = yyyymmdd;
        this.shiftCode = shiftCode;
        this.empCode = empCode;
        this.deptCode = deptCode;
        this.holidayYn = holidayYn;
    }
}
