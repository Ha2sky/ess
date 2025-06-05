package com.jb.ess.attendance.controller;

import com.jb.ess.attendance.service.AttendanceApplyService;
import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.domain.Department;
import com.jb.ess.common.domain.AnnualDetail;
import com.jb.ess.common.domain.ShiftMaster;
import com.jb.ess.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/user/apply")
@RequiredArgsConstructor
public class AttendanceApplyController {
    private final AttendanceApplyService attendanceApplyService;

    // 근태 신청 페이지 로드 - 현재 사용자 정보 포함 (신청내역조회기능 제거)
    @GetMapping("/")
    public String attendanceApplyPage(Model model, @AuthenticationPrincipal CustomUserDetails user) {
        String empCode = user.getUsername();
        Employee currentEmp = attendanceApplyService.getCurrentEmployee(empCode);

        // 부서 정보 조회
        Department department = attendanceApplyService.getDepartmentInfo(currentEmp.getDeptCode());
        currentEmp.setDeptName(department.getDeptName());

        // 수정: 부서장인 경우 하위부서 목록도 포함 (요청사항 5)
        List<Department> availableDepartments;
        if ("Y".equals(currentEmp.getIsHeader())) {
            availableDepartments = attendanceApplyService.getSubDepartments(currentEmp.getDeptCode());
        } else {
            availableDepartments = List.of(department);
        }

        // 수정: 연차잔여 정보 추가 (요청사항 2)
        AnnualDetail annualDetail = attendanceApplyService.getAnnualDetail(empCode);

        // 수정: 근태 마스터 목록 조회 - 필터링 추가 (요청사항 3)
        List<ShiftMaster> shiftMasters = attendanceApplyService.getFilteredShiftMasters();

        // 현재 사용자 정보 추가
        model.addAttribute("currentEmp", currentEmp);
        model.addAttribute("availableDepartments", availableDepartments);
        model.addAttribute("annualDetail", annualDetail);
        model.addAttribute("shiftMasters", shiftMasters);
        model.addAttribute("today", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

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

    // 수정: 연차잔여 조회 API 추가 (요청사항 2)
    @GetMapping("/annual/{empCode}")
    @ResponseBody
    public AnnualDetail getAnnualDetail(@PathVariable String empCode) {
        return attendanceApplyService.getAnnualDetail(empCode);
    }

    // 수정: 근무계획/실적/예상근로시간 조회 API 추가 (요청사항 4)
    @GetMapping("/workInfo/{empCode}/{workDate}")
    @ResponseBody
    public Map<String, Object> getWorkInfo(@PathVariable String empCode, @PathVariable String workDate) {
        return attendanceApplyService.getWorkInfo(empCode, workDate);
    }

    // 수정: 근태 마스터 목록 조회 API 추가 (요청사항 3)
    @GetMapping("/shift-masters")
    @ResponseBody
    public List<ShiftMaster> getShiftMasters() {
        return attendanceApplyService.getFilteredShiftMasters();
    }

    // 일반근태 신청 저장 API - 수정: 저장 로직 개선 (요청사항 6)
    @PostMapping("/general")
    @ResponseBody
    public String saveGeneralApply(@RequestBody AttendanceApplyGeneral apply,
                                   @AuthenticationPrincipal CustomUserDetails user) {
        try {
            apply.setApplicantCode(user.getUsername());
            apply.setApplyDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            apply.setStatus("저장");

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

    // 기타근태 신청 저장 API - 수정: 저장 로직 개선 (요청사항 6)
    @PostMapping("/etc")
    @ResponseBody
    public String saveEtcApply(@RequestBody AttendanceApplyEtc apply,
                               @AuthenticationPrincipal CustomUserDetails user) {
        try {
            apply.setApplicantCode(user.getUsername());
            apply.setApplyDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            apply.setStatus("저장");

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

    // 일반근태 신청 상신 API (요청사항 6)
    @PostMapping("/submit/general")
    @ResponseBody
    public String submitGeneralApply(@RequestParam String applyGeneralNo,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.submitGeneralApply(applyGeneralNo, user.getUsername());
            return "success";
        } catch (Exception e) {
            return "상신에 실패했습니다: " + e.getMessage();
        }
    }

    // 기타근태 신청 상신 API (요청사항 6)
    @PostMapping("/submit/etc")
    @ResponseBody
    public String submitEtcApply(@RequestParam String applyEtcNo,
                                 @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.submitEtcApply(applyEtcNo, user.getUsername());
            return "success";
        } catch (Exception e) {
            return "상신에 실패했습니다: " + e.getMessage();
        }
    }

    // 수정: 일반근태 신청 상신취소 API 추가 (요청사항 6)
    @PostMapping("/cancel/general")
    @ResponseBody
    public String cancelGeneralApply(@RequestParam String applyGeneralNo,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.cancelGeneralApply(applyGeneralNo, user.getUsername());
            return "success";
        } catch (Exception e) {
            return "상신취소에 실패했습니다: " + e.getMessage();
        }
    }

    // 수정: 기타근태 신청 상신취소 API 추가 (요청사항 6)
    @PostMapping("/cancel/etc")
    @ResponseBody
    public String cancelEtcApply(@RequestParam String applyEtcNo,
                                 @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.cancelEtcApply(applyEtcNo, user.getUsername());
            return "success";
        } catch (Exception e) {
            return "상신취소에 실패했습니다: " + e.getMessage();
        }
    }

    // 일반근태 신청 삭제 API (요청사항 6)
    @PostMapping("/delete/general")
    @ResponseBody
    public String deleteGeneralApply(@RequestParam String applyGeneralNo,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.deleteGeneralApply(applyGeneralNo, user.getUsername());
            return "success";
        } catch (Exception e) {
            return "삭제에 실패했습니다: " + e.getMessage();
        }
    }

    // 기타근태 신청 삭제 API (요청사항 6)
    @PostMapping("/delete/etc")
    @ResponseBody
    public String deleteEtcApply(@RequestParam String applyEtcNo,
                                 @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.deleteEtcApply(applyEtcNo, user.getUsername());
            return "success";
        } catch (Exception e) {
            return "삭제에 실패했습니다: " + e.getMessage();
        }
    }
}
