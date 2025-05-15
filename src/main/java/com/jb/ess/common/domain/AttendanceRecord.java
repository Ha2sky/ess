package com.jb.ess.common.domain;
import lombok.Data;

@Data
public class AttendanceRecord {
    private String recordId;     // 실적 ID
    private String empCode;      // 사원코드
    private String workDate;     // 근무일
    private String checkInTime;  // 출근시간
    private String checkOutTime; // 퇴근시간
}
