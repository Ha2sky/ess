package com.jb.diagram;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "HRTSHIFTMASTER")
@Getter @Setter
@NoArgsConstructor
public class HrtShiftMaster {
    @Id
    @Column(name = "SHIFT_CODE", length = 10)
    private String shiftCode;

    @Column(name = "SHIFT_NAME", length = 50, nullable = false)
    private String shiftName;

    @ManyToOne
    @JoinColumn(name = "TIME_ITEM_CODE")
    private HrtTimeItem hrtTimeItem;

    @Column(name = "WORK_ON_DAY_TYPE", length = 2, nullable = false)
    private String workOnDayType;

    @Column(name = "WORK_ON_HHMM", length = 4, nullable = false)
    private String workOnHhmm;

    @Column(name = "WORK_OFF_DAY_TYPE", length = 2, nullable = false)
    private String workOffDayType;

    @Column(name = "WORK_OFF_HHMM", length = 4, nullable = false)
    private String workOffHhmm;

    @Column(name = "BREAK1_START_DAY_TYPE", length = 2)
    private String break1StartDayType;

    @Column(name = "BREAK1_START_HHMM", length = 4)
    private String break1StartHhmm;

    @Column(name = "BREAK1_END_DAY_TYPE", length = 2)
    private String break1EndDayType;

    @Column(name = "BREAK1_END_HHMM", length = 4)
    private String break1EndHhmm;

    @Column(name = "BREAK2_START_DAY_TYPE", length = 2)
    private String break2StartDayType;

    @Column(name = "BREAK2_START_HHMM", length = 4)
    private String break2StartHhmm;

    @Column(name = "BREAK2_END_DAY_TYPE", length = 2)
    private String break2EndDayType;

    @Column(name = "BREAK2_END_HHMM", length = 4)
    private String break2EndHhmm;

    @Column(name = "OVER_START_DAY_TYPE", length = 2, nullable = false)
    private String overStartDayType;

    @Column(name = "OVER_START_HHMM", length = 4, nullable = false)
    private String overStartHhmm;

    @Column(name = "OVER_END_DAY_TYPE", length = 2, nullable = false)
    private String overEndDayType;

    @Column(name = "OVER_END_HHMM", length = 4, nullable = false)
    private String overEndHhmm;

    @Column(name = "OVER2_START_DAY_TYPE", length = 2)
    private String over2StartDayType;

    @Column(name = "OVER2_START_HHMM", length = 4)
    private String over2StartHhmm;

    @Column(name = "OVER2_END_DAY_TYPE", length = 2)
    private String over2EndDayType;

    @Column(name = "OVER2_END_HHMM", length = 4)
    private String over2EndHhmm;

    @Column(name = "MEMO", length = 300)
    private String memo;

    @Column(name = "USE_YN", length = 1)
    private String useYn = "1";

    @Column(name = "WORK_TYPE_CODE", length = 10)
    private String workTypeCode;

    @Column(name = "WORK_DAY_TYPE", length = 10)
    private String workDayType;

    @OneToMany(mappedBy = "hrtShiftMaster")
    private List<OrgDeptMaster> orgDeptMasters = new ArrayList<>();
}
