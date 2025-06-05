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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/user/apply")
@RequiredArgsConstructor
public class AttendanceApplyController {
    private final AttendanceApplyService attendanceApplyService;

    // 근태 신청 페이지 로드
    @GetMapping("/")
    public String attendanceApplyPage(Model model, @AuthenticationPrincipal CustomUserDetails user) {
        try {
            String empCode = user.getUsername();
            Employee currentEmp = attendanceApplyService.getCurrentEmployee(empCode);

            if (currentEmp == null) {
                log.error("사용자 정보를 찾을 수 없음: {}", empCode);
                throw new RuntimeException("사용자 정보를 찾을 수 없습니다.");
            }

            // 부서 정보 조회
            Department department = attendanceApplyService.getDepartmentInfo(currentEmp.getDeptCode());
            if (department != null) {
                currentEmp.setDeptName(department.getDeptName());
            }

            // 수정: 부서장인 경우 하위부서 목록도 포함
            List<Department> availableDepartments;
            if ("Y".equals(currentEmp.getIsHeader())) {
                availableDepartments = attendanceApplyService.getSubDepartments(currentEmp.getDeptCode());
            } else {
                availableDepartments = List.of(department);
            }

            // 수정: 연차잔여 정보 추가
            AnnualDetail annualDetail = attendanceApplyService.getAnnualDetail(empCode);

            // 수정: 근태 마스터 목록 조회
            List<ShiftMaster> shiftMasters = attendanceApplyService.getFilteredShiftMasters();

            // 현재 사용자 정보 추가
            model.addAttribute("currentEmp", currentEmp);
            model.addAttribute("availableDepartments", availableDepartments);
            model.addAttribute("annualDetail", annualDetail);
            model.addAttribute("shiftMasters", shiftMasters);
            model.addAttribute("today", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

            return "user/apply";
        } catch (Exception e) {
            log.error("근태신청 페이지 로드 실패", e);
            throw new RuntimeException("페이지 로드에 실패했습니다.");
        }
    }

    // 부서별 사원 조회 API - 부서장과 일반사원에 따른 다른 로직 적용
    @GetMapping("/employees")
    @ResponseBody
    public List<Employee> getEmployeesByDept(@RequestParam String deptCode,
                                             @RequestParam String workDate,
                                             @RequestParam(required = false) String workPlan,
                                             @AuthenticationPrincipal CustomUserDetails user) {

        try {
            // 파라미터 유효성 검사
            if (deptCode == null || deptCode.trim().isEmpty()) {
                throw new IllegalArgumentException("부서코드가 필요합니다.");
            }

            if (workDate == null || workDate.trim().isEmpty()) {
                throw new IllegalArgumentException("근무일이 필요합니다.");
            }

            // 날짜 형식 검증 (yyyyMMdd)
            if (!workDate.matches("\\d{8}")) {
                throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. (yyyyMMdd)");
            }

            String empCode = user.getUsername();
            Employee currentEmp = attendanceApplyService.getCurrentEmployee(empCode);

            // 현재 사용자 정보 검증
            if (currentEmp == null) {
                throw new RuntimeException("현재 사용자 정보를 찾을 수 없습니다.");
            }

            log.debug("사원 조회 요청: empCode={}, deptCode={}, workDate={}", empCode, deptCode, workDate);

            // 부서장인 경우 부서원 전체, 일반 사원인 경우 본인만 조회
            if ("Y".equals(currentEmp.getIsHeader())) {
                // 부서장: 부서 전체 사원 조회
                return attendanceApplyService.getEmployeesByDept(deptCode, workDate, workPlan);
            } else {
                // 일반 사원: 본인만 조회
                return attendanceApplyService.getCurrentEmployeeList(empCode, workDate);
            }
        } catch (IllegalArgumentException e) {
            log.warn("사원 조회 파라미터 오류: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("사원 조회 중 오류 발생", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "사원 조회에 실패했습니다.");
        }
    }

    // 수정: 연차잔여 조회 API 추가
    @GetMapping("/annual/{empCode}")
    @ResponseBody
    public AnnualDetail getAnnualDetail(@PathVariable String empCode) {
        try {
            return attendanceApplyService.getAnnualDetail(empCode);
        } catch (Exception e) {
            log.error("연차잔여 조회 실패: empCode={}", empCode, e);
            // 기본값 반환
            AnnualDetail defaultDetail = new AnnualDetail();
            defaultDetail.setEmpCode(empCode);
            defaultDetail.setBalanceDay(java.math.BigDecimal.ZERO);
            return defaultDetail;
        }
    }

    // 수정: 근무계획/실적/예상근로시간 조회 API 추가
    @GetMapping("/workInfo/{empCode}/{workDate}")
    @ResponseBody
    public Map<String, Object> getWorkInfo(@PathVariable String empCode, @PathVariable String workDate) {
        try {
            return attendanceApplyService.getWorkInfo(empCode, workDate);
        } catch (Exception e) {
            log.error("근무정보 조회 실패: empCode={}, workDate={}", empCode, workDate, e);
            return Map.of("plan", "", "record", null, "expectedHours", "0");
        }
    }

    // 수정: 근태 마스터 목록 조회 API 추가
    @GetMapping("/shift-masters")
    @ResponseBody
    public List<ShiftMaster> getShiftMasters() {
        try {
            return attendanceApplyService.getFilteredShiftMasters();
        } catch (Exception e) {
            log.error("근태 마스터 조회 실패", e);
            return List.of();
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
            apply.setStatus("저장");

            // 유효한 TIME_ITEM_CODE 조회 후 설정
            String validTimeItemCode = attendanceApplyService.getValidTimeItemCode();
            if (validTimeItemCode != null) {
                apply.setTimeItemCode(validTimeItemCode);
            } else {
                // 기본값 설정
                apply.setTimeItemCode("1010");
            }

            // 신청 유효성 검증
            String validationResult = attendanceApplyService.validateGeneralApply(apply);
            if (!"valid".equals(validationResult)) {
                return validationResult;
            }

            attendanceApplyService.saveGeneralApply(apply);
            return "success";
        } catch (Exception e) {
            log.error("일반근태 저장 실패", e);
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
            apply.setStatus("저장");

            // 신청 유효성 검증
            String validationResult = attendanceApplyService.validateEtcApply(apply);
            if (!"valid".equals(validationResult)) {
                return validationResult;
            }

            attendanceApplyService.saveEtcApply(apply);
            return "success";
        } catch (Exception e) {
            log.error("기타근태 저장 실패", e);
            return "저장에 실패했습니다: " + e.getMessage();
        }
    }

    // 일반근태 신청 상신 API
    @PostMapping("/submit/general")
    @ResponseBody
    public String submitGeneralApply(@RequestParam String applyGeneralNo,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.submitGeneralApply(applyGeneralNo, user.getUsername());
            return "success";
        } catch (Exception e) {
            log.error("일반근태 상신 실패", e);
            return "상신에 실패했습니다: " + e.getMessage();
        }
    }

    // 기타근태 신청 상신 API
    @PostMapping("/submit/etc")
    @ResponseBody
    public String submitEtcApply(@RequestParam String applyEtcNo,
                                 @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.submitEtcApply(applyEtcNo, user.getUsername());
            return "success";
        } catch (Exception e) {
            log.error("기타근태 상신 실패", e);
            return "상신에 실패했습니다: " + e.getMessage();
        }
    }

    // 일반근태 신청 상신취소 API
    @PostMapping("/cancel/general")
    @ResponseBody
    public String cancelGeneralApply(@RequestParam String applyGeneralNo,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.cancelGeneralApply(applyGeneralNo, user.getUsername());
            return "success";
        } catch (Exception e) {
            log.error("일반근태 상신취소 실패", e);
            return "상신취소에 실패했습니다: " + e.getMessage();
        }
    }

    // 수정: 기타근태 신청 상신취소 API
    @PostMapping("/cancel/etc")
    @ResponseBody
    public String cancelEtcApply(@RequestParam String applyEtcNo,
                                 @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.cancelEtcApply(applyEtcNo, user.getUsername());
            return "success";
        } catch (Exception e) {
            log.error("기타근태 상신취소 실패", e);
            return "상신취소에 실패했습니다: " + e.getMessage();
        }
    }

    // 일반근태 신청 삭제 API
    @PostMapping("/delete/general")
    @ResponseBody
    public String deleteGeneralApply(@RequestParam String applyGeneralNo,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.deleteGeneralApply(applyGeneralNo, user.getUsername());
            return "success";
        } catch (Exception e) {
            log.error("일반근태 삭제 실패", e);
            return "삭제에 실패했습니다: " + e.getMessage();
        }
    }

    // 기타근태 신청 삭제 API
    @PostMapping("/delete/etc")
    @ResponseBody
    public String deleteEtcApply(@RequestParam String applyEtcNo,
                                 @AuthenticationPrincipal CustomUserDetails user) {
        try {
            attendanceApplyService.deleteEtcApply(applyEtcNo, user.getUsername());
            return "success";
        } catch (Exception e) {
            log.error("기타근태 삭제 실패", e);
            return "삭제에 실패했습니다: " + e.getMessage();
        }
    }
}
