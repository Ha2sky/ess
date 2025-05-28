package com.jb.ess.attendance.controller;

import com.jb.ess.attendance.service.AttendanceApplyService;
import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequestMapping("/user/apply")
@RequiredArgsConstructor
public class AttendanceApplyController {
    private final AttendanceApplyService attendanceApplyService;

    // 근태 신청 페이지 로드 - 현재 사용자 정보와 신청 내역 포함
    @GetMapping("/")
    public String attendanceApplyPage(Model model, @AuthenticationPrincipal CustomUserDetails user) {
        String empCode = user.getUsername();
        Employee currentEmp = attendanceApplyService.getCurrentEmployee(empCode);

        // 현재 사용자 정보 추가
        model.addAttribute("currentEmp", currentEmp);
        model.addAttribute("today", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

        // 신청 내역 조회 추가 - 부서장인 경우 부서원 전체, 일반 사원인 경우 본인만
        List<AttendanceApplyGeneral> generalApplies;
        List<AttendanceApplyEtc> etcApplies;

        if ("Y".equals(currentEmp.getIsHeader())) {
            // 부서장인 경우 부서 전체 신청 내역 조회
            generalApplies = attendanceApplyService.getGeneralAppliesByDept(currentEmp.getDeptCode());
            etcApplies = attendanceApplyService.getEtcAppliesByDept(currentEmp.getDeptCode());
        } else {
            // 일반 사원인 경우 본인 신청 내역만 조회
            generalApplies = attendanceApplyService.getGeneralAppliesByApplicant(empCode);
            etcApplies = attendanceApplyService.getEtcAppliesByApplicant(empCode);
        }

        model.addAttribute("generalApplies", generalApplies);
        model.addAttribute("etcApplies", etcApplies);

        return "user/apply";
    }

    // 부서별 사원 조회 API - 부서장과 일반사원에 따른 다른 로직 적용
    @GetMapping("/employees")
    @ResponseBody
    public List<Employee> getEmployeesByDept(@RequestParam String deptCode,
                                             @RequestParam String workDate,
                                             @RequestParam(required = false) String workPlan,
                                             @AuthenticationPrincipal CustomUserDetails user) {

        String empCode = user.getUsername();
        Employee currentEmp = attendanceApplyService.getCurrentEmployee(empCode);

        // 부서장인 경우 부서원 전체, 일반 사원인 경우 본인만 조회
        if ("Y".equals(currentEmp.getIsHeader())) {
            // 부서장: 부서 전체 사원 조회
            return attendanceApplyService.getEmployeesByDept(deptCode, workDate, workPlan);
        } else {
            // 일반 사원: 본인만 조회
            return attendanceApplyService.getCurrentEmployeeList(empCode, workDate);
        }
    }

    // 일반근태 신청 저장 API
    @PostMapping("/general")
    @ResponseBody
    public String saveGeneralApply(@RequestBody AttendanceApplyGeneral apply,
                                   @AuthenticationPrincipal CustomUserDetails user) {
        try {
            apply.setApplicantCode(user.getUsername());
            apply.setApplyDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            apply.setStatus("대기");

            // 신청 유효성 검증
            String validationResult = attendanceApplyService.validateGeneralApply(apply);
            if (!"valid".equals(validationResult)) {
                return validationResult;
            }

            attendanceApplyService.saveGeneralApply(apply);
            return "success";
        } catch (Exception e) {
            return "저장에 실패했습니다: " + e.getMessage();
        }
    }

    // 기타근태 신청 저장 API
    @PostMapping("/etc")
    @ResponseBody
    public String saveEtcApply(@RequestBody AttendanceApplyEtc apply,
                               @AuthenticationPrincipal CustomUserDetails user) {
        try {
            apply.setApplicantCode(user.getUsername());
            apply.setApplyDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            apply.setStatus("대기");

            // 신청 유효성 검증
            String validationResult = attendanceApplyService.validateEtcApply(apply);
            if (!"valid".equals(validationResult)) {
                return validationResult;
            }

            attendanceApplyService.saveEtcApply(apply);
            return "success";
        } catch (Exception e) {
            return "저장에 실패했습니다: " + e.getMessage();
        }
    }

    // 신청 상신 API
    @PostMapping("/submit")
    @ResponseBody
    public String submitApply(@RequestParam String applyNo,
                              @RequestParam String applyType,
                              @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.submitApply(applyNo, applyType, user.getUsername());
            return "success";
        } catch (Exception e) {
            return "상신에 실패했습니다: " + e.getMessage();
        }
    }

    // 신청 삭제 API
    @PostMapping("/delete")
    @ResponseBody
    public String deleteApply(@RequestParam String applyNo,
                              @RequestParam String applyType,
                              @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.deleteApply(applyNo, applyType, user.getUsername());
            return "success";
        } catch (Exception e) {
            return "삭제에 실패했습니다: " + e.getMessage();
        }
    }
}