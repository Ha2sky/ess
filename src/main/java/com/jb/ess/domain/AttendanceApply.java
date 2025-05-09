package com.jb.ess.domain;
import lombok.Data;

@Data
public class AttendanceApply {
    private String applyNo;    // 신청코드
    private String empCode;    // 신청 사원코드
    private String shiftCode;  // 신청 패턴코드
    private String applyDate;  // 신청일자
    private String targetDate; // 신청하려는 근무일
    private String startTime;  // 신청 시작시간
    private String endTime;    // 신청 종료시간
    private String applyType;  // 신청유형
    private String status;     // 신청상태
}
