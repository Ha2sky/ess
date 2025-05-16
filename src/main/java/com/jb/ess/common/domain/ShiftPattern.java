package com.jb.ess.common.domain;

import lombok.Data;

@Data
/* HRTSHIFTPATTERN */
public class ShiftPattern {
    private String workPatternCode; // 근태패턴코드
    private String workPatternName; // 근태패턴명
    private String useYn;           // 사용여부
    private String createAt;        // 생성날짜
}
