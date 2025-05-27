package com.jb.ess.attendance.controller;

import com.jb.ess.attendance.service.AttendanceApplyService;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.security.CustomUserDetails;
import com.jb.ess.common.util.DateUtil;
import com.jb.ess.depart.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/user/apply")
@RequiredArgsConstructor
public class AttendanceApplyController {

    private final AttendanceApplyService attendanceApplyService;
    private final EmployeeService employeeService;

    /**
     * 근태신청 페이지
     */
    @GetMapping("/")
    public String showApplyPage(Model model, @AuthenticationPrincipal CustomUserDetails user) {
        String empCode = user.getUsername();
        Employee employee = employeeService.findIsHeader(empCode);

        // 기본값 설정
        model.addAttribute("currentEmp", employee);
        model.addAttribute("today", DateUtil.getDateTimeNow());
        model.addAttribute("generalApplies", attendanceApplyService.getGeneralAppliesByApplicant(empCode));
        model.addAttribute("etcApplies", attendanceApplyService.getEtcAppliesByApplicant(empCode));

        return "user/apply";
    }

    /**
     * 신청 가능 사원 조회 (AJAX)
     */
    @GetMapping("/employees")
    @ResponseBody
    public List<Employee> getApplicableEmployees(@RequestParam String deptCode,
                                                 @RequestParam String workDate,
                                                 @AuthenticationPrincipal CustomUserDetails user) {
        String empCode = user.getUsername();
        Employee employee = employeeService.findIsHeader(empCode);
        String isLeader = employee.getIsHeader();

        return attendanceApplyService.getApplicableEmployees(deptCode, workDate, empCode, isLeader);
    }

    /**
     * 일반근태 신청 저장
     */
    @PostMapping("/general")
    @ResponseBody
    public String saveGeneralApply(@RequestBody AttendanceApplyGeneral apply,
                                   @AuthenticationPrincipal CustomUserDetails user) {
        try {
            apply.setApplicantCode(user.getUsername());
            attendanceApplyService.saveGeneralApply(apply);
            return "success";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    /**
     * 기타근태 신청 저장
     */
    @PostMapping("/etc")
    @ResponseBody
    public String saveEtcApply(@RequestBody AttendanceApplyEtc apply,
                               @AuthenticationPrincipal CustomUserDetails user) {
        try {
            apply.setApplicantCode(user.getUsername());
            attendanceApplyService.saveEtcApply(apply);
            return "success";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    /**
     * 근태신청 상신
     */
    @PostMapping("/submit")
    @ResponseBody
    public String submitApply(@RequestParam String applyNo,
                              @RequestParam String applyType,
                              @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.submitApply(applyNo, applyType, user.getUsername());
            return "success";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }

    /**
     * 근태신청 삭제
     */
    @PostMapping("/delete")
    @ResponseBody
    public String deleteApply(@RequestParam String applyNo,
                              @RequestParam String applyType) {
        try {
            attendanceApplyService.deleteApply(applyNo, applyType);
            return "success";
        } catch (Exception e) {
            return "error: " + e.getMessage();
        }
    }
}
