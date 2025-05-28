package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.Employee;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AttendanceApplyMapper {

    // 사원 정보 조회 (직위, 직책, 부서장 여부 포함)
    @Select("""
        SELECT h.*, p.POSITION_NAME, d.DUTY_NAME
        FROM HRIMASTER h
        LEFT JOIN HRTGRADEINFO p ON h.POSITION_CODE = p.POSITION_CODE
        LEFT JOIN HRTDUTYINFO d ON h.DUTY_CODE = d.DUTY_CODE
        WHERE h.EMP_CODE = #{empCode}
    """)
    Employee findEmployeeByEmpCode(String empCode);

    // 부서별 사원 조회 (근무계획 포함) - 부서장용
    @Select("""
        SELECT h.*, p.POSITION_NAME, d.DUTY_NAME, dept.DEPT_NAME,
               wc.SHIFT_CODE as workPlan,
               sm.SHIFT_NAME as workPlanName
        FROM HRIMASTER h
        LEFT JOIN HRTGRADEINFO p ON h.POSITION_CODE = p.POSITION_CODE
        LEFT JOIN HRTDUTYINFO d ON h.DUTY_CODE = d.DUTY_CODE
        LEFT JOIN ORGDEPTMASTER dept ON h.DEPT_CODE = dept.DEPT_CODE
        LEFT JOIN HRTWORKEMPCALENDAR wc ON h.EMP_CODE = wc.EMP_CODE 
               AND wc.YYYYMMDD = #{workDate}
        LEFT JOIN HRTSHIFTMASTER sm ON wc.SHIFT_CODE = sm.SHIFT_CODE
        WHERE h.DEPT_CODE = #{deptCode}
        AND h.EMP_STATE = '재직'
        ORDER BY h.EMP_CODE
    """)
    List<Employee> findEmployeesByDept(@Param("deptCode") String deptCode,
                                       @Param("workDate") String workDate,
                                       @Param("workPlan") String workPlan);

    // 현재 사원 정보 조회 (근무계획 포함) - 일반 사원용
    @Select("""
        SELECT h.*, p.POSITION_NAME, d.DUTY_NAME, dept.DEPT_NAME,
               wc.SHIFT_CODE as workPlan,
               sm.SHIFT_NAME as workPlanName
        FROM HRIMASTER h
        LEFT JOIN HRTGRADEINFO p ON h.POSITION_CODE = p.POSITION_CODE
        LEFT JOIN HRTDUTYINFO d ON h.DUTY_CODE = d.DUTY_CODE
        LEFT JOIN ORGDEPTMASTER dept ON h.DEPT_CODE = dept.DEPT_CODE
        LEFT JOIN HRTWORKEMPCALENDAR wc ON h.EMP_CODE = wc.EMP_CODE 
               AND wc.YYYYMMDD = #{workDate}
        LEFT JOIN HRTSHIFTMASTER sm ON wc.SHIFT_CODE = sm.SHIFT_CODE
        WHERE h.EMP_CODE = #{empCode}
    """)
    List<Employee> findCurrentEmployeeWithCalendar(@Param("empCode") String empCode,
                                                   @Param("workDate") String workDate);

    // 일반근태 중복 신청 확인
    @Select("""
        SELECT COUNT(*) > 0
        FROM HRTATTAPLGENERAL
        WHERE EMP_CODE = #{empCode}
        AND TARGET_DATE = #{targetDate}
        AND APPLY_TYPE = #{applyType}
        AND STATUS != '삭제'
    """)
    boolean checkDuplicateGeneralApply(@Param("empCode") String empCode,
                                       @Param("targetDate") String targetDate,
                                       @Param("applyType") String applyType);

    // 기타근태 중복 신청 확인
    @Select("""
        SELECT COUNT(*) > 0
        FROM HRTATTAPLETC
        WHERE EMP_CODE = #{empCode}
        AND ((TARGET_START_DATE <= #{endDate} AND TARGET_END_DATE >= #{startDate}))
        AND STATUS != '삭제'
    """)
    boolean checkDuplicateEtcApply(@Param("empCode") String empCode,
                                   @Param("startDate") String startDate,
                                   @Param("endDate") String endDate);

    // 일반근태 신청 저장
    @Insert("""
        INSERT INTO HRTATTAPLGENERAL (
            APPLY_GENERAL_NO, EMP_CODE, TIME_ITEM_CODE, APPLY_DATE, 
            TARGET_DATE, START_TIME, END_TIME, APPLY_TYPE, STATUS, 
            DEPT_CODE, APPLICANT_CODE
        ) VALUES (
            #{applyGeneralNo}, #{empCode}, #{timeItemCode}, #{applyDate},
            #{targetDate}, #{startTime}, #{endTime}, #{applyType}, #{status},
            #{deptCode}, #{applicantCode}
        )
    """)
    void insertGeneralApply(AttendanceApplyGeneral apply);

    // 기타근태 신청 저장
    @Insert("""
        INSERT INTO HRTATTAPLETC (
            APPLY_ETC_NO, EMP_CODE, SHIFT_CODE, APPLY_DATE,
            TARGET_START_DATE, TARGET_END_DATE, APPLY_DATE_TIME, REASON,
            STATUS, DEPT_CODE, APPLICANT_CODE
        ) VALUES (
            #{applyEtcNo}, #{empCode}, #{shiftCode}, #{applyDate},
            #{targetStartDate}, #{targetEndDate}, #{applyDateTime}, #{reason},
            #{status}, #{deptCode}, #{applicantCode}
        )
    """)
    void insertEtcApply(AttendanceApplyEtc apply);

    // 일반근태 신청 상태 변경
    @Update("UPDATE HRTATTAPLGENERAL SET STATUS = #{status} WHERE APPLY_GENERAL_NO = #{applyNo}")
    void updateGeneralApplyStatus(@Param("applyNo") String applyNo, @Param("status") String status);

    // 기타근태 신청 상태 변경
    @Update("UPDATE HRTATTAPLETC SET STATUS = #{status} WHERE APPLY_ETC_NO = #{applyNo}")
    void updateEtcApplyStatus(@Param("applyNo") String applyNo, @Param("status") String status);

    // 신청번호로 부서코드 조회
    @Select("""
        <script>
        <choose>
            <when test="applyType == 'general'">
                SELECT DEPT_CODE FROM HRTATTAPLGENERAL WHERE APPLY_GENERAL_NO = #{applyNo}
            </when>
            <when test="applyType == 'etc'">
                SELECT DEPT_CODE FROM HRTATTAPLETC WHERE APPLY_ETC_NO = #{applyNo}
            </when>
        </choose>
        </script>
    """)
    String getDeptCodeByApplyNo(@Param("applyNo") String applyNo, @Param("applyType") String applyType);

    // 부서코드로 부서장 조회
    @Select("SELECT DEPT_LEADER FROM ORGDEPTMASTER WHERE DEPT_CODE = #{deptCode}")
    String getDeptLeaderByDeptCode(String deptCode);

    // 결재 이력 생성
    @Insert("""
        INSERT INTO HRTAPRHIST (
            HIST_NO, 
            <if test="applyType == 'general'">APPLY_GENERAL_NO,</if>
            <if test="applyType == 'etc'">APPLY_ETC_NO,</if>
            APPROVER_CODE, APPROVAL_STATUS
        ) VALUES (
            #{histNo}, 
            <if test="applyType == 'general'">#{applyNo},</if>
            <if test="applyType == 'etc'">#{applyNo},</if>
            #{approverCode}, '대기'
        )
    """)
    void insertApprovalHistory(@Param("histNo") String histNo,
                               @Param("applyNo") String applyNo,
                               @Param("applyType") String applyType,
                               @Param("approverCode") String approverCode);

    // 신청 소유권 확인
    @Select("""
        <script>
        <choose>
            <when test="applyType == 'general'">
                SELECT COUNT(*) > 0 FROM HRTATTAPLGENERAL 
                WHERE APPLY_GENERAL_NO = #{applyNo} AND APPLICANT_CODE = #{applicantCode}
            </when>
            <when test="applyType == 'etc'">
                SELECT COUNT(*) > 0 FROM HRTATTAPLETC 
                WHERE APPLY_ETC_NO = #{applyNo} AND APPLICANT_CODE = #{applicantCode}
            </when>
        </choose>
        </script>
    """)
    boolean checkApplyOwnership(@Param("applyNo") String applyNo,
                                @Param("applyType") String applyType,
                                @Param("applicantCode") String applicantCode);

    // 신청 상태 조회
    @Select("""
        <script>
        <choose>
            <when test="applyType == 'general'">
                SELECT STATUS FROM HRTATTAPLGENERAL WHERE APPLY_GENERAL_NO = #{applyNo}
            </when>
            <when test="applyType == 'etc'">
                SELECT STATUS FROM HRTATTAPLETC WHERE APPLY_ETC_NO = #{applyNo}
            </when>
        </choose>
        </script>
    """)
    String getApplyStatus(@Param("applyNo") String applyNo, @Param("applyType") String applyType);

    // 일반근태 신청 삭제
    @Delete("DELETE FROM HRTATTAPLGENERAL WHERE APPLY_GENERAL_NO = #{applyNo}")
    void deleteGeneralApply(String applyNo);

    // 기타근태 신청 삭제
    @Delete("DELETE FROM HRTATTAPLETC WHERE APPLY_ETC_NO = #{applyNo}")
    void deleteEtcApply(String applyNo);

    // 신청자별 일반근태 신청 내역 조회
    @Select("""
        SELECT g.*, h.EMP_NAME, applicant.EMP_NAME as applicantName
        FROM HRTATTAPLGENERAL g
        LEFT JOIN HRIMASTER h ON g.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON g.APPLICANT_CODE = applicant.EMP_CODE
        WHERE g.APPLICANT_CODE = #{applicantCode}
        ORDER BY g.APPLY_DATE DESC, g.APPLY_GENERAL_NO DESC
    """)
    List<AttendanceApplyGeneral> findGeneralAppliesByApplicant(String applicantCode);

    // 신청자별 기타근태 신청 내역 조회
    @Select("""
        SELECT e.*, h.EMP_NAME, applicant.EMP_NAME as applicantName
        FROM HRTATTAPLETC e
        LEFT JOIN HRIMASTER h ON e.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON e.APPLICANT_CODE = applicant.EMP_CODE
        WHERE e.APPLICANT_CODE = #{applicantCode}
        ORDER BY e.APPLY_DATE DESC, e.APPLY_ETC_NO DESC
    """)
    List<AttendanceApplyEtc> findEtcAppliesByApplicant(String applicantCode);

    // 부서별 일반근태 신청 내역 조회 (부서장용)
    @Select("""
        SELECT g.*, h.EMP_NAME, applicant.EMP_NAME as applicantName
        FROM HRTATTAPLGENERAL g
        LEFT JOIN HRIMASTER h ON g.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON g.APPLICANT_CODE = applicant.EMP_CODE
        WHERE g.DEPT_CODE = #{deptCode}
        ORDER BY g.APPLY_DATE DESC, g.APPLY_GENERAL_NO DESC
    """)
    List<AttendanceApplyGeneral> findGeneralAppliesByDept(String deptCode);

    // 부서별 기타근태 신청 내역 조회 (부서장용)
    @Select("""
        SELECT e.*, h.EMP_NAME, applicant.EMP_NAME as applicantName
        FROM HRTATTAPLETC e
        LEFT JOIN HRIMASTER h ON e.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON e.APPLICANT_CODE = applicant.EMP_CODE
        WHERE e.DEPT_CODE = #{deptCode}
        ORDER BY e.APPLY_DATE DESC, e.APPLY_ETC_NO DESC
    """)
    List<AttendanceApplyEtc> findEtcAppliesByDept(String deptCode);
}