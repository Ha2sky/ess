package com.jb.ess.common.domain;

import lombok.Data;

@Data
public class ShiftCalendar {
    private String workPatternCode; // 근태패턴코드
    private String shiftDate;       // yyyymmdd
    private String shiftCode;       // 근태코드

    public ShiftCalendar(String workPatternCode, String shiftDate, String shiftCode) {
        this.workPatternCode = workPatternCode;
        this.shiftDate = shiftDate;
        this.shiftCode = shiftCode;
    }
}
