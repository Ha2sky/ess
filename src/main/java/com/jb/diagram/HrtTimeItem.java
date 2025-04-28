package com.jb.diagram;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "HRTTIMEITEM")
@Getter @Setter
@NoArgsConstructor
public class HrtTimeItem {
    @Id
    @Column(name = "TIME_ITEM_CODE", length = 10)
    private String timeItemCode;

    @Column(name = "TIME_ITEM_NAME", length = 50, nullable = false)
    private String timeItemName;

    @Column(name = "MEMO", length = 300)
    private String memo;

    @Column(name = "USE_YN", length = 1)
    private String useYn = "1";

    @OneToMany(mappedBy = "hrtTimeItem")
    private List<HrtShiftMaster> hrtShiftMasters = new ArrayList<>();
}
