package com.jb.diagram;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "HRTANNUALDETAIL")
@Getter @Setter
@NoArgsConstructor
public class HrtAnnualDetail {
    @Id
    @OneToOne
    @JoinColumn(name = "EMP_CODE")
    private HriMaster hriMaster;

    @ManyToOne
    @JoinColumn(name = "POSITION_CODE")
    private HrtGradeInfo hrtGradeInfo;

    @ManyToOne
    @JoinColumn(name = "DEPT_CODE")
    private OrgDeptMaster orgDeptMaster;

    @Column(name = "ANNUAL_START_DATE", length = 8)
    private String annualStartDate;

    @Column(name = "ANNUAL_END_DATE", length = 8)
    private String annualEndDate;

    @Column(name = "TOTAL_WORK_DAY", precision = 19, scale = 5)
    private BigDecimal totalWorkDay = new BigDecimal("365");

    @Column(name = "REAL_WORK_DAY", precision = 19, scale = 5)
    private BigDecimal realWorkDay;

    @Column(name = "TOT_DAY", precision = 19, scale = 5)
    private BigDecimal totDay;

    @Column(name = "USE_DAY", precision = 19, scale = 5)
    private BigDecimal useDay;

    @Column(name = "BALANCE_DAY", precision = 19, scale = 5)
    private BigDecimal balanceDay;
}
