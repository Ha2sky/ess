package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.ApprovalHistory;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ApprovalMapper {

    // 결재할 일반근태 문서 조회 (승인중 상태) - 수정된 부분
    @Select("""
        SELECT g.*, h.EMP_NAME, d.DEPT_NAME, applicant.EMP_NAME AS APPLICANT_NAME
        FROM HRTATTAPLGENERAL g
        LEFT JOIN HRIMASTER h ON g.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON g.APPLICANT_CODE = applicant.EMP_CODE
        LEFT JOIN ORGDEPTMASTER d ON g.DEPT_CODE = d.DEPT_CODE
        LEFT JOIN HRTAPRHIST hist ON g.APPLY_GENERAL_NO = hist.APPLY_GENERAL_NO
        WHERE g.STATUS = '상신'
          AND hist.APPROVER_CODE = #{approverCode}
          AND hist.APPROVAL_STATUS = '대기'
          AND g.TARGET_DATE BETWEEN #{startDate} AND #{endDate}
        ORDER BY g.APPLY_GENERAL_NO DESC
    """)
    List<AttendanceApplyGeneral> findPendingGeneralApprovals(@Param("approverCode") String approverCode,
                                                             @Param("startDate") String startDate,
                                                             @Param("endDate") String endDate);

    // 결재할 기타근태 문서 조회 (승인중 상태) - 수정된 부분
    @Select("""
        SELECT e.*, h.EMP_NAME, d.DEPT_NAME, applicant.EMP_NAME AS APPLICANT_NAME
        FROM HRTATTAPLETC e
        LEFT JOIN HRIMASTER h ON e.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON e.APPLICANT_CODE = applicant.EMP_CODE
        LEFT JOIN ORGDEPTMASTER d ON e.DEPT_CODE = d.DEPT_CODE
        LEFT JOIN HRTAPRHIST hist ON e.APPLY_ETC_NO = hist.APPLY_ETC_NO
        WHERE e.STATUS = '상신'
          AND hist.APPROVER_CODE = #{approverCode}
          AND hist.APPROVAL_STATUS = '대기'
          AND e.TARGET_START_DATE BETWEEN #{startDate} AND #{endDate}
        ORDER BY e.APPLY_ETC_NO DESC
    """)
    List<AttendanceApplyEtc> findPendingEtcApprovals(@Param("approverCode") String approverCode,
                                                     @Param("startDate") String startDate,
                                                     @Param("endDate") String endDate);

    // 승인된 일반근태 문서 조회 - 수정된 부분
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

    // 반려된 일반근태 문서 조회 - 수정된 부분
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

    // 결재 처리 - 수정된 부분
    @Update("""
        UPDATE HRTAPRHIST SET
            APPROVAL_DATE = #{approvalDate},
            APPROVAL_STATUS = #{approvalStatus},
            REJECT_REASON = #{rejectReason}
        WHERE APPROVAL_NO = #{approvalNo}
    """)
    void updateApprovalHistory(ApprovalHistory history);

    // 일반근태 결재 이력 조회 - 수정된 부분
    @Select("""
        SELECT * FROM HRTAPRHIST
        WHERE APPLY_GENERAL_NO = #{applyGeneralNo}
        ORDER BY APPROVAL_NO
    """)
    List<ApprovalHistory> findApprovalHistoryByGeneralNo(String applyGeneralNo);

    // 기타근태 결재 이력 조회 - 수정된 부분
    @Select("""
        SELECT * FROM HRTAPRHIST
        WHERE APPLY_ETC_NO = #{applyEtcNo}
        ORDER BY APPROVAL_NO
    """)
    List<ApprovalHistory> findApprovalHistoryByEtcNo(String applyEtcNo);

    // 일반근태 결재 이력 조회 (결재자별) - 수정된 부분
    @Select("""
        SELECT * FROM HRTAPRHIST
        WHERE APPLY_GENERAL_NO = #{applyGeneralNo} 
        AND APPROVER_CODE = #{approverCode}
    """)
    ApprovalHistory findGeneralApprovalHistoryByApprover(@Param("applyGeneralNo") String applyGeneralNo,
                                                         @Param("approverCode") String approverCode);

    // 기타근태 결재 이력 조회 (결재자별) - 수정된 부분
    @Select("""
        SELECT * FROM HRTAPRHIST
        WHERE APPLY_ETC_NO = #{applyEtcNo} 
        AND APPROVER_CODE = #{approverCode}
    """)
    ApprovalHistory findEtcApprovalHistoryByApprover(@Param("applyEtcNo") String applyEtcNo,
                                                     @Param("approverCode") String approverCode);
}
