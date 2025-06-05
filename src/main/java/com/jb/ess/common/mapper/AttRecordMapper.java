package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.AttendanceRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AttRecordMapper {
    @Select("""
        SELECT att.*,
               cal.SHIFT_CODE
        FROM HRTATTRECORD att
        LEFT JOIN HRTWORKEMPCALENDAR cal ON att.EMP_CODE = cal.EMP_CODE AND att.WORK_DATE = cal.YYYYMMDD
        WHERE att.EMP_CODE = #{empCode}
        AND att.WORK_DATE = #{yyyymmdd}
    """)
    AttendanceRecord getAttRecordByEmpCode(String empCode, String yyyymmdd);

    @Insert("""
        INSERT INTO HRTATTRECORD (EMP_CODE, WORK_DATE, CHECK_IN_TIME, CHECK_IN_DAY_TYPE,
                                  CHECK_OUT_TIME, CHECK_OUT_DAY_TYPE)
        VALUES (#{empCode}, #{workDate}, null, null, null, null)
    """)
    void insertAttRecord(@Param("empCode") String empCode,
                         @Param("workDate") String workDate);
}
