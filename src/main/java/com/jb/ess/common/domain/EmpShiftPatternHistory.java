package com.jb.ess.common.domain;
import lombok.Data;

@Data
public class EmpShiftPatternHistory {
    private String empPatternId;         // 사원 근무패턴 이력 ID
    private String empCode;              // 사원코드
    private String deptCode;             // 적용당시 부서코드
    private String startDate;            // 적용시작일
    private String endDate;              // 적용종료일
    private String changeReason;         // 변경사유
    private String createdAt;            // 등록일자
    private String createdBy;            // 등록자 사번
}
