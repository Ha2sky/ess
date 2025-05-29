package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AttRecordMapper {
    @Select("""
        SELECT *
        FROM HRTATTRECORD
        WHERE EMP_CODE = #{empCode}
    """)
    AttendanceRecord getAttRecordByEmpCode(String empCode);
}
