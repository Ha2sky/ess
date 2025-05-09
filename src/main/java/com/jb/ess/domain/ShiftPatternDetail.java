package com.jb.ess.domain;
import lombok.Data;

@Data
public class ShiftPatternDetail {
    private String shiftCode;     // 근태코드
    private Integer dayNum;       // 일자
    private String timeItemCode;  // 근태항목코드
}
