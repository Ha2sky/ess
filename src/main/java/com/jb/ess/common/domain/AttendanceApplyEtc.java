package com.jb.ess.common.domain;
import lombok.Data;

@Data
public class AttendanceApplyEtc {
    private String applyEtcNo;         // 신청코드
    private String empCode;         // 신청대상자 사원코드
    private String shiftCode;       // 근태코드
    private String applyDate;       // 신청일자
    private String targetStartDate; // 신청시작일
    private String targetEndDate;   // 신청종료일
    private String applyDateTime;   // 신청시각
    private String reason;          // 사유
    private String status;          // 신청상태
    private String deptCode;        // 부서코드
    private String applicantCode;   // 신청자 사원 코드
}
