package com.jb.ess.common.domain;

import com.jb.ess.common.util.DateUtil;
import lombok.Data;

@Data
/* HRTSHIFTPATTERN */
public class ShiftPattern {
    private String workPatternCode; // 근태패턴코드
    private String workPatternName; // 근태패턴명
    private String useYn = "Y";     // 사용여부
    private String createAt = DateUtil.getDateTimeNow(); // 생성날짜
    private String totalWorkingHours;  // 총 근무시간
}
