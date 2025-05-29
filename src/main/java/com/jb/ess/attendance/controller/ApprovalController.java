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
     * 일반근태 승인 처리 - 수정: applyNo -> applyGeneralNo
     */
    @PostMapping("/approve/general")
    @ResponseBody
    public String approveGeneralApply(@RequestParam String applyGeneralNo,
                                      @AuthenticationPrincipal CustomUserDetails user) {
        try {
            approvalService.approveGeneralApply(applyGeneralNo, user.getUsername());
            return "success";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    /**
     * 기타근태 승인 처리 - 수정: applyNo -> applyEtcNo
     */
    @PostMapping("/approve/etc")
    @ResponseBody
    public String approveEtcApply(@RequestParam String applyEtcNo,
                                  @AuthenticationPrincipal CustomUserDetails user) {
        try {
            approvalService.approveEtcApply(applyEtcNo, user.getUsername());
            return "success";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    /**
     * 일반근태 반려 처리 - 수정: applyNo -> applyGeneralNo
     */
    @PostMapping("/reject/general")
    @ResponseBody
    public String rejectGeneralApply(@RequestParam String applyGeneralNo,
                                     @RequestParam String rejectReason,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        try {
            approvalService.rejectGeneralApply(applyGeneralNo, user.getUsername(), rejectReason);
            return "success";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    /**
     * 기타근태 반려 처리 - 수정: applyNo -> applyEtcNo
     */
    @PostMapping("/reject/etc")
    @ResponseBody
    public String rejectEtcApply(@RequestParam String applyEtcNo,
                                 @RequestParam String rejectReason,
                                 @AuthenticationPrincipal CustomUserDetails user) {
        try {
            approvalService.rejectEtcApply(applyEtcNo, user.getUsername(), rejectReason);
            return "success";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    /**
     * 일반근태 결재 이력 조회 (AJAX) - 수정: applyNo -> applyGeneralNo
     */
    @GetMapping("/history/general/{applyGeneralNo}")
    @ResponseBody
    public List<ApprovalHistory> getGeneralApprovalHistory(@PathVariable String applyGeneralNo) {
        return approvalService.getGeneralApprovalHistory(applyGeneralNo);
    }

    /**
     * 기타근태 결재 이력 조회 (AJAX) - 수정: applyNo -> applyEtcNo
     */
    @GetMapping("/history/etc/{applyEtcNo}")
    @ResponseBody
    public List<ApprovalHistory> getEtcApprovalHistory(@PathVariable String applyEtcNo) {
        return approvalService.getEtcApprovalHistory(applyEtcNo);
    }
}
