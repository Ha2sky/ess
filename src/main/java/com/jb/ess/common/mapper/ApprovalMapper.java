package com.jb.ess.common.mapper;

import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.ApprovalHistory;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ApprovalMapper {

    // 결재할 일반근태 문서 조회
    @Select("""
        <script>
        SELECT g.APPLY_GENERAL_NO, g.EMP_CODE, g.TIME_ITEM_CODE, g.APPLY_DATE, 
               g.TARGET_DATE, g.START_TIME, g.END_TIME, g.APPLY_TYPE, g.STATUS,
               g.DEPT_CODE, g.APPLICANT_CODE,
               h.EMP_NAME, d.DEPT_NAME, applicant.EMP_NAME AS APPLICANT_NAME
        FROM HRTATTAPLGENERAL g
        LEFT JOIN HRIMASTER h ON g.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON g.APPLICANT_CODE = applicant.EMP_CODE
        LEFT JOIN ORGDEPTMASTER d ON g.DEPT_CODE = d.DEPT_CODE
        LEFT JOIN HRTAPRHIST hist ON g.APPLY_GENERAL_NO = hist.APPLY_GENERAL_NO
        WHERE g.STATUS = '상신'
          AND hist.APPROVER_CODE = #{approverCode}
          AND hist.APPROVAL_STATUS = '대기'
          AND g.TARGET_DATE BETWEEN #{startDate} AND #{endDate}
          <if test="applyType != null and applyType != ''">
            AND g.APPLY_TYPE = #{applyType}
          </if>
          <if test="empCode != null and empCode != ''">
            AND g.EMP_CODE = #{empCode}
          </if>
        ORDER BY g.APPLY_GENERAL_NO DESC
        </script>
    """)
    List<AttendanceApplyGeneral> findPendingGeneralApprovals(@Param("approverCode") String approverCode,
                                                             @Param("startDate") String startDate,
                                                             @Param("endDate") String endDate,
                                                             @Param("applyType") String applyType,
                                                             @Param("empCode") String empCode);

    // 결재할 기타근태 문서 조회
    @Select("""
        <script>
        SELECT e.APPLY_ETC_NO, e.EMP_CODE, e.SHIFT_CODE, e.APPLY_DATE,
               e.TARGET_START_DATE, e.TARGET_END_DATE, e.APPLY_DATE_TIME, e.REASON,
               e.STATUS, e.DEPT_CODE, e.APPLICANT_CODE,
               h.EMP_NAME, d.DEPT_NAME, applicant.EMP_NAME AS APPLICANT_NAME,
               s.SHIFT_NAME
        FROM HRTATTAPLETC e
        LEFT JOIN HRIMASTER h ON e.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON e.APPLICANT_CODE = applicant.EMP_CODE
        LEFT JOIN ORGDEPTMASTER d ON e.DEPT_CODE = d.DEPT_CODE
        LEFT JOIN HRTSHIFTMASTER s ON e.SHIFT_CODE = s.SHIFT_CODE
        LEFT JOIN HRTAPRHIST hist ON e.APPLY_ETC_NO = hist.APPLY_ETC_NO
        WHERE e.STATUS = '상신'
          AND hist.APPROVER_CODE = #{approverCode}
          AND hist.APPROVAL_STATUS = '대기'
          AND e.TARGET_START_DATE BETWEEN #{startDate} AND #{endDate}
          <if test="empCode != null and empCode != ''">
            AND e.EMP_CODE = #{empCode}
          </if>
        ORDER BY e.APPLY_ETC_NO DESC
        </script>
    """)
    List<AttendanceApplyEtc> findPendingEtcApprovals(@Param("approverCode") String approverCode,
                                                     @Param("startDate") String startDate,
                                                     @Param("endDate") String endDate,
                                                     @Param("empCode") String empCode);

    // 승인된 일반근태 문서 조회
    @Select("""
        <script>
        SELECT g.APPLY_GENERAL_NO, g.EMP_CODE, g.TIME_ITEM_CODE, g.APPLY_DATE, 
               g.TARGET_DATE, g.START_TIME, g.END_TIME, g.APPLY_TYPE, g.STATUS,
               g.DEPT_CODE, g.APPLICANT_CODE,
               h.EMP_NAME, d.DEPT_NAME, applicant.EMP_NAME AS APPLICANT_NAME
        FROM HRTATTAPLGENERAL g
        LEFT JOIN HRIMASTER h ON g.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON g.APPLICANT_CODE = applicant.EMP_CODE
        LEFT JOIN ORGDEPTMASTER d ON g.DEPT_CODE = d.DEPT_CODE
        WHERE g.STATUS = '승인완료'
          AND g.TARGET_DATE BETWEEN #{startDate} AND #{endDate}
          AND (
            -- 일반 결재 승인 문서
            EXISTS (
              SELECT 1 FROM HRTAPRHIST hist 
              WHERE g.APPLY_GENERAL_NO = hist.APPLY_GENERAL_NO
                AND hist.APPROVER_CODE = #{approverCode}
                AND hist.APPROVAL_STATUS = '승인'
            )
            OR
            -- 부서장 자동승인 문서 (결재이력이 없는 경우)
            (g.APPLICANT_CODE = #{approverCode} AND NOT EXISTS (
              SELECT 1 FROM HRTAPRHIST hist2 
              WHERE g.APPLY_GENERAL_NO = hist2.APPLY_GENERAL_NO
            ))
          )
          <if test="applyType != null and applyType != ''">
            AND g.APPLY_TYPE = #{applyType}
          </if>
          <if test="empCode != null and empCode != ''">
            AND g.EMP_CODE = #{empCode}
          </if>
        ORDER BY g.APPLY_GENERAL_NO DESC
        </script>
    """)
    List<AttendanceApplyGeneral> findApprovedGeneralApprovals(@Param("approverCode") String approverCode,
                                                              @Param("startDate") String startDate,
                                                              @Param("endDate") String endDate,
                                                              @Param("applyType") String applyType,
                                                              @Param("empCode") String empCode);

    // 승인된 기타근태 문서 조회
    @Select("""
        <script>
        SELECT e.APPLY_ETC_NO, e.EMP_CODE, e.SHIFT_CODE, e.APPLY_DATE,
               e.TARGET_START_DATE, e.TARGET_END_DATE, e.APPLY_DATE_TIME, e.REASON,
               e.STATUS, e.DEPT_CODE, e.APPLICANT_CODE,
               h.EMP_NAME, d.DEPT_NAME, applicant.EMP_NAME AS APPLICANT_NAME,
               s.SHIFT_NAME
        FROM HRTATTAPLETC e
        LEFT JOIN HRIMASTER h ON e.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON e.APPLICANT_CODE = applicant.EMP_CODE
        LEFT JOIN ORGDEPTMASTER d ON e.DEPT_CODE = d.DEPT_CODE
        LEFT JOIN HRTSHIFTMASTER s ON e.SHIFT_CODE = s.SHIFT_CODE
        WHERE e.STATUS = '승인완료'
          AND e.TARGET_START_DATE BETWEEN #{startDate} AND #{endDate}
          AND (
            -- 일반 결재 승인 문서
            EXISTS (
              SELECT 1 FROM HRTAPRHIST hist 
              WHERE e.APPLY_ETC_NO = hist.APPLY_ETC_NO
                AND hist.APPROVER_CODE = #{approverCode}
                AND hist.APPROVAL_STATUS = '승인'
            )
            OR
            -- 부서장 자동승인 문서 (결재이력이 없는 경우)
            (e.APPLICANT_CODE = #{approverCode} AND NOT EXISTS (
              SELECT 1 FROM HRTAPRHIST hist2 
              WHERE e.APPLY_ETC_NO = hist2.APPLY_ETC_NO
            ))
          )
          <if test="empCode != null and empCode != ''">
            AND e.EMP_CODE = #{empCode}
          </if>
        ORDER BY e.APPLY_ETC_NO DESC
        </script>
    """)
    List<AttendanceApplyEtc> findApprovedEtcApprovals(@Param("approverCode") String approverCode,
                                                      @Param("startDate") String startDate,
                                                      @Param("endDate") String endDate,
                                                      @Param("empCode") String empCode);

    // 반려된 일반근태 문서 조회
    @Select("""
        <script>
        SELECT g.APPLY_GENERAL_NO, g.EMP_CODE, g.TIME_ITEM_CODE, g.APPLY_DATE, 
               g.TARGET_DATE, g.START_TIME, g.END_TIME, g.APPLY_TYPE, g.STATUS,
               g.DEPT_CODE, g.APPLICANT_CODE,
               h.EMP_NAME, d.DEPT_NAME, applicant.EMP_NAME AS APPLICANT_NAME
        FROM HRTATTAPLGENERAL g
        LEFT JOIN HRIMASTER h ON g.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON g.APPLICANT_CODE = applicant.EMP_CODE
        LEFT JOIN ORGDEPTMASTER d ON g.DEPT_CODE = d.DEPT_CODE
        LEFT JOIN HRTAPRHIST hist ON g.APPLY_GENERAL_NO = hist.APPLY_GENERAL_NO
        WHERE g.STATUS = '반려'
          AND hist.APPROVER_CODE = #{approverCode}
          AND hist.APPROVAL_STATUS = '반려'
          AND g.TARGET_DATE BETWEEN #{startDate} AND #{endDate}
          <if test="applyType != null and applyType != ''">
            AND g.APPLY_TYPE = #{applyType}
          </if>
          <if test="empCode != null and empCode != ''">
            AND g.EMP_CODE = #{empCode}
          </if>
        ORDER BY g.APPLY_GENERAL_NO DESC
        </script>
    """)
    List<AttendanceApplyGeneral> findRejectedGeneralApprovals(@Param("approverCode") String approverCode,
                                                              @Param("startDate") String startDate,
                                                              @Param("endDate") String endDate,
                                                              @Param("applyType") String applyType,
                                                              @Param("empCode") String empCode);

    // 반려된 기타근태 문서 조회
    @Select("""
        <script>
        SELECT e.APPLY_ETC_NO, e.EMP_CODE, e.SHIFT_CODE, e.APPLY_DATE,
               e.TARGET_START_DATE, e.TARGET_END_DATE, e.APPLY_DATE_TIME, e.REASON,
               e.STATUS, e.DEPT_CODE, e.APPLICANT_CODE,
               h.EMP_NAME, d.DEPT_NAME, applicant.EMP_NAME AS APPLICANT_NAME,
               s.SHIFT_NAME
        FROM HRTATTAPLETC e
        LEFT JOIN HRIMASTER h ON e.EMP_CODE = h.EMP_CODE
        LEFT JOIN HRIMASTER applicant ON e.APPLICANT_CODE = applicant.EMP_CODE
        LEFT JOIN ORGDEPTMASTER d ON e.DEPT_CODE = d.DEPT_CODE
        LEFT JOIN HRTSHIFTMASTER s ON e.SHIFT_CODE = s.SHIFT_CODE
        LEFT JOIN HRTAPRHIST hist ON e.APPLY_ETC_NO = hist.APPLY_ETC_NO
        WHERE e.STATUS = '반려'
          AND hist.APPROVER_CODE = #{approverCode}
          AND hist.APPROVAL_STATUS = '반려'
          AND e.TARGET_START_DATE BETWEEN #{startDate} AND #{endDate}
          <if test="empCode != null and empCode != ''">
            AND e.EMP_CODE = #{empCode}
          </if>
        ORDER BY e.APPLY_ETC_NO DESC
        </script>
    """)
    List<AttendanceApplyEtc> findRejectedEtcApprovals(@Param("approverCode") String approverCode,
                                                      @Param("startDate") String startDate,
                                                      @Param("endDate") String endDate,
                                                      @Param("empCode") String empCode);

    // 결재 처리
    @Update("""
        <script>
        UPDATE HRTAPRHIST SET
            APPROVAL_DATE = #{approvalDate},
            APPROVAL_STATUS = #{approvalStatus}
            <if test="rejectReason != null and rejectReason != ''">
            , REJECT_REASON = #{rejectReason}
            </if>
        WHERE APPROVAL_NO = #{approvalNo}
        </script>
    """)
    void updateApprovalHistory(ApprovalHistory history);

    // 일반근태 결재 이력 조회
    @Select("""
        SELECT APPROVAL_NO, APPLY_GENERAL_NO, APPROVER_CODE, APPROVAL_DATE, 
               APPROVAL_STATUS, REJECT_REASON
        FROM HRTAPRHIST
        WHERE APPLY_GENERAL_NO = #{applyGeneralNo}
        ORDER BY APPROVAL_NO ASC
    """)
    List<ApprovalHistory> findApprovalHistoryByGeneralNo(String applyGeneralNo);

    // 기타근태 결재 이력 조회
    @Select("""
        SELECT APPROVAL_NO, APPLY_ETC_NO, APPROVER_CODE, APPROVAL_DATE, 
               APPROVAL_STATUS, REJECT_REASON
        FROM HRTAPRHIST
        WHERE APPLY_ETC_NO = #{applyEtcNo}
        ORDER BY APPROVAL_NO ASC
    """)
    List<ApprovalHistory> findApprovalHistoryByEtcNo(String applyEtcNo);

    // 일반근태 결재 이력 조회 (결재자별)
    @Select("""
        SELECT APPROVAL_NO, APPLY_GENERAL_NO, APPROVER_CODE, APPROVAL_DATE, 
               APPROVAL_STATUS, REJECT_REASON
        FROM HRTAPRHIST
        WHERE APPLY_GENERAL_NO = #{applyGeneralNo} 
        AND APPROVER_CODE = #{approverCode}
        AND APPROVAL_STATUS = '대기'
    """)
    ApprovalHistory findGeneralApprovalHistoryByApprover(@Param("applyGeneralNo") String applyGeneralNo,
                                                         @Param("approverCode") String approverCode);

    // 기타근태 결재 이력 조회 (결재자별)
    @Select("""
        SELECT APPROVAL_NO, APPLY_ETC_NO, APPROVER_CODE, APPROVAL_DATE, 
               APPROVAL_STATUS, REJECT_REASON
        FROM HRTAPRHIST
        WHERE APPLY_ETC_NO = #{applyEtcNo} 
        AND APPROVER_CODE = #{approverCode}
        AND APPROVAL_STATUS = '대기'
    """)
    ApprovalHistory findEtcApprovalHistoryByApprover(@Param("applyEtcNo") String applyEtcNo,
                                                     @Param("approverCode") String approverCode);
}
