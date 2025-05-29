package com.jb.ess.common.domain;

import java.io.Serial;
import java.io.Serializable;
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
}
