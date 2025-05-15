package com.jb.ess.login.mapper;

import com.jb.ess.common.domain.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Select("""
        SELECT
            EMP_CODE AS empCode,
            PASSWORD AS password,
            EMP_NAME AS empName,
            EMP_STATE AS empState,
            DEPT_CODE AS deptCode
        FROM HRIMASTER
        WHERE EMP_CODE = #{empCode}
    """)
    /* 로그인 */
    Employee findByEmployeeName(String empCode);
}
