package com.jb.ess.attendance.service;

import com.jb.ess.common.mapper.ApprovalMapper;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.ApprovalHistory;
import com.jb.ess.common.mapper.AttendanceApplyMapper;
import com.jb.ess.common.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalMapper approvalMapper;
    private final AttendanceApplyMapper attendanceApplyMapper;

    /**
     * 결재할 문서 조회
     */
    public List<AttendanceApplyGeneral> getPendingGeneralApprovals(String approverCode, String startDate, String endDate) {
        return approvalMapper.findPendingGeneralApprovals(approverCode, startDate, endDate);
    }

    public List<AttendanceApplyEtc> getPendingEtcApprovals(String approverCode, String startDate, String endDate) {
        return approvalMapper.findPendingEtcApprovals(approverCode, startDate, endDate);
    }

    /**
     * 승인된 문서 조회
     */
    public List<AttendanceApplyGeneral> getApprovedGeneralApprovals(String approverCode, String startDate, String endDate) {
        return approvalMapper.findApprovedGeneralApprovals(approverCode, startDate, endDate);
    }

    /**
     * 반려된 문서 조회
     */
    public List<AttendanceApplyGeneral> getRejectedGeneralApprovals(String approverCode, String startDate, String endDate) {
        return approvalMapper.findRejectedGeneralApprovals(approverCode, startDate, endDate);
    }

    /**
     * 일반근태 승인 처리 - 수정: applyNo -> applyGeneralNo 분리
     */
    @Transactional
    public void approveGeneralApply(String applyGeneralNo, String approverCode) {
        // 결재 이력 업데이트
        ApprovalHistory currentHistory = approvalMapper.findGeneralApprovalHistoryByApprover(applyGeneralNo, approverCode);
        if (currentHistory == null) {
            throw new RuntimeException("결재 이력을 찾을 수 없습니다.");
        }

        currentHistory.setApprovalDate(DateUtil.getDateTimeNow());
        currentHistory.setApprovalStatus("승인");
        approvalMapper.updateApprovalHistory(currentHistory);

        // 신청 상태 업데이트
        attendanceApplyMapper.updateGeneralApplyStatus(applyGeneralNo, "승인완료");
    }

    /**
     * 기타근태 승인 처리 - 수정: applyNo -> applyEtcNo 분리
     */
    @Transactional
    public void approveEtcApply(String applyEtcNo, String approverCode) {
        // 결재 이력 업데이트
        ApprovalHistory currentHistory = approvalMapper.findEtcApprovalHistoryByApprover(applyEtcNo, approverCode);
        if (currentHistory == null) {
            throw new RuntimeException("결재 이력을 찾을 수 없습니다.");
        }

        currentHistory.setApprovalDate(DateUtil.getDateTimeNow());
        currentHistory.setApprovalStatus("승인");
        approvalMapper.updateApprovalHistory(currentHistory);

        // 신청 상태 업데이트
        attendanceApplyMapper.updateEtcApplyStatus(applyEtcNo, "승인완료");
    }

    /**
     * 일반근태 반려 처리 - 수정: applyNo -> applyGeneralNo 분리
     */
    @Transactional
    public void rejectGeneralApply(String applyGeneralNo, String approverCode, String rejectReason) {
        // 결재 이력 업데이트
        ApprovalHistory currentHistory = approvalMapper.findGeneralApprovalHistoryByApprover(applyGeneralNo, approverCode);
        if (currentHistory == null) {
            throw new RuntimeException("결재 이력을 찾을 수 없습니다.");
        }

        currentHistory.setApprovalDate(DateUtil.getDateTimeNow());
        currentHistory.setApprovalStatus("반려");
        currentHistory.setRejectReason(rejectReason);
        approvalMapper.updateApprovalHistory(currentHistory);

        // 신청 상태 업데이트
        attendanceApplyMapper.updateGeneralApplyStatus(applyGeneralNo, "반려");
    }

    /**
     * 기타근태 반려 처리 - 수정: applyNo -> applyEtcNo 분리
     */
    @Transactional
    public void rejectEtcApply(String applyEtcNo, String approverCode, String rejectReason) {
        // 결재 이력 업데이트
        ApprovalHistory currentHistory = approvalMapper.findEtcApprovalHistoryByApprover(applyEtcNo, approverCode);
        if (currentHistory == null) {
            throw new RuntimeException("결재 이력을 찾을 수 없습니다.");
        }

        currentHistory.setApprovalDate(DateUtil.getDateTimeNow());
        currentHistory.setApprovalStatus("반려");
        currentHistory.setRejectReason(rejectReason);
        approvalMapper.updateApprovalHistory(currentHistory);

        // 신청 상태 업데이트
        attendanceApplyMapper.updateEtcApplyStatus(applyEtcNo, "반려");
    }

    /**
     * 일반근태 결재 이력 조회 - 수정: applyNo -> applyGeneralNo 분리
     */
    public List<ApprovalHistory> getGeneralApprovalHistory(String applyGeneralNo) {
        return approvalMapper.findApprovalHistoryByGeneralNo(applyGeneralNo);
    }

    /**
     * 기타근태 결재 이력 조회 - 수정: applyNo -> applyEtcNo 분리
     */
    public List<ApprovalHistory> getEtcApprovalHistory(String applyEtcNo) {
        return approvalMapper.findApprovalHistoryByEtcNo(applyEtcNo);
    }
}
