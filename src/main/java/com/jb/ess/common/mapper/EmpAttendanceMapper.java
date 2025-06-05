package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.Department;
import com.jb.ess.common.domain.Employee;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EmpAttendanceMapper {
    @Select("""
        SELECT emp.*,
               att.*,
               grd.POSITION_NAME,
               dept.DEPT_NAME,
               cal.*
        FROM HRIMASTER emp
        LEFT JOIN HRTATTRECORD att ON emp.EMP_CODE = att.EMP_CODE
            AND att.WORK_DATE = #{workDate}
        LEFT JOIN HRTGRADEINFO grd ON emp.POSITION_CODE = grd.POSITION_CODE
        LEFT JOIN ORGDEPTMASTER dept ON emp.DEPT_CODE = dept.DEPT_CODE
        LEFT JOIN HRTWORKEMPCALENDAR cal ON emp.EMP_CODE = cal.EMP_CODE
            AND cal.YYYYMMDD = #{workDate}
        WHERE emp.DEPT_CODE = #{deptCode}
        ORDER BY emp.EMP_NAME
    """)
    /* Employee + 실적 목록 */
    List<Employee> getEmpAttendanceByDeptCodeAndWorkDate(@Param("deptCode") String deptCode,
                                              @Param("workDate") String workDate);

    @Select("""
        SELECT emp.*,
               att.*,
               grd.POSITION_NAME,
               dept.DEPT_NAME,
               cal.*
        FROM HRIMASTER emp
        LEFT JOIN HRTATTRECORD att ON emp.EMP_CODE = att.EMP_CODE
            AND att.WORK_DATE = #{workDate}
        LEFT JOIN HRTGRADEINFO grd ON emp.POSITION_CODE = grd.POSITION_CODE
        LEFT JOIN ORGDEPTMASTER dept ON emp.DEPT_CODE = dept.DEPT_CODE
        LEFT JOIN HRTWORKEMPCALENDAR cal ON emp.EMP_CODE = cal.EMP_CODE
            AND cal.YYYYMMDD = #{workDate}
        WHERE emp.EMP_CODE = #{empCode}
    """)
    /* Employee + 실적 */
    List<Employee> getEmpAttendanceByEmpCodeAndWorkDate(@Param("empCode") String empCode,
                                             @Param("workDate") String workDate);

    @Select("""
        SELECT emp.*,
               att.*,
               grd.POSITION_NAME,
               dept.DEPT_NAME,
               cal.*
        FROM HRIMASTER emp
        LEFT JOIN HRTATTRECORD att ON emp.EMP_CODE = att.EMP_CODE
            AND att.WORK_DATE = #{workDate}
        LEFT JOIN HRTGRADEINFO grd ON emp.POSITION_CODE = grd.POSITION_CODE
        LEFT JOIN ORGDEPTMASTER dept ON emp.DEPT_CODE = dept.DEPT_CODE
        LEFT JOIN HRTWORKEMPCALENDAR cal ON emp.EMP_CODE = cal.EMP_CODE
            AND cal.YYYYMMDD = #{workDate}
        WHERE emp.DEPT_CODE = #{deptCode}
        AND cal.SHIFT_CODE_ORIG = #{planType}
        ORDER BY emp.EMP_NAME
    """)
        /* Employee + 실적 목록 */
    List<Employee> getEmpAttendanceByDeptCodeAndWorkDateAndPlanType(@Param("deptCode") String deptCode,
                                                                    @Param("workDate") String workDate,
                                                                    @Param("planType") String planType);

    @Select("""
        SELECT emp.*,
               att.*,
               grd.POSITION_NAME,
               dept.DEPT_NAME,
               cal.*
        FROM HRIMASTER emp
        LEFT JOIN HRTATTRECORD att ON emp.EMP_CODE = att.EMP_CODE
            AND att.WORK_DATE = #{workDate}
        LEFT JOIN HRTGRADEINFO grd ON emp.POSITION_CODE = grd.POSITION_CODE
        LEFT JOIN ORGDEPTMASTER dept ON emp.DEPT_CODE = dept.DEPT_CODE
        LEFT JOIN HRTWORKEMPCALENDAR cal ON emp.EMP_CODE = cal.EMP_CODE
            AND cal.YYYYMMDD = #{workDate}
        WHERE emp.EMP_CODE = #{empCode}
        AND cal.SHIFT_CODE_ORIG = #{planType}
    """)
        /* Employee + 실적 */
    List<Employee> getEmpAttendanceByEmpCodeAndWorkDateAndPlanType(@Param("empCode") String empCode,
                                                                   @Param("workDate") String workDate,
                                                                   @Param("planType") String planType);

    @Select("""
        SELECT *
        FROM ORGDEPTMASTER
        WHERE PARENT_DEPT = #{deptCode}
    """)
    /* 자식 부서 목록 */
    List<Department> getChildDepartmentsByDeptCode(String deptCode);
}
