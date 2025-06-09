package com.jb.ess.common.domain;

import lombok.Data;

@Data
public class Department {
    private String deptCode;        // 부서코드
    private String deptName;        // 부서명
    private String parentDept;      // 상위부서
    private String deptLeader;      // 부서장
    private String deptCategory;    // 부서구분
    private String startDate;       // 시작일자
    private String endDate;         // 종료일자
    private String useYn;           // 사용여부
    private String workPatternCode; // 근태패턴코드
    private int empCount;           // 부서별 사원수
    private String leaderName;      // 부서장 이름

    private String workPatternName; // 근태패턴명
}
