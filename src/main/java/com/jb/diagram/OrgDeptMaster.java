package com.jb.diagram;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ORGDEPTMASTER")
@Getter @Setter
@NoArgsConstructor
public class OrgDeptMaster {
    @Id
    @Column(name = "DEPT_CODE", length = 10)
    private String deptCode;

    @Column(name = "DEPT_NAME", length = 50)
    private String deptName;

    @ManyToOne
    @JoinColumn(name = "PARENT_DEPT")
    private OrgDeptMaster parentDeptMaster;

    @OneToMany(mappedBy = "parentDeptMaster")
    private List<OrgDeptMaster> subDeptMasters = new ArrayList<>();

    @Column(name = "DEPT_LEADER", length = 10)
    private String deptLeader;

    @Column(name = "DEPT_CATEGORY", length = 10, nullable = false)
    private String deptCategory;

    @Column(name = "START_DATE", length = 8)
    private String startDate;

    @Column(name = "END_DATE", length = 8)
    private String endDate;

    @Column(name = "USE_YN", length = 1)
    private String useYn = "1";

    @ManyToOne
    @JoinColumn(name = "SHIFT_CODE")
    private HrtShiftMaster hrtShiftMaster;

    @OneToMany(mappedBy = "orgDeptMaster")
    private List<HriMaster> hriMasters = new ArrayList<>();

    @OneToMany(mappedBy = "orgDeptMaster")
    private List<HrtAnnualDetail> hrtAnnualDetails = new ArrayList<>();
}
