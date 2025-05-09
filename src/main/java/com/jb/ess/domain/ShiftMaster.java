package com.jb.ess.domain;
import lombok.Data;

@Data
public class ShiftMaster {
    private String shiftCode;           // 근태코드
    private String shiftName;           // 근태명
    private String timeItemCode;        // 근태항목코드
    private String workOnDayType;       // 출근타입
    private String workOnHhmm;          // 출근시간
    private String workOffDayType;      // 퇴근타입
    private String workOffHhmm;         // 퇴근시간
    private String break1StartDayType;  // 휴게1시작타입
    private String break1StartHhmm;     // 휴게1시작시간
    private String break1EndDayType;    // 휴게1종료타입
    private String break1EndHhmm;       // 휴게1종료시간
    private String break2StartDayType;  // 휴게2시작타입
    private String break2StartHhmm;     // 휴게2시작시간
    private String break2EndDayType;    // 휴게2종료타입
    private String break2EndHhmm;       // 휴게2종료시간
    private String overStartDayType;    // 연장근무 시작타입
    private String overStartHhmm;       // 연장근무 시작시간
    private String overEndDayType;      // 연장근무 종료타입
    private String overEndHhmm;         // 연장근무 종료타입
    private String over2StartDayType;   // 조출연장 시작타입
    private String over2StartHhmm;      // 조출연장 시작시간
    private String over2EndDayType;     // 조출연장 종료타입
    private String over2EndHhmm;        // 조출연장 종료시간
    private String memo;                // 메모
    private String useYn;               // 사용여부
    private String workTypeCode;        // 근무유형
    private String workDayType;         // 근무타입
}
