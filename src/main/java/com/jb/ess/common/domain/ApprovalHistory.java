package com.jb.ess.common.domain;
import lombok.Data;


@Data
public class ApprovalHistory {
    private String histNo;         // 결재이력번호
    private String applyNo;        // 신청번호
    private String approverCode;   // 결재자 사원코드
    private String approvalDate;   // 결재일
    private String approvalStatus; // 결재상태
    private String rejectReason;   // 반려사유
}
