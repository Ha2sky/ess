package com.jb.ess.common.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;

@Data
public class Employee implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String empCode;         // 사원번호
    private String password;        // 비밀번호
    private String empName;         // 이름
    private String socialNum;       // 주민번호
    private String gender;          // 성별
    private String enterDate;       // 입사일
    private String positionCode;    // 직위
    private String positionDate;    // 직위일자
    private String dutyCode;        // 직책
    private String dutyDate;        // 직책일자
    private String empState;        // 재직구분
    private String deptCode;        // 부서코드
    private String retireDate;      // 퇴사일
    private String retireReason;    // 퇴사사유

    private String positionName;  // 직위명
    private String dutyName;      // 직책명
    private String isHeader;      // 부서장(Y/N)

    private String deptName;      // 부서이름

    private String workHours;       // 주 근무시간
    private String remainWorkHours; // 잔여 근무시간

    private String workDate;        // 근무일(yyyyMMdd)
    private String checkInTime;     // 출근시간(HHmmSS)
    private String checkOutTime;    // 퇴근시간(HHmmSS)
    private String checkInDayType;  // 출근타입(N0, N1)
    private String checkOutDayType; // 퇴근타입(N0, N1)

    private String shiftCode;       // 근태코드
    private String shiftCodeOrig;   // 근태코드 변경전
    private String holidayYn;       // 휴일여부
    private String timeItemCode;    // 가근태코드(TIME_ITEM_CODE)

    private String shiftOrigName;   // 계획근태명
    private String shiftName;       // 실적근태명
    private List<String> timeItemNames; // 가근태명

    private String applyGeneralNo;      // 일반근태 신청번호
    private String generalApplyStatus;  // 일반근태 신청상태
    private String applyEtcNo;          // 기타근태 신청번호
    private String etcApplyStatus;      // 기타근태 신청상태

    private String overTime;    // 연장근무 시간
    private String overTime2;   // 조출연장 시간
}
