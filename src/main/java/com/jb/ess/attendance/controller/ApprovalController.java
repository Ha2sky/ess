package com.jb.ess.attendance.controller;

import com.jb.ess.attendance.service.ApprovalService;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.ApprovalHistory;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.security.CustomUserDetails;
import com.jb.ess.common.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
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
                                   @RequestParam(required = false) String applyType,
                                   @RequestParam(required = false) String empCode,
                                   @RequestParam(required = false, defaultValue = "pending") String activeTab,
                                   @AuthenticationPrincipal CustomUserDetails user) {
        try {
            log.debug("근태승인 페이지 접근: 사용자={}, 활성탭={}", user.getUsername(), activeTab);

            // 부서장 권한 체크 강화
            Employee currentUser = approvalService.getCurrentEmployee(user.getUsername());
            if (currentUser == null) {
                log.error("사용자 정보를 찾을 수 없음: {}", user.getUsername());
                model.addAttribute("errorMessage", "사용자 정보를 찾을 수 없습니다.");
                return "error/403";
            }

            if (!"Y".equals(currentUser.getIsHeader())) {
                log.warn("부서장이 아닌 사용자의 접근 시도: {}", user.getUsername());
                model.addAttribute("errorMessage", "부서장만 접근 가능한 페이지입니다.");
                return "error/403";
            }

            if (startDate == null || endDate == null) {
                LocalDate today = LocalDate.now();
                LocalDate firstDay = today.withDayOfMonth(1);
                LocalDate lastDay = today.withDayOfMonth(today.lengthOfMonth());

                startDate = firstDay.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                endDate = lastDay.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

                log.debug("기본 날짜 설정: startDate={}, endDate={}", startDate, endDate);
            }

            String approverCode = user.getUsername();
            log.debug("결재 문서 조회: 결재자={}, 기간={} ~ {}, 활성탭={}", approverCode, startDate, endDate, activeTab);

            // 결재 문서 조회
            List<AttendanceApplyGeneral> pendingGenerals = approvalService.getPendingGeneralApprovals(approverCode, startDate, endDate, applyType, empCode);
            List<AttendanceApplyEtc> pendingEtcs = approvalService.getPendingEtcApprovals(approverCode, startDate, endDate, empCode);
            List<AttendanceApplyGeneral> approvedGenerals = approvalService.getApprovedGeneralApprovals(approverCode, startDate, endDate, applyType, empCode);
            List<AttendanceApplyEtc> approvedEtcs = approvalService.getApprovedEtcApprovals(approverCode, startDate, endDate, empCode);
            List<AttendanceApplyGeneral> rejectedGenerals = approvalService.getRejectedGeneralApprovals(approverCode, startDate, endDate, applyType, empCode);
            List<AttendanceApplyEtc> rejectedEtcs = approvalService.getRejectedEtcApprovals(approverCode, startDate, endDate, empCode);

            log.debug("조회 결과: 결재대기일반={}, 결재대기기타={}, 승인완료일반={}, 승인완료기타={}, 반려일반={}, 반려기타={}",
                    pendingGenerals.size(), pendingEtcs.size(), approvedGenerals.size(), approvedEtcs.size(), rejectedGenerals.size(), rejectedEtcs.size());

            model.addAttribute("pendingGenerals", pendingGenerals);
            model.addAttribute("pendingEtcs", pendingEtcs);
            model.addAttribute("approvedGenerals", approvedGenerals);
            model.addAttribute("approvedEtcs", approvedEtcs);
            model.addAttribute("rejectedGenerals", rejectedGenerals);
            model.addAttribute("rejectedEtcs", rejectedEtcs);
            model.addAttribute("startDate", DateUtil.formatDate(startDate));
            model.addAttribute("endDate", DateUtil.formatDate(endDate));
            model.addAttribute("applyType", applyType);
            model.addAttribute("empCode", empCode);
            model.addAttribute("activeTab", activeTab);
            model.addAttribute("currentUser", currentUser);

            return "user/approval";
        } catch (Exception e) {
            log.error("근태승인 페이지 로딩 실패", e);
            model.addAttribute("errorMessage", "페이지 로딩 중 오류가 발생했습니다: " + e.getMessage());
            return "error/500";
        }
    }

    /**
     * 신청 상세 정보 조회 API
     */
    @GetMapping("/detail/{type}/{applyNo}")
    @ResponseBody
    public Map<String, Object> getApplyDetail(@PathVariable String type, @PathVariable String applyNo) {
        try {
            log.debug("상세 정보 조회: type={}, applyNo={}", type, applyNo);
            Map<String, Object> result;

            if ("general".equals(type)) {
                result = approvalService.getGeneralApplyDetail(applyNo);
            } else {
                result = approvalService.getEtcApplyDetail(applyNo);
            }

            // 근태기 정보
            if (result != null && !result.containsKey("error")) {
                try {
                    List<Map<String, Object>> attendanceInfo = approvalService.getAttendanceInfo(type, applyNo);
                    result.put("attendanceInfo", attendanceInfo);
                    log.debug("근태기 정보 추가됨: {}", attendanceInfo.size());
                } catch (Exception e) {
                    log.warn("근태기 정보 조회 실패, 계속 진행: {}", e.getMessage());
                    result.put("attendanceInfo", List.of());
                }
            }

            return result;
        } catch (Exception e) {
            log.error("상세 정보 조회 실패: type={}, applyNo={}", type, applyNo, e);
            return Map.of("error", "상세 정보 조회에 실패했습니다.");
        }
    }

    /**
     * 일반근태 승인 처리
     */
    @PostMapping("/approve/general")
    @ResponseBody
    public String approveGeneralApply(@RequestParam String applyGeneralNo,
                                      @AuthenticationPrincipal CustomUserDetails user) {
        try {
            log.debug("일반근태 승인 처리: 신청번호={}, 결재자={}", applyGeneralNo, user.getUsername());

            // 부서장 권한 재확인
            Employee currentUser = approvalService.getCurrentEmployee(user.getUsername());
            if (currentUser == null || !"Y".equals(currentUser.getIsHeader())) {
                return "error: 부서장만 승인 가능합니다.";
            }

            // 승인 처리 시 연차 차감 및 실적 업데이트
            approvalService.approveGeneralApply(applyGeneralNo, user.getUsername());
            log.info("일반근태 승인 완료: 신청번호={}", applyGeneralNo);
            return "success";
        } catch (Exception e) {
            log.error("일반근태 승인 실패: 신청번호={}", applyGeneralNo, e);
            return "error: " + e.getMessage();
        }
    }

    /**
     * 기타근태 승인 처리
     */
    @PostMapping("/approve/etc")
    @ResponseBody
    public String approveEtcApply(@RequestParam String applyEtcNo,
                                  @AuthenticationPrincipal CustomUserDetails user) {
        try {
            log.debug("기타근태 승인 처리: 신청번호={}, 결재자={}", applyEtcNo, user.getUsername());

            // 부서장 권한 재확인
            Employee currentUser = approvalService.getCurrentEmployee(user.getUsername());
            if (currentUser == null || !"Y".equals(currentUser.getIsHeader())) {
                return "error: 부서장만 승인 가능합니다.";
            }

            // 승인 처리 시 연차 차감 및 실적 업데이트
            approvalService.approveEtcApply(applyEtcNo, user.getUsername());
            log.info("기타근태 승인 완료: 신청번호={}", applyEtcNo);
            return "success";
        } catch (Exception e) {
            log.error("기타근태 승인 실패: 신청번호={}", applyEtcNo, e);
            return "error: " + e.getMessage();
        }
    }

    /**
     * 일반근태 반려 처리
     */
    @PostMapping("/reject/general")
    @ResponseBody
    public String rejectGeneralApply(@RequestParam String applyGeneralNo,
                                     @RequestParam String rejectReason,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        try {
            log.debug("일반근태 반려 처리: 신청번호={}, 결재자={}", applyGeneralNo, user.getUsername());

            if (rejectReason == null || rejectReason.trim().isEmpty()) {
                return "error: 반려 사유를 입력해주세요.";
            }

            // 부서장 권한 재확인
            Employee currentUser = approvalService.getCurrentEmployee(user.getUsername());
            if (currentUser == null || !"Y".equals(currentUser.getIsHeader())) {
                return "error: 부서장만 반려 가능합니다.";
            }

            approvalService.rejectGeneralApply(applyGeneralNo, user.getUsername(), rejectReason);
            log.info("일반근태 반려 완료: 신청번호={}", applyGeneralNo);
            return "success";
        } catch (Exception e) {
            log.error("일반근태 반려 실패: 신청번호={}", applyGeneralNo, e);
            return "error: " + e.getMessage();
        }
    }

    /**
     * 기타근태 반려 처리
     */
    @PostMapping("/reject/etc")
    @ResponseBody
    public String rejectEtcApply(@RequestParam String applyEtcNo,
                                 @RequestParam String rejectReason,
                                 @AuthenticationPrincipal CustomUserDetails user) {
        try {
            log.debug("기타근태 반려 처리: 신청번호={}, 결재자={}", applyEtcNo, user.getUsername());

            if (rejectReason == null || rejectReason.trim().isEmpty()) {
                return "error: 반려 사유를 입력해주세요.";
            }

            // 부서장 권한 재확인
            Employee currentUser = approvalService.getCurrentEmployee(user.getUsername());
            if (currentUser == null || !"Y".equals(currentUser.getIsHeader())) {
                return "error: 부서장만 반려 가능합니다.";
            }

            approvalService.rejectEtcApply(applyEtcNo, user.getUsername(), rejectReason);
            log.info("기타근태 반려 완료: 신청번호={}", applyEtcNo);
            return "success";
        } catch (Exception e) {
            log.error("기타근태 반려 실패: 신청번호={}", applyEtcNo, e);
            return "error: " + e.getMessage();
        }
    }

    /**
     * 일반근태 결재 이력 조회 (AJAX)
     */
    @GetMapping("/history/general/{applyGeneralNo}")
    @ResponseBody
    public List<ApprovalHistory> getGeneralApprovalHistory(@PathVariable String applyGeneralNo) {
        try {
            log.debug("일반근태 결재 이력 조회: 신청번호={}", applyGeneralNo);
            return approvalService.getGeneralApprovalHistory(applyGeneralNo);
        } catch (Exception e) {
            log.error("일반근태 결재 이력 조회 실패: 신청번호={}", applyGeneralNo, e);
            return List.of();
        }
    }

    /**
     * 기타근태 결재 이력 조회 (AJAX)
     */
    @GetMapping("/history/etc/{applyEtcNo}")
    @ResponseBody
    public List<ApprovalHistory> getEtcApprovalHistory(@PathVariable String applyEtcNo) {
        try {
            log.debug("기타근태 결재 이력 조회: 신청번호={}", applyEtcNo);
            return approvalService.getEtcApprovalHistory(applyEtcNo);
        } catch (Exception e) {
            log.error("기타근태 결재 이력 조회 실패: 신청번호={}", applyEtcNo, e);
            return List.of();
        }
    }
}
