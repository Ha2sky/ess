package com.jb.ess.common.domain;
import lombok.Data;

@Data
public class GradeInfo {
    private String positionCode; // 직위
    private String positionName; // 직위명
    private String useYn;        // 사용여부
}
