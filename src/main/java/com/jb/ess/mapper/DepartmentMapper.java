package com.jb.ess.mapper;

import com.jb.ess.domain.Department;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DepartmentMapper {
    @Select("""
        SELECT
            DEPT_CODE AS deptCode,
            DEPT_NAME AS deptName,
            PARENT_DEPT AS parentDept,
            DEPT_LEADER AS deptLeader,
            DEPT_CATEGORY AS deptCategory,
            START_DATE AS startDate,
            END_DATE AS endDate,
            USE_YN AS useYn
        FROM ORGDEPTMASTER
        ORDER BY DEPT_CODE
    """)
    List<Department> findAll();
}
