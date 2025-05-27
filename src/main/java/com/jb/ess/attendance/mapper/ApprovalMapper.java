package com.jb.ess.attendance.mapper;

import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.ApprovalHistory;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ApprovalMapper {

    // 결재할 문서 조회 (승인중 상태)
    @Select("""
        SELECT g.*, h.EMP_NAME, d.DEPT_NAME, applicant.EMP_NAME AS APPLICANT_NAME
        FROM HRTATTAPLGENERAL g
        LEFT JOIN HRIMASTER h ON g.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON g.APPLICANT_CODE = applicant.EMP_CODE
        LEFT JOIN ORGDEPTMASTER d ON g.DEPT_CODE = d.DEPT_CODE
        LEFT JOIN HRTAPRHIST hist ON g.APPLY_GENERAL_NO = hist.APPLY_GENERAL_NO
        WHERE g.STATUS = '승인중' 
          AND hist.APPROVER_CODE = #{approverCode}
          AND hist.APPROVAL_STATUS IS NULL
          AND g.TARGET_DATE BETWEEN #{startDate} AND #{endDate}
        ORDER BY g.APPLY_GENERAL_NO DESC
    """)
    List<AttendanceApplyGeneral> findPendingGeneralApprovals(@Param("approverCode") String approverCode,
                                                             @Param("startDate") String startDate,
                                                             @Param("endDate") String endDate);

    @Select("""
        SELECT e.*, h.EMP_NAME, d.DEPT_NAME, applicant.EMP_NAME AS APPLICANT_NAME
        FROM HRTATTAPLETC e
        LEFT JOIN HRIMASTER h ON e.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON e.APPLICANT_CODE = applicant.EMP_CODE
        LEFT JOIN ORGDEPTMASTER d ON e.DEPT_CODE = d.DEPT_CODE
        LEFT JOIN HRTAPRHIST hist ON e.APPLY_ETC_NO = hist.APPLY_ETC_NO
        WHERE e.STATUS = '승인중' 
          AND hist.APPROVER_CODE = #{approverCode}
          AND hist.APPROVAL_STATUS IS NULL
          AND e.TARGET_START_DATE BETWEEN #{startDate} AND #{endDate}
        ORDER BY e.APPLY_ETC_NO DESC
    """)
    List<AttendanceApplyEtc> findPendingEtcApprovals(@Param("approverCode") String approverCode,
                                                     @Param("startDate") String startDate,
                                                     @Param("endDate") String endDate);

    // 승인된 문서 조회
    @Select("""
        SELECT g.*, h.EMP_NAME, d.DEPT_NAME, applicant.EMP_NAME AS APPLICANT_NAME
        FROM HRTATTAPLGENERAL g
        LEFT JOIN HRIMASTER h ON g.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON g.APPLICANT_CODE = applicant.EMP_CODE
        LEFT JOIN ORGDEPTMASTER d ON g.DEPT_CODE = d.DEPT_CODE
        LEFT JOIN HRTAPRHIST hist ON g.APPLY_GENERAL_NO = hist.APPLY_GENERAL_NO
        WHERE g.STATUS = '승인완료' 
          AND hist.APPROVER_CODE = #{approverCode}
          AND hist.APPROVAL_STATUS = '승인'
          AND g.TARGET_DATE BETWEEN #{startDate} AND #{endDate}
        ORDER BY g.APPLY_GENERAL_NO DESC
    """)
    List<AttendanceApplyGeneral> findApprovedGeneralApprovals(@Param("approverCode") String approverCode,
                                                              @Param("startDate") String startDate,
                                                              @Param("endDate") String endDate);

    // 반려된 문서 조회
    @Select("""
        SELECT g.*, h.EMP_NAME, d.DEPT_NAME, applicant.EMP_NAME AS APPLICANT_NAME
        FROM HRTATTAPLGENERAL g
        LEFT JOIN HRIMASTER h ON g.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON g.APPLICANT_CODE = applicant.EMP_CODE
        LEFT JOIN ORGDEPTMASTER d ON g.DEPT_CODE = d.DEPT_CODE
        LEFT JOIN HRTAPRHIST hist ON g.APPLY_GENERAL_NO = hist.APPLY_GENERAL_NO
        WHERE g.STATUS = '반려' 
          AND hist.APPROVER_CODE = #{approverCode}
          AND hist.APPROVAL_STATUS = '반려'
          AND g.TARGET_DATE BETWEEN #{startDate} AND #{endDate}
        ORDER BY g.APPLY_GENERAL_NO DESC
    """)
    List<AttendanceApplyGeneral> findRejectedGeneralApprovals(@Param("approverCode") String approverCode,
                                                              @Param("startDate") String startDate,
                                                              @Param("endDate") String endDate);

    // 결재 이력 저장
    @Insert("""
        INSERT INTO HRTAPRHIST (
            HIST_NO, APPLY_GENERAL_NO, APPLY_ETC_NO, APPROVER_CODE, 
            APPROVAL_DATE, APPROVAL_STATUS, REJECT_REASON
        ) VALUES (
            #{histNo}, #{applyGeneralNo}, #{applyEtcNo}, #{approverCode},
            #{approvalDate}, #{approvalStatus}, #{rejectReason}
        )
    """)
    void insertApprovalHistory(ApprovalHistory history);

    // 결재 처리
    @Update("""
        UPDATE HRTAPRHIST SET 
            APPROVAL_DATE = #{approvalDate}, 
            APPROVAL_STATUS = #{approvalStatus},
            REJECT_REASON = #{rejectReason}
        WHERE HIST_NO = #{histNo}
    """)
    void updateApprovalHistory(ApprovalHistory history);

    // 결재 이력 조회
    @Select("""
        SELECT * FROM HRTAPRHIST 
        WHERE APPLY_GENERAL_NO = #{applyNo} OR APPLY_ETC_NO = #{applyNo}
        ORDER BY HIST_NO
    """)
    List<ApprovalHistory> findApprovalHistoryByApplyNo(String applyNo);
}
