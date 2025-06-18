package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.ApplyHistory;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ApplyHistoryMapper {
    @Select("""
        SELECT
            general.APPLY_DATE AS applyDate,
            general.TARGET_DATE AS targetDate,
            general.APPLICANT_CODE AS applyEmpCode,
            general.REASON AS reason,
            general.START_TIME AS startTime,
            general.END_TIME AS endTime,
            NULL AS targetEndDate,
            general.EMP_CODE AS empCode,
            general.APPLY_TYPE AS applyType,
            general.STATUS AS status,
            NULL AS applyDateTime,
            dept.DEPT_NAME AS deptName,
            emp.EMP_NAME AS empName,
            hist.APPROVAL_DATE AS approvalDate
        
        FROM HRTATTAPLGENERAL general
        LEFT JOIN ORGDEPTMASTER dept ON general.DEPT_CODE = dept.DEPT_CODE
        LEFT JOIN HRIMASTER emp ON general.EMP_CODE = emp.EMP_CODE
        LEFT JOIN HRTAPRHIST hist ON general.APPLY_GENERAL_NO = hist.APPLY_GENERAL_NO
        
        WHERE (general.APPLICANT_CODE = #{empCode} OR general.EMP_CODE = #{empCode})
        AND general.TARGET_DATE BETWEEN #{startDate} AND #{endDate}
        AND general.STATUS LIKE CONCAT(#{status}, '%')
        AND general.STATUS != N'저장'
        
        UNION ALL
        
        SELECT
            etc.APPLY_DATE AS applyDate,
            etc.TARGET_START_DATE AS targetDate,
            etc.APPLICANT_CODE AS applyEmpCode,
            etc.REASON AS reason,
            NULL AS startTime,      
            NULL AS endTime,        
            etc.TARGET_END_DATE AS targetEndDate,
            etc.EMP_CODE AS empCode,
            shift.SHIFT_NAME AS applyType,
            etc.STATUS AS status,
            etc.APPLY_DATE_TIME AS applyDateTime,
            dept.DEPT_NAME AS deptName,
            emp.EMP_NAME AS empName,
            hist.APPROVAL_DATE AS approvalDate
        
        FROM HRTATTAPLETC etc
        LEFT JOIN ORGDEPTMASTER dept ON etc.DEPT_CODE = dept.DEPT_CODE
        LEFT JOIN HRIMASTER emp ON etc.EMP_CODE = emp.EMP_CODE
        LEFT JOIN HRTSHIFTMASTER shift ON etc.SHIFT_CODE = shift.SHIFT_CODE
        LEFT JOIN HRTAPRHIST hist ON etc.APPLY_ETC_NO = hist.APPLY_ETC_NO
        
        WHERE (etc.APPLICANT_CODE = #{empCode} OR etc.EMP_CODE = #{empCode})
        AND etc.TARGET_START_DATE BETWEEN #{startDate} AND #{endDate}
        AND etc.TARGET_END_DATE BETWEEN #{startDate} AND #{endDate}
        AND etc.STATUS LIKE CONCAT(#{status}, '%')
        AND etc.STATUS != N'저장'
        
        ORDER BY applyDate, targetDate, applyType;
    """)
    List<ApplyHistory> getAllApplyList(@Param("startDate") String startDate, @Param("endDate") String endDate,
                                       @Param("status") String status, @Param("empCode") String empCode);

    @Select("""
        SELECT
            general.APPLY_DATE AS applyDate,
            general.TARGET_DATE AS targetDate,
            general.APPLICANT_CODE AS applyEmpCode,
            general.REASON AS reason,
            general.START_TIME AS startTime,
            general.END_TIME AS endTime,
            NULL AS targetEndDate,
            general.EMP_CODE AS empCode,
            general.APPLY_TYPE AS applyType,
            general.STATUS AS status,
            dept.DEPT_NAME AS deptName,
            emp.EMP_NAME AS empName,
            hist.APPROVAL_DATE AS approvalDate
        
        FROM HRTATTAPLGENERAL general
        LEFT JOIN ORGDEPTMASTER dept ON general.DEPT_CODE = dept.DEPT_CODE
        LEFT JOIN HRIMASTER emp ON general.EMP_CODE = emp.EMP_CODE
        LEFT JOIN HRTAPRHIST hist ON general.APPLY_GENERAL_NO = hist.APPLY_GENERAL_NO
        
        WHERE (general.APPLICANT_CODE = #{empCode} OR general.EMP_CODE = #{empCode})
        AND general.TARGET_DATE BETWEEN #{startDate} AND #{endDate}
        AND general.STATUS LIKE CONCAT(#{status}, '%')
        AND general.STATUS != N'저장'
        AND general.APPLY_TYPE LIKE CONCAT('%', #{applyType}, '%')
        
        ORDER BY applyDate, targetDate, applyType;
    """)
    List<ApplyHistory> getApplyList(@Param("startDate") String startDate, @Param("endDate") String endDate,
                                    @Param("applyType") String applyType, @Param("status") String status,
                                    @Param("empCode") String empCode);

    @Select("""
        SELECT
            general.APPLY_DATE AS applyDate,
            general.TARGET_DATE AS targetDate,
            general.APPLICANT_CODE AS applyEmpCode,
            general.REASON AS reason,
            general.START_TIME AS startTime,
            general.END_TIME AS endTime,
            NULL AS targetEndDate,
            general.EMP_CODE AS empCode,
            general.APPLY_TYPE AS applyType,
            general.STATUS AS status,
            dept.DEPT_NAME AS deptName,
            emp.EMP_NAME AS empName,
            hist.APPROVAL_DATE AS approvalDate
        
        FROM HRTATTAPLGENERAL general
        LEFT JOIN ORGDEPTMASTER dept ON general.DEPT_CODE = dept.DEPT_CODE
        LEFT JOIN HRIMASTER emp ON general.EMP_CODE = emp.EMP_CODE
        LEFT JOIN HRTAPRHIST hist ON general.APPLY_GENERAL_NO = hist.APPLY_GENERAL_NO
        
        WHERE (general.APPLICANT_CODE = #{empCode} OR general.EMP_CODE = #{empCode})
        AND general.TARGET_DATE BETWEEN #{startDate} AND #{endDate}
        AND general.STATUS LIKE CONCAT(#{status}, '%')
        AND general.STATUS != N'저장'
        AND general.APPLY_TYPE IN (N'%조퇴%', N'%외출%', N'%반차%')
        
        ORDER BY applyDate, targetDate, applyType;
    """)
        /* 조퇴, 외출, 반차 신청 리스트 */
    List<ApplyHistory> getApplyList2(@Param("startDate") String startDate, @Param("endDate") String endDate,
                                     @Param("status") String status, @Param("empCode") String empCode);

    @Select("""
        SELECT
            etc.APPLY_DATE AS applyDate,
            etc.TARGET_START_DATE AS targetDate,
            etc.TARGET_END_DATE AS targetEndDate,
            etc.EMP_CODE AS empCode,
            shift.SHIFT_NAME AS applyType,
            etc.STATUS AS status,
            etc.APPLICANT_CODE AS applyEmpCode,
            etc.REASON AS reason,
            etc.APPLY_DATE_TIME AS applyDateTime,
            dept.DEPT_NAME AS deptName,
            emp.EMP_NAME AS empName,
            hist.APPROVAL_DATE AS approvalDate
        
        FROM HRTATTAPLETC etc
        LEFT JOIN ORGDEPTMASTER dept ON etc.DEPT_CODE = dept.DEPT_CODE
        LEFT JOIN HRIMASTER emp ON etc.EMP_CODE = emp.EMP_CODE
        LEFT JOIN HRTSHIFTMASTER shift ON etc.SHIFT_CODE = shift.SHIFT_CODE
        LEFT JOIN HRTAPRHIST hist ON etc.APPLY_ETC_NO = hist.APPLY_ETC_NO
        
        WHERE (etc.APPLICANT_CODE = #{empCode} OR etc.EMP_CODE = #{empCode})
        AND etc.TARGET_START_DATE BETWEEN #{startDate} AND #{endDate}
        AND etc.TARGET_END_DATE BETWEEN #{startDate} AND #{endDate}
        AND etc.STATUS LIKE CONCAT(#{status}, '%')
        AND etc.STATUS != N'저장'
        
        ORDER BY applyDate, targetDate, applyType;
    """)
        /* 기타근태 신청 리스트 */
    List<ApplyHistory> getApplyListEtc(@Param("startDate") String startDate, @Param("endDate") String endDate,
                                       @Param("status") String status, @Param("empCode") String empCode);
}
