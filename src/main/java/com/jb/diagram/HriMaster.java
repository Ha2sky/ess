package com.jb.diagram;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;

@Entity
@Table(name = "HRIMASTER")
@Getter @Setter
@NoArgsConstructor
public class HriMaster {
    @Id
    @Column(name = "EMP_CODE", length = 20)
    private String empCode;

    @Column(name = "PASSWORD", length = 100, nullable = false)
    private String password;

    @Column(name = "EMP_NAME", length = 30, nullable = false)
    private String empName;

    @Column(name = "SOCIAL_NUM", length = 200)
    private String socialNum;

    @Column(name = "GENDER", length = 1)
    private String gender;

    @Column(name = "ENTER_DATE", length = 8)
    private String enterDate;

    @ManyToOne
    @JoinColumn(name = "POSITION_CODE")
    private HrtGradeInfo hrtGradeInfo;

    @Column(name = "POSITION_DATE", length = 8)
    private String positionDate;

    @ManyToOne
    @JoinColumn(name = "DUTY_CODE")
    private HrtDutyInfo hrtDutyInfo;

    @Column(name = "DUTY_DATE", length = 8)
    private String dutyDate;

    @Column(name = "EMP_STATE", length = 4)
    private String empState;

    @ManyToOne
    @JoinColumn(name = "DEPT_CODE")
    private OrgDeptMaster orgDeptMaster;

    @Column(name = "RETIRE_DATE", length = 8)
    private String retireDate;

    @Column(name = "RETIRE_REASON", length = 4)
    private String retireReason;

    @OneToOne(mappedBy = "hriMaster")
    private HrtAnnualDetail hrtAnnualDetail;
}
