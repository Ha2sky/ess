package com.jb.ess.domain;

import lombok.Data;

@Data
public class Department {
    private String deptCode;    // 부서코드
    private String deptName;    // 부서명
    private String parentDept;  // 상위부서
    private String deptLeader;  // 부서장
    private String deptCategory;// 부서구분
    private String startDate;   // 시작일자
    private String endDate;     // 종료일자
    private String useYn;       // 사용여부

    private int empCount;
}
