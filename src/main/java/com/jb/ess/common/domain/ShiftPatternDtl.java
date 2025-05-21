package com.jb.ess.common.domain;

import lombok.Data;

@Data
/* HRTSHIFTPATTERNDTL */
public class ShiftPatternDtl {
    private String workPatternCode; // 근태패턴코드
    private String shiftCode;       // 근태코드
    private int dayOfWeek;          // 요일 (1: 월, 2: 화 ... 7: 금)
    private int totalWorkingHours;  // 총근무시간
}
