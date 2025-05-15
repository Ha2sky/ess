package com.jb.ess.common.domain;
import lombok.Data;

@Data
public class AttendanceApply {
    private String applyNo;       // 신청코드
    private String empCode;       // 신청대상자 사원코드
    private String timeItemCode;  // 근태항목코드
    private String applyDate;     // 신청일자
    private String targetDate;    // 신청하려는 근무일
    private String startTime;     // 신청 시작시간
    private String endTime;       // 신청 종료시간
    private String applyType;     // 신청유형
    private String status;        // 신청상태
    private String deptCode;      // 부서코드
    private String applicantCode; // 신청자 사원 코드
}
