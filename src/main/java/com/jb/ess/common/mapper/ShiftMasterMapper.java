package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.ShiftMaster;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShiftMasterMapper {
    @Select("""
        SELECT *
        FROM HRTSHIFTMASTER
        WHERE USE_YN = 'Y'
    """)
    /* 모든 근태코드 */
    List<ShiftMaster> findAllShiftCodes();

    @Select("""
        SELECT *
        FROM HRTSHIFTMASTER
        WHERE SHIFT_CODE = #{shiftCode}
    """)
    ShiftMaster findShiftByCode(String shiftCode);

    @Select("""
        SELECT WORK_ON_HHMM
        FROM HRTSHIFTMASTER
        WHERE SHIFT_CODE = #{shiftCode}
    """)
    String findWorkOnHourByShiftCode(String shiftCode);

    @Select("""
        SELECT WORK_OFF_HHMM
        FROM HRTSHIFTMASTER
        WHERE SHIFT_CODE = #{shiftCode}
    """)
    String findWorkOffHourByShiftCode(String shiftCode);

    @Select("""
        SELECT SHIFT_NAME
        FROM HRTSHIFTMASTER
        WHERE SHIFT_CODE = #{shiftCode}
    """)
    String findShiftNameByShiftCode(String shiftCode);
}
