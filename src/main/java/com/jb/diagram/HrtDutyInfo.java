package com.jb.diagram;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "HRTDUTYINFO")
@Getter @Setter
@NoArgsConstructor
public class HrtDutyInfo {
    @Id
    @Column(name = "DUTY_CODE", length = 3)
    private String dutyCode;

    @Column(name = "DUTY_NAME", length = 10, nullable = false)
    private String dutyName;

    @Column(name = "USE_YN", length = 1)
    private String useYn = "1";

    @OneToMany(mappedBy = "hrtDutyInfo")
    private List<HriMaster> hriMasters = new ArrayList<>();
}
