package com.jb.ess.mapper;

import com.jb.ess.domain.Department;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DepartmentMapper {
    @Select("""
        SELECT
            DEPT_CODE       AS deptCode,        DEPT_NAME   AS deptName,
            PARENT_DEPT     AS parentDept,      DEPT_LEADER AS deptLeader,
            DEPT_CATEGORY   AS deptCategory,    START_DATE  AS startDate,
            END_DATE        AS endDate,         USE_YN      AS useYn
        FROM ORGDEPTMASTER
        ORDER BY DEPT_CODE
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
        SELECT
            DEPT_CODE       AS deptCode,        DEPT_NAME   AS deptName,
            PARENT_DEPT     AS parentDept,      DEPT_LEADER AS deptLeader,
            DEPT_CATEGORY   AS deptCategory,    START_DATE  AS startDate,
            END_DATE        AS endDate,         USE_YN      AS useYn
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
}
