package com.jb.diagram;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "HRTGRADEINFO")
@Getter @Setter
@NoArgsConstructor
public class HrtGradeInfo {
    @Id
    @Column(name = "POSITION_CODE", length = 10)
    private String positionCode;

    @Column(name = "POSITION_NAME", length = 30, nullable = false)
    private String positionName;

    @Column(name = "USE_YN", length = 1)
    private String useYn = "1";

    @OneToMany(mappedBy = "hrtGradeInfo")
    private List<HriMaster> hriMasters = new ArrayList<>();

    @OneToMany(mappedBy = "hrtGradeInfo")
    private List<HrtAnnualDetail> hrtAnnualDetails = new ArrayList<>();
}
