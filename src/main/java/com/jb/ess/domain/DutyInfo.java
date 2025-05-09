package com.jb.ess.domain;
import lombok.Data;

@Data
public class DutyInfo {
    private String dutyCode; // 직책코드
    private String dutyName; // 직책명
    private String useYn;    // 사용여부
}
