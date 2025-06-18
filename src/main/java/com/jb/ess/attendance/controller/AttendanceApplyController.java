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
import java.util.HashMap;

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

            // 부서장인 경우 하위부서 목록도 포함
            List<Department> availableDepartments;
            if ("Y".equals(currentEmp.getIsHeader())) {
                availableDepartments = attendanceApplyService.getSubDepartments(currentEmp.getDeptCode());
            } else {
                availableDepartments = List.of(department);
            }

            // 연차잔여 정보
            AnnualDetail annualDetail = attendanceApplyService.getAnnualDetail(empCode);

            // 근태 마스터 목록 조회
            List<ShiftMaster> shiftMasters = attendanceApplyService.getFilteredShiftMasters();

            // 현재 사용자 정보
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

    // 부서별 사원 조회 API
    @GetMapping("/employees")
    @ResponseBody
    public List<Employee> getEmployeesByDept(@RequestParam String deptCode,
                                             @RequestParam String workDate,
                                             @RequestParam(required = false) String workPlan,
                                             @RequestParam(required = false) String sortBy,
                                             @RequestParam(required = false) String applyTypeCategory,
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

            log.debug("사원 조회 요청: empCode={}, deptCode={}, workDate={}, sortBy={}, applyTypeCategory={}",
                    empCode, deptCode, workDate, sortBy, applyTypeCategory);

            String finalSortBy = (sortBy != null && !sortBy.trim().isEmpty()) ? sortBy : "position,empCode,empName";

            // 부서장인 경우 부서원 전체, 일반 사원인 경우 본인만 조회
            if ("Y".equals(currentEmp.getIsHeader())) {
                // 근태신청종류별 필터링
                if (applyTypeCategory != null && !applyTypeCategory.trim().isEmpty()) {
                    // 근태신청종류가 지정된 경우 필터링된 조회
                    return attendanceApplyService.getEmployeesByDeptWithApplyType(deptCode, workDate, workPlan, finalSortBy, applyTypeCategory);
                } else {
                    // 근태신청종류가 지정되지 않은 경우 전체 조회
                    return attendanceApplyService.getEmployeesByDept(deptCode, workDate, workPlan, finalSortBy);
                }
            } else {
                // 근태신청종류별 필터링 적용
                if (applyTypeCategory != null && !applyTypeCategory.trim().isEmpty()) {
                    // 근태신청종류가 지정된 경우 필터링된 조회
                    return attendanceApplyService.getCurrentEmployeeListWithApplyType(empCode, workDate, applyTypeCategory);
                } else {
                    // 근태신청종류가 지정되지 않은 경우 전체 조회
                    return attendanceApplyService.getCurrentEmployeeList(empCode, workDate);
                }
            }
        } catch (IllegalArgumentException e) {
            log.warn("사원 조회 파라미터 오류: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("사원 조회 중 오류 발생", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "사원 조회에 실패했습니다.");
        }
    }

    // 기타근태 날짜 범위 검증 API
    @GetMapping("/validateDateRange")
    @ResponseBody
    public Map<String, Object> validateDateRange(@RequestParam String startDate, @RequestParam String endDate) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean isValid = attendanceApplyService.validateDateRange(startDate, endDate);
            response.put("valid", isValid);
            if (!isValid) {
                response.put("message", "신청 기간에 휴일/휴무일이 포함되어 있습니다.");
            }
            return response;
        } catch (Exception e) {
            log.error("날짜 범위 검증 실패: startDate={}, endDate={}", startDate, endDate, e);
            response.put("valid", false);
            response.put("message", "날짜 범위 검증 중 오류가 발생했습니다.");
            return response;
        }
    }

    // 연차잔여 조회 API
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

    // 근무계획/실적/예상근로시간 조회 API
    @GetMapping("/workInfo/{empCode}/{workDate}")
    @ResponseBody
    public Map<String, Object> getWorkInfo(@PathVariable String empCode, @PathVariable String workDate) {
        try {
            return attendanceApplyService.getWorkInfoWithEmpCalendar(empCode, workDate);
        } catch (Exception e) {
            log.error("근무정보 조회 실패: empCode={}, workDate={}", empCode, workDate, e);
            return Map.of(
                    "plan", "",
                    "empCalendarPlan", "",
                    "record", Map.of("checkInTime", "-", "checkOutTime", "-", "shiftCode", "", "shiftName", ""),
                    "appliedRecord", null,
                    "expectedHours", "40.00"  // 주간 예상근로시간 기본값
            );
        }
    }

    // 근태 마스터 목록 조회 API
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

    // 저장된 일반근태 신청 조회 API
    @GetMapping("/general/{applyGeneralNo}")
    @ResponseBody
    public AttendanceApplyGeneral getSavedGeneralApply(@PathVariable String applyGeneralNo) {
        try {
            return attendanceApplyService.getSavedGeneralApply(applyGeneralNo);
        } catch (Exception e) {
            log.error("저장된 일반근태 신청 조회 실패: applyGeneralNo={}", applyGeneralNo, e);
            return null;
        }
    }

    // 저장된 기타근태 신청 조회 API
    @GetMapping("/etc/{applyEtcNo}")
    @ResponseBody
    public AttendanceApplyEtc getSavedEtcApply(@PathVariable String applyEtcNo) {
        try {
            return attendanceApplyService.getSavedEtcApply(applyEtcNo);
        } catch (Exception e) {
            log.error("저장된 기타근태 신청 조회 실패: applyEtcNo={}", applyEtcNo, e);
            return null;
        }
    }

    // 일반근태 신청 저장 API
    @PostMapping("/general")
    @ResponseBody
    public Map<String, Object> saveGeneralApply(@RequestBody AttendanceApplyGeneral apply,
                                                @AuthenticationPrincipal CustomUserDetails user) {
        Map<String, Object> response = new HashMap<>();
        try {
            apply.setApplicantCode(user.getUsername());
            apply.setApplyDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            apply.setStatus("저장");

            // 결근 사원 체크
            boolean isAbsent = attendanceApplyService.isEmployeeAbsent(apply.getEmpCode(), apply.getTargetDate());
            if (isAbsent) {
                response.put("result", "error");
                response.put("message", "결근 사원은 근태 신청을 할 수 없습니다.");
                return response;
            }

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
                response.put("result", "error");
                response.put("message", validationResult);
                return response;
            }

            attendanceApplyService.saveGeneralApply(apply);

            // 휴일근로 신청 후 실적 업데이트 처리
            if ("휴일근무".equals(apply.getApplyType())) {
                attendanceApplyService.updateWorkRecordForHolidayWork(apply.getEmpCode(), apply.getTargetDate());
            }

            // 저장된 데이터 조회 및 반환
            AttendanceApplyGeneral savedApply = attendanceApplyService.getSavedGeneralApply(apply.getApplyGeneralNo());
            response.put("result", "success");
            response.put("message", "저장되었습니다.");
            response.put("data", savedApply);

            return response;
        } catch (Exception e) {
            log.error("일반근태 저장 실패", e);
            response.put("result", "error");
            response.put("message", "저장에 실패했습니다: " + e.getMessage());
            return response;
        }
    }

    // 기타근태 신청 저장 API
    @PostMapping("/etc")
    @ResponseBody
    public Map<String, Object> saveEtcApply(@RequestBody AttendanceApplyEtc apply,
                                            @AuthenticationPrincipal CustomUserDetails user) {
        Map<String, Object> response = new HashMap<>();
        try {
            apply.setApplicantCode(user.getUsername());
            apply.setApplyDate(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            apply.setStatus("저장");

            // 결근 사원 체크
            boolean isAbsent = attendanceApplyService.isEmployeeAbsent(apply.getEmpCode(), apply.getTargetStartDate());
            if (isAbsent) {
                response.put("result", "error");
                response.put("message", "결근 사원은 근태 신청을 할 수 없습니다.");
                return response;
            }

            // 신청 유효성 검증
            String validationResult = attendanceApplyService.validateEtcApply(apply);
            if (!"valid".equals(validationResult)) {
                response.put("result", "error");
                response.put("message", validationResult);
                return response;
            }

            attendanceApplyService.saveEtcApply(apply);

            // 연차/반차 신청 후 실적 업데이트
            if (apply.getShiftCode() != null) {
                attendanceApplyService.updateWorkRecordForAnnualLeave(apply.getEmpCode(), apply.getTargetStartDate(), apply.getShiftCode());
            }

            // 저장된 데이터 조회 및 반환
            AttendanceApplyEtc savedApply = attendanceApplyService.getSavedEtcApply(apply.getApplyEtcNo());
            response.put("result", "success");
            response.put("message", "저장되었습니다.");
            response.put("data", savedApply);

            return response;
        } catch (Exception e) {
            log.error("기타근태 저장 실패", e);
            response.put("result", "error");
            response.put("message", "저장에 실패했습니다: " + e.getMessage());
            return response;
        }
    }

    // 일반근태 신청 상신 API
    @PostMapping("/submit/general")
    @ResponseBody
    public String submitGeneralApply(@RequestParam String applyGeneralNo,
                                     @RequestParam(required = false) String isHeader,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        try {
            AttendanceApplyGeneral apply = attendanceApplyService.getSavedGeneralApply(applyGeneralNo);
            if (apply != null) {
                boolean isAbsent = attendanceApplyService.isEmployeeAbsent(apply.getEmpCode(), apply.getTargetDate());
                if (isAbsent) {
                    return "결근 사원은 근태 신청을 할 수 없습니다.";
                }
            }

            attendanceApplyService.submitGeneralApply(applyGeneralNo, user.getUsername(), isHeader);
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
                                 @RequestParam(required = false) String isHeader,
                                 @AuthenticationPrincipal CustomUserDetails user) {
        try {
            AttendanceApplyEtc apply = attendanceApplyService.getSavedEtcApply(applyEtcNo);
            if (apply != null) {
                boolean isAbsent = attendanceApplyService.isEmployeeAbsent(apply.getEmpCode(), apply.getTargetStartDate());
                if (isAbsent) {
                    return "결근 사원은 근태 신청을 할 수 없습니다.";
                }
            }

            attendanceApplyService.submitEtcApply(applyEtcNo, user.getUsername(), isHeader);
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

    // 기타근태 신청 상신취소 API
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

    // 실시간 주 52시간 검증 API
    @PostMapping("/calculateWorkHours")
    @ResponseBody
    public Map<String, Object> calculateRealTimeWorkHours(@RequestParam String empCode,
                                                          @RequestParam String workDate,
                                                          @RequestParam(required = false) String startTime,
                                                          @RequestParam(required = false) String endTime,
                                                          @RequestParam String applyType) {
        try {
            log.debug("실시간 주 52시간 계산 요청: empCode={}, workDate={}, startTime={}, endTime={}, applyType={}",
                    empCode, workDate, startTime, endTime, applyType);

            return attendanceApplyService.calculateRealTimeWeeklyHours(empCode, workDate, startTime, endTime, applyType);
        } catch (Exception e) {
            log.error("실시간 주 52시간 계산 실패: empCode={}, workDate={}", empCode, workDate, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("totalWeeklyHours", 40.0);
            errorResponse.put("requestHours", 0.0);
            errorResponse.put("isValid", true);
            errorResponse.put("message", "계산 오류");
            return errorResponse;
        }
    }

    // 조출연장 시간 제한 검증 API
    @PostMapping("/validateEarlyOvertimeTime")
    @ResponseBody
    public Map<String, Object> validateEarlyOvertimeTime(@RequestParam String startTime) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean isValid = true;
            String message = "정상";

            if (startTime != null && !startTime.isEmpty()) {
                try {
                    // HH:MM 형식을 HHMM으로 변환
                    String timeStr = startTime.replace(":", "");
                    int timeInt = Integer.parseInt(timeStr);

                    if (timeInt >= 730) {
                        isValid = false;
                        message = "조출연장은 07:30 이전에만 신청할 수 있습니다.";
                    }
                } catch (NumberFormatException e) {
                    isValid = false;
                    message = "시간 형식이 올바르지 않습니다.";
                }
            }

            response.put("isValid", isValid);
            response.put("message", message);
            return response;
        } catch (Exception e) {
            log.error("조출연장 시간 검증 실패: startTime={}", startTime, e);
            response.put("isValid", false);
            response.put("message", "검증 중 오류가 발생했습니다.");
            return response;
        }
    }

    // 일반연장 시간 제한 검증 API
    @PostMapping("/validateRegularOvertimeTime")
    @ResponseBody
    public Map<String, Object> validateRegularOvertimeTime(@RequestParam String startTime) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean isValid = true;
            String message = "정상";

            if (startTime != null && !startTime.isEmpty()) {
                try {
                    // HH:MM 형식을 HHMM으로 변환
                    String timeStr = startTime.replace(":", "");
                    int timeInt = Integer.parseInt(timeStr);

                    if (timeInt < 1620) {
                        isValid = false;
                        message = "정상근무시간(16:20) 이후에만 연장근무를 신청할 수 있습니다.";
                    }
                } catch (NumberFormatException e) {
                    isValid = false;
                    message = "시간 형식이 올바르지 않습니다.";
                }
            }

            response.put("isValid", isValid);
            response.put("message", message);
            return response;
        } catch (Exception e) {
            log.error("일반연장 시간 검증 실패: startTime={}", startTime, e);
            response.put("isValid", false);
            response.put("message", "검증 중 오류가 발생했습니다.");
            return response;
        }
    }

    // 신청근무별 기존 신청 조회 API
    @GetMapping("/getApplyByType/{empCode}/{workDate}/{applyType}")
    @ResponseBody
    public Map<String, Object> getApplyByType(@PathVariable String empCode,
                                              @PathVariable String workDate,
                                              @PathVariable String applyType) {
        Map<String, Object> response = new HashMap<>();
        try {
            log.debug("신청근무별 기존 신청 조회: empCode={}, workDate={}, applyType={}", empCode, workDate, applyType);

            // 연장근로, 조출연장, 휴일근무, 조퇴, 외출, 전반차, 후반차는 일반근태에서 조회
            if ("연장".equals(applyType) || "조출연장".equals(applyType) || "휴일근무".equals(applyType) ||
                    "조퇴".equals(applyType) || "외근".equals(applyType) || "외출".equals(applyType) ||
                    "전반차".equals(applyType) || "후반차".equals(applyType)) {

                AttendanceApplyGeneral generalApply = attendanceApplyService.findGeneralApplyByEmpAndDate(empCode, workDate);
                if (generalApply != null && applyType.equals(generalApply.getApplyType())) {
                    response.put("hasExisting", true);
                    response.put("applyType", "general");
                    response.put("applyNo", generalApply.getApplyGeneralNo());
                    response.put("status", generalApply.getStatus());
                    response.put("startTime", generalApply.getStartTime());
                    response.put("endTime", generalApply.getEndTime());
                    response.put("reason", generalApply.getReason());
                } else {
                    response.put("hasExisting", false);
                    response.put("applyType", "general");
                }
            } else {
                // 연차, 반차 등은 기타근태에서 조회
                AttendanceApplyEtc etcApply = attendanceApplyService.findEtcApplyByEmpAndDate(empCode, workDate);
                if (etcApply != null) {
                    response.put("hasExisting", true);
                    response.put("applyType", "etc");
                    response.put("applyNo", etcApply.getApplyEtcNo());
                    response.put("status", etcApply.getStatus());
                    response.put("reason", etcApply.getReason());
                } else {
                    response.put("hasExisting", false);
                    response.put("applyType", "etc");
                }
            }

            return response;
        } catch (Exception e) {
            log.error("신청근무별 기존 신청 조회 실패: empCode={}, workDate={}, applyType={}", empCode, workDate, applyType, e);
            response.put("hasExisting", false);
            response.put("message", "조회 중 오류가 발생했습니다.");
            return response;
        }
    }

    // 예상근로시간 실시간 업데이트 API
    @GetMapping("/updateExpectedHours/{empCode}/{workDate}")
    @ResponseBody
    public Map<String, Object> updateExpectedHours(@PathVariable String empCode, @PathVariable String workDate) {
        try {
            log.debug("예상근로시간 실시간 업데이트: empCode={}, workDate={}", empCode, workDate);

            Map<String, Object> workInfo = attendanceApplyService.getWorkInfoWithEmpCalendar(empCode, workDate);

            Map<String, Object> response = new HashMap<>();
            response.put("expectedHours", workInfo.get("expectedHours"));
            response.put("success", true);

            return response;
        } catch (Exception e) {
            log.error("예상근로시간 실시간 업데이트 실패: empCode={}, workDate={}", empCode, workDate, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("expectedHours", "40.00");
            errorResponse.put("success", false);
            errorResponse.put("message", "업데이트 실패");
            return errorResponse;
        }
    }

    // 전반차/후반차 시간 입력 제한 검증 API
    @GetMapping("/validateHalfDayApply/{applyType}")
    @ResponseBody
    public Map<String, Object> validateHalfDayApply(@PathVariable String applyType) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean timeInputDisabled = "전반차".equals(applyType) || "후반차".equals(applyType);
            String message = timeInputDisabled ? "반차는 시간을 입력할 수 없습니다." : "정상";

            response.put("timeInputDisabled", timeInputDisabled);
            response.put("message", message);
            response.put("deductHours", timeInputDisabled ? 4.0 : 0.0); // 반차는 4시간 차감

            return response;
        } catch (Exception e) {
            log.error("반차 검증 실패: applyType={}", applyType, e);
            response.put("timeInputDisabled", false);
            response.put("message", "검증 중 오류가 발생했습니다.");
            return response;
        }
    }

    // 조퇴 시간 입력 제한 검증 API
    @GetMapping("/validateEarlyLeaveApply/{applyType}")
    @ResponseBody
    public Map<String, Object> validateEarlyLeaveApply(@PathVariable String applyType) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean endTimeDisabled = "조퇴".equals(applyType);
            String message = endTimeDisabled ? "조퇴는 시작시간만 입력할 수 있습니다." : "정상";

            response.put("endTimeDisabled", endTimeDisabled);
            response.put("message", message);

            return response;
        } catch (Exception e) {
            log.error("조퇴 검증 실패: applyType={}", applyType, e);
            response.put("endTimeDisabled", false);
            response.put("message", "검증 중 오류가 발생했습니다.");
            return response;
        }
    }
}
