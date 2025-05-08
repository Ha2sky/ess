package com.jb.ess.mapper;

import com.jb.ess.domain.Employee;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EmployeeMapper {
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

    @Select("""
        SELECT u.*,
            p.POSITION_NAME,
            d.DUTY_NAME
        FROM HRIMASTER u
        LEFT JOIN HRTGRADEINFO p ON u.POSITION_CODE = p.POSITION_CODE
        LEFT JOIN HRTDUTYINFO d ON u.DUTY_CODE = d.DUTY_CODE
        WHERE u.DEPT_CODE = #{deptCode}
        ORDER BY u.EMP_CODE
    """)
    /* 특정 부서에 소속된 사원 목록 조회 */
    List<Employee> findEmployeesByDeptCode(@Param("deptCode") String deptCode);

    /* 사원코드로 단일 사원 조회 */
    @Select("""
        SELECT *
        FROM HRIMASTER
        WHERE EMP_CODE = #{empCode}
    """)

    /* 사원의 부서코드 변경 (배정 또는 삭제) */
    @Update("""
        UPDATE HRIMASTER
        SET DEPT_CODE = #{deptCode}
        WHERE EMP_CODE = #{empCode}
    """)
    void updateEmployeeDepartment(@Param("empCode") String empCode, @Param("deptCode") String deptCode);

    /* 부서 미배정 사원 조회 */
    @Select("""
        SELECT u.*,
            p.POSITION_NAME,
            d.DUTY_NAME
        FROM HRIMASTER u
        LEFT JOIN HRTGRADEINFO p ON u.POSITION_CODE = p.POSITION_CODE
        LEFT JOIN HRTDUTYINFO d ON u.DUTY_CODE = d.DUTY_CODE
        WHERE u.DEPT_CODE IS NULL OR u.DEPT_CODE = ''
        ORDER BY u.EMP_CODE
    """)
    List<Employee> findEmployeesWithoutDepartment();
}
