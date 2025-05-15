package com.jb.ess.common.domain;
import lombok.Data;

@Data
public class TimeItem {
    private String timeItemCode;  // 근태항목코드
    private String timeItemName;  // 근태항목명
    private String memo;          // 메모
    private String useYn;         // 사용여부
}
