package com.jb.ess.common.domain;

import lombok.Data;

@Data
public class AttHistory {
    private String applyDate;    // 신청일
    private String targetDate;   // 대상일
    private String deptName;     // 부서명
    private String empCode;      // 대상자 사번
    private String empName;      // 대상자 이름
    private String applyType;    // 신청근태
    private String status;       // 상태

    private String targetEndDate;   // 대상종료일
}
