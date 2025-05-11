package com.jb.ess.mapper;

import com.jb.ess.domain.Department;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DepartmentMapper {
    @Select("""
        SELECT
            d.DEPT_CODE,
            d.DEPT_NAME,
            d.DEPT_CATEGORY,
            d.PARENT_DEPT,
            d.USE_YN,
            d.START_DATE,
            d.END_DATE,
            d.DEPT_LEADER,
            COUNT(e.EMP_CODE) AS EMP_COUNT
        FROM ORGDEPTMASTER d
        LEFT JOIN HRIMASTER e ON d.DEPT_CODE = e.DEPT_CODE
        GROUP BY
            d.DEPT_CODE,
            d.DEPT_NAME,
            d.DEPT_CATEGORY,
            d.PARENT_DEPT,
            d.USE_YN,
            d.START_DATE,
            d.END_DATE,
            d.DEPT_LEADER
        ORDER BY d.DEPT_CODE
    """)
    List<Department> findAll();

    @Insert("""
        INSERT INTO ORGDEPTMASTER (DEPT_CODE, DEPT_NAME, PARENT_DEPT, DEPT_LEADER,
                                   DEPT_CATEGORY, START_DATE, END_DATE, USE_YN)
        VALUES (#{deptCode}, #{deptName}, #{parentDept}, #{deptLeader},
                #{deptCategory}, #{startDate}, #{endDate}, #{useYn})
    """)
    void insertDepartment(Department department);

    @Update("""
        UPDATE ORGDEPTMASTER
        SET DEPT_NAME = #{deptName}, PARENT_DEPT = #{parentDept}, DEPT_LEADER = #{deptLeader},
            START_DATE = #{startDate}, END_DATE = #{endDate}, USE_YN = #{useYn}, DEPT_CATEGORY = #{deptCategory}
        WHERE DEPT_CODE = #{deptCode}
    """)
    void updateDepartment(Department department);

    @Select("""
        SELECT *
        FROM ORGDEPTMASTER
        WHERE DEPT_CODE = #{deptCode}
    """)
    Department findByDeptCode(String deptCode);

    @Select("SELECT COUNT(*) FROM ORGDEPTMASTER WHERE DEPT_CODE = #{deptCode}")
    int countByDeptCode(String deptCode);

    @Delete("""
        DELETE FROM ORGDEPTMASTER
        WHERE DEPT_CODE = #{deptCode}
    """)
    void deleteDepartment(String deptCode);

    @Update("UPDATE ORGDEPTMASTER SET DEPT_LEADER = #{empCode} WHERE DEPT_CODE = #{deptCode}")
    void updateDeptLeader(@Param("deptCode") String deptCode, @Param("empCode") String empCode);

    // 현재 부서장의 사번 조회
    @Select("SELECT DEPT_LEADER FROM ORGDEPTMASTER WHERE DEPT_CODE = #{deptCode}")
    String findDepartmentLeader(String deptCode);

    // 부서장의 사번 업데이트 (null로 설정해 해제)
    @Update("UPDATE ORGDEPTMASTER SET DEPT_LEADER = #{empCode} WHERE DEPT_CODE = #{deptCode}")
    void updateDepartmentLeader(@Param("deptCode") String deptCode, @Param("empCode") String empCode);
}
