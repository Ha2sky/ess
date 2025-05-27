package com.jb.ess.attendance.mapper;

import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.domain.ApprovalHistory;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AttendanceApplyMapper {

    // 일반근태 신청 관련
    @Select("""
        SELECT 
            h.EMP_CODE, h.EMP_NAME, g.POSITION_NAME, h.DEPT_CODE, d.DEPT_NAME,
            c.SHIFT_CODE AS WORK_PLAN, h.EMP_STATE
        FROM HRIMASTER h
        LEFT JOIN HRTGRADEINFO g ON h.POSITION_CODE = g.POSITION_CODE
        LEFT JOIN ORGDEPTMASTER d ON h.DEPT_CODE = d.DEPT_CODE
        LEFT JOIN HRTWORKEMPCALENDAR c ON h.EMP_CODE = c.EMP_CODE AND c.YYYYMMDD = #{workDate}
        WHERE h.DEPT_CODE = #{deptCode} 
          AND h.EMP_STATE != '휴직'
          AND (#{isLeader} = 'Y' OR h.EMP_CODE = #{empCode})
        ORDER BY g.POSITION_NAME, h.EMP_CODE, h.EMP_NAME
    """)
    List<Employee> findApplicableEmployees(@Param("deptCode") String deptCode,
                                           @Param("workDate") String workDate,
                                           @Param("empCode") String empCode,
                                           @Param("isLeader") String isLeader);

    @Insert("""
        INSERT INTO HRTATTAPLGENERAL (
            APPLY_GENERAL_NO, EMP_CODE, TIME_ITEM_CODE, APPLY_DATE, TARGET_DATE,
            START_TIME, END_TIME, APPLY_TYPE, STATUS, DEPT_CODE, APPLICANT_CODE
        ) VALUES (
            #{applyGeneralNo}, #{empCode}, #{timeItemCode}, #{applyDate}, #{targetDate},
            #{startTime}, #{endTime}, #{applyType}, #{status}, #{deptCode}, #{applicantCode}
        )
    """)
    void insertGeneralApply(AttendanceApplyGeneral apply);

    @Update("""
        UPDATE HRTATTAPLGENERAL SET
            TIME_ITEM_CODE = #{timeItemCode}, START_TIME = #{startTime}, 
            END_TIME = #{endTime}, APPLY_TYPE = #{applyType}
        WHERE APPLY_GENERAL_NO = #{applyGeneralNo}
    """)
    void updateGeneralApply(AttendanceApplyGeneral apply);

    @Delete("DELETE FROM HRTATTAPLGENERAL WHERE APPLY_GENERAL_NO = #{applyGeneralNo}")
    void deleteGeneralApply(String applyGeneralNo);

    // 기타근태 신청 관련
    @Insert("""
        INSERT INTO HRTATTAPLETC (
            APPLY_ETC_NO, EMP_CODE, SHIFT_CODE, APPLY_DATE, TARGET_START_DATE,
            TARGET_END_DATE, APPLY_DATE_TIME, REASON, STATUS, DEPT_CODE, APPLICANT_CODE
        ) VALUES (
            #{applyEtcNo}, #{empCode}, #{shiftCode}, #{applyDate}, #{targetStartDate},
            #{targetEndDate}, #{applyDateTime}, #{reason}, #{status}, #{deptCode}, #{applicantCode}
        )
    """)
    void insertEtcApply(AttendanceApplyEtc apply);

    @Update("""
        UPDATE HRTATTAPLETC SET
            SHIFT_CODE = #{shiftCode}, TARGET_START_DATE = #{targetStartDate},
            TARGET_END_DATE = #{targetEndDate}, REASON = #{reason}
        WHERE APPLY_ETC_NO = #{applyEtcNo}
    """)
    void updateEtcApply(AttendanceApplyEtc apply);

    @Delete("DELETE FROM HRTATTAPLETC WHERE APPLY_ETC_NO = #{applyEtcNo}")
    void deleteEtcApply(String applyEtcNo);

    // 상신 처리
    @Update("UPDATE HRTATTAPLGENERAL SET STATUS = #{status} WHERE APPLY_GENERAL_NO = #{applyNo}")
    void updateGeneralStatus(@Param("applyNo") String applyNo, @Param("status") String status);

    @Update("UPDATE HRTATTAPLETC SET STATUS = #{status} WHERE APPLY_ETC_NO = #{applyNo}")
    void updateEtcStatus(@Param("applyNo") String applyNo, @Param("status") String status);

    // 신청 내역 조회
    @Select("""
        SELECT g.*, h.EMP_NAME, d.DEPT_NAME 
        FROM HRTATTAPLGENERAL g
        LEFT JOIN HRIMASTER h ON g.EMP_CODE = h.EMP_CODE
        LEFT JOIN ORGDEPTMASTER d ON g.DEPT_CODE = d.DEPT_CODE
        WHERE g.APPLICANT_CODE = #{applicantCode}
        ORDER BY g.APPLY_DATE DESC
    """)
    List<AttendanceApplyGeneral> findGeneralAppliesByApplicant(String applicantCode);

    @Select("""
        SELECT e.*, h.EMP_NAME, d.DEPT_NAME 
        FROM HRTATTAPLETC e
        LEFT JOIN HRIMASTER h ON e.EMP_CODE = h.EMP_CODE
        LEFT JOIN ORGDEPTMASTER d ON e.DEPT_CODE = d.DEPT_CODE
        WHERE e.APPLICANT_CODE = #{applicantCode}
        ORDER BY e.APPLY_DATE DESC
    """)
    List<AttendanceApplyEtc> findEtcAppliesByApplicant(String applicantCode);
}
