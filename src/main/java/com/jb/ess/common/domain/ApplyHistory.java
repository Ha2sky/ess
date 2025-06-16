package com.jb.ess.common.domain;

import lombok.Data;

@Data
public class ApplyHistory {
    private String targetDate;   // 대상일

    private String empCode;      // 대상자 사번
    private String empName;      // 대상자 이름
    private String deptName;     // 부서명

    private String applyType;    // 신청근태
    private String status;       // 상태 (승인완료, 승인중, 반려)
    private String reason;       // 사유

    private String startTime;    // 시작시간
    private String endTime;      // 종료시간

    private String applyDateTime; // 신청시각

    private String targetEndDate;   // 대상종료일

    private String applyEmpCode;    // 신청자 사번
    private String applyEmpName;    // 신청자 이름
    private String applyDeptName;   // 신청자 부서

    private String targetEmpCode;   // 대상자 사번
    private String targetEmpName;   // 대상자 이름
    private String targetDeptName;  // 대상자 부서

    private String applyDate;   // 신청일
    private String shiftName;   // 계획근무

    private String overtime;    // 연장근무시간
    private String holiday;     // 휴일근로시간

    private String checkInTime;     // 출근 (HH:mm:SS)
    private String checkOutTime;    // 퇴근 (HH:mm:SS)

    private String applicantDeptName;   // 상신자 부서명
    private String applicantDutyName;   // 상신자 직책명
    private String applicantEmpName;    // 상신자 이름
    private String applicantEmpCode;    // 상신자 사번
    private String applyResult;         // 상신 결과

    private String approvalDeptName;    // 결재자 부서명
    private String approvalDutyName;    // 결재자 직책명
    private String approvalEmpName;     // 결재자 이름
    private String approvalEmpCode;     // 결재자 사번
    private String approvalResult;      // 결재 결과

    private String approvalDate;        // 결재일
}
