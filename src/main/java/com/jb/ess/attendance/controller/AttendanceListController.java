package com.jb.ess.attendance.controller;

import com.jb.ess.attendance.service.EmpAttService;
import com.jb.ess.common.domain.Department;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.mapper.DepartmentMapper;
import com.jb.ess.common.mapper.EmpCalendarMapper;
import com.jb.ess.common.mapper.EmployeeMapper;
import com.jb.ess.common.mapper.ShiftMasterMapper;
import com.jb.ess.common.security.CustomUserDetails;
import com.jb.ess.pattern.service.PatternService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import jakarta.servlet.http.HttpSession;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/user/attendance")
@RequiredArgsConstructor
/* 부서근태조회 */
public class AttendanceListController {
    private final PatternService patternService;
    private final EmpAttService empAttService;
    private final DepartmentMapper departmentMapper;
    private final EmpCalendarMapper empCalendarMapper;
    private final ShiftMasterMapper shiftMasterMapper;
    private final EmployeeMapper employeeMapper;

    @GetMapping("/list")
    public String attendanceList(@AuthenticationPrincipal CustomUserDetails user,
        @RequestParam(value = "empCode", required = false) String empCode,
        @RequestParam(value = "workDate", required = false) LocalDate workDate,
        @RequestParam(value = "deptCode", required = false) String deptCode,
        @RequestParam(value = "weekStart", required = false) LocalDate weekStart,
        @RequestParam(value = "weekEnd", required = false) LocalDate weekEnd,
        @RequestParam(value = "planType", required = false) String planType,
        Model model, HttpSession session) {

        // 날짜 기본값 설정
        if (workDate == null) workDate = LocalDate.now();
        if (weekStart == null) weekStart = workDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (weekEnd == null) weekEnd = workDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        // 부서코드 기본값 설정
        if (deptCode == null || deptCode.isEmpty()) {
            deptCode = empAttService.empDepartmentInfo(user.getUsername()).getDeptCode();
        }

        // 미래날짜의 경우 패턴 캘린더 자동 생성
        if (workDate.isAfter(LocalDate.now())) {
            String yyyymm = workDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
            String workPatternCode = departmentMapper.findWorkPatternCodeByDeptCode(deptCode);
            String yyyymmWithDash = workDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));

            if (Boolean.TRUE.equals(patternService.findShiftCalendar(workPatternCode, yyyymm))){
                patternService.generateShiftCalendar(workPatternCode, YearMonth.parse(yyyymmWithDash));
            }

            if (Boolean.TRUE.equals(patternService.findEmpCalendar(workPatternCode, yyyymm))) {
                patternService.generateEmpCalendar(workPatternCode, YearMonth.parse(yyyymmWithDash));
            }
        }

        // 사원 목록 조회
        String workDateStr = workDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        List<Employee> empList = (empCode == null || empCode.isEmpty())
            ? empAttService.empAttendanceList(deptCode, workDateStr, shiftMasterMapper.findShiftCodeByPlanType(planType))
            : empAttService.empAttendance(empCode, workDateStr, shiftMasterMapper.findShiftCodeByPlanType(planType));

        // 근태 정보 세팅
        empList = empAttService.setAttendanceInfo(empList, weekStart, weekEnd, workDate);

        model.addAttribute("workDate", workDate);
        model.addAttribute("empCode", empCode);
        model.addAttribute("deptCode", deptCode);
        model.addAttribute("employees", empList);
        model.addAttribute("planType", planType);
        // 계획/실적
        model.addAttribute("shiftList", empCalendarMapper.getShiftCodeByDeptCodeAndWorkDate(deptCode, workDateStr));

        // 부서 목록 - 매 요청시 최신화 (세션 캐싱 제거)
        String loginDeptCode = empAttService.empDepartmentInfo(user.getUsername()).getDeptCode();
        List<Department> departments = empAttService.childDepartmentList(loginDeptCode);
        session.setAttribute("departments", departments);

        model.addAttribute("departments", departments);

        model.addAttribute("user", user.getUsername());
        if (Objects.equals(employeeMapper.findIsHeader(user.getUsername()).getIsHeader(), "Y")) {
            model.addAttribute("isHeader", true);
        } else model.addAttribute("isHeader", false);

        return "user/attendance/list";
    }
}
