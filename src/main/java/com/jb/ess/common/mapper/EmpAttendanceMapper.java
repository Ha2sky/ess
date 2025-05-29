package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.Department;
import com.jb.ess.common.domain.Employee;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmpAttendanceMapper {
    @Select("""
        SELECT emp.*,
               att.*,
               grd.POSITION_NAME,
               dept.DEPT_NAME
        FROM HRIMASTER emp
        LEFT JOIN HRTATTRECORD att ON emp.EMP_CODE = att.EMP_CODE
        LEFT JOIN HRTGRADEINFO grd ON emp.POSITION_CODE = grd.POSITION_CODE
        LEFT JOIN ORGDEPTMASTER dept ON emp.DEPT_CODE = dept.DEPT_CODE
        WHERE emp.DEPT_CODE = #{deptCode}
        ORDER BY emp.EMP_NAME
    """)
    /* Employee + 실적 목록 */
    List<Employee> getEmpAttendanceByDeptCode(String deptCode);

    @Select("""
        SELECT emp.*,
               att.*,
               grd.POSITION_NAME,
               dept.DEPT_NAME
        FROM HRIMASTER emp
        LEFT JOIN HRTATTRECORD att ON emp.EMP_CODE = att.EMP_CODE
        LEFT JOIN HRTGRADEINFO grd ON emp.POSITION_CODE = grd.POSITION_CODE
        LEFT JOIN ORGDEPTMASTER dept ON emp.DEPT_CODE = dept.DEPT_CODE
        WHERE emp.EMP_CODE = #{empCode}
    """)
    /* Employee + 실적 */
    List<Employee> getEmpAttendanceByEmpCode(String empCode);

    @Select("""
        SELECT *
        FROM ORGDEPTMASTER
        WHERE PARENT_DEPT = #{deptCode}
    """)
    /* 자식 부서 목록 */
    List<Department> getChildDepartmentsByDeptCode(String deptCode);
}
