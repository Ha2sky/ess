package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.Department;
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
                leader.EMP_NAME AS LEADER_NAME,
                d.WORK_PATTERN_CODE,
                COUNT(e.EMP_CODE) AS EMP_COUNT,
                pattern.WORK_PATTERN_NAME
            FROM ORGDEPTMASTER d
            LEFT JOIN HRIMASTER e ON d.DEPT_CODE = e.DEPT_CODE
            LEFT JOIN HRIMASTER leader ON d.DEPT_LEADER = leader.EMP_CODE
            LEFT JOIN HRTSHIFTPATTERN pattern ON d.WORK_PATTERN_CODE = pattern.WORK_PATTERN_CODE
            GROUP BY
                d.DEPT_CODE,
                d.DEPT_NAME,
                d.DEPT_CATEGORY,
                d.PARENT_DEPT,
                d.USE_YN,
                d.START_DATE,
                d.END_DATE,
                d.DEPT_LEADER,
                leader.EMP_NAME,
                d.WORK_PATTERN_CODE,
                pattern.WORK_PATTERN_NAME
            ORDER BY d.DEPT_CODE
    """)
        // 부서 리스트
    List<Department> findAll();

    // 수정: 전체 부서 구조 조회를 위한 메서드 추가 (하위부서 재귀 검색용)
    @Select("""
        SELECT DEPT_CODE, DEPT_NAME, DEPT_CATEGORY, PARENT_DEPT, USE_YN, 
               START_DATE, END_DATE, DEPT_LEADER, WORK_PATTERN_CODE
        FROM ORGDEPTMASTER
        WHERE USE_YN = 'Y'
        ORDER BY DEPT_CODE
    """)
    List<Department> findAllDepartments();

    @Insert("""
        INSERT INTO ORGDEPTMASTER (DEPT_CODE, DEPT_NAME, PARENT_DEPT, DEPT_LEADER,
                                   DEPT_CATEGORY, START_DATE, END_DATE, USE_YN, WORK_PATTERN_CODE)
        VALUES (#{deptCode}, #{deptName}, #{parentDept}, #{deptLeader},
                #{deptCategory}, #{startDate}, #{endDate}, #{useYn}, #{workPatternCode})
    """)
        // 부서 생성
    void insertDepartment(Department department);

    @Update("""
        UPDATE ORGDEPTMASTER
        SET DEPT_CODE = #{dept.deptCode}, DEPT_NAME = #{dept.deptName}, PARENT_DEPT = #{dept.parentDept},
            START_DATE = #{dept.startDate}, END_DATE = #{dept.endDate},
            USE_YN = #{dept.useYn}, DEPT_CATEGORY = #{dept.deptCategory}, WORK_PATTERN_CODE = #{dept.workPatternCode}
        WHERE DEPT_CODE = #{originalDeptCode}
    """)
        // 부서 수정
    void updateDepartment(@Param("dept") Department department,
                          @Param("originalDeptCode") String originalDeptCode);

    @Select("""
        SELECT *
        FROM ORGDEPTMASTER
        WHERE DEPT_CODE = #{deptCode}
    """)
        // 부서 코드로 부서 찾기
    Department findByDeptCode(String deptCode);

    @Select("SELECT COUNT(*) FROM ORGDEPTMASTER WHERE DEPT_CODE = #{deptCode}")
        // 부서에 소속된 인원수
    int countByDeptCode(String deptCode);

    @Delete("""
        DELETE FROM ORGDEPTMASTER
        WHERE DEPT_CODE = #{deptCode}
    """)
        // 부서 삭제
    void deleteDepartment(String deptCode);

    @Update("UPDATE ORGDEPTMASTER SET DEPT_LEADER = #{empCode} WHERE DEPT_CODE = #{deptCode}")
        // 부서장 변경
    void updateDeptLeader(@Param("deptCode") String deptCode, @Param("empCode") String empCode);

    // 현재 부서장의 사번 조회
    @Select("SELECT DEPT_LEADER FROM ORGDEPTMASTER WHERE DEPT_CODE = #{deptCode}")
    String findDepartmentLeader(String deptCode);

    // 부서장의 사번 업데이트 (null로 설정해 해제)
    @Update("UPDATE ORGDEPTMASTER SET DEPT_LEADER = #{empCode} WHERE DEPT_CODE = #{deptCode}")
    void updateDepartmentLeader(@Param("deptCode") String deptCode, @Param("empCode") String empCode);

    @Select("""
        SELECT DEPT_CODE
        FROM ORGDEPTMASTER
        WHERE WORK_PATTERN_CODE = #{workPatternCode}
    """)
        // workPatternCode를 사용중인 부서코드 리스트
    List<String> findDeptCodesByWorkPatternCode(@Param("workPatternCode") String workPatternCode);

    @Select("""
        SELECT WORK_PATTERN_CODE
        FROM ORGDEPTMASTER
        WHERE DEPT_CODE = #{deptCode}
    """)
        /* deptCode가 사용중인 workPatternCode 반환 */
    String findWorkPatternCodeByDeptCode(String deptCode);

    @Select("""
        SELECT DEPT_NAME
        FROM ORGDEPTMASTER
        WHERE DEPT_CODE = (
            SELECT DEPT_CODE
            FROM HRIMASTER
            WHERE EMP_CODE = #{empCode}
        )
    """)
    String findDeptNameByEmpCode(String empCode);
}
