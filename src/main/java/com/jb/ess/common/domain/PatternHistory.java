package com.jb.ess.common.domain;
import lombok.Data;

@Data
public class PatternHistory {
    private String histId;          // 사원 근무패턴 이력 ID
    private String workPatternCode; // 근태패턴코드
    private String actionType;      // INSERT, UPDATE, DELETE
    private String beforeName;      // 변경전패턴명
    private String afterName;       // 변경후패턴명
    private String changedAt;       // 변경일
}
