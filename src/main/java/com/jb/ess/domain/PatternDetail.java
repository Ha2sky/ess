package com.jb.ess.domain;

import lombok.Data;

@Data
// HRTSHIFTPATTERNDTL
public class PatternDetail {
    private String workPatternCode; // 근태패턴코드
    private String workPatternName; // 근태패턴명
    private String monShiftCode;    // 월요일 근태코드
    private String monShiftName;    // 월요일 근태명
    private String tueShiftCode;    // 화요일 근태코드
    private String tueShiftName;    // 화요일 근태명
    private String wedShiftCode;    // 수요일 근태코드
    private String wedShiftName;    // 수요일 근태명
    private String thuShiftCode;    // 목요일 근태코드
    private String thuShiftName;    // 목요일 근태명
    private String friShiftCode;    // 금요일 근태코드
    private String friShiftName;    // 금요일 근태명
    private String satShiftCode;    // 토요일 근태코드
    private String satShiftName;    // 토요일 근태명
    private String sunShiftCode;    // 일요일 근태코드
    private String sunShiftName;    // 일요일 근태명
}
