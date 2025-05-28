package com.jb.ess.attendance.service;

import com.jb.ess.attendance.mapper.ApprovalMapper;
import com.jb.ess.attendance.mapper.AttendanceApplyMapper;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.ApprovalHistory;
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
     * 근태 승인 처리
     */
    @Transactional
    public void approveApply(String applyNo, String applyType, String approverCode) {
        // 결재 이력 업데이트
        List<ApprovalHistory> histories = approvalMapper.findApprovalHistoryByApplyNo(applyNo);
        ApprovalHistory currentHistory = histories.stream()
                .filter(h -> h.getApproverCode().equals(approverCode))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("결재 이력을 찾을 수 없습니다."));

        currentHistory.setApprovalDate(DateUtil.getDateTimeNow());
        currentHistory.setApprovalStatus("승인");
        approvalMapper.updateApprovalHistory(currentHistory);

        // 신청 상태 업데이트
        if ("general".equals(applyType)) {
            attendanceApplyMapper.updateGeneralStatus(applyNo, "승인완료");
        } else {
            attendanceApplyMapper.updateEtcStatus(applyNo, "승인완료");
        }
    }

    /**
     * 근태 반려 처리
     */
    @Transactional
    public void rejectApply(String applyNo, String applyType, String approverCode, String rejectReason) {
        // 결재 이력 업데이트
        List<ApprovalHistory> histories = approvalMapper.findApprovalHistoryByApplyNo(applyNo);
        ApprovalHistory currentHistory = histories.stream()
                .filter(h -> h.getApproverCode().equals(approverCode))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("결재 이력을 찾을 수 없습니다."));

        currentHistory.setApprovalDate(DateUtil.getDateTimeNow());
        currentHistory.setApprovalStatus("반려");
        currentHistory.setRejectReason(rejectReason);
        approvalMapper.updateApprovalHistory(currentHistory);

        // 신청 상태 업데이트
        if ("general".equals(applyType)) {
            attendanceApplyMapper.updateGeneralStatus(applyNo, "반려");
        } else {
            attendanceApplyMapper.updateEtcStatus(applyNo, "반려");
        }
    }

    /**
     * 결재 이력 조회
     */
    public List<ApprovalHistory> getApprovalHistory(String applyNo) {
        return approvalMapper.findApprovalHistoryByApplyNo(applyNo);
    }
}
