package com.jb.ess.mapper;

import com.jb.ess.domain.ShiftMaster;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShiftMasterMapper {
    @Select("""
        SELECT SHIFT_CODE, SHIFT_NAME
        FROM HRTSHIFTMASTER
        WHERE USE_YN = 'Y'
    """)
    List<ShiftMaster> findAllShiftCodes();
}
