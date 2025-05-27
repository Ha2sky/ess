package com.jb.ess.attendance.controller;

import com.jb.ess.attendance.service.ApprovalService;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.ApprovalHistory;
import com.jb.ess.common.security.CustomUserDetails;
import com.jb.ess.common.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Controller
@RequestMapping("/user/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    /**
     * 근태승인 페이지 (부서장만 접근 가능)
     */
    @GetMapping("/")
    public String showApprovalPage(Model model,
                                   @RequestParam(required = false) String startDate,
                                   @RequestParam(required = false) String endDate,
                                   @AuthenticationPrincipal CustomUserDetails user) {

        // 기본 날짜 설정 (이번 달 1일 ~ 마지막일)
        if (startDate == null || endDate == null) {
            YearMonth thisMonth = YearMonth.now();
            startDate = DateUtil.reverseFormatDate(thisMonth.atDay(1));
            endDate = DateUtil.reverseFormatDate(thisMonth.atEndOfMonth());
        }

        String approverCode = user.getUsername();

        // 결재할 문서, 승인된 문서, 반려된 문서 조회
        List<AttendanceApplyGeneral> pendingGenerals = approvalService.getPendingGeneralApprovals(approverCode, startDate, endDate);
        List<AttendanceApplyEtc> pendingEtcs = approvalService.getPendingEtcApprovals(approverCode, startDate, endDate);
        List<AttendanceApplyGeneral> approvedGenerals = approvalService.getApprovedGeneralApprovals(approverCode, startDate, endDate);
        List<AttendanceApplyGeneral> rejectedGenerals = approvalService.getRejectedGeneralApprovals(approverCode, startDate, endDate);

        model.addAttribute("pendingGenerals", pendingGenerals);
        model.addAttribute("pendingEtcs", pendingEtcs);
        model.addAttribute("approvedGenerals", approvedGenerals);
        model.addAttribute("rejectedGenerals", rejectedGenerals);
        model.addAttribute("startDate", DateUtil.formatDate(startDate));
        model.addAttribute("endDate", DateUtil.formatDate(endDate));

        return "user/approval";
    }

    /**
     * 근태 승인 처리
     */
    @PostMapping("/approve")
    @ResponseBody
    public String approveApply(@RequestParam String applyNo,
                               @RequestParam String applyType,
                               @AuthenticationPrincipal CustomUserDetails user) {
        try {
            approvalService.approveApply(applyNo, applyType, user.getUsername());
            return "success";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    /**
     * 근태 반려 처리
     */
    @PostMapping("/reject")
    @ResponseBody
    public String rejectApply(@RequestParam String applyNo,
                              @RequestParam String applyType,
                              @RequestParam String rejectReason,
                              @AuthenticationPrincipal CustomUserDetails user) {
        try {
            approvalService.rejectApply(applyNo, applyType, user.getUsername(), rejectReason);
            return "success";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    /**
     * 결재 이력 조회 (AJAX)
     */
    @GetMapping("/history/{applyNo}")
    @ResponseBody
    public List<ApprovalHistory> getApprovalHistory(@PathVariable String applyNo) {
        return approvalService.getApprovalHistory(applyNo);
    }
}
